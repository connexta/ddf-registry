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
package org.codice.ddf.registry.identification;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;

import org.apache.commons.collections.CollectionUtils;
import org.codice.ddf.parser.Parser;
import org.codice.ddf.parser.ParserConfigurator;
import org.codice.ddf.parser.ParserException;
import org.codice.ddf.registry.common.RegistryConstants;
import org.codice.ddf.registry.common.metacard.RegistryObjectMetacardType;
import org.codice.ddf.security.common.Security;
import org.opengis.filter.Filter;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.metatype.AttributeDefinition;
import org.osgi.service.metatype.MetaTypeInformation;
import org.osgi.service.metatype.MetaTypeService;
import org.osgi.service.metatype.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;

import ddf.catalog.CatalogFramework;
import ddf.catalog.Constants;
import ddf.catalog.data.Attribute;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.Result;
import ddf.catalog.data.impl.AttributeImpl;
import ddf.catalog.federation.FederationException;
import ddf.catalog.filter.FilterBuilder;
import ddf.catalog.operation.CreateRequest;
import ddf.catalog.operation.CreateResponse;
import ddf.catalog.operation.DeleteRequest;
import ddf.catalog.operation.DeleteResponse;
import ddf.catalog.operation.OperationTransaction;
import ddf.catalog.operation.QueryRequest;
import ddf.catalog.operation.QueryResponse;
import ddf.catalog.operation.Update;
import ddf.catalog.operation.UpdateRequest;
import ddf.catalog.operation.UpdateResponse;
import ddf.catalog.operation.impl.QueryImpl;
import ddf.catalog.operation.impl.QueryRequestImpl;
import ddf.catalog.plugin.PluginExecutionException;
import ddf.catalog.plugin.PostIngestPlugin;
import ddf.catalog.plugin.PreIngestPlugin;
import ddf.catalog.plugin.StopProcessingException;
import ddf.catalog.source.SourceUnavailableException;
import ddf.catalog.source.UnsupportedQueryException;
import ddf.catalog.util.impl.Requests;
import ddf.security.SecurityConstants;
import ddf.security.Subject;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ExternalIdentifierType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryObjectListType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryObjectType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryPackageType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ServiceBindingType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ServiceType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.SlotType1;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ValueListType;

/**
 * IdentificationPlugin is a Pre/PostIngestPlugin that assigns a localID when a metacard is added to the
 * catalog and an originID to a registry metacard during creation. It also ensures that duplicate
 * registry-ids are not added to the catalog.
 */
public class IdentificationPlugin implements PreIngestPlugin, PostIngestPlugin {

    private Parser parser;

    private CatalogFramework catalogFramework;

    private FilterBuilder filterBuilder;

    private ParserConfigurator marshalConfigurator;

    private ParserConfigurator unmarshalConfigurator;

    private ConfigurationAdmin configurationAdmin;

    private Set<String> registryIds = ConcurrentHashMap.newKeySet();

    private static final Logger LOGGER = LoggerFactory.getLogger(IdentificationPlugin.class);

    private static final String BINDING_TYPE = "bindingType";

    private static final String DISABLED_CONFIGURATION_SUFFIX = "_disabled";

    private static final String ID = "id";

    private static final String SHORTNAME = "shortname";

    private MetaTypeService metatype;

    @Override
    public CreateRequest process(CreateRequest input)
            throws PluginExecutionException, StopProcessingException {
        if (Requests.isLocal(input)) {
            for (Metacard metacard : input.getMetacards()) {
                if (metacard.getTags()
                        .contains(RegistryConstants.REGISTRY_TAG)) {
                    if (registryIds.contains(getRegistryId(metacard))) {
                        throw new StopProcessingException(String.format(
                                "Duplication error. Can not create metacard with registry-id %s since it already exists",
                                getRegistryId(metacard)));
                    }
                    setMetacardExtID(metacard);
                }
            }
        }
        return input;
    }

