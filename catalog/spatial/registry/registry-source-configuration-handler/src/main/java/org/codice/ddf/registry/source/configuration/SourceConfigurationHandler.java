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
import java.util.concurrent.Executors;

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

    private static final String DELETE_CONFIGURATION = "deleteConfiguration";

    private ConfigurationAdmin configurationAdmin;

    private FederationAdminService federationAdminService;

    private MetaTypeService metaTypeService;

    private Parser parser;

    private ParserConfigurator unmarshalConfigurator;

    private ExecutorService executor;

    private int threadPoolSize = 1;

    private String urlBindingName;

    private Map<String, String> bindingTypeToFactoryPidMap = new HashMap<>();

    private List<String> sourceActivationPriorityOrder = new ArrayList<>();

    private Boolean activateSourceOnCreation;

    public SourceConfigurationHandler(FederationAdminService federationAdminService) {
        this.federationAdminService = federationAdminService;
    }

    public void init() {
        executor = Executors.newFixedThreadPool(threadPoolSize);
    }

    public void destroy() {
        executor.shutdown();
    }

    @Override
    public void handleEvent(Event event) {
        LOGGER.debug("Received event");
        Metacard mcard = (Metacard) event.getProperty(METACARD_PROPERTY);
        if (mcard == null) {
            return;
        }
        if (mcard.getTags()
                .contains(RegistryConstants.REGISTRY_TAG)) {
            executor.execute(() -> {
                if (event.getTopic()
                        .equals("ddf/catalog/event/CREATED")) {
                    processCreate(mcard);
                } else if (event.getTopic()
                        .equals("ddf/catalog/event/UPDATED")) {
                    processUpdate(mcard);
                }
            });
        }
    }

    private void processCreate(Metacard metacard) {
        try {
            updateRegistryConfigurations(metacard);
        } catch (IOException | InvalidSyntaxException | ParserException e) {
            LOGGER.error("Unable to update registry configurations, metacard still ingested");
        }
    }

    private void processUpdate(Metacard metacard) {
        try {
            updateRegistryConfigurations(metacard);
        } catch (IOException | InvalidSyntaxException | ParserException e) {
            LOGGER.error("Unable to update registry configurations, metacard still ingested");
        }
    }

    private void updateRegistryConfigurations(Metacard metacard)
            throws IOException, InvalidSyntaxException, ParserException {
        String metadata = metacard.getMetadata();
        InputStream inputStream = new ByteArrayInputStream(metadata.getBytes(Charsets.UTF_8));

        Attribute identityNode =
                metacard.getAttribute(RegistryObjectMetacardType.REGISTRY_IDENTITY_NODE);
        if (identityNode != null) {
            return;
        }

        JAXBElement<RegistryObjectType> registryObjectTypeJAXBElement = parser.unmarshal(
                unmarshalConfigurator,
                JAXBElement.class,
                inputStream);

        if (registryObjectTypeJAXBElement == null) {
            LOGGER.error("Error trying to unmarshal metadata for metacard id: {}. Metadata: {}",
                    metacard.getId(),
                    metadata);
            return;
        }

        RegistryPackageType registryPackage =
                (RegistryPackageType) registryObjectTypeJAXBElement.getValue();

        String configId = metacard.getTitle();
        if (StringUtils.isEmpty(configId)) {
            configId = registryPackage.getId();
        }

        Configuration[] configurations = configurationAdmin.listConfigurations(String.format(
                "(id=%s)",
                configId));

        // check for duplicate name situation
        if (configurations != null && configurations.length > 0) {
            String registryId = (String) configurations[0].getProperties()
                    .get(RegistryObjectMetacardType.REGISTRY_ID);
            if (registryId == null || !registryId.equals(registryPackage.getId())) {
                configId = String.format("%s - %s", configId, registryPackage.getId());
            }
        }

        configurations = configurationAdmin.listConfigurations(String.format("(%s=%s)",
                RegistryObjectMetacardType.REGISTRY_ID,
                registryPackage.getId()));

        List<ServiceBindingType> bindingTypes =
                RegistryPackageUtils.getBindingTypes(registryPackage);
        String bindingTypeToActivate = getBindingTypeToActivate(bindingTypes);
        String deletedActiveConfigurationFPid = null;
        String deletedDisabledConfigurationFPid = null;
        Hashtable<String, Object> deletedActiveConfigurationProperties = new Hashtable<>();
        Hashtable<String, Object> deletedDisabledConfigurationProperties = new Hashtable<>();

        Map<String, Configuration> configMap = new HashMap<>();
        if (configurations != null && configurations.length > 0) {
            boolean activeHandled = false;
            for (Configuration configuration : configurations) {
                configMap.put(configuration.getFactoryPid(), configuration);

                if (activateSourceOnCreation & !activeHandled) {
                    String fPidToActivate = bindingTypeToFactoryPidMap.get(bindingTypeToActivate);

                    if (configuration.getFactoryPid()
                            .equals(fPidToActivate)) {
                        activeHandled = true;
                    } else if (!configuration.getFactoryPid()
                            .contains(DISABLED_CONFIGURATION_SUFFIX)) {
                        String factoryPidDisabled = configuration.getFactoryPid()
                                .concat(DISABLED_CONFIGURATION_SUFFIX);
                        deletedActiveConfigurationFPid = configuration.getFactoryPid();
                        deletedActiveConfigurationProperties.putAll(getConfigurationsFromDictionary(
                                configuration.getProperties()));

                        configMap.remove(configuration.getFactoryPid());

                        configuration.delete();
                        configurationAdmin.createFactoryConfiguration(factoryPidDisabled, null);

                    } else if (configuration.getFactoryPid()
                            .equals(fPidToActivate.concat(DISABLED_CONFIGURATION_SUFFIX))) {
                        deletedDisabledConfigurationFPid = configuration.getFactoryPid();
                        deletedDisabledConfigurationProperties.putAll(
                                getConfigurationsFromDictionary(configuration.getProperties()));
                        configMap.remove(configuration.getFactoryPid());

                        configuration.delete();
                    }
                }
            }
        }

        for (ServiceBindingType bindingType : bindingTypes) {
            Map<String, List<SlotType1>> slotMap =
                    RegistryPackageUtils.getNameSlotMapWithMultipleValues(bindingType.getSlot());

            Hashtable<String, Object> serviceConfigurationProperties = new Hashtable<>();

            if (slotMap.get(BINDING_TYPE) == null
                    || CollectionUtils.isEmpty(RegistryPackageUtils.getSlotStringAttributes(slotMap.get(
                    BINDING_TYPE)
                    .get(0)))) {
                continue;
            }

            String factoryPidMask = RegistryPackageUtils.getSlotStringAttributes(slotMap.get(
                    BINDING_TYPE)
                    .get(0))
                    .get(0);
            String factoryPid = bindingTypeToFactoryPidMap.get(factoryPidMask);

            if (StringUtils.isBlank(factoryPid)) {
                continue;
            }

            String factoryPidDisabled = factoryPid.concat(DISABLED_CONFIGURATION_SUFFIX);

            if (configMap.containsKey(factoryPid)) {
                Dictionary<String, Object> properties = configMap.get(factoryPid)
                        .getProperties();
                serviceConfigurationProperties.putAll(getConfigurationsFromDictionary(properties));

            } else if (configMap.containsKey(factoryPidDisabled)) {
                Dictionary<String, Object> properties = configMap.get(factoryPidDisabled)
                        .getProperties();
                serviceConfigurationProperties.putAll(getConfigurationsFromDictionary(properties));
            } else {
                if (activateSourceOnCreation) {
                    if (factoryPid.equals(deletedActiveConfigurationFPid)) {
                        serviceConfigurationProperties.putAll(deletedActiveConfigurationProperties);
                    } else if (factoryPidDisabled.equals(deletedDisabledConfigurationFPid)) {
                        serviceConfigurationProperties.putAll(deletedDisabledConfigurationProperties);
                    } else {
                        Map<String, Object> defaults = getMetatypeDefaults(factoryPid);
                        serviceConfigurationProperties.putAll(defaults);
                    }
                } else {
                    Map<String, Object> defaults = getMetatypeDefaults(factoryPid);
                    serviceConfigurationProperties.putAll(defaults);
                }
            }

            String bindingName = null;
            for (Map.Entry slotMapEntry : slotMap.entrySet()) {
                SlotType1 mapSlot = (SlotType1) ((ArrayList) slotMapEntry.getValue()).get(0);
                List<String> slotAttributes = RegistryPackageUtils.getSlotStringAttributes(mapSlot);
                if (CollectionUtils.isEmpty(slotAttributes)) {
                    continue;
                }

                String name = (String) slotMapEntry.getKey();
                String value = slotAttributes.get(0);
                serviceConfigurationProperties.put(name, value);
                if (name.equals(urlBindingName)) {
                    bindingName = value;
                }

            }
            serviceConfigurationProperties.put(ID, configId);
            serviceConfigurationProperties.put(SHORTNAME, configId);
            serviceConfigurationProperties.put(RegistryObjectMetacardType.REGISTRY_ID,
                    registryPackage.getId());
            if (StringUtils.isNotBlank(bindingName) && bindingType.isSetAccessURI()) {
                serviceConfigurationProperties.put(bindingName, bindingType.getAccessURI());
            }

            Configuration configuration = configMap.get(factoryPid);
            if (configuration == null) {

                configuration = configMap.get(factoryPidDisabled);
                if (configuration == null) {

                    String pid = factoryPidDisabled;
                    if (activateSourceOnCreation && factoryPidMask.equals(bindingTypeToActivate)) {
                        pid = factoryPid;
                    }
                    configuration = configurationAdmin.createFactoryConfiguration(pid, null);
                }
            }
            configuration.update(serviceConfigurationProperties);
        }

    }

    private String getBindingTypeToActivate(List<ServiceBindingType> bindingTypes) {
        if (CollectionUtils.isEmpty(sourceActivationPriorityOrder)) {
            return null;
        }

        String bindingTypeToActivate = null;
        String topPriority = sourceActivationPriorityOrder.get(0);
        List<String> bindingTypesNames = new ArrayList<>();

        for (ServiceBindingType bindingType : bindingTypes) {
            Map<String, List<SlotType1>> slotMap =
                    RegistryPackageUtils.getNameSlotMapWithMultipleValues(bindingType.getSlot());

            if (slotMap.get(BINDING_TYPE) == null
                    || CollectionUtils.isEmpty(RegistryPackageUtils.getSlotStringAttributes(slotMap.get(
                    BINDING_TYPE)
                    .get(0)))) {
                continue;
            }

            String factoryPidMask = RegistryPackageUtils.getSlotStringAttributes(slotMap.get(
                    BINDING_TYPE)
                    .get(0))
                    .get(0);

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

    private BundleContext getBundleContext() {
        return FrameworkUtil.getBundle(this.getClass())
                .getBundleContext();
    }

    private void updateRegistrySourceConfigurations() {
        try {
            List<Metacard> metacards = federationAdminService.getRegistryMetacards();

            for (Metacard metacard : metacards) {
                try {
                    updateRegistryConfigurations(metacard);
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

    public void setActivateSourceOnCreation(Boolean activateSourceOnCreation) {
        this.activateSourceOnCreation = activateSourceOnCreation;
        if (activateSourceOnCreation) {
            updateRegistrySourceConfigurations();
        }
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
        boolean orderUpdated = false;
        if (sourceActivationPriorityOrder != null
                && !sourceActivationPriorityOrder.equals(this.sourceActivationPriorityOrder)) {
            orderUpdated = true;
        }

        this.sourceActivationPriorityOrder = sourceActivationPriorityOrder;

        if (activateSourceOnCreation && orderUpdated) {
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
                net.opengis.ogc.ObjectFactory.class.getPackage()
                        .getName(),
                net.opengis.gml.v_3_1_1.ObjectFactory.class.getPackage()
                        .getName());
        ClassLoader classLoader = this.getClass()
                .getClassLoader();

        this.unmarshalConfigurator = parser.configureParser(contextPath, classLoader);
        this.parser = parser;
    }

}
