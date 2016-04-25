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
package org.codice.ddf.registry.federationadmin.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.StandardMBean;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.datatype.DatatypeConstants;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.codice.ddf.configuration.SystemBaseUrl;
import org.codice.ddf.parser.Parser;
import org.codice.ddf.parser.ParserConfigurator;
import org.codice.ddf.parser.ParserException;
import org.codice.ddf.registry.api.RegistryStore;
import org.codice.ddf.registry.common.RegistryConstants;
import org.codice.ddf.registry.common.metacard.RegistryObjectMetacardType;
import org.codice.ddf.registry.federationadmin.FederationAdminMBean;
import org.codice.ddf.registry.federationadmin.converter.RegistryPackageWebConverter;
import org.codice.ddf.registry.federationadmin.service.FederationAdminException;
import org.codice.ddf.registry.federationadmin.service.FederationAdminService;
import org.codice.ddf.ui.admin.api.ConfigurationAdminExt;
import org.geotools.filter.FilterFactoryImpl;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.metatype.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import ddf.catalog.CatalogFramework;
import ddf.catalog.data.Attribute;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.Result;
import ddf.catalog.data.impl.AttributeImpl;
import ddf.catalog.endpoint.CatalogEndpoint;
import ddf.catalog.federation.FederationException;
import ddf.catalog.operation.CreateRequest;
import ddf.catalog.operation.CreateResponse;
import ddf.catalog.operation.DeleteRequest;
import ddf.catalog.operation.DeleteResponse;
import ddf.catalog.operation.Query;
import ddf.catalog.operation.QueryResponse;
import ddf.catalog.operation.SourceResponse;
import ddf.catalog.operation.impl.CreateRequestImpl;
import ddf.catalog.operation.impl.DeleteRequestImpl;
import ddf.catalog.operation.impl.QueryImpl;
import ddf.catalog.operation.impl.QueryRequestImpl;
import ddf.catalog.operation.impl.UpdateRequestImpl;
import ddf.catalog.service.ConfiguredService;
import ddf.catalog.source.CatalogStore;
import ddf.catalog.source.IngestException;
import ddf.catalog.source.Source;
import ddf.catalog.source.SourceUnavailableException;
import ddf.catalog.source.UnsupportedQueryException;
import ddf.catalog.transform.CatalogTransformerException;
import ddf.catalog.transform.InputTransformer;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ExtrinsicObjectType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ObjectFactory;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryObjectType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryPackageType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.SlotType1;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ValueListType;