    @Override
    public UpdateRequest process(UpdateRequest input)
            throws PluginExecutionException, StopProcessingException {

        OperationTransaction operationTransaction = (OperationTransaction) input.getProperties()
                .get(Constants.OPERATION_TRANSACTION_KEY);

        if (operationTransaction == null) {
            throw new UnsupportedOperationException(
                    "Unable to get OperationTransaction from UpdateRequest");
        }

        if (Requests.isLocal(input)) {
            List<Metacard> previousMetacards = operationTransaction.getPreviousStateMetacards();

            boolean transientAttributeUpdates = false;
            if (input.getProperties()
                    .get(RegistryConstants.TRANSIENT_ATTRIBUTE_UPDATE) != null) {
                transientAttributeUpdates = true;
            }

            Map<String, Metacard> previousMetacardsMap = new HashMap<>();
            for (Metacard metacard : previousMetacards) {
                previousMetacardsMap.put(metacard.getId(), metacard);
            }

            ArrayList<Metacard> metacardsToRemove = new ArrayList<>();

            for (Map.Entry<Serializable, Metacard> entry : input.getUpdates()) {
                Metacard updateMetacard = entry.getValue();
                Metacard existingMetacard = previousMetacardsMap.get(updateMetacard.getId());

                if (existingMetacard != null) {
                    if (existingMetacard.getAttribute(RegistryObjectMetacardType.REGISTRY_ID)
                            .equals(updateMetacard.getAttribute(RegistryObjectMetacardType.REGISTRY_ID))) {

                        if (transientAttributeUpdates || updateMetacard.getModifiedDate()
                                .after(existingMetacard.getModifiedDate())) {
                            for (String transientAttributeKey : RegistryObjectMetacardType.TRANSIENT_ATTRIBUTES) {
                                Attribute transientAttribute = updateMetacard.getAttribute(
                                        transientAttributeKey);
                                if (transientAttribute == null) {
                                    transientAttribute = existingMetacard.getAttribute(
                                            transientAttributeKey);
                                    if (transientAttribute != null) {
                                        updateMetacard.setAttribute(transientAttribute);
                                    }
                                }
                            }
                        } else {
                            metacardsToRemove.add(updateMetacard);
                        }
                    }
                }
            }
            input.getUpdates()
                    .removeAll(metacardsToRemove);

        }
        return input;
    }

    @Override

    public DeleteRequest process(DeleteRequest input)
            throws PluginExecutionException, StopProcessingException {
        return input;
    }

    @Override
    public CreateResponse process(CreateResponse input) throws PluginExecutionException {
        if (Requests.isLocal(input.getRequest())) {
            for (Metacard metacard : input.getCreatedMetacards()) {
                if (metacard.getTags()
                        .contains(RegistryConstants.REGISTRY_TAG)) {
                    registryIds.add(getRegistryId(metacard));
                    try {
                        updateRegistryConfigurations(metacard);
                    } catch (IOException | InvalidSyntaxException | ParserException e) {
                        LOGGER.error(
                                "Unable to update registry configurations, metacard still ingested");
                    }
                }
            }
        }
        return input;
    }

    @Override
    public UpdateResponse process(UpdateResponse input) throws PluginExecutionException {
        if (Requests.isLocal(input.getRequest())) {
            for (Update update : input.getUpdatedMetacards()) {
                if (update.getNewMetacard()
                        .getTags()
                        .contains(RegistryConstants.REGISTRY_TAG)) {
                    try {
                        updateRegistryConfigurations(update.getNewMetacard());
                    } catch (IOException | InvalidSyntaxException | ParserException e) {
                        LOGGER.error(
                                "Unable to update registry configurations, metacard still ingested");
                    }
                }
            }
        }
        return input;
    }

    @Override
    public DeleteResponse process(DeleteResponse input) throws PluginExecutionException {
        if (Requests.isLocal(input.getRequest())) {
            for (Metacard metacard : input.getDeletedMetacards()) {
                if (metacard.getTags()
                        .contains(RegistryConstants.REGISTRY_TAG)) {
                    registryIds.remove(getRegistryId(metacard));
                }
            }
        }
        return input;
    }

