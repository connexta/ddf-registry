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

package org.codice.ddf.registry.admin.poller;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.apache.shiro.util.CollectionUtils;
import org.codice.ddf.registry.common.metacard.RegistryObjectMetacardType;
import org.codice.ddf.registry.federationadmin.service.FederationAdminException;
import org.codice.ddf.registry.federationadmin.service.FederationAdminService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

import ddf.catalog.CatalogFramework;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.Result;
import ddf.catalog.data.impl.AttributeImpl;
import ddf.catalog.data.impl.MetacardImpl;
import ddf.catalog.data.impl.ResultImpl;
import ddf.catalog.federation.FederationException;
import ddf.catalog.filter.FilterBuilder;
import ddf.catalog.filter.proxy.builder.GeotoolsFilterBuilder;
import ddf.catalog.operation.impl.CreateRequestImpl;
import ddf.catalog.operation.impl.CreateResponseImpl;
import ddf.catalog.operation.impl.DeleteRequestImpl;
import ddf.catalog.operation.impl.DeleteResponseImpl;
import ddf.catalog.operation.impl.QueryRequestImpl;
import ddf.catalog.operation.impl.QueryResponseImpl;
import ddf.catalog.operation.impl.SourceResponseImpl;
import ddf.catalog.service.ConfiguredService;
import ddf.catalog.source.CatalogStore;
import ddf.catalog.source.IngestException;
import ddf.catalog.source.Source;
import ddf.catalog.source.SourceUnavailableException;
import ddf.catalog.source.UnsupportedQueryException;
import ddf.catalog.source.opensearch.OpenSearchSource;

public class AdminPollerTest {

    private static final String CONFIG_PID = "properPid";

    private static final String EXCEPTION_PID = "throwsAnException";

    private static final String SOURCE_FPID = "OpenSearchSource";

    private static MockedSourceAdminPoller sourcePoller;

    private static MockedRegistryAdminPoller registryPoller;

    private static final String METACARD_TYPE = "metacard-type";

    private static final String SOURCE_FILTERSPEC =
            "(|(service.factoryPid=*source*)(service.factoryPid=*Source*)(service.factoryPid=*service*)(service.factoryPid=*Service*))";

    private static final String REGISTRY_FILTERSPEC =
            "(|(service.factoryPid=*Registry*Store*)(service.factoryPid=*registry*store*))";

    @Mock
    private CatalogFramework catalogFramework;

    @Mock
    private CatalogStore catalogStore1;

    @Mock
    private CatalogStore catalogStore2;

    @Mock
    private CatalogStore catalogStore3;

    @Mock
    private FederationAdminService federationAdminService;

    private FilterBuilder filterBuilder;

    private Map<String, CatalogStore> catalogStoreMap;

    private ArrayList<String> publishedPlaces;

    private ArrayList<String> destinations;

    private List<Result> results;

    private Metacard sourceMetacard, registryMetacard;

    @Before
    public void setup() {
        federationAdminService = mock(FederationAdminService.class);

        catalogFramework = mock(CatalogFramework.class);
        catalogStoreMap = new HashMap<>();

        catalogStore1 = mock(CatalogStore.class);
        catalogStore2 = mock(CatalogStore.class);
        catalogStore3 = mock(CatalogStore.class);
        catalogStoreMap.put("destination1", catalogStore1);
        catalogStoreMap.put("destination2", catalogStore2);
        catalogStoreMap.put("destination3", catalogStore3);

        publishedPlaces = new ArrayList<>();
        publishedPlaces.add("destination1");
        publishedPlaces.add("destination3");

        destinations = new ArrayList<>();
        destinations.add("destination1");
        destinations.add("destination2");

        sourceMetacard = new MetacardImpl();
        sourceMetacard.setAttribute(new AttributeImpl(RegistryObjectMetacardType.PUBLISHED_LOCATIONS,
                publishedPlaces));
        sourceMetacard.setAttribute(new AttributeImpl(RegistryObjectMetacardType.REGISTRY_ID,
                "registry1"));

        registryMetacard = new MetacardImpl();
        registryMetacard.setAttribute(new AttributeImpl(RegistryObjectMetacardType.REGISTRY_ID,
                "registry1"));
        registryMetacard.setAttribute(new AttributeImpl(METACARD_TYPE, "registry"));

        results = new ArrayList<>();
        results.add(new ResultImpl(sourceMetacard));
        results.add(new ResultImpl(registryMetacard));

        filterBuilder = new GeotoolsFilterBuilder();

        sourcePoller = new AdminPollerTest().new MockedSourceAdminPoller(null,
                catalogFramework,
                filterBuilder,
                catalogStoreMap,
                federationAdminService);

        registryPoller = new AdminPollerTest().new MockedRegistryAdminPoller(null,
                catalogFramework,
                filterBuilder,
                catalogStoreMap,
                federationAdminService);
    }

