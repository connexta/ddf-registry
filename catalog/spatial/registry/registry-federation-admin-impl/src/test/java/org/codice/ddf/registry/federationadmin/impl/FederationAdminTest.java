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

import static org.custommonkey.xmlunit.XMLAssert.assertXpathEvaluatesTo;
import static org.custommonkey.xmlunit.XMLAssert.assertXpathExists;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBElement;

import org.apache.commons.io.IOUtils;
import org.codice.ddf.configuration.SystemBaseUrl;
import org.codice.ddf.parser.Parser;
import org.codice.ddf.parser.ParserConfigurator;
import org.codice.ddf.parser.ParserException;
import org.codice.ddf.parser.xml.XmlParser;
import org.codice.ddf.registry.api.RegistryStore;
import org.codice.ddf.registry.common.RegistryConstants;
import org.codice.ddf.registry.common.metacard.RegistryObjectMetacardType;
import org.codice.ddf.registry.federationadmin.service.FederationAdminException;
import org.codice.ddf.registry.federationadmin.service.FederationAdminService;
import org.codice.ddf.registry.schemabindings.RegistryPackageUtils;
import org.codice.ddf.registry.schemabindings.RegistryPackageWebConverter;
import org.codice.ddf.registry.transformer.RegistryTransformer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;

import ddf.catalog.CatalogFramework;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.impl.AttributeImpl;
import ddf.catalog.data.impl.MetacardImpl;
import ddf.catalog.data.impl.ResultImpl;
import ddf.catalog.endpoint.CatalogEndpoint;
import ddf.catalog.operation.CreateRequest;
import ddf.catalog.operation.CreateResponse;
import ddf.catalog.operation.DeleteRequest;
import ddf.catalog.operation.DeleteResponse;
import ddf.catalog.operation.QueryRequest;
import ddf.catalog.operation.QueryResponse;
import ddf.catalog.operation.UpdateRequest;
import ddf.catalog.operation.impl.ProcessingDetailsImpl;
import ddf.catalog.service.ConfiguredService;
import ddf.catalog.source.CatalogStore;
import ddf.catalog.source.IngestException;
import ddf.catalog.source.Source;
import ddf.catalog.transform.CatalogTransformerException;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryObjectType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryPackageType;

