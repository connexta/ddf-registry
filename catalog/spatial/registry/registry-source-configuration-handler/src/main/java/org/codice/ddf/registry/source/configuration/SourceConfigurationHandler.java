/**
 * Copyright (c) Codice Foundation
 * <p>
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package org.codice.ddf.registry.source.configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import javax.xml.bind.JAXBElement;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.codice.ddf.parser.Parser;
import org.codice.ddf.parser.ParserConfigurator;
import org.codice.ddf.parser.ParserException;
import org.codice.ddf.registry.common.RegistryConstants;
import org.codice.ddf.registry.common.metacard.RegistryObjectMetacardType;
import org.codice.ddf.registry.federationadmin.service.FederationAdminException;
import org.codice.ddf.registry.federationadmin.service.FederationAdminService;
import org.codice.ddf.registry.schemabindings.RegistryPackageUtils;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.service.metatype.AttributeDefinition;
import org.osgi.service.metatype.MetaTypeInformation;
import org.osgi.service.metatype.MetaTypeService;
import org.osgi.service.metatype.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;

import ddf.catalog.data.Attribute;
import ddf.catalog.data.Metacard;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryObjectType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryPackageType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ServiceBindingType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.SlotType1;

public class SourceConfigurationHandler implements EventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SourceConfigurationHandler.class);

    private static final String METACARD_PROPERTY = "ddf.catalog.event.metacard";

    private static final String BINDING_TYPE = "bindingType";

    private static final String DISABLED_CONFIGURATION_SUFFIX = "_disabled";

    private static final String ID = "id";

    private static final String SHORTNAME = "shortname";

    private static final String CONFIGURATION_FILTER =
            "(&(%s=%s)(|(service.factoryPid=*source*)(service.factoryPid=*Source*)(service.factoryPid=*service*)(service.factoryPid=*Service*)))";

    private static final int SHUTDOWN_TIMEOUT_SECONDS = 60;

    private ConfigurationAdmin configurationAdmin;

    private FederationAdminService federationAdminService;

    private MetaTypeService metaTypeService;

    private Parser parser;

    private ParserConfigurator unmarshalConfigurator;

    private ExecutorService executor;

    private String urlBindingName;

    private Map<String, String> bindingTypeToFactoryPidMap = new HashMap<>();

    private List<String> sourceActivationPriorityOrder = new ArrayList<>();

    private Boolean activateConfigurations;

    private Boolean preserveActiveConfigurations;

    private Boolean cleanUpOnDelete;

    public SourceConfigurationHandler(FederationAdminService federationAdminService,
            ExecutorService executor) {
        this.federationAdminService = federationAdminService;
        this.executor = executor;
    }

    public void destroy() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    LOGGER.error("Thread pool didn't terminate");
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }

    @Override
    public void handleEvent(Event event) {
        Metacard mcard = (Metacard) event.getProperty(METACARD_PROPERTY);
        if (mcard == null || !mcard.getTags()
                .contains(RegistryConstants.REGISTRY_TAG)) {
            return;
        }

        executor.execute(() -> {
            if (event.getTopic()
                    .equals("ddf/catalog/event/CREATED")) {
                processCreate(mcard);
            } else if (event.getTopic()
                    .equals("ddf/catalog/event/UPDATED")) {
                processUpdate(mcard);
            } else if (event.getTopic()
                    .equals("ddf/catalog/event/DELETED")) {
                processDelete(mcard);
            }
        });

    }

    protected void processCreate(Metacard metacard) {
        try {
            updateRegistryConfigurations(metacard, true);
        } catch (IOException | InvalidSyntaxException | ParserException e) {
            LOGGER.error("Unable to update registry configurations, metacard still ingested");
        }
    }

    protected void processUpdate(Metacard metacard) {
        try {
            updateRegistryConfigurations(metacard, false);
        } catch (IOException | InvalidSyntaxException | ParserException e) {
            LOGGER.error("Unable to update registry configurations, metacard still updated");
        }
    }

    protected void processDelete(Metacard metacard) {
        try {
            if (cleanUpOnDelete) {
                deleteRegistryConfigurations(metacard);
            }
        } catch (IOException | InvalidSyntaxException e) {
            LOGGER.error("Unable to delete registry configurations, metacard still deleted");
        }
    }

    /**
     * Finds all source configurations associated with the registry metacard creates/updates them
     * with the information in the metacards service bindings. This method will enable/disable
     * configurations if the right conditions are met but will never delete a configuration other
     * than for switching a configuration from enabled to disabled or vic-versa
     * @param metacard Registry metacard with new service binding info
     * @param createEvent Flag indicating if this was a metacard create or update event
     * @throws IOException
     * @throws InvalidSyntaxException
     * @throws ParserException
     */
    private void updateRegistryConfigurations(Metacard metacard, boolean createEvent)
            throws IOException, InvalidSyntaxException, ParserException {

        boolean identityNode =
                metacard.getAttribute(RegistryObjectMetacardType.REGISTRY_IDENTITY_NODE) != null;

        boolean autoActivateConfigurations = activateConfigurations && !identityNode && (createEvent
                || !preserveActiveConfigurations);

        List<ServiceBindingType> bindingTypes = getServiceBindings(metacard);

        String registryId = metacard.getAttribute(RegistryObjectMetacardType.REGISTRY_ID)
                .getValue()
                .toString();

        String configId = getDeconflictedConfigId(metacard.getTitle(), registryId);

        Map<String, Configuration> fpidToConfigurationMap = getCurrentConfigurations(registryId);

        String bindingTypeToActivate = getBindingTypeToActivate(bindingTypes);
        String fPidToActivate = bindingTypeToFactoryPidMap.get(bindingTypeToActivate);

        if (autoActivateConfigurations) {
            String fpid;
            Configuration configToEnable = null;
            Configuration configToDisable = null;
            for (Map.Entry<String, Configuration> entry : fpidToConfigurationMap.entrySet()) {
                fpid = entry.getKey();
                if (!fpid.contains(DISABLED_CONFIGURATION_SUFFIX) && !fpid.equals(fPidToActivate)) {
                    configToDisable = entry.getValue();
                } else if (fpid.equals(fPidToActivate.concat(DISABLED_CONFIGURATION_SUFFIX))) {
                    configToEnable = entry.getValue();
                }
            }
            //Order of disable/enable important. Can't have two enabled configurations with the same
            //id so if there is one to disable, disable it before enabling another one.
            if (configToDisable != null) {
                fpidToConfigurationMap.remove(configToDisable.getFactoryPid());
                Configuration config = toggleConfiguration(configToDisable);
                fpidToConfigurationMap.put(config.getFactoryPid(), config);
            }
            if (configToEnable != null) {
                fpidToConfigurationMap.remove(configToEnable.getFactoryPid());
                Configuration config = toggleConfiguration(configToEnable);
                fpidToConfigurationMap.put(config.getFactoryPid(), config);
            }
        }

        for (ServiceBindingType bindingType : bindingTypes) {
            Map<String, Object> slotMap = this.getServiceBindingProperties(bindingType);

            Hashtable<String, Object> serviceConfigurationProperties = new Hashtable<>();

            if (slotMap.get(BINDING_TYPE) == null) {
                continue;
            }

            String factoryPidMask = slotMap.get(BINDING_TYPE)
                    .toString();
            String factoryPid = bindingTypeToFactoryPidMap.get(factoryPidMask);

            if (StringUtils.isBlank(factoryPid)) {
                continue;
            }

            String factoryPidDisabled = factoryPid.concat(DISABLED_CONFIGURATION_SUFFIX);
            Configuration curConfig = fpidToConfigurationMap.get(factoryPid);
            if (curConfig == null) {
                curConfig = fpidToConfigurationMap.get(factoryPidDisabled);
            }

            if (curConfig == null) {
                serviceConfigurationProperties.putAll(getMetatypeDefaults(factoryPid));
                String pid = factoryPidDisabled;
                if (autoActivateConfigurations && factoryPidMask.equals(bindingTypeToActivate)) {
                    pid = factoryPid;
                }
                curConfig = configurationAdmin.createFactoryConfiguration(pid, null);
            } else {
                serviceConfigurationProperties.putAll(getConfigurationsFromDictionary(curConfig.getProperties()));
            }

            serviceConfigurationProperties.putAll(slotMap);
            serviceConfigurationProperties.put(ID, configId);
            serviceConfigurationProperties.put(SHORTNAME, configId);
            serviceConfigurationProperties.put(RegistryObjectMetacardType.REGISTRY_ID, registryId);

            curConfig.update(serviceConfigurationProperties);
        }

    }

    private void deleteRegistryConfigurations(Metacard metacard)
            throws IOException, InvalidSyntaxException {
        String registryId = null;
        Attribute registryIdAttribute =
                metacard.getAttribute(RegistryObjectMetacardType.REGISTRY_ID);
        if (registryIdAttribute != null) {
            registryId = registryIdAttribute.getValue()
                    .toString();
        }

        Configuration[] configurations = configurationAdmin.listConfigurations(String.format(
                CONFIGURATION_FILTER,
                RegistryObjectMetacardType.REGISTRY_ID,
                registryId));
        if (configurations != null) {
            for (Configuration configuration : configurations) {
                configuration.delete();
            }
        }
    }

    /**
     * Toggles a configuration between enabled and disabled.
     *
     * @param config The configuration to enable/disable
     * @return The new enabled/disabled configuration
     * @throws IOException
     */
    private Configuration toggleConfiguration(Configuration config) throws IOException {
        String newFpid;
        if (config.getFactoryPid()
                .contains(DISABLED_CONFIGURATION_SUFFIX)) {
            newFpid = config.getFactoryPid()
                    .replace(DISABLED_CONFIGURATION_SUFFIX, "");
        } else {
            newFpid = config.getFactoryPid()
                    .concat(DISABLED_CONFIGURATION_SUFFIX);
        }
        Dictionary<String, Object> properties = config.getProperties();
        config.delete();
        Configuration newConfig = configurationAdmin.createFactoryConfiguration(newFpid, null);
        newConfig.update(properties);
        return newConfig;
    }

    /**
     * Returns the service binding slots as a map of string properties with the slot name as the key
     *
     * @param binding ServiceBindingType to generate the map from
     * @return A map of service binding slots
     */
    private Map<String, Object> getServiceBindingProperties(ServiceBindingType binding) {
        Map<String, Object> properties = new HashMap<>();
        for (SlotType1 slot : binding.getSlot()) {
            List<String> slotValues = RegistryPackageUtils.getSlotStringValues(slot);
            if (CollectionUtils.isEmpty(slotValues)) {
                continue;
            }
            properties.put(slot.getName(), slotValues.size() == 1 ? slotValues.get(0) : slotValues);
        }
        if (binding.isSetAccessURI() && properties.get(urlBindingName) != null) {
            properties.put(properties.get(urlBindingName)
                    .toString(), binding.getAccessURI());
        }
        return properties;
    }

    /**
     * Gets all the configurations that have a matching registry id
     *
     * @param registryId The registry id to match
     * @return A map of configurations with factory pids as keys
     * @throws IOException
     * @throws InvalidSyntaxException
     */
    private Map<String, Configuration> getCurrentConfigurations(String registryId)
            throws IOException, InvalidSyntaxException {
        Map<String, Configuration> configurationMap = new HashMap<>();
        Configuration[] configurations = configurationAdmin.listConfigurations(String.format(
                CONFIGURATION_FILTER,
                RegistryObjectMetacardType.REGISTRY_ID,
                registryId));

        if (configurations == null) {
            return configurationMap;
        }

        for (Configuration config : configurations) {
            configurationMap.put(config.getFactoryPid(), config);
        }

        return configurationMap;
    }

    /**
     * Unmarshal the metacards metadata and extracts the service bindings
     *
     * @param metacard Metacard to extract bindings from
     * @return List of ServiceBindingType
     * @throws ParserException
     */
    private List<ServiceBindingType> getServiceBindings(Metacard metacard) throws ParserException {
        String metadata = metacard.getMetadata();
        InputStream inputStream = new ByteArrayInputStream(metadata.getBytes(Charsets.UTF_8));
        JAXBElement<RegistryObjectType> registryObjectTypeJAXBElement = parser.unmarshal(
                unmarshalConfigurator,
                JAXBElement.class,
                inputStream);

        RegistryPackageType registryPackage =
                (RegistryPackageType) registryObjectTypeJAXBElement.getValue();

        return RegistryPackageUtils.getBindingTypes(registryPackage);

    }

    /**
     * Gets the deconflicted configuration id. Checks for current configurations with the same ID
     * and if present makes sure they belong to this registry entry. If configuration not present or
     * present with the same registryId then the id passed in will be returned. If configuration is
     * present and does not have the same registry id then return a new id that is a combination of
     * the id and registry id which is guaranteed to be unique.
     *
     * @param id         Initial configuration ID
     * @param registryId Associated registry ID
     * @return A unique valid configuration ID
     * @throws IOException
     * @throws InvalidSyntaxException
     */
    private String getDeconflictedConfigId(String id, String registryId)
            throws IOException, InvalidSyntaxException {
        String configId = id;
        if (StringUtils.isEmpty(configId)) {
            configId = registryId;
        }

        Configuration[] configurations = configurationAdmin.listConfigurations(String.format(
                "(id=%s)",
                configId));

        if (configurations == null) {
            return configId;
        }

        String configRegistryId = (String) configurations[0].getProperties()
                .get(RegistryObjectMetacardType.REGISTRY_ID);

        if (configRegistryId == null || !configRegistryId.equals(registryId)) {
            configId = String.format("%s - %s", configId, registryId);
        }
        return configId;
    }

    private String getBindingTypeToActivate(List<ServiceBindingType> bindingTypes) {
        String bindingTypeToActivate = null;
        String topPriority = sourceActivationPriorityOrder.get(0);
        List<String> bindingTypesNames = new ArrayList<>();

        for (ServiceBindingType bindingType : bindingTypes) {
            Map<String, Object> slotMap = this.getServiceBindingProperties(bindingType);

            if (slotMap.get(BINDING_TYPE) == null) {
                continue;
            }

            String factoryPidMask = slotMap.get(BINDING_TYPE)
                    .toString();

            if (StringUtils.isNotBlank(factoryPidMask)) {
                if (factoryPidMask.equals(topPriority)) {
                    return factoryPidMask;
                }

                bindingTypesNames.add(factoryPidMask);
            }
        }

        for (String prioritySource : sourceActivationPriorityOrder.subList(1,
                sourceActivationPriorityOrder.size())) {
            if (bindingTypesNames.contains(prioritySource)) {
                return prioritySource;
            }
        }

        return bindingTypeToActivate;
    }

    private Hashtable<String, Object> getConfigurationsFromDictionary(
            Dictionary<String, Object> properties) {
        Hashtable<String, Object> configProperties = new Hashtable<>();

        Enumeration<String> enumeration = properties.keys();
        while (enumeration.hasMoreElements()) {
            String key = enumeration.nextElement();
            configProperties.put(key, properties.get(key));
        }

        return configProperties;
    }

    private Map<String, Object> getMetatypeDefaults(String factoryPid) {
        Map<String, Object> properties = new HashMap<>();
        ObjectClassDefinition bundleMetatype = getObjectClassDefinition(factoryPid);
        if (bundleMetatype != null) {
            for (AttributeDefinition attributeDef : bundleMetatype.getAttributeDefinitions(
                    ObjectClassDefinition.ALL)) {
                if (attributeDef.getID() != null) {
                    if (attributeDef.getDefaultValue() != null) {
                        if (attributeDef.getCardinality() == 0) {
                            properties.put(attributeDef.getID(),
                                    getAttributeValue(attributeDef.getDefaultValue()[0],
                                            attributeDef.getType()));
                        } else {
                            properties.put(attributeDef.getID(), attributeDef.getDefaultValue());
                        }
                    } else if (attributeDef.getCardinality() != 0) {
                        properties.put(attributeDef.getID(), new String[0]);
                    }
                }
            }
        }

        return properties;
    }

    private ObjectClassDefinition getObjectClassDefinition(String pid) {
        Bundle[] bundles = this.getBundleContext()
                .getBundles();
        for (int i = 0; i < bundles.length; i++) {
            try {
                ObjectClassDefinition ocd = this.getObjectClassDefinition(bundles[i], pid);
                if (ocd != null) {
                    return ocd;
                }
            } catch (IllegalArgumentException iae) {
                // don't care
            }
        }
        return null;
    }

    private ObjectClassDefinition getObjectClassDefinition(Bundle bundle, String pid) {
        Locale locale = Locale.getDefault();
        if (bundle != null) {
            if (metaTypeService != null) {
                MetaTypeInformation mti = metaTypeService.getMetaTypeInformation(bundle);
                if (mti != null) {
                    try {
                        return mti.getObjectClassDefinition(pid, locale.toString());
                    } catch (IllegalArgumentException e) {
                        // MetaTypeProvider.getObjectClassDefinition might throw illegal
                        // argument exception. So we must catch it here, otherwise the
                        // other configurations will not be shown
                        // See https://issues.apache.org/jira/browse/FELIX-2390
                        // https://issues.apache.org/jira/browse/FELIX-3694
                    }
                }
            }
        }

        // fallback to nothing found
        return null;
    }

    private Object getAttributeValue(String value, int type) {
        switch (type) {
        case AttributeDefinition.BOOLEAN:
            return Boolean.valueOf(value);
        case AttributeDefinition.BYTE:
            return Byte.valueOf(value);
        case AttributeDefinition.DOUBLE:
            return Double.valueOf(value);
        case AttributeDefinition.CHARACTER:
            return value.toCharArray()[0];
        case AttributeDefinition.FLOAT:
            return Float.valueOf(value);
        case AttributeDefinition.INTEGER:
            return Integer.valueOf(value);
        case AttributeDefinition.LONG:
            return Long.valueOf(value);
        case AttributeDefinition.SHORT:
            return Short.valueOf(value);
        case AttributeDefinition.PASSWORD:
        case AttributeDefinition.STRING:
        default:
            return value;
        }
    }

    protected BundleContext getBundleContext() {
        return FrameworkUtil.getBundle(this.getClass())
                .getBundleContext();
    }

    private void updateRegistrySourceConfigurations() {
        try {
            List<Metacard> metacards = federationAdminService.getRegistryMetacards();

            for (Metacard metacard : metacards) {
                try {
                    updateRegistryConfigurations(metacard, false);
                } catch (InvalidSyntaxException | ParserException | IOException e) {
                    LOGGER.error(
                            "Unable to update registry configurations. Registry source configurations won't be updated for metacard id: {}",
                            metacard.getId());
                }
            }

        } catch (FederationAdminException e) {
            LOGGER.error(
                    "Error getting registry metacards. Registry source configurations won't be updated.");
        }
    }

    public void setActivateConfigurations(Boolean activateConfigurations) {
        if (!activateConfigurations.equals(this.activateConfigurations)) {
            this.activateConfigurations = activateConfigurations;

            if (!activateConfigurations || preserveActiveConfigurations == null
                    || preserveActiveConfigurations) {
                return;
            }

            updateRegistrySourceConfigurations();
        }
    }

    public void setPreserveActiveConfigurations(Boolean preserveActiveConfigurations) {
        if (!preserveActiveConfigurations.equals(this.preserveActiveConfigurations)) {
            this.preserveActiveConfigurations = preserveActiveConfigurations;

            if (preserveActiveConfigurations || activateConfigurations == null
                    || !activateConfigurations) {
                return;
            }

            updateRegistrySourceConfigurations();
        }
    }

    public void setCleanUpOnDelete(Boolean cleanUpOnDelete) {
        this.cleanUpOnDelete = cleanUpOnDelete;
    }

    public void setBindingTypeFactoryPid(List<String> bindingTypeFactoryPid) {
        bindingTypeToFactoryPidMap = new HashMap<>();

        for (String mapping : bindingTypeFactoryPid) {
            String bindingType = StringUtils.substringBefore(mapping, "=");
            String factoryPid = StringUtils.substringAfter(mapping, "=");

            if (StringUtils.isNotBlank(bindingType) && StringUtils.isNotBlank(factoryPid)) {
                bindingTypeToFactoryPidMap.put(bindingType, factoryPid);
            }
        }
    }

    public void setSourceActivationPriorityOrder(List<String> sourceActivationPriorityOrder) {
        if (sourceActivationPriorityOrder != null
                && !sourceActivationPriorityOrder.equals(this.sourceActivationPriorityOrder)) {
            this.sourceActivationPriorityOrder = sourceActivationPriorityOrder;

            if (activateConfigurations == null || preserveActiveConfigurations == null
                    || !activateConfigurations && preserveActiveConfigurations) {
                return;
            }

            updateRegistrySourceConfigurations();
        }
    }

    public void setUrlBindingName(String urlBindingName) {
        this.urlBindingName = urlBindingName;
    }

    public void setConfigurationAdmin(ConfigurationAdmin configurationAdmin) {
        this.configurationAdmin = configurationAdmin;
    }

    public void setMetaTypeService(MetaTypeService metaTypeService) {
        this.metaTypeService = metaTypeService;
    }

    public void setParser(Parser parser) {
        List<String> contextPath = Arrays.asList(RegistryObjectType.class.getPackage()
                        .getName(),
                RegistryPackageUtils.OGC_FACTORY.getClass()
                        .getPackage()
                        .getName(),
                RegistryPackageUtils.GML_FACTORY.getClass()
                        .getPackage()
                        .getName());
        ClassLoader classLoader = this.getClass()
                .getClassLoader();

        this.unmarshalConfigurator = parser.configureParser(contextPath, classLoader);
        this.parser = parser;
    }
}