    private void setMetacardExtID(Metacard metacard) throws StopProcessingException {

        boolean extOriginFound = false;
        String metacardID = metacard.getId();
        String metadata = metacard.getMetadata();
        String registryID = getRegistryId(metacard);

        InputStream inputStream = new ByteArrayInputStream(metadata.getBytes(Charsets.UTF_8));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try {
            JAXBElement<RegistryObjectType> registryObjectTypeJAXBElement = parser.unmarshal(
                    unmarshalConfigurator,
                    JAXBElement.class,
                    inputStream);

            if (registryObjectTypeJAXBElement != null) {
                RegistryObjectType registryObjectType = registryObjectTypeJAXBElement.getValue();

                if (registryObjectType != null) {

                    List<ExternalIdentifierType> extIdList = new ArrayList<>();

                    //check if external ids are already present
                    if (registryObjectType.isSetExternalIdentifier()) {
                        List<ExternalIdentifierType> currentExtIdList =
                                registryObjectType.getExternalIdentifier();

                        for (ExternalIdentifierType extId : currentExtIdList) {
                            extId.setRegistryObject(registryID);
                            if (extId.getId()
                                    .equals(RegistryConstants.REGISTRY_MCARD_LOCAL_ID)) {
                                //update local id
                                extId.setValue(metacardID);
                            } else if (extId.getId()
                                    .equals(RegistryConstants.REGISTRY_MCARD_ORIGIN_ID)) {
                                extOriginFound = true;
                            }
                            extIdList.add(extId);
                        }

                        if (!extOriginFound) {
                            ExternalIdentifierType originExtId = new ExternalIdentifierType();
                            originExtId.setId(RegistryConstants.REGISTRY_MCARD_ORIGIN_ID);
                            originExtId.setRegistryObject(registryID);
                            originExtId.setIdentificationScheme(RegistryConstants.REGISTRY_METACARD_ID_CLASS);
                            originExtId.setValue(metacardID);

                            extIdList.add(originExtId);
                        }

                    } else {
                        //create both ids
                        extIdList = new ArrayList<>(2);

                        ExternalIdentifierType localExtId = new ExternalIdentifierType();
                        localExtId.setId(RegistryConstants.REGISTRY_MCARD_LOCAL_ID);
                        localExtId.setRegistryObject(registryID);
                        localExtId.setIdentificationScheme(RegistryConstants.REGISTRY_METACARD_ID_CLASS);
                        localExtId.setValue(metacardID);

                        ExternalIdentifierType originExtId = new ExternalIdentifierType();
                        originExtId.setId(RegistryConstants.REGISTRY_MCARD_ORIGIN_ID);
                        originExtId.setRegistryObject(registryID);
                        originExtId.setIdentificationScheme(RegistryConstants.REGISTRY_METACARD_ID_CLASS);
                        originExtId.setValue(metacardID);

                        extIdList.add(localExtId);
                        extIdList.add(originExtId);

                    }

                    registryObjectType.setExternalIdentifier(extIdList);
                    registryObjectTypeJAXBElement.setValue(registryObjectType);
                    parser.marshal(marshalConfigurator,
                            registryObjectTypeJAXBElement,
                            outputStream);

                    metacard.setAttribute(new AttributeImpl(Metacard.METADATA,
                            new String(outputStream.toByteArray(), Charsets.UTF_8)));
                }
            }

        } catch (ParserException e) {
            throw new StopProcessingException(
                    "Unable to access Registry Metadata. Parser exception caught");
        }
    }

    private String getRegistryId(Metacard mcard) {
        return mcard.getAttribute(RegistryObjectMetacardType.REGISTRY_ID)
                .getValue()
                .toString();
    }

    public void init()
            throws UnsupportedQueryException, SourceUnavailableException, FederationException {

        List<Metacard> registryMetacards;
        Filter registryFilter = filterBuilder.attribute(Metacard.TAGS)
                .is()
                .like()
                .text(RegistryConstants.REGISTRY_TAG);
        Subject systemSubject = Security.getInstance()
                .getSystemSubject();
        QueryRequest request = new QueryRequestImpl(new QueryImpl(registryFilter));
        request.getProperties()
                .put(SecurityConstants.SECURITY_SUBJECT, systemSubject);

        QueryResponse response = catalogFramework.query(request);
        registryMetacards = response.getResults()
                .stream()
                .map(Result::getMetacard)
                .collect(Collectors.toList());
        registryIds.addAll(registryMetacards.stream()
                .map(this::getRegistryId)
                .collect(Collectors.toList()));

    }

    public void setFilterBuilder(FilterBuilder filterBuilder) {
        this.filterBuilder = filterBuilder;
    }

    public void setCatalogFramework(CatalogFramework framework) {
        this.catalogFramework = framework;
    }

    public void setConfigurationAdmin(ConfigurationAdmin configurationAdmin) {
        this.configurationAdmin = configurationAdmin;
    }

    public void setMetatype(MetaTypeService metatype) {
        this.metatype = metatype;
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
        this.marshalConfigurator = parser.configureParser(contextPath, classLoader);
        this.marshalConfigurator.addProperty(Marshaller.JAXB_FRAGMENT, true);
        this.parser = parser;
    }