@RunWith(MockitoJUnitRunner.class)
public class FederationAdminTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Mock
    private FederationAdminService federationAdminService;

    @Mock
    private RegistryTransformer registryTransformer;

    @Mock
    private AdminHelper helper;

    @Mock
    private CatalogFramework catalogFramework;

    @Mock
    private BundleContext context;

    private Parser parser;

    private ParserConfigurator configurator;

    private FederationAdmin federationAdmin;

    private Map<String, CatalogStore> catalogStoreMap = new HashMap<>();

    private static final String LOCAL_NODE_KEY = "localNodes";

    @Before
    public void setUp() {
        parser = new XmlParser();
        configurator = parser.configureParser(Arrays.asList(RegistryObjectType.class.getPackage()
                        .getName(),
                RegistryPackageUtils.OGC_FACTORY.getClass()
                        .getPackage()
                        .getName(),
                RegistryPackageUtils.GML_FACTORY.getClass()
                        .getPackage()
                        .getName()),
                this.getClass()
                        .getClassLoader());
        federationAdmin = new FederationAdmin(helper) {
            @Override
            public BundleContext getContext() {
                return context;
            }
        };
        federationAdmin.setFederationAdminService(federationAdminService);
        federationAdmin.setRegistryTransformer(registryTransformer);
        federationAdmin.setParser(parser);
        federationAdmin.setCatalogFramework(catalogFramework);
        federationAdmin.setCatalogStoreMap(catalogStoreMap);
    }

    @Test
    public void testCreateLocalEntry() throws Exception {
        String metacardId = "metacardId";
        RegistryObjectType registryObject = getRegistryObjectFromResource(
                "/csw-full-registry-package.xml");
        Map<String, Object> registryMap = getMapFromRegistryObject(registryObject);

        Metacard metacard = getTestMetacard();
        when(registryTransformer.transform(any(InputStream.class))).thenReturn(metacard);
        when(federationAdminService.addRegistryEntry(any(Metacard.class))).thenReturn(metacardId);

        String createdMetacardId = federationAdmin.createLocalEntry(registryMap);

        assertThat(createdMetacardId, is(equalTo(metacardId)));
        verify(federationAdminService).addRegistryEntry(metacard);
    }

    @Test
    public void testCreateLocalEntryMissingAttributes() throws Exception {
        String metacardId = "metacardId";
        RegistryObjectType registryObject = getRegistryObjectFromResource(
                "/csw-full-registry-package.xml");
        Map<String, Object> registryMap = getMapFromRegistryObject(registryObject);
        registryMap.remove("id");
        registryMap.remove("home");
        registryMap.remove("objectType");
        Metacard metacard = getTestMetacard();
        when(registryTransformer.transform(any(InputStream.class))).thenReturn(metacard);
        when(federationAdminService.addRegistryEntry(any(Metacard.class))).thenReturn(metacardId);

        federationAdmin.createLocalEntry(registryMap);
        ArgumentCaptor<InputStream> captor = ArgumentCaptor.forClass(InputStream.class);
        verify(registryTransformer).transform(captor.capture());
        String ebrim = IOUtils.toString(captor.getValue());
        IOUtils.closeQuietly(captor.getValue());
        assertXpathEvaluatesTo(SystemBaseUrl.getBaseUrl(),
                "/*[local-name() = 'RegistryPackage']/@home",
                ebrim);
        assertXpathEvaluatesTo(RegistryConstants.REGISTRY_NODE_OBJECT_TYPE,
                "/*[local-name() = 'RegistryPackage']/@objectType",
                ebrim);
        assertXpathExists("/*[local-name() = 'RegistryPackage']/@id", ebrim);
    }

    @Test(expected = FederationAdminException.class)
    public void testCreateLocalEntryWithEmptyMap() throws Exception {
        Map<String, Object> registryMap = new HashMap<>();
        federationAdmin.createLocalEntry(registryMap);

        verify(federationAdminService, never()).addRegistryEntry(any(Metacard.class));
    }

    @Test(expected = FederationAdminException.class)
    public void testCreateLocalEntryWithBadMap() throws Exception {
        Map<String, Object> registryMap = new HashMap<>();
        registryMap.put("BadKey", "BadValue");

        federationAdmin.createLocalEntry(registryMap);
        verify(federationAdminService, never()).addRegistryEntry(any(Metacard.class));
    }

    @Test(expected = FederationAdminException.class)
    public void testCreateLocalEntryWithFederationAdminException() throws Exception {
        RegistryObjectType registryObject = getRegistryObjectFromResource(
                "/csw-full-registry-package.xml");
        Map<String, Object> registryMap = getMapFromRegistryObject(registryObject);

        Metacard metacard = getTestMetacard();
        when(registryTransformer.transform(any(InputStream.class))).thenReturn(metacard);
        when(federationAdminService.addRegistryEntry(any(Metacard.class))).thenThrow(
                FederationAdminException.class);

        federationAdmin.createLocalEntry(registryMap);

        verify(federationAdminService).addRegistryEntry(metacard);
    }

    @Test
    public void testCreateLocalEntryString() throws Exception {
        String encodeThisString = "aPretendXmlRegistryPackage";
        String metacardId = "createdMetacardId";
        String base64EncodedString = Base64.getEncoder()
                .encodeToString(encodeThisString.getBytes());

        Metacard metacard = getTestMetacard();

        when(registryTransformer.transform(any(InputStream.class))).thenReturn(metacard);
        when(federationAdminService.addRegistryEntry(metacard)).thenReturn(metacardId);

        String createdMetacardId = federationAdmin.createLocalEntry(base64EncodedString);

        assertThat(createdMetacardId, is(equalTo(metacardId)));
        verify(registryTransformer).transform(any(InputStream.class));
        verify(federationAdminService).addRegistryEntry(metacard);
    }

    @Test(expected = FederationAdminException.class)
    public void testCreateLocalEntryStringWithBlankString() throws Exception {
        String base64EncodedString = "";

        federationAdmin.createLocalEntry(base64EncodedString);

        verify(registryTransformer, never()).transform(any(InputStream.class));
        verify(federationAdminService, never()).addRegistryEntry(any(Metacard.class));
    }

    @Test(expected = FederationAdminException.class)
    public void testCreateLocalEntryStringWithTransformerException() throws Exception {
        String encodeThisString = "aPretendXmlRegistryPackage";
        String base64EncodedString = Base64.getEncoder()
                .encodeToString(encodeThisString.getBytes());

        when(registryTransformer.transform(any(InputStream.class))).thenThrow(
                CatalogTransformerException.class);

        federationAdmin.createLocalEntry(base64EncodedString);

        verify(registryTransformer).transform(any(InputStream.class));
        verify(federationAdminService, never()).addRegistryEntry(any(Metacard.class));
    }

    @Test(expected = FederationAdminException.class)
    public void testCreateLocalEntryStringWithDecodeError() throws Exception {
        // This is has an illegal base64 character
        String base64EncodedString = "[B@6499375d";

        federationAdmin.createLocalEntry(base64EncodedString);

        verify(registryTransformer, never()).transform(any(InputStream.class));
        verify(federationAdminService, never()).addRegistryEntry(any(Metacard.class));
    }

    @Test
    public void testUpdateLocalEntry() throws Exception {
        RegistryObjectType registryObject = getRegistryObjectFromResource(
                "/csw-registry-package-smaller.xml");
        Map<String, Object> registryMap = getMapFromRegistryObject(registryObject);
        String existingMetacardId = "someUpdateMetacardId";

        Metacard existingMetacard = getTestMetacard();
        existingMetacard.setAttribute(new AttributeImpl(Metacard.ID, existingMetacardId));
        List<Metacard> existingMetacards = new ArrayList<>();
        existingMetacards.add(existingMetacard);
        Metacard updateMetacard = getTestMetacard();

        when(federationAdminService.getLocalRegistryMetacardsByRegistryIds(Collections.singletonList(
                registryObject.getId()))).thenReturn(existingMetacards);
        when(registryTransformer.transform(any(InputStream.class))).thenReturn(updateMetacard);

        federationAdmin.updateLocalEntry(registryMap);

        verify(federationAdminService).getLocalRegistryMetacardsByRegistryIds(Collections.singletonList(
                registryObject.getId()));
        verify(registryTransformer).transform(any(InputStream.class));
        verify(federationAdminService).updateRegistryEntry(updateMetacard);
    }

    @Test(expected = FederationAdminException.class)
    public void testUpdateLocalEntryWithEmptyMap() throws Exception {
        Map<String, Object> registryMap = new HashMap<>();

        federationAdmin.updateLocalEntry(registryMap);

        verify(federationAdminService,
                never()).getLocalRegistryMetacardsByRegistryIds(Collections.singletonList(any(String.class)));
        verify(registryTransformer, never()).transform(any(InputStream.class));
        verify(federationAdminService, never()).updateRegistryEntry(any(Metacard.class));
    }

    @Test(expected = FederationAdminException.class)
    public void testUpdateLocalEntryWithBadMap() throws Exception {
        Map<String, Object> registryMap = new HashMap<>();
        registryMap.put("BadKey", "BadValue");

        federationAdmin.updateLocalEntry(registryMap);

        verify(federationAdminService,
                never()).getLocalRegistryMetacardsByRegistryIds(Collections.singletonList(any(String.class)));
        verify(registryTransformer, never()).transform(any(InputStream.class));
        verify(federationAdminService, never()).updateRegistryEntry(any(Metacard.class));
    }

    @Test(expected = FederationAdminException.class)
    public void testUpdateLocalEntryWithEmptyExistingList() throws Exception {
        RegistryObjectType registryObject = getRegistryObjectFromResource(
                "/csw-full-registry-package.xml");
        Map<String, Object> registryMap = getMapFromRegistryObject(registryObject);
        List<Metacard> existingMetacards = new ArrayList<>();

        when(federationAdminService.getLocalRegistryMetacardsByRegistryIds(Collections.singletonList(
                registryObject.getId()))).thenReturn(existingMetacards);

        federationAdmin.updateLocalEntry(registryMap);

        verify(federationAdminService,
                never()).getLocalRegistryMetacardsByRegistryIds(Collections.singletonList(
                registryObject.getId()));
        verify(registryTransformer, never()).transform(any(InputStream.class));
        verify(federationAdminService, never()).updateRegistryEntry(any(Metacard.class));
    }

    @Test(expected = FederationAdminException.class)
    public void testUpdateLocalEntryWithMultipleExistingMetacards() throws Exception {
        RegistryObjectType registryObject = getRegistryObjectFromResource(
                "/csw-full-registry-package.xml");
        Map<String, Object> registryMap = getMapFromRegistryObject(registryObject);
        List<Metacard> existingMetacards = new ArrayList<>();
        existingMetacards.add(getTestMetacard());
        existingMetacards.add(getTestMetacard());

        when(federationAdminService.getLocalRegistryMetacardsByRegistryIds(Collections.singletonList(
                registryObject.getId()))).thenReturn(existingMetacards);

        federationAdmin.updateLocalEntry(registryMap);

        verify(federationAdminService,
                never()).getLocalRegistryMetacardsByRegistryIds(Collections.singletonList(
                registryObject.getId()));
        verify(registryTransformer, never()).transform(any(InputStream.class));
        verify(federationAdminService, never()).updateRegistryEntry(any(Metacard.class));
    }

    @Test(expected = FederationAdminException.class)
    public void testUpdateLocalEntryWithFederationAdminServiceException() throws Exception {
        RegistryObjectType registryObject = getRegistryObjectFromResource(
                "/csw-full-registry-package.xml");
        Map<String, Object> registryMap = getMapFromRegistryObject(registryObject);
        String existingMetacardId = "someUpdateMetacardId";

        Metacard existingMetacard = getTestMetacard();
        existingMetacard.setAttribute(new AttributeImpl(Metacard.ID, existingMetacardId));
        List<Metacard> existingMetacards = new ArrayList<>();
        existingMetacards.add(existingMetacard);
        Metacard updateMetacard = getTestMetacard();

        when(federationAdminService.getLocalRegistryMetacardsByRegistryIds(Collections.singletonList(
                registryObject.getId()))).thenReturn(existingMetacards);
        when(registryTransformer.transform(any(InputStream.class))).thenReturn(updateMetacard);
        doThrow(FederationAdminException.class).when(federationAdminService)
                .updateRegistryEntry(updateMetacard);

        federationAdmin.updateLocalEntry(registryMap);

        verify(federationAdminService).getLocalRegistryMetacardsByRegistryIds(Collections.singletonList(
                registryObject.getId()));
        verify(registryTransformer).transform(any(InputStream.class));
        verify(federationAdminService).updateRegistryEntry(updateMetacard);
    }

    @Test
    public void testDeleteLocalEntry() throws Exception {
        String firstRegistryId = "firstRegistryId";
        String secondRegistryId = "secondRegistryId";
        List<String> ids = new ArrayList<>();
        ids.add(firstRegistryId);
        ids.add(secondRegistryId);

        String firstMetacardId = "firstMetacardId";
        String secondMetacardId = "secondMetacardId";

        Metacard firstMetacard = getTestMetacard();
        firstMetacard.setAttribute(new AttributeImpl(Metacard.ID, firstMetacardId));
        Metacard secondMetacard = getTestMetacard();
        secondMetacard.setAttribute(new AttributeImpl(Metacard.ID, secondMetacardId));

        List<Metacard> matchingMetacards = new ArrayList<>();
        matchingMetacards.add(firstMetacard);
        matchingMetacards.add(secondMetacard);

        List<String> metacardIds = new ArrayList<>();
        metacardIds.addAll(matchingMetacards.stream()
                .map(Metacard::getId)
                .collect(Collectors.toList()));

        when(federationAdminService.getLocalRegistryMetacardsByRegistryIds(ids)).thenReturn(
                matchingMetacards);

        federationAdmin.deleteLocalEntry(ids);

        verify(federationAdminService).getLocalRegistryMetacardsByRegistryIds(ids);
        verify(federationAdminService).deleteRegistryEntriesByMetacardIds(metacardIds);
    }

    @Test(expected = FederationAdminException.class)
    public void testDeleteLocalEntryWithEmptyList() throws Exception {
        List<String> ids = new ArrayList<>();

        federationAdmin.deleteLocalEntry(ids);

        verify(federationAdminService, never()).getLocalRegistryMetacardsByRegistryIds(anyList());
        verify(federationAdminService, never()).deleteRegistryEntriesByMetacardIds(anyList());
    }

    @Test(expected = FederationAdminException.class)
    public void testDeleteLocalEntryWithExceptionGettingLocalMetacards() throws Exception {
        List<String> ids = new ArrayList<>();
        ids.add("whatever");

        when(federationAdminService.getLocalRegistryMetacardsByRegistryIds(ids)).thenThrow(
                FederationAdminException.class);

        federationAdmin.deleteLocalEntry(ids);

        verify(federationAdminService).getLocalRegistryMetacardsByRegistryIds(ids);
        verify(federationAdminService, never()).deleteRegistryEntriesByMetacardIds(anyList());
    }

    @Test(expected = FederationAdminException.class)
    public void testDeleteLocalEntryWithNonMatchingLists() throws Exception {
        String firstRegistryId = "firstRegistryId";
        String secondRegistryId = "secondRegistryId";
        List<String> ids = new ArrayList<>();
        ids.add(firstRegistryId);
        ids.add(secondRegistryId);

        String firstMetacardId = "firstMetacardId";

        Metacard firstMetacard = getTestMetacard();
        firstMetacard.setAttribute(new AttributeImpl(Metacard.ID, firstMetacardId));

        List<Metacard> matchingMetacards = new ArrayList<>();
        matchingMetacards.add(firstMetacard);

        List<String> metacardIds = new ArrayList<>();
        metacardIds.addAll(matchingMetacards.stream()
                .map(Metacard::getId)
                .collect(Collectors.toList()));

        when(federationAdminService.getLocalRegistryMetacardsByRegistryIds(ids)).thenReturn(
                matchingMetacards);

        federationAdmin.deleteLocalEntry(ids);

        verify(federationAdminService).getLocalRegistryMetacardsByRegistryIds(ids);
        verify(federationAdminService, never()).deleteRegistryEntriesByMetacardIds(metacardIds);
    }

    @Test(expected = FederationAdminException.class)
    public void testDeleteLocalEntryWithExceptionDeletingEntries() throws Exception {
        List<String> ids = new ArrayList<>();
        ids.add("firstId");

        doThrow(FederationAdminException.class).when(federationAdminService)
                .deleteRegistryEntriesByRegistryIds(ids);
        federationAdmin.deleteLocalEntry(ids);

        verify(federationAdminService).deleteRegistryEntriesByRegistryIds(ids);
    }

    @Test
    public void testGetLocalNodes() throws Exception {
        RegistryObjectType registryObject = getRegistryObjectFromResource(
                "/csw-registry-package-smaller.xml");
        Map<String, Object> registryObjectMap = RegistryPackageWebConverter.getRegistryObjectWebMap(
                registryObject);
        List<RegistryPackageType> registryPackages = new ArrayList<>();
        registryPackages.add((RegistryPackageType) registryObject);

        when(federationAdminService.getLocalRegistryObjects()).thenReturn(registryPackages);
        Map<String, Object> localNodes = federationAdmin.getLocalNodes();

        Map<String, Object> localNode =
                ((List<Map<String, Object>>) localNodes.get(LOCAL_NODE_KEY)).get(0);
        verify(federationAdminService).getLocalRegistryObjects();
        assertThat(localNode, is(equalTo(registryObjectMap)));
    }

    @Test(expected = FederationAdminException.class)
    public void testGetLocalNodesWithFederationAdminException() throws Exception {
        when(federationAdminService.getLocalRegistryObjects()).thenThrow(FederationAdminException.class);

        federationAdmin.getLocalNodes();

        verify(federationAdminService).getLocalRegistryObjects();
    }

    @Test
    public void testUpdatePublicationsNoDestinations() throws Exception {
        assertThat(federationAdmin.updatePublications("myid", null)
                .size(), is(0));
    }

    @Test
    public void testUpdatePublicationsNoSource() throws Exception {
        assertThat(federationAdmin.updatePublications(null, new ArrayList<>())
                .size(), is(0));
        assertThat(federationAdmin.updatePublications("", new ArrayList<>())
                .size(), is(0));
    }

    @Test
    public void testUpdatePublicationsNewPublication() throws Exception {
        MetacardImpl mcard = new MetacardImpl(new RegistryObjectMetacardType());
        mcard.setAttribute(RegistryObjectMetacardType.REGISTRY_ID, "myId");
        mcard.setAttribute(RegistryObjectMetacardType.PUBLISHED_LOCATIONS, new ArrayList<>());
        QueryResponse queryResponse = mock(QueryResponse.class);
        when(queryResponse.getResults()).thenReturn(Collections.singletonList(new ResultImpl(mcard)));
        when(catalogFramework.query(any(QueryRequest.class))).thenReturn(queryResponse);
        CatalogStore store = mock(CatalogStore.class);
        catalogStoreMap.put("myDest", store);
        CreateResponse createResponse = mock(CreateResponse.class);
        when(store.create(any(CreateRequest.class))).thenReturn(createResponse);
        when(createResponse.getProcessingErrors()).thenReturn(new HashSet<>());

        List<Serializable> result = federationAdmin.updatePublications("myId",
                Collections.singletonList("myDest"));
        verify(store, times(1)).create(any(CreateRequest.class));
        verify(catalogFramework, times(1)).update(any(UpdateRequest.class));
        assertThat(result.get(0), equalTo("myDest"));
    }

    @Test
    public void testUpdatePublicationsNewPublicationError() throws Exception {
        MetacardImpl mcard = new MetacardImpl(new RegistryObjectMetacardType());
        mcard.setAttribute(RegistryObjectMetacardType.REGISTRY_ID, "myId");
        mcard.setAttribute(RegistryObjectMetacardType.PUBLISHED_LOCATIONS, new ArrayList<>());
        QueryResponse queryResponse = mock(QueryResponse.class);
        when(queryResponse.getResults()).thenReturn(Collections.singletonList(new ResultImpl(mcard)));
        when(catalogFramework.query(any(QueryRequest.class))).thenReturn(queryResponse);
        CatalogStore store = mock(CatalogStore.class);
        catalogStoreMap.put("myDest", store);

        when(store.create(any(CreateRequest.class))).thenThrow(new IngestException("Bad Ingest"));

        List<Serializable> result = federationAdmin.updatePublications("myId",
                Collections.singletonList("myDest"));
        verify(store, times(1)).create(any(CreateRequest.class));
        verify(catalogFramework, times(0)).update(any(UpdateRequest.class));
        assertThat(result.size(), is(0));
    }

    @Test
    public void testUpdatePublicationsNewPublicationError2() throws Exception {
        MetacardImpl mcard = new MetacardImpl(new RegistryObjectMetacardType());
        mcard.setAttribute(RegistryObjectMetacardType.REGISTRY_ID, "myId");
        mcard.setAttribute(RegistryObjectMetacardType.PUBLISHED_LOCATIONS, new ArrayList<>());
        QueryResponse queryResponse = mock(QueryResponse.class);
        when(queryResponse.getResults()).thenReturn(Collections.singletonList(new ResultImpl(mcard)));
        when(catalogFramework.query(any(QueryRequest.class))).thenReturn(queryResponse);
        CatalogStore store = mock(CatalogStore.class);
        catalogStoreMap.put("myDest", store);
        CreateResponse createResponse = mock(CreateResponse.class);
        when(store.create(any(CreateRequest.class))).thenReturn(createResponse);
        when(createResponse.getProcessingErrors()).thenReturn(Collections.singleton(new ProcessingDetailsImpl()));

        List<Serializable> result = federationAdmin.updatePublications("myId",
                Collections.singletonList("myDest"));
        verify(store, times(1)).create(any(CreateRequest.class));
        verify(catalogFramework, times(0)).update(any(UpdateRequest.class));
        assertThat(result.size(), is(0));
    }

    @Test
    public void testUpdatePublicationsRemovePublication() throws Exception {
        MetacardImpl mcard = new MetacardImpl(new RegistryObjectMetacardType());
        mcard.setAttribute(RegistryObjectMetacardType.REGISTRY_ID, "myId");
        ArrayList<String> dest = new ArrayList<>();
        dest.add("myDest");
        mcard.setAttribute(new AttributeImpl(RegistryObjectMetacardType.PUBLISHED_LOCATIONS, dest));
        QueryResponse queryResponse = mock(QueryResponse.class);
        when(queryResponse.getResults()).thenReturn(Collections.singletonList(new ResultImpl(mcard)));
        when(catalogFramework.query(any(QueryRequest.class))).thenReturn(queryResponse);
        CatalogStore store = mock(CatalogStore.class);
        catalogStoreMap.put("myDest", store);
        DeleteResponse deleteResponse = mock(DeleteResponse.class);
        when(store.delete(any(DeleteRequest.class))).thenReturn(deleteResponse);

        List<Serializable> result = federationAdmin.updatePublications("myId", new ArrayList<>());
        verify(store, times(0)).create(any(CreateRequest.class));
        verify(store, times(1)).delete(any(DeleteRequest.class));
        verify(catalogFramework, times(1)).update(any(UpdateRequest.class));
        assertThat(result.size(), is(0));
    }

    @Test
    public void testUpdatePublicationsRemovePublicationError() throws Exception {
        MetacardImpl mcard = new MetacardImpl(new RegistryObjectMetacardType());
        mcard.setAttribute(RegistryObjectMetacardType.REGISTRY_ID, "myId");
        ArrayList<String> dest = new ArrayList<>();
        dest.add("myDest");
        mcard.setAttribute(new AttributeImpl(RegistryObjectMetacardType.PUBLISHED_LOCATIONS, dest));
        QueryResponse queryResponse = mock(QueryResponse.class);
        when(queryResponse.getResults()).thenReturn(Collections.singletonList(new ResultImpl(mcard)));
        when(catalogFramework.query(any(QueryRequest.class))).thenReturn(queryResponse);
        CatalogStore store = mock(CatalogStore.class);
        catalogStoreMap.put("myDest", store);
        when(store.delete(any(DeleteRequest.class))).thenThrow(new IngestException("Bad delete"));

        List<Serializable> result = federationAdmin.updatePublications("myId", new ArrayList<>());
        verify(store, times(0)).create(any(CreateRequest.class));
        verify(store, times(1)).delete(any(DeleteRequest.class));
        verify(catalogFramework, times(0)).update(any(UpdateRequest.class));
        assertThat(result.size(), is(1));
    }

    @Test
    public void testUpdatePublicationsRemovePublicationError2() throws Exception {
        MetacardImpl mcard = new MetacardImpl(new RegistryObjectMetacardType());
        mcard.setAttribute(RegistryObjectMetacardType.REGISTRY_ID, "myId");
        ArrayList<String> dest = new ArrayList<>();
        dest.add("myDest");
        mcard.setAttribute(new AttributeImpl(RegistryObjectMetacardType.PUBLISHED_LOCATIONS, dest));
        QueryResponse queryResponse = mock(QueryResponse.class);
        when(queryResponse.getResults()).thenReturn(Collections.singletonList(new ResultImpl(mcard)));
        when(catalogFramework.query(any(QueryRequest.class))).thenReturn(queryResponse);
        CatalogStore store = mock(CatalogStore.class);
        catalogStoreMap.put("myDest", store);
        DeleteResponse deleteResponse = mock(DeleteResponse.class);
        when(store.delete(any(DeleteRequest.class))).thenReturn(deleteResponse);
        when(deleteResponse.getProcessingErrors()).thenReturn(Collections.singleton(new ProcessingDetailsImpl()));
        List<Serializable> result = federationAdmin.updatePublications("myId", new ArrayList<>());
        verify(store, times(0)).create(any(CreateRequest.class));
        verify(store, times(1)).delete(any(DeleteRequest.class));
        verify(catalogFramework, times(0)).update(any(UpdateRequest.class));
        assertThat(result.size(), is(1));
    }

    @Test
    public void testUpdatePublicationsNoUpdateNeeded() throws Exception {
        MetacardImpl mcard = new MetacardImpl(new RegistryObjectMetacardType());
        mcard.setAttribute(RegistryObjectMetacardType.REGISTRY_ID, "myId");
        ArrayList<String> dest = new ArrayList<>();
        dest.add("myDest");
        mcard.setAttribute(RegistryObjectMetacardType.PUBLISHED_LOCATIONS, dest);
        QueryResponse queryResponse = mock(QueryResponse.class);
        when(queryResponse.getResults()).thenReturn(Collections.singletonList(new ResultImpl(mcard)));
        when(catalogFramework.query(any(QueryRequest.class))).thenReturn(queryResponse);
        CatalogStore store = mock(CatalogStore.class);
        catalogStoreMap.put("myDest", store);

        List<Serializable> result = federationAdmin.updatePublications("myId",
                Collections.singletonList("myDest"));
        verify(store, times(0)).create(any(CreateRequest.class));
        verify(store, times(0)).delete(any(DeleteRequest.class));
        verify(catalogFramework, times(0)).update(any(UpdateRequest.class));
        assertThat(result.size(), is(1));
    }

    @Test
    public void testAllRegistryInfoNoMetatypes() throws Exception {
        List<Map<String, Object>> metatypes = new ArrayList<>();
        when(helper.getMetatypes()).thenReturn(metatypes);
        assertThat(federationAdmin.allRegistryInfo()
                .size(), is(0));
    }

    @Test
    public void testAllRegistryInfo() throws Exception {
        List<Map<String, Object>> metatypes = new ArrayList<>();
        List<Configuration> configurations = new ArrayList<>();
        Dictionary<String, Object> props = new Hashtable<>();
        Dictionary<String, Object> propsDisabled = new Hashtable<>();

        metatypes.add(new HashMap<>());

        props.put("key1", "value1");
        propsDisabled.put("key2", "value2");

        Configuration config = mock(Configuration.class);
        configurations.add(config);
        when(config.getPid()).thenReturn("myPid");
        when(config.getFactoryPid()).thenReturn("myFpid");
        when(config.getProperties()).thenReturn(props);

        Configuration configDisabled = mock(Configuration.class);
        configurations.add(configDisabled);
        when(configDisabled.getPid()).thenReturn("myPid_disabled");
        when(configDisabled.getFactoryPid()).thenReturn("myFpid_disabled");
        when(configDisabled.getProperties()).thenReturn(propsDisabled);

        when(helper.getMetatypes()).thenReturn(metatypes);
        when(helper.getConfigurations(any(Map.class))).thenReturn(configurations);
        when(helper.getName(any(Configuration.class))).thenReturn("name");
        when(helper.getBundleName(any(Configuration.class))).thenReturn("bundleName");
        when(helper.getBundleId(any(Configuration.class))).thenReturn(1234L);

        List<Map<String, Object>> updatedMetatypes = federationAdmin.allRegistryInfo();
        assertThat(updatedMetatypes.size(), is(1));
        ArrayList<Map<String, Object>> configs =
                (ArrayList<Map<String, Object>>) updatedMetatypes.get(0)
                        .get("configurations");
        assertThat(configs.size(), is(2));
        Map<String, Object> activeConfig = configs.get(0);
        Map<String, Object> disabledConfig = configs.get(1);
        assertThat(activeConfig.get("name"), equalTo("name"));
        assertThat(activeConfig.get("id"), equalTo("myPid"));
        assertThat(activeConfig.get("fpid"), equalTo("myFpid"));
        assertThat(activeConfig.get("enabled"), equalTo(true));
        assertThat(((Map<String, Object>) activeConfig.get("properties")).get("key1"),
                equalTo("value1"));

        assertThat(disabledConfig.get("name"), equalTo("myPid_disabled"));
        assertThat(disabledConfig.get("id"), equalTo("myPid_disabled"));
        assertThat(disabledConfig.get("fpid"), equalTo("myFpid_disabled"));
        assertThat(disabledConfig.get("enabled"), equalTo(false));
        assertThat(((Map<String, Object>) disabledConfig.get("properties")).get("key2"),
                equalTo("value2"));
    }

    @Test
    public void testAllRegistryMetacards() throws Exception {
        List<RegistryPackageType> regObjects =
                Collections.singletonList((RegistryPackageType) getRegistryObjectFromResource(
                        "/csw-full-registry-package.xml"));
        when(federationAdminService.getRegistryObjects()).thenReturn(regObjects);
        MetacardImpl mcard = new MetacardImpl(new RegistryObjectMetacardType());
        mcard.setAttribute(RegistryObjectMetacardType.REGISTRY_ID,
                "urn:uuid:2014ca7f59ac46f495e32b4a67a51276");
        mcard.setId("someUUID");
        mcard.setAttribute(RegistryObjectMetacardType.PUBLISHED_LOCATIONS, "location1");
        when(federationAdminService.getRegistryMetacards()).thenReturn(Collections.singletonList(
                mcard));
        List<Map<String, Object>> result = federationAdmin.allRegistryMetacards();
        assertThat(result.size(), is(1));
        Map<String, Object> mcardMap = result.get(0);
        assertThat(mcardMap.get("TransientValues"), notNullValue());
        Map<String, Object> transValues = (Map<String, Object>) mcardMap.get("TransientValues");
        assertThat(((List) transValues.get(RegistryObjectMetacardType.PUBLISHED_LOCATIONS)).get(0),
                equalTo("location1"));
    }

    @Test
    public void testRegistryStatusNotConfiguredService() throws Exception {
        Source source = mock(Source.class);
        when(helper.getRegistrySources()).thenReturn(Collections.singletonList(source));
        assertThat(federationAdmin.registryStatus("servicePid"), is(false));
    }

    @Test
    public void testRegistryStatusNoMatchingConfig() throws Exception {
        RegistryStore source = mock(RegistryStore.class);
        Configuration config = mock(Configuration.class);
        Dictionary<String, Object> props = new Hashtable<>();
        props.put("service.pid", "servicePid2");
        when(config.getProperties()).thenReturn(props);
        when(source.isAvailable()).thenReturn(true);
        when(helper.getRegistrySources()).thenReturn(Collections.singletonList(source));
        when(helper.getConfiguration(any(ConfiguredService.class))).thenReturn(config);
        assertThat(federationAdmin.registryStatus("servicePid"), is(false));
    }

    @Test
    public void testRegistryStatusNoConfig() throws Exception {
        RegistryStore source = mock(RegistryStore.class);
        when(source.isAvailable()).thenReturn(true);
        when(helper.getRegistrySources()).thenReturn(Collections.singletonList(source));
        when(helper.getConfiguration(any(ConfiguredService.class))).thenReturn(null);
        assertThat(federationAdmin.registryStatus("servicePid"), is(false));
    }

    @Test
    public void testRegistryStatus() throws Exception {
        RegistryStore source = mock(RegistryStore.class);
        Configuration config = mock(Configuration.class);
        Dictionary<String, Object> props = new Hashtable<>();
        props.put("service.pid", "servicePid");
        when(config.getProperties()).thenReturn(props);
        when(source.isAvailable()).thenReturn(true);
        when(helper.getRegistrySources()).thenReturn(Collections.singletonList(source));
        when(helper.getConfiguration(any(ConfiguredService.class))).thenReturn(config);
        assertThat(federationAdmin.registryStatus("servicePid"), is(true));
    }

    @Test
    public void testInit() throws Exception {

        copyJsonFileToKarafDir();

        List<RegistryPackageType> regObjects =
                Collections.singletonList((RegistryPackageType) getRegistryObjectFromResource(
                        "/csw-full-registry-package.xml"));
        when(federationAdminService.getRegistryObjects()).thenReturn(regObjects);
        MetacardImpl mcard = new MetacardImpl(new RegistryObjectMetacardType());
        mcard.setAttribute(RegistryObjectMetacardType.REGISTRY_ID,
                "urn:uuid:2014ca7f59ac46f495e32b4a67a51276");
        mcard.setId("someUUID");
        mcard.setAttribute(RegistryObjectMetacardType.PUBLISHED_LOCATIONS, "location1");
        when(federationAdminService.getRegistryMetacards()).thenReturn(Collections.singletonList(
                mcard));
        federationAdmin.init();
        Map<String, Object> nodes = federationAdmin.getLocalNodes();
        Map<String, Object> customSlots = (Map<String, Object>) nodes.get("customSlots");
        assertThat(customSlots.size(), is(6));
    }

    @Test
    public void testInitNoFile() throws Exception {
        List<RegistryPackageType> regObjects =
                Collections.singletonList((RegistryPackageType) getRegistryObjectFromResource(
                        "/csw-full-registry-package.xml"));
        when(federationAdminService.getRegistryObjects()).thenReturn(regObjects);
        MetacardImpl mcard = new MetacardImpl(new RegistryObjectMetacardType());
        mcard.setAttribute(RegistryObjectMetacardType.REGISTRY_ID,
                "urn:uuid:2014ca7f59ac46f495e32b4a67a51276");
        mcard.setId("someUUID");
        mcard.setAttribute(RegistryObjectMetacardType.PUBLISHED_LOCATIONS, "location1");
        when(federationAdminService.getRegistryMetacards()).thenReturn(Collections.singletonList(
                mcard));
        federationAdmin.init();
        Map<String, Object> nodes = federationAdmin.getLocalNodes();
        Map<String, Object> customSlots = (Map<String, Object>) nodes.get("customSlots");
        assertThat(customSlots, nullValue());
    }

    @Test
    public void testBindEndpointNullReference() throws Exception {
        List<RegistryPackageType> regObjects =
                Collections.singletonList((RegistryPackageType) getRegistryObjectFromResource(
                        "/csw-full-registry-package.xml"));
        when(federationAdminService.getRegistryObjects()).thenReturn(regObjects);
        MetacardImpl mcard = new MetacardImpl(new RegistryObjectMetacardType());
        mcard.setAttribute(RegistryObjectMetacardType.REGISTRY_ID,
                "urn:uuid:2014ca7f59ac46f495e32b4a67a51276");
        mcard.setId("someUUID");
        when(federationAdminService.getRegistryMetacards()).thenReturn(Collections.singletonList(
                mcard));
        federationAdmin.bindEndpoint(null);
        Map<String, Object> autoValues = (Map<String, Object>) federationAdmin.getLocalNodes()
                .get("autoPopulateValues");
        assertThat(autoValues.size(), is(1));
        Collection bindingValues = (Collection) autoValues.get("ServiceBinding");
        assertThat(bindingValues.size(), is(0));
    }

    @Test
    public void testBindEndpoint() throws Exception {
        List<RegistryPackageType> regObjects =
                Collections.singletonList((RegistryPackageType) getRegistryObjectFromResource(
                        "/csw-full-registry-package.xml"));
        when(federationAdminService.getRegistryObjects()).thenReturn(regObjects);
        MetacardImpl mcard = new MetacardImpl(new RegistryObjectMetacardType());
        mcard.setAttribute(RegistryObjectMetacardType.REGISTRY_ID,
                "urn:uuid:2014ca7f59ac46f495e32b4a67a51276");
        mcard.setId("someUUID");
        when(federationAdminService.getRegistryMetacards()).thenReturn(Collections.singletonList(
                mcard));
        ServiceReference reference = mock(ServiceReference.class);
        CatalogEndpoint endpoint = mock(CatalogEndpoint.class);
        Map<String, String> props = new HashMap<>();
        props.put(CatalogEndpoint.ID_KEY, "myId");
        when(endpoint.getEndpointProperties()).thenReturn(props);
        when(context.getService(reference)).thenReturn(endpoint);
        federationAdmin.bindEndpoint(reference);
        Map<String, Object> autoValues = (Map<String, Object>) federationAdmin.getLocalNodes()
                .get("autoPopulateValues");
        assertThat(autoValues.size(), is(1));
        Collection bindingValues = (Collection) autoValues.get("ServiceBinding");
        assertThat(bindingValues.size(), is(1));
        Map<String, String> bindings = (Map<String, String>) bindingValues.iterator()
                .next();
        assertThat(bindings.get(CatalogEndpoint.ID_KEY), equalTo("myId"));

    }

    @Test
    public void testUnbindEndpoint() throws Exception {
        List<RegistryPackageType> regObjects =
                Collections.singletonList((RegistryPackageType) getRegistryObjectFromResource(
                        "/csw-full-registry-package.xml"));
        when(federationAdminService.getRegistryObjects()).thenReturn(regObjects);
        MetacardImpl mcard = new MetacardImpl(new RegistryObjectMetacardType());
        mcard.setAttribute(RegistryObjectMetacardType.REGISTRY_ID,
                "urn:uuid:2014ca7f59ac46f495e32b4a67a51276");
        mcard.setId("someUUID");
        when(federationAdminService.getRegistryMetacards()).thenReturn(Collections.singletonList(
                mcard));
        ServiceReference reference = mock(ServiceReference.class);
        CatalogEndpoint endpoint = mock(CatalogEndpoint.class);
        Map<String, String> props = new HashMap<>();
        props.put(CatalogEndpoint.ID_KEY, "myId");
        when(endpoint.getEndpointProperties()).thenReturn(props);
        when(context.getService(reference)).thenReturn(endpoint);
        federationAdmin.bindEndpoint(reference);

        federationAdmin.unbindEndpoint(reference);
        Map<String, Object> autoValues = (Map<String, Object>) federationAdmin.getLocalNodes()
                .get("autoPopulateValues");
        assertThat(autoValues.size(), is(1));
        Collection bindingValues = (Collection) autoValues.get("ServiceBinding");
        assertThat(bindingValues.size(), is(0));
    }

    private void copyJsonFileToKarafDir() throws Exception {
        File etc = folder.getRoot();
        File registry = folder.newFolder("registry");
        File jsonFile = new File(registry, "registry-custom-slots.json");
        FileOutputStream outputStream = new FileOutputStream(jsonFile);
        InputStream inputStream = this.getClass()
                .getResourceAsStream("/etc/registry/registry-custom-slots.json");
        IOUtils.copy(inputStream, outputStream);
        inputStream.close();
        outputStream.close();
        System.setProperty("karaf.etc", etc.getCanonicalPath());
    }

    private RegistryObjectType getRegistryObjectFromResource(String path) throws ParserException {
        RegistryObjectType registryObject = null;
        JAXBElement<RegistryObjectType> jaxbRegistryObject = parser.unmarshal(configurator,
                JAXBElement.class,
                getClass().getResourceAsStream(path));

        if (jaxbRegistryObject != null) {
            registryObject = jaxbRegistryObject.getValue();
        }

        return registryObject;
    }

    private Map<String, Object> getMapFromRegistryObject(RegistryObjectType registryObject) {
        return RegistryPackageWebConverter.getRegistryObjectWebMap(registryObject);
    }

    private RegistryPackageType getRegistryObjectFromMap(Map<String, Object> registryMap) {
        return RegistryPackageWebConverter.getRegistryPackageFromWebMap(registryMap);
    }

    private Metacard getTestMetacard() {
        return new MetacardImpl(new RegistryObjectMetacardType());
    }
}