    @Test
    public void testAllSourceInfo() throws FederationAdminException {
        //create List<Map<String, Object>>
        Map<String, Object> registryWebMap = new HashMap<>();
        //catch getRegistryObjects pass List<RegistryObjectTypes>
        when(federationAdminService.getRegistryObjects()).thenReturn(new ArrayList<>());
        List<Map<String, Object>> sources = sourcePoller.allSourceInfo();
        assertThat(sources, notNullValue());
        assertThat(sources.size(), is(2));
        assertThat(sources.get(0), notNullValue());
        assertThat(sources.get(0)
                .size(), is(2));
        assertThat(sources.get(1), notNullValue());
        assertThat((Map<String, Object>) sources.get(0)
                .get("source"), not(hasKey("configurations")));
        assertThat((Map<String, Object>) sources.get(1)
                .get("source"), hasKey("configurations"));
    }

    @Test
    public void testAllRegistryInfo() {
        List<Map<String, Object>> registries = registryPoller.allRegistryInfo();
        assertThat(registries, notNullValue());
        assertThat(registries.size(), is(2));
        for (Map<String, Object> registry : registries) {
            assertThat(registry, hasKey("metatype"));
            assertThat(registry, hasKey("id"));
        }
    }

    @Test
    public void testSourceStatus() {
        assertThat(sourcePoller.sourceStatus(CONFIG_PID), is(true));
        assertThat(sourcePoller.sourceStatus(EXCEPTION_PID), is(false));
        assertThat(sourcePoller.sourceStatus("FAKE SOURCE"), is(false));
    }

    @Test
    public void testSuccessfulPublish()
            throws UnsupportedQueryException, SourceUnavailableException, FederationException,
            IngestException {
        when(catalogFramework.query(any())).thenReturn(new QueryResponseImpl(new QueryRequestImpl(
                null), results, 1));
        when(catalogStore1.create(any())).thenReturn(new CreateResponseImpl(new CreateRequestImpl(
                new MetacardImpl()), new HashMap<>(), Arrays.asList(sourceMetacard)));
        when(catalogStore2.create(any())).thenReturn(new CreateResponseImpl(new CreateRequestImpl(
                new MetacardImpl()), new HashMap<>(), Arrays.asList(sourceMetacard)));
        when(catalogStore3.delete(any())).thenReturn(new DeleteResponseImpl(new DeleteRequestImpl(""),
                new HashMap<>(),
                Arrays.asList(sourceMetacard)));

        List<Serializable> newPublishedPlaces = sourcePoller.updatePublications("mySource",
                destinations);
        assertThat(newPublishedPlaces, hasItems("destination1", "destination2"));
        newPublishedPlaces.remove("destination1");
        newPublishedPlaces.remove("destination2");
        assertThat(newPublishedPlaces, empty());
    }

    @Test
    public void testUnsuccessfulCreatePublish()
            throws UnsupportedQueryException, SourceUnavailableException, FederationException,
            IngestException {
        when(catalogFramework.query(any())).thenReturn(new QueryResponseImpl(new QueryRequestImpl(
                null), results, 1));
        when(catalogStore1.create(any())).thenReturn(new CreateResponseImpl(new CreateRequestImpl(
                new MetacardImpl()),
                new HashMap<String, Serializable>(),
                Arrays.asList(sourceMetacard)));
        when(catalogStore2.create(any())).thenThrow(new IngestException());
        when(catalogStore2.query(any())).thenReturn(new SourceResponseImpl(new QueryRequestImpl(null),
                new ArrayList<Result>()));
        when(catalogStore3.delete(any())).thenReturn(new DeleteResponseImpl(new DeleteRequestImpl(""),
                new HashMap<String, Serializable>(),
                Arrays.asList(sourceMetacard)));

        List<Serializable> newPublishedPlaces = sourcePoller.updatePublications("mySource",
                destinations);
        assertThat(newPublishedPlaces, hasItems("destination1"));
        newPublishedPlaces.remove("destination1");
        assertThat(newPublishedPlaces, empty());
    }