public class FederationAdmin implements FederationAdminMBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(FederationAdmin.class);

    private static final FilterFactory FILTER_FACTORY = new FilterFactoryImpl();

    private static final ObjectFactory RIM_FACTORY = new ObjectFactory();

    private static final String MAP_ENTRY_ID = "id";

    private static final String MAP_ENTRY_ENABLED = "enabled";

    private static final String MAP_ENTRY_FPID = "fpid";

    private static final String MAP_ENTRY_NAME = "name";

    private static final String MAP_ENTRY_BUNDLE_NAME = "bundle_name";

    private static final String MAP_ENTRY_BUNDLE_LOCATION = "bundle_location";

    private static final String MAP_ENTRY_BUNDLE = "bundle";

    private static final String MAP_ENTRY_PROPERTIES = "properties";

    private static final String MAP_ENTRY_CONFIGURATIONS = "configurations";

    private static final String DISABLED = "_disabled";

    private static final String TEMP_REGISTRY_ID = "temp-id";

    private static final String REGISTRY_FILTER =
            "(|(service.factoryPid=*Registry*Store*)(service.factoryPid=*registry*store*))";

    private static final String TRANSIENT_VALUES_KEY = "TransientValues";

    private static final String AUTO_POPULATE_VALUES_KEY = "autoPopulateValues";

    private static final String SERVICE_BINDINGS_KEY = "ServiceBinding";

    private static final String CUSTOM_SLOTS_KEY = "customSlots";

    private static final String KARAF_ETC = "karaf.etc";

    private static final String REGISTRY_CONFIG_DIR = "registry";

    private static final String REGISTRY_FIELDS_FILE = "registry-custom-slots.json";

    private MBeanServer mbeanServer;

    private ObjectName objectName;

    private FederationAdminService federationAdminService;

    private InputTransformer registryTransformer;

    private Parser parser;

    private ParserConfigurator marshalConfigurator;

    private AdminRegistryHelper helper;

    private CatalogFramework catalogFramework;

    private Map<String, CatalogStore> catalogStoreMap;

    private Map<String, Object> customSlots;

    private Map<String, Map<String, String>> endpointMap = new HashMap<>();

    public FederationAdmin(ConfigurationAdmin configurationAdmin) {
        configureMBean();
        helper = getAdminRegistryHelper(configurationAdmin);
    }

    @Override
    public String createLocalEntry(Map<String, Object> registryMap)
            throws FederationAdminException {
        if (MapUtils.isEmpty(registryMap)) {
            throw new FederationAdminException(
                    "Error creating local registry entry. Null map provided.");
        }

        RegistryPackageType registryPackage =
                RegistryPackageWebConverter.getRegistryPackageFromWebMap(registryMap);

        if (registryPackage == null) {
            throw new FederationAdminException(
                    "Error creating local registry entry. Couldn't convert registry map to a registry package.");
        }

        if (!registryPackage.isSetHome()) {
            String home = SystemBaseUrl.getBaseUrl();
            if (StringUtils.isNotBlank(home)) {
                registryPackage.setHome(home);
            }
        }

        if (!registryPackage.isSetObjectType()) {
            registryPackage.setObjectType(RegistryConstants.REGISTRY_NODE_OBJECT_TYPE);
        }

        if (!registryPackage.isSetId()) {
            String registryPackageId = RegistryConstants.GUID_PREFIX + UUID.randomUUID()
                    .toString()
                    .replaceAll("-", "");
            registryPackage.setId(registryPackageId);
        }

        updateDateFields(registryPackage);

        Metacard metacard = getRegistryMetacardFromRegistryPackage(registryPackage);
        metacard.setAttribute(new AttributeImpl(RegistryObjectMetacardType.REGISTRY_LOCAL_NODE,
                true));
        return federationAdminService.addRegistryEntry(metacard);
    }

    @Override
    public String createLocalEntry(String base64EncodedXmlData) throws FederationAdminException {
        if (StringUtils.isBlank(base64EncodedXmlData)) {
            throw new FederationAdminException(
                    "Error creating local entry. String provided was blank.");
        }

        String metacardId;
        try (InputStream xmlStream = new ByteArrayInputStream(Base64.getDecoder()
                .decode(base64EncodedXmlData))) {
            Metacard metacard = getRegistryMetacardFromInputStream(xmlStream);
            metacard.setAttribute(new AttributeImpl(RegistryObjectMetacardType.REGISTRY_LOCAL_NODE,
                    true));
            metacardId = federationAdminService.addRegistryEntry(metacard);
        } catch (IOException | IllegalArgumentException e) {
            String message = "Error creating local entry. Couldn't decode string.";
            LOGGER.error("{} Base64 encoded xml: {}", message, base64EncodedXmlData);
            throw new FederationAdminException(message, e);
        }
        return metacardId;
    }

    @Override
    public void updateLocalEntry(Map<String, Object> registryObjectMap)
            throws FederationAdminException {
        if (MapUtils.isEmpty(registryObjectMap)) {
            throw new FederationAdminException(
                    "Error updating local registry entry. Null map provided.");
        }
        RegistryPackageType registryPackage =
                RegistryPackageWebConverter.getRegistryPackageFromWebMap(registryObjectMap);

        if (registryPackage == null) {
            String message =
                    "Error updating local registry entry. Couldn't convert registry map to a registry package.";
            LOGGER.error("{} Registry Map: {}", message, registryObjectMap);
            throw new FederationAdminException(message);
        }

        updateDateFields(registryPackage);

        List<Metacard> existingMetacards =
                federationAdminService.getLocalRegistryMetacardsByRegistryIds(Collections.singletonList(
                        registryPackage.getId()));

        if (CollectionUtils.isEmpty(existingMetacards)) {
            String message = "Error updating local registry entry. Registry metacard not found.";
            LOGGER.error("{} Registry ID: {}", message, registryPackage.getId());
            throw new FederationAdminException(message);
        }

        if (existingMetacards.size() > 1) {
            String message =
                    "Error updating local registry entry. Multiple registry metacards found.";

            List<String> metacardIds = new ArrayList<>();
            metacardIds.addAll(existingMetacards.stream()
                    .map(Metacard::getId)
                    .collect(Collectors.toList()));
            LOGGER.error("{} Matching registry metacard ids: {}", message, metacardIds);

            throw new FederationAdminException(message);
        }
        Metacard existingMetacard = existingMetacards.get(0);

        Metacard updateMetacard = getRegistryMetacardFromRegistryPackage(registryPackage);
        updateMetacard.setAttribute(new AttributeImpl(Metacard.ID, existingMetacard.getId()));

        federationAdminService.updateRegistryEntry(updateMetacard);
    }

    @Override
    public void deleteLocalEntry(List<String> ids) throws FederationAdminException {
        if (CollectionUtils.isEmpty(ids)) {
            throw new FederationAdminException(
                    "Error deleting local registry entries. No ids provided.");
        }

        List<Metacard> localMetacards =
                federationAdminService.getLocalRegistryMetacardsByRegistryIds(ids);
        List<String> metacardIds = new ArrayList<>();

        metacardIds.addAll(localMetacards.stream()
                .map(Metacard::getId)
                .collect(Collectors.toList()));
        if (ids.size() != metacardIds.size()) {
            String message = "Error deleting local registry entries. Registry ids provided.";
            LOGGER.error("{} Registry Ids provided: {}. Registry metacard ids found: {}",
                    message,
                    ids,
                    metacardIds);
            throw new FederationAdminException(message);
        }

        federationAdminService.deleteRegistryEntriesByMetacardIds(metacardIds);
    }

    @Override
    public Map<String, Object> getLocalNodes() throws FederationAdminException {
        Map<String, Object> localNodes = new HashMap<>();
        List<Map<String, Object>> registryWebMaps = new ArrayList<>();

        List<RegistryPackageType> registryPackages =
                federationAdminService.getLocalRegistryObjects();

        List<Metacard> metacards = federationAdminService.getLocalRegistryMetacards();
        Map<String, Metacard> metacardByRegistryIdMap = getRegistryIdMetacardMap(metacards);

        for (RegistryPackageType registryPackage : registryPackages) {
            Map<String, Object> registryWebMap =
                    RegistryPackageWebConverter.getRegistryObjectWebMap(registryPackage);

            Metacard metacard = metacardByRegistryIdMap.get(registryPackage.getId());
            Map<String, Object> transientValues = getTransientValuesMap(metacard);
            if (MapUtils.isNotEmpty(transientValues)) {
                registryWebMap.put(TRANSIENT_VALUES_KEY, transientValues);
            }

            if (MapUtils.isNotEmpty(registryWebMap)) {
                registryWebMaps.add(registryWebMap);
            }
        }

        if (customSlots != null) {
            localNodes.put(CUSTOM_SLOTS_KEY, customSlots);
        }

        Map<String, Object> autoPopulateMap = new HashMap<>();
        autoPopulateMap.put(SERVICE_BINDINGS_KEY, endpointMap.values());
        localNodes.put(AUTO_POPULATE_VALUES_KEY, autoPopulateMap);

        localNodes.put("localNodes", registryWebMaps);

        return localNodes;
    }

    @Override
    public boolean registryStatus(String servicePID) {
        try {
            List<Source> sources = helper.getRegistrySources();
            for (Source source : sources) {
                if (source instanceof ConfiguredService) {
                    ConfiguredService cs = (ConfiguredService) source;
                    try {
                        Configuration config = helper.getConfiguration(cs);
                        if (config != null && config.getProperties()
                                .get("service.pid")
                                .equals(servicePID)) {
                            try {
                                return source.isAvailable();
                            } catch (Exception e) {
                                LOGGER.warn("Couldn't get availability on registry {}: {}",
                                        servicePID,
                                        e);
                            }
                        }
                    } catch (IOException e) {
                        LOGGER.warn("Couldn't find configuration for source '{}'", source.getId());
                    }
                } else {
                    LOGGER.warn("Source '{}' not a configured service", source.getId());
                }
            }
        } catch (InvalidSyntaxException e) {
            LOGGER.error("Could not get service reference list");
        }

        return false;
    }

    @Override
    public List<Map<String, Object>> allRegistryInfo() {

        List<Map<String, Object>> metatypes = helper.getMetatypes();

        for (Map metatype : metatypes) {
            try {
                List<Configuration> configs = helper.getConfigurations(metatype);

                ArrayList<Map<String, Object>> configurations = new ArrayList<>();
                if (configs != null) {
                    for (Configuration config : configs) {
                        Map<String, Object> registry = new HashMap<>();

                        boolean disabled = config.getPid()
                                .contains(DISABLED);
                        registry.put(MAP_ENTRY_ID, config.getPid());
                        registry.put(MAP_ENTRY_ENABLED, !disabled);
                        registry.put(MAP_ENTRY_FPID, config.getFactoryPid());

                        if (!disabled) {
                            registry.put(MAP_ENTRY_NAME, helper.getName(config));
                            registry.put(MAP_ENTRY_BUNDLE_NAME, helper.getBundleName(config));
                            registry.put(MAP_ENTRY_BUNDLE_LOCATION, config.getBundleLocation());
                            registry.put(MAP_ENTRY_BUNDLE, helper.getBundleId(config));
                        } else {
                            registry.put(MAP_ENTRY_NAME, config.getPid());
                        }

                        Dictionary<String, Object> properties = config.getProperties();
                        Map<String, Object> plist = new HashMap<>();
                        for (String key : Collections.list(properties.keys())) {
                            plist.put(key, properties.get(key));
                        }
                        registry.put(MAP_ENTRY_PROPERTIES, plist);

                        configurations.add(registry);
                    }
                    metatype.put(MAP_ENTRY_CONFIGURATIONS, configurations);
                }
            } catch (Exception e) {
                LOGGER.warn("Error getting registry info: {}", e.getMessage());
            }
        }

        Collections.sort(metatypes,
                (o1, o2) -> ((String) o1.get("id")).compareToIgnoreCase((String) o2.get("id")));
        return metatypes;
    }

    @Override
    public List<Map<String, Object>> allRegistryMetacards() {
        List<Map<String, Object>> registryMetacardInfo = new ArrayList<>();

        try {
            List<RegistryPackageType> registryMetacardObjects =
                    federationAdminService.getRegistryObjects();

            List<Metacard> metacards = federationAdminService.getRegistryMetacards();
            Map<String, Metacard> metacardByRegistryIdMap = getRegistryIdMetacardMap(metacards);

            for (RegistryPackageType registryPackage : registryMetacardObjects) {
                Map<String, Object> registryWebMap =
                        RegistryPackageWebConverter.getRegistryObjectWebMap(registryPackage);

                Metacard metacard = metacardByRegistryIdMap.get(registryPackage.getId());
                Map<String, Object> transientValues = getTransientValuesMap(metacard);
                if (MapUtils.isNotEmpty(transientValues)) {
                    registryWebMap.put(TRANSIENT_VALUES_KEY, transientValues);
                }

                if (MapUtils.isNotEmpty(registryWebMap)) {
                    registryMetacardInfo.add(registryWebMap);
                }
            }

        } catch (FederationAdminException e) {
            LOGGER.warn("Couldn't get remote registry metacards '{}'", e);
        }

        return registryMetacardInfo;
    }

    @Override
    public List<Serializable> updatePublications(String source, List<String> destinations)
            throws UnsupportedQueryException, SourceUnavailableException, FederationException {
        List<Serializable> updatedPublishedLocations = new ArrayList<>();

        if (CollectionUtils.isEmpty(destinations)) {
            return updatedPublishedLocations;
        }

        Filter filter = FILTER_FACTORY.and(FILTER_FACTORY.like(FILTER_FACTORY.property(
                RegistryObjectMetacardType.REGISTRY_ID), source),
                FILTER_FACTORY.like(FILTER_FACTORY.property(Metacard.TAGS),
                        RegistryConstants.REGISTRY_TAG));

        Query query = new QueryImpl(filter);

        QueryResponse queryResponse = catalogFramework.query(new QueryRequestImpl(query));

        List<Result> metacards = queryResponse.getResults();
        if (!CollectionUtils.isEmpty(metacards)) {
            Metacard metacard = metacards.get(0)
                    .getMetacard();
            if (metacard == null) {
                return updatedPublishedLocations;
            }

            List<Serializable> currentlyPublishedLocations = new ArrayList<>();
            Attribute publishedLocations =
                    metacard.getAttribute(RegistryObjectMetacardType.PUBLISHED_LOCATIONS);
            if (publishedLocations != null) {
                currentlyPublishedLocations.addAll(publishedLocations.getValues());
            }

            List<String> publishLocations = new ArrayList<>();
            for (String newDestination : destinations) {
                if (currentlyPublishedLocations.contains(newDestination)) {
                    updatedPublishedLocations.add(newDestination);
                } else {
                    publishLocations.add(newDestination);
                }
            }

            List<String> unpublishLocations = currentlyPublishedLocations.stream()
                    .map(destination -> (String) destination)
                    .filter(destination -> !destinations.contains(destination))
                    .collect(Collectors.toList());

            for (String id : publishLocations) {
                CreateRequest createRequest = new CreateRequestImpl(metacard);
                try {
                    CreateResponse createResponse = catalogStoreMap.get(id)
                            .create(createRequest);
                    if (createResponse.getProcessingErrors()
                            .isEmpty()) {
                        updatedPublishedLocations.addAll(createResponse.getCreatedMetacards()
                                .stream()
                                .filter(createdMetacard -> createdMetacard.getAttribute(
                                        RegistryObjectMetacardType.REGISTRY_ID)
                                        .equals(metacard.getAttribute(RegistryObjectMetacardType.REGISTRY_ID)))
                                .map(createdMetacard -> id)
                                .collect(Collectors.toList()));
                    }
                } catch (IngestException e) {
                    LOGGER.error("Unable to create metacard in catalogStore {}", id, e);
                    if (checkIfMetacardExists(id,
                            metacard.getAttribute(RegistryObjectMetacardType.REGISTRY_ID)
                                    .getValue()
                                    .toString())) {
                        //The metacard is still in the catalogStore, thus it was persisted
                        updatedPublishedLocations.add(id);
                    }
                }
            }
            for (String id : unpublishLocations) {
                DeleteRequest deleteRequest = new DeleteRequestImpl(metacard.getId());
                try {
                    DeleteResponse deleteResponse = catalogStoreMap.get(id)
                            .delete(deleteRequest);
                } catch (IngestException e) {
                    LOGGER.error("Unable to delete metacard from catalogStore {}", id, e);
                    if (checkIfMetacardExists(id,
                            metacard.getAttribute(RegistryObjectMetacardType.REGISTRY_ID)
                                    .getValue()
                                    .toString())) {
                        //The metacard is still in the catalogStore, thus wasn't unpublished
                        updatedPublishedLocations.add(id);
                    }

                }
            }

            metacard.setAttribute(new AttributeImpl(RegistryObjectMetacardType.PUBLISHED_LOCATIONS,
                    updatedPublishedLocations));
            Map<String, Serializable> properties = new HashMap<>();
            properties.put(RegistryConstants.TRANSIENT_ATTRIBUTE_UPDATE, true);

            List<Map.Entry<Serializable, Metacard>> updateList = new ArrayList<>();
            updateList.add(new AbstractMap.SimpleEntry<>(metacard.getId(), metacard));

            try {
                catalogFramework.update(new UpdateRequestImpl(updateList, Metacard.ID, properties));
            } catch (IngestException e) {
                LOGGER.error("Unable to update metacard", e);
            } catch (SourceUnavailableException e) {
                LOGGER.error("Unable to update metacard, source unavailable", e);
            }
        }

        return updatedPublishedLocations;
    }

    private Boolean checkIfMetacardExists(String storeId, String registryId)
            throws UnsupportedQueryException {

        Filter filter = FILTER_FACTORY.and(FILTER_FACTORY.like(FILTER_FACTORY.property(
                RegistryObjectMetacardType.REGISTRY_ID), registryId),
                FILTER_FACTORY.like(FILTER_FACTORY.property(Metacard.TAGS),
                        RegistryConstants.REGISTRY_TAG));

        Query query = new QueryImpl(filter);
        SourceResponse queryResponse = catalogStoreMap.get(storeId)
                .query(new QueryRequestImpl(query));
        return queryResponse.getHits() > 0;
    }

    private Metacard getRegistryMetacardFromRegistryPackage(RegistryPackageType registryPackage)
            throws FederationAdminException {
        if (registryPackage == null) {
            throw new FederationAdminException(
                    "Error creating metacard from registry package. Null package was received.");
        }
        Metacard metacard;

        try {
            JAXBElement<RegistryPackageType> jaxbRegistryObjectType =
                    RIM_FACTORY.createRegistryPackage(registryPackage);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            parser.marshal(marshalConfigurator, jaxbRegistryObjectType, baos);
            InputStream xmlInputStream = new ByteArrayInputStream(baos.toByteArray());
            metacard = registryTransformer.transform(xmlInputStream);

        } catch (IOException | CatalogTransformerException | ParserException e) {
            String message = "Error creating metacard from registry package.";
            LOGGER.error("{} Registry id: {}", message, registryPackage.getId());
            throw new FederationAdminException(message, e);
        }

        return metacard;
    }

    private Metacard getRegistryMetacardFromInputStream(InputStream inputStream)
            throws FederationAdminException {
        if (inputStream == null) {
            throw new FederationAdminException(
                    "Error converting input stream to a metacard. Null input stream provided.");
        }

        Metacard metacard;
        try {
            metacard = registryTransformer.transform(inputStream);
        } catch (IOException | CatalogTransformerException e) {
            throw new FederationAdminException(
                    "Error getting metacard. RegistryTransformer couldn't convert the input stream.",
                    e);
        }

        return metacard;
    }

    private Map<String, Metacard> getRegistryIdMetacardMap(List<Metacard> metacards) {
        Map<String, Metacard> registryIdMetacardMap = new HashMap<>();

        for (Metacard metacard : metacards) {
            String registryId = metacard.getAttribute(RegistryObjectMetacardType.REGISTRY_ID)
                    .getValue()
                    .toString();

            registryIdMetacardMap.put(registryId, metacard);
        }

        return registryIdMetacardMap;
    }

    private Map<String, Object> getTransientValuesMap(Metacard metacard) {
        Map<String, Object> transientValuesMap = new HashMap<>();
        if (metacard != null) {
            for (String transientAttributeKey : RegistryObjectMetacardType.TRANSIENT_ATTRIBUTES) {
                Attribute transientAttribute = metacard.getAttribute(transientAttributeKey);

                if (transientAttribute != null) {
                    transientValuesMap.put(transientAttributeKey, transientAttribute.getValues());
                }
            }
        }
        return transientValuesMap;
    }

    private void updateDateFields(RegistryPackageType rpt) {

        ExtrinsicObjectType nodeInfo = null;
        for (JAXBElement identifiable : rpt.getRegistryObjectList()
                .getIdentifiable()) {
            RegistryObjectType registryObject = (RegistryObjectType) identifiable.getValue();

            if (registryObject instanceof ExtrinsicObjectType
                    && RegistryConstants.REGISTRY_NODE_OBJECT_TYPE.equals(registryObject.getObjectType())) {
                nodeInfo = (ExtrinsicObjectType) registryObject;
                break;
            }
        }
        if (nodeInfo != null) {
            boolean liveDateFound = false;
            boolean lastUpdatedFound = false;

            OffsetDateTime now = OffsetDateTime.now(ZoneId.of(ZoneOffset.UTC.toString()));
            String rightNow = now.toString();
            ValueListType valueList = RIM_FACTORY.createValueListType();
            valueList.getValue()
                    .add(rightNow);
            for (SlotType1 slot : nodeInfo.getSlot()) {
                if (slot.getName()
                        .equals(RegistryConstants.XML_LIVE_DATE_NAME)) {
                    liveDateFound = true;
                } else if (slot.getName()
                        .equals(RegistryConstants.XML_LAST_UPDATED_NAME)) {
                    slot.setValueList(RIM_FACTORY.createValueList(valueList));
                    lastUpdatedFound = true;
                }
            }

            if (!liveDateFound) {
                SlotType1 liveDate = RIM_FACTORY.createSlotType1();
                liveDate.setValueList(RIM_FACTORY.createValueList(valueList));
                liveDate.setSlotType(DatatypeConstants.DATETIME.toString());
                liveDate.setName(RegistryConstants.XML_LIVE_DATE_NAME);
                nodeInfo.getSlot()
                        .add(liveDate);
            }

            if (!lastUpdatedFound) {
                SlotType1 lastUpdated = RIM_FACTORY.createSlotType1();
                lastUpdated.setValueList(RIM_FACTORY.createValueList(valueList));
                lastUpdated.setSlotType(DatatypeConstants.DATETIME.toString());
                lastUpdated.setName(RegistryConstants.XML_LAST_UPDATED_NAME);
                nodeInfo.getSlot()
                        .add(lastUpdated);
            }
        }
    }

    private void configureMBean() {
        mbeanServer = ManagementFactory.getPlatformMBeanServer();

        try {
            objectName = new ObjectName(FederationAdminMBean.OBJECT_NAME);
        } catch (MalformedObjectNameException e) {
            LOGGER.warn("Exception while creating object name: " + FederationAdminMBean.OBJECT_NAME,
                    e);
        }

        try {
            try {
                mbeanServer.registerMBean(new StandardMBean(this, FederationAdminMBean.class),
                        objectName);
            } catch (InstanceAlreadyExistsException e) {
                mbeanServer.unregisterMBean(objectName);
                mbeanServer.registerMBean(new StandardMBean(this, FederationAdminMBean.class),
                        objectName);
            }
        } catch (Exception e) {
            LOGGER.error("Could not register mbean.", e);
        }
    }

    public void destroy() {
        try {
            if (objectName != null && mbeanServer != null) {
                mbeanServer.unregisterMBean(objectName);
            }
        } catch (Exception e) {
            LOGGER.warn("Exception un registering mbean: ", e);
            throw new RuntimeException(e);
        }
    }

    public void init() {

        String registryFieldsPath = String.format("%s%s%s%s%s",
                System.getProperty(KARAF_ETC),
                File.separator,
                REGISTRY_CONFIG_DIR,
                File.separator,
                REGISTRY_FIELDS_FILE);

        Path path = Paths.get(registryFieldsPath);
        if (path.toFile()
                .exists()) {
            try {
                String registryFieldsJsonString = new String(Files.readAllBytes(path),
                        StandardCharsets.UTF_8);
                Gson gson = new Gson();
                customSlots = new HashMap<>();
                customSlots = (Map<String, Object>) gson.fromJson(registryFieldsJsonString,
                        customSlots.getClass());
            } catch (IOException e) {
                LOGGER.error(
                        "Error reading {}. This will result in no custom fields being shown for registry node editing",
                        registryFieldsPath,
                        e);
            }
        }
    }

    public void bindEndpoint(ServiceReference reference) {
        BundleContext context = getContext();
        if (reference != null && context != null) {
            CatalogEndpoint endpoint = (CatalogEndpoint) context.getService(reference);
            Map<String, String> properties = endpoint.getEndpointProperties();
            endpointMap.put(properties.get(CatalogEndpoint.ID_KEY), properties);
        }
    }

    public void unbindEndpoint(ServiceReference reference) {
        BundleContext context = getContext();
        if (reference != null && context != null) {
            CatalogEndpoint endpoint = (CatalogEndpoint) context.getService(reference);
            Map<String, String> properties = endpoint.getEndpointProperties();
            endpointMap.remove(properties.get(CatalogEndpoint.ID_KEY));
        }
    }

    protected BundleContext getContext() {
        Bundle bundle = FrameworkUtil.getBundle(FederationAdmin.class);
        if (bundle != null) {
            return bundle.getBundleContext();
        }
        return null;
    }

    protected AdminRegistryHelper getAdminRegistryHelper(ConfigurationAdmin configurationAdmin) {
        return new AdminRegistryHelper(configurationAdmin);
    }

    protected static class AdminRegistryHelper {
        private ConfigurationAdmin configurationAdmin;

        public AdminRegistryHelper(ConfigurationAdmin configurationAdmin) {
            this.configurationAdmin = configurationAdmin;
        }

        private BundleContext getBundleContext() {
            Bundle bundle = FrameworkUtil.getBundle(this.getClass());
            if (bundle != null) {
                return bundle.getBundleContext();
            }
            return null;
        }

        protected List<Source> getRegistrySources()
                throws org.osgi.framework.InvalidSyntaxException {
            List<Source> sources = new ArrayList<>();
            List<ServiceReference<? extends Source>> refs = new ArrayList<>();
            refs.addAll(getBundleContext().getServiceReferences(RegistryStore.class, null));

            for (ServiceReference<? extends Source> ref : refs) {
                sources.add(getBundleContext().getService(ref));
            }

            return sources;
        }

        protected List<Map<String, Object>> getMetatypes() {
            ConfigurationAdminExt configAdminExt = new ConfigurationAdminExt(configurationAdmin);
            return configAdminExt.addMetaTypeNamesToMap(configAdminExt.getFactoryPidObjectClasses(),
                    REGISTRY_FILTER,
                    "service.factoryPid");
        }

        protected List getConfigurations(Map metatype) throws InvalidSyntaxException, IOException {
            return org.apache.shiro.util.CollectionUtils.asList(configurationAdmin.listConfigurations(
                    "(|(service.factoryPid=" + metatype.get(MAP_ENTRY_ID) + ")(service.factoryPid="
                            + metatype.get(MAP_ENTRY_ID) + DISABLED + "))"));
        }

        protected Configuration getConfiguration(ConfiguredService cs) throws IOException {
            return configurationAdmin.getConfiguration(cs.getConfigurationPid());
        }

        protected String getBundleName(Configuration config) {
            ConfigurationAdminExt configAdminExt = new ConfigurationAdminExt(configurationAdmin);
            return configAdminExt.getName(getBundleContext().getBundle(config.getBundleLocation()));
        }

        protected long getBundleId(Configuration config) {
            return getBundleContext().getBundle(config.getBundleLocation())
                    .getBundleId();
        }

        protected String getName(Configuration config) {
            ConfigurationAdminExt configAdminExt = new ConfigurationAdminExt(configurationAdmin);
            return ((ObjectClassDefinition) configAdminExt.getFactoryPidObjectClasses()
                    .get(config.getFactoryPid())).getName();
        }
    }

    public void setCatalogFramework(CatalogFramework catalogFramework) {
        this.catalogFramework = catalogFramework;
    }

    public void setCatalogStoreMap(Map<String, CatalogStore> catalogStoreMap) {
        this.catalogStoreMap = catalogStoreMap;
    }

    public void setFederationAdminService(FederationAdminService federationAdminService) {
        this.federationAdminService = federationAdminService;
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

        this.marshalConfigurator = parser.configureParser(contextPath, classLoader);
        this.marshalConfigurator.addProperty(Marshaller.JAXB_FRAGMENT, true);

        this.parser = parser;
    }

    public void setRegistryTransformer(InputTransformer inputTransformer) {
        this.registryTransformer = inputTransformer;
    }
}
