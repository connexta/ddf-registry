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
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
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
import org.codice.ddf.registry.federationadmin.service.FederationAdminException;
import org.codice.ddf.registry.federationadmin.service.FederationAdminService;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;

import ddf.catalog.Constants;
import ddf.catalog.data.Attribute;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.impl.AttributeImpl;
import ddf.catalog.operation.CreateRequest;
import ddf.catalog.operation.CreateResponse;
import ddf.catalog.operation.DeleteRequest;
import ddf.catalog.operation.DeleteResponse;
import ddf.catalog.operation.OperationTransaction;
import ddf.catalog.operation.UpdateRequest;
import ddf.catalog.operation.UpdateResponse;
import ddf.catalog.plugin.PluginExecutionException;
import ddf.catalog.plugin.PostIngestPlugin;
import ddf.catalog.plugin.PreIngestPlugin;
import ddf.catalog.plugin.StopProcessingException;
import ddf.catalog.util.impl.Requests;
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

    private FederationAdminService federationAdmin;

    private ParserConfigurator marshalConfigurator;

    private ParserConfigurator unmarshalConfigurator;

    private ConfigurationAdmin configurationAdmin;

    private Set<String> registryIds = ConcurrentHashMap.newKeySet();

    private static final Logger LOGGER = LoggerFactory.getLogger(IdentificationPlugin.class);

    private static final String BINDING_TYPE = "bindingType";

    private static final String DISABLED_CONFIGURATION_SUFFIX = "_disabled";

    private static final String ID = "id";

    private static final String SHORTNAME = "shortname";

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

                    metacard.setAttribute(new AttributeImpl(Metacard.METADATA, new String(
                            outputStream.toByteArray(),
                            Charsets.UTF_8)));
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

    public void init() throws FederationAdminException {

        List<Metacard> registryMetacards = federationAdmin.getRegistryMetacards();

        registryIds.addAll(registryMetacards.stream()
                .map(this::getRegistryId)
                .collect(Collectors.toList()));
    }

    public void setFederationAdmin(FederationAdminService federationAdmin) {
        this.federationAdmin = federationAdmin;
    }

    public void setConfigurationAdmin(ConfigurationAdmin configurationAdmin) {
        this.configurationAdmin = configurationAdmin;
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

                        if (slotMap.get(BINDING_TYPE) == null) {
                            continue;
                        }
                        String factoryPid = getSlotStringAttributes(slotMap.get(BINDING_TYPE)
                                .get(0)).get(0);
                        factoryPid = factoryPid.concat(DISABLED_CONFIGURATION_SUFFIX);

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
                        serviceConfigurationProperties.put(ID, metacard.getTitle());
                        serviceConfigurationProperties.put(SHORTNAME, metacard.getTitle());
                        serviceConfigurationProperties.put(RegistryObjectMetacardType.REGISTRY_ID,
                                metacard.getAttribute(RegistryObjectMetacardType.REGISTRY_ID)
                                        .toString());

                        Configuration configuration = configurationAdmin.createFactoryConfiguration(
                                factoryPid,
                                null);
                        configuration.update(serviceConfigurationProperties);
                    }

                }
            }
        }

    }

    private Map<String, SlotType1> getSlotMap(List<SlotType1> slots) {
        Map<String, SlotType1> slotMap = new HashMap<>();

        for (SlotType1 slot : slots) {
            slotMap.put(slot.getName(), slot);
        }
        return slotMap;
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
}