    @Test
    public void testUnsuccessfulDeletePublish()
            throws UnsupportedQueryException, SourceUnavailableException, FederationException,
            IngestException {
        when(catalogFramework.query(any())).thenReturn(new QueryResponseImpl(new QueryRequestImpl(
                null), results, 1));
        when(catalogStore1.create(any())).thenReturn(new CreateResponseImpl(new CreateRequestImpl(
                new MetacardImpl()),
                new HashMap<String, Serializable>(),
                Arrays.asList(sourceMetacard)));
        when(catalogStore2.create(any())).thenReturn(new CreateResponseImpl(new CreateRequestImpl(
                new MetacardImpl()),
                new HashMap<String, Serializable>(),
                Arrays.asList(sourceMetacard)));
        when(catalogStore3.delete(any())).thenThrow(new IngestException());
        when(catalogStore3.query(any())).thenReturn(new SourceResponseImpl(new QueryRequestImpl(null),
                Arrays.asList(new ResultImpl(sourceMetacard))));

        List<Serializable> newPublishedPlaces = sourcePoller.updatePublications("mySource",
                destinations);
        assertThat(newPublishedPlaces, hasItems("destination1", "destination2", "destination3"));
    }

    private class MockedSourceAdminPoller extends AdminPollerServiceBean {
        public MockedSourceAdminPoller(ConfigurationAdmin configAdmin,
                CatalogFramework catalogFramework, FilterBuilder filterBuilder,
                Map<String, CatalogStore> catalogStoreMap,
                FederationAdminService federationAdminService) {
            super(configAdmin,
                    catalogFramework,
                    filterBuilder,
                    catalogStoreMap,
                    federationAdminService);
        }

        @Override
        protected AdminHelper getHelper() {
            AdminHelper helper = mock(AdminHelper.class);
            try {
                // Mock out the configuration
                Configuration config = mock(Configuration.class);
                when(config.getPid()).thenReturn(CONFIG_PID);
                when(config.getFactoryPid()).thenReturn(SOURCE_FPID);
                Dictionary<String, Object> dict = new Hashtable<>();
                dict.put("service.pid", CONFIG_PID);
                dict.put("service.factoryPid", SOURCE_FPID);
                when(config.getProperties()).thenReturn(dict);
                when(helper.getConfigurations(anyMap())).thenReturn(CollectionUtils.asList(config),
                        null);

                // Mock out the sources
                OpenSearchSource source = mock(OpenSearchSource.class);
                when(source.isAvailable()).thenReturn(true);

                OpenSearchSource badSource = mock(OpenSearchSource.class);
                when(badSource.isAvailable()).thenThrow(new RuntimeException());

                //CONFIG_PID, EXCEPTION_PID, FAKE_SOURCE
                when(helper.getConfiguration(any(ConfiguredService.class))).thenReturn(config,
                        config,
                        config);
                when(helper.getSources()).thenReturn(CollectionUtils.asList((Source) source,
                        badSource));

                // Mock out the metatypes
                Map<String, Object> metatype = new HashMap<>();
                metatype.put("id", "OpenSearchSource");
                metatype.put("metatype", new ArrayList<Map<String, Object>>());

                Map<String, Object> noConfigMetaType = new HashMap<>();
                noConfigMetaType.put("id", "No Configurations");
                noConfigMetaType.put("metatype", new ArrayList<Map<String, Object>>());

                when(helper.getMetatypes(SOURCE_FILTERSPEC)).thenReturn(CollectionUtils.asList(
                        metatype,
                        noConfigMetaType));
            } catch (Exception e) {

            }

            return helper;
        }
    }

    private class MockedRegistryAdminPoller extends AdminPollerServiceBean {
        public MockedRegistryAdminPoller(ConfigurationAdmin configAdmin,
                CatalogFramework catalogFramework, FilterBuilder filterBuilder,
                Map<String, CatalogStore> catalogStoreMap,
                FederationAdminService federationAdminService) {
            super(configAdmin,
                    catalogFramework,
                    filterBuilder,
                    catalogStoreMap,
                    federationAdminService);
        }

        @Override
        protected AdminHelper getHelper() {
            AdminHelper helper = mock(AdminHelper.class);
            try {
                // Mock out the metatypes
                Map<String, Object> metatype = new HashMap<>();
                metatype.put("id", "CswRegistryStore");
                metatype.put("metatype", new ArrayList<Map<String, Object>>());
                Map<String, Object> miscMetatype = new HashMap<>();
                miscMetatype.put("id", "misc_registry_store");
                miscMetatype.put("metatype", new ArrayList<Map<String, Object>>());
                when(helper.getMetatypes(REGISTRY_FILTERSPEC)).thenReturn(CollectionUtils.asList(
                        metatype,
                        miscMetatype));
            } catch (Exception e) {
            }
            return helper;
        }
    }
}