    private void updateRegistryConfigurations(Metacard metacard)
            throws IOException, InvalidSyntaxException, ParserException {
        String metadata = metacard.getMetadata();
        InputStream inputStream = new ByteArrayInputStream(metadata.getBytes(Charsets.UTF_8));

        JAXBElement<RegistryObjectType> registryObjectTypeJAXBElement = parser.unmarshal(
                unmarshalConfigurator,
                JAXBElement.class,
                inputStream);

        if (registryObjectTypeJAXBElement != null) {
            RegistryPackageType registryPackageType =
                    (RegistryPackageType) registryObjectTypeJAXBElement.getValue();
            String configId = metacard.getTitle();
            if (configId == null || configId.isEmpty()) {
                configId = registryPackageType.getId();
            }
            Configuration[] configurations = configurationAdmin.listConfigurations(String.format(
                    "(id=%s)",
                    configId));
            //check for duplicate name situation
            if (configurations != null && configurations.length > 0) {
                String regId = (String) configurations[0].getProperties()
                        .get(RegistryObjectMetacardType.REGISTRY_ID);
                if (regId == null || !regId.equals(registryPackageType.getId())) {
                    configId = String.format("%s - %s", configId, registryPackageType.getId());
                }
            }

            configurations = configurationAdmin.listConfigurations(String.format("(registry-id=%s)",
                    registryPackageType.getId()));

            Map<String, Configuration> configMap = new HashMap<>();
            if (configurations != null && configurations.length > 0) {
                for (Configuration config : configurations) {
                    configMap.put((String) config.getProperties()
                            .get(BINDING_TYPE), config);
                }
            }
            RegistryObjectListType registryObjectListType =
                    registryPackageType.getRegistryObjectList();
            for (JAXBElement id : registryObjectListType.getIdentifiable()) {
                RegistryObjectType registryObject = (RegistryObjectType) id.getValue();
                if (registryObject instanceof ServiceType
                        && RegistryConstants.REGISTRY_SERVICE_OBJECT_TYPE.equals(registryObject.getObjectType())) {
                    ServiceType service = (ServiceType) registryObject;

                    for (ServiceBindingType binding : service.getServiceBinding()) {
                        Map<String, List<SlotType1>> slotMap =
                                getSlotMapWithMultipleValues(binding.getSlot());

                        Hashtable<String, Object> serviceConfigurationProperties =
                                new Hashtable<>();

                        if (slotMap.get(BINDING_TYPE) == null || CollectionUtils.isEmpty(
                                getSlotStringAttributes(slotMap.get(BINDING_TYPE)
                                        .get(0)))) {
                            continue;
                        }
                        String factoryPid = getSlotStringAttributes(slotMap.get(BINDING_TYPE)
                                .get(0)).get(0);
                        String factoryPidDisabled =
                                factoryPid.concat(DISABLED_CONFIGURATION_SUFFIX);

                        if (configMap.containsKey(factoryPid)) {
                            Dictionary<String, Object> props = configMap.get(factoryPid)
                                    .getProperties();
                            Enumeration<String> enumeration = props.keys();
                            while (enumeration.hasMoreElements()) {
                                String key = enumeration.nextElement();
                                serviceConfigurationProperties.put(key, props.get(key));
                            }
                        } else {

                            Map<String, Object> defaults = getMetatypeDefaults(factoryPid);
                            serviceConfigurationProperties.putAll(defaults);
                        }

                        for (Map.Entry slotValue : slotMap.entrySet()) {
                            if (CollectionUtils.isEmpty(((SlotType1) (((ArrayList) slotValue.getValue()).get(
                                    0))).getValueList()
                                    .getValue()
                                    .getValue())) {
                                continue;
                            }
                            String key =
                                    ((SlotType1) (((ArrayList) slotValue.getValue()).get(0))).getName();
                            String value =
                                    ((SlotType1) (((ArrayList) slotValue.getValue()).get(0))).getValueList()
                                            .getValue()
                                            .getValue()
                                            .get(0);
                            serviceConfigurationProperties.put(key, value);
                        }
                        serviceConfigurationProperties.put(ID, configId);
                        serviceConfigurationProperties.put(SHORTNAME, configId);
                        serviceConfigurationProperties.put(RegistryObjectMetacardType.REGISTRY_ID,
                                registryPackageType.getId());

                        Configuration configuration = configMap.get(factoryPid);
                        if (configuration == null) {
                            configuration = configurationAdmin.createFactoryConfiguration(
                                    factoryPidDisabled,
                                    null);
                        }
                        configuration.update(serviceConfigurationProperties);
                    }

                }
            }
        }

    }

    private Map<String, List<SlotType1>> getSlotMapWithMultipleValues(List<SlotType1> slots) {
        Map<String, List<SlotType1>> slotMap = new HashMap<>();

        for (SlotType1 slot : slots) {
            if (!slotMap.containsKey(slot.getName())) {
                List<SlotType1> slotList = new ArrayList<>();
                slotList.add(slot);

                slotMap.put(slot.getName(), slotList);
            } else {
                slotMap.get(slot.getName())
                        .add(slot);
            }
        }
        return slotMap;
    }

    private static List<String> getSlotStringAttributes(SlotType1 slot) {
        List<String> slotAttributes = new ArrayList<>();

        if (slot.isSetValueList()) {
            ValueListType valueList = slot.getValueList()
                    .getValue();
            if (valueList.isSetValue()) {
                slotAttributes = valueList.getValue();
            }
        }

        return slotAttributes;
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

    private ObjectClassDefinition getObjectClassDefinition(Bundle bundle, String pid) {
        Locale locale = Locale.getDefault();
        if (bundle != null) {
            if (metatype != null) {
                MetaTypeInformation mti = metatype.getMetaTypeInformation(bundle);
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

}
