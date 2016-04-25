/**
 * Copyright (c) Codice Foundation
 * <p/>
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package org.codice.ddf.registry.report.action.provider;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.codice.ddf.registry.common.RegistryConstants;
import org.junit.Test;

import ddf.action.Action;
import ddf.catalog.data.impl.MetacardImpl;

public class TestRegistryMetacardActionProvider {

    protected static final String SAMPLE_PATH = "/registries/";

    protected static final String SAMPLE_SERVICES_ROOT = "/services";

    protected static final String SAMPLE_PROTOCOL = "http://";

    protected static final String SAMPLE_SECURE_PROTOCOL = "https://";

    protected static final String SAMPLE_PORT = "8181";

    protected static final String SAMPLE_SECURE_PORT = "8993";

    protected static final String SAMPLE_IP = "192.168.1.1";

    protected static final String SAMPLE_ID = "abcdef1234567890abdcef1234567890";

    protected static final String ACTION_PROVIDER_ID = "catalog.view.metacard";

    protected static final Set<String> SAMPLE_REGISTRY_TAGS = new HashSet<>(Arrays.asList(
            RegistryConstants.REGISTRY_TAG));

    protected static final String PATH_AND_FORMAT = "/report.html";

    protected static final String SAMPLE_SOURCE_ID = "ddf.distribution";

    protected static final String SOURCE_ID_QUERY_PARAM = "?sourceId=";

    @Test
    public void testMetacardNull() {
        assertThat(new RegistryMetacardActionProvider(ACTION_PROVIDER_ID).getActions(null),
                hasSize(0));
    }

    @Test
    public void testUriSyntaxException() {

        MetacardImpl metacard = new MetacardImpl();

        metacard.setId(SAMPLE_ID);

        RegistryMetacardActionProvider actionProvider = new RegistryMetacardActionProvider(
                ACTION_PROVIDER_ID);
        this.configureActionProvider(SAMPLE_PROTOCOL, "23^&*#", SAMPLE_PORT, SAMPLE_SERVICES_ROOT);

        assertThat("A bad url should have been caught and an empty list returned.",
                actionProvider.getActions(metacard),
                hasSize(0));

    }

    @Test
    public void testMetacardIdUrlEncodedSpace() {

        // given
        MetacardImpl metacard = new MetacardImpl();

        metacard.setId("abd ef");
        metacard.setTags(SAMPLE_REGISTRY_TAGS);

        RegistryMetacardActionProvider actionProvider = new RegistryMetacardActionProvider(
                ACTION_PROVIDER_ID);
        this.configureActionProvider();

        // when
        String url = actionProvider.getActions(metacard)
                .get(0)
                .getUrl()
                .toString();

        // then
        assertThat(url, is(expectedDefaultAddressWith("abd+ef")));

    }

    @Test
    public void testMetacardIdUrlEncodedAmpersand() {

        // given
        MetacardImpl metacard = new MetacardImpl();

        metacard.setId("abd&ef");
        metacard.setTags(SAMPLE_REGISTRY_TAGS);

        RegistryMetacardActionProvider actionProvider = new RegistryMetacardActionProvider(
                ACTION_PROVIDER_ID);
        this.configureActionProvider();

        // when
        String url = actionProvider.getActions(metacard)
                .get(0)
                .getUrl()
                .toString();

        // then
        assertThat(url, is(expectedDefaultAddressWith("abd%26ef")));

    }

    @Test
    public void testMetacardIdNull() {

        MetacardImpl metacard = new MetacardImpl();

        metacard.setId(null);

        RegistryMetacardActionProvider actionProvider = new RegistryMetacardActionProvider(
                ACTION_PROVIDER_ID);
        this.configureActionProvider();

        assertThat("An action should not have been created when no id is provided.",
                actionProvider.getActions(metacard),
                hasSize(0));

    }

    @Test
    public void testIpNull() {

        MetacardImpl metacard = new MetacardImpl();

        metacard.setId(SAMPLE_ID);
        metacard.setTags(SAMPLE_REGISTRY_TAGS);

        RegistryMetacardActionProvider actionProvider = new RegistryMetacardActionProvider(
                ACTION_PROVIDER_ID);

        this.configureActionProvider(SAMPLE_PROTOCOL, null, SAMPLE_PORT, SAMPLE_SERVICES_ROOT);

        assertThat(actionProvider.getActions(metacard)
                .get(0)
                .getUrl()
                .toString(), containsString("localhost"));

    }

    @Test
    public void testIpUnknown() {

        MetacardImpl metacard = new MetacardImpl();

        metacard.setId(SAMPLE_ID);

        RegistryMetacardActionProvider actionProvider = new RegistryMetacardActionProvider(
                ACTION_PROVIDER_ID);

        this.configureActionProvider(SAMPLE_PROTOCOL, "0.0.0.0", SAMPLE_PORT, SAMPLE_SERVICES_ROOT);

        assertThat("An action should not have been created when ip is unknown (0.0.0.0).",
                actionProvider.getActions(metacard),
                hasSize(0));
    }

    @Test
    public void testPortNull() {

        MetacardImpl metacard = new MetacardImpl();

        metacard.setId(SAMPLE_ID);
        metacard.setTags(SAMPLE_REGISTRY_TAGS);

        RegistryMetacardActionProvider actionProvider = new RegistryMetacardActionProvider(
                ACTION_PROVIDER_ID);

        this.configureActionProvider(SAMPLE_PROTOCOL, SAMPLE_IP, null, SAMPLE_SERVICES_ROOT);

        assertThat(actionProvider.getActions(metacard)
                .get(0)
                .getUrl()
                .toString(), containsString("8181"));
    }

    @Test
    public void testContextRootNull() {

        MetacardImpl metacard = new MetacardImpl();

        metacard.setId(SAMPLE_ID);
        metacard.setTags(SAMPLE_REGISTRY_TAGS);

        RegistryMetacardActionProvider actionProvider = new RegistryMetacardActionProvider(
                ACTION_PROVIDER_ID);

        this.configureActionProvider(SAMPLE_PROTOCOL, SAMPLE_IP, SAMPLE_PORT, null);

        assertThat(actionProvider.getActions(metacard)
                .get(0)
                .getUrl()
                .toString(), not(containsString("/services")));
    }

    @Test
    public void testNonMetacard() {

        RegistryMetacardActionProvider actionProvider = new RegistryMetacardActionProvider(
                ACTION_PROVIDER_ID);
        this.configureActionProvider();

        assertThat("An action when metacard was not provided.",
                actionProvider.getActions(new Date()),
                hasSize(0));

    }

    @Test
    public void testMetacard() {

        // given
        MetacardImpl metacard = new MetacardImpl();

        metacard.setId(SAMPLE_ID);
        metacard.setTags(SAMPLE_REGISTRY_TAGS);

        RegistryMetacardActionProvider actionProvider = new RegistryMetacardActionProvider(
                ACTION_PROVIDER_ID);
        this.configureActionProvider();

        // when
        Action action = actionProvider.getActions(metacard)
                .get(0);

        // then
        assertEquals(RegistryMetacardActionProvider.TITLE, action.getTitle());
        assertEquals(RegistryMetacardActionProvider.DESCRIPTION, action.getDescription());
        assertThat(action.getUrl()
                .toString(), is(expectedDefaultAddressWith(metacard.getId())));
        assertEquals(ACTION_PROVIDER_ID, actionProvider.getId());

    }

    @Test
    public void testFederatedMetacard() {

        // given
        MetacardImpl metacard = new MetacardImpl();

        metacard.setId(SAMPLE_ID);

        String newSourceName = "newSource";
        metacard.setSourceId(newSourceName);

        RegistryMetacardActionProvider actionProvider = new RegistryMetacardActionProvider(
                ACTION_PROVIDER_ID);
        this.configureActionProvider();

        // when
        List<Action> action = actionProvider.getActions(metacard);

        // then
        assertThat(action.size(), is(0));

    }

    @Test
    public void testSecureMetacard() {

        MetacardImpl metacard = new MetacardImpl();

        metacard.setId(SAMPLE_ID);
        metacard.setTags(SAMPLE_REGISTRY_TAGS);

        RegistryMetacardActionProvider actionProvider = new RegistryMetacardActionProvider(
                ACTION_PROVIDER_ID);
        this.configureSecureActionProvider();

        Action action = actionProvider.getActions(metacard)
                .get(0);

        assertEquals(RegistryMetacardActionProvider.TITLE, action.getTitle());
        assertEquals(RegistryMetacardActionProvider.DESCRIPTION, action.getDescription());
        assertEquals(
                SAMPLE_SECURE_PROTOCOL + SAMPLE_IP + ":" + SAMPLE_SECURE_PORT + SAMPLE_SERVICES_ROOT
                        + SAMPLE_PATH + metacard.getId() + PATH_AND_FORMAT,
                action.getUrl()
                        .toString());
        assertEquals(ACTION_PROVIDER_ID, actionProvider.getId());

    }

    @Test
    public void testNullProtocol() {

        MetacardImpl metacard = new MetacardImpl();

        metacard.setId(SAMPLE_ID);
        metacard.setTags(SAMPLE_REGISTRY_TAGS);

        RegistryMetacardActionProvider actionProvider = new RegistryMetacardActionProvider(
                ACTION_PROVIDER_ID);

        this.configureActionProvider(null, SAMPLE_IP, SAMPLE_SECURE_PORT, SAMPLE_SERVICES_ROOT);

        Action action = actionProvider.getActions(metacard)
                .get(0);

        //when null protocal should default to https
        assertThat(action.getUrl()
                .toString(), containsString("https"));

    }

    @Test
    public void testSourceIdPresent() {

        MetacardImpl metacard = new MetacardImpl();

        metacard.setId(SAMPLE_ID);
        metacard.setSourceId(SAMPLE_SOURCE_ID);
        metacard.setTags(SAMPLE_REGISTRY_TAGS);

        RegistryMetacardActionProvider actionProvider = new RegistryMetacardActionProvider(
                ACTION_PROVIDER_ID);
        this.configureActionProvider();

        Action action = actionProvider.getActions(metacard)
                .get(0);

        assertEquals(RegistryMetacardActionProvider.TITLE, action.getTitle());
        assertEquals(RegistryMetacardActionProvider.DESCRIPTION, action.getDescription());
        assertThat(action.getUrl()
                        .toString(),
                is(expectedDefaultAddressWith(metacard.getId()) + SOURCE_ID_QUERY_PARAM
                        + SAMPLE_SOURCE_ID));
        assertEquals(ACTION_PROVIDER_ID, actionProvider.getId());

    }

    private String expectedDefaultAddressWith(String id) {
        return SAMPLE_PROTOCOL + SAMPLE_IP + ":" + SAMPLE_PORT + SAMPLE_SERVICES_ROOT + SAMPLE_PATH
                + id + PATH_AND_FORMAT;
    }

    protected void configureActionProvider() {
        configureActionProvider(SAMPLE_PROTOCOL, SAMPLE_IP, SAMPLE_PORT, SAMPLE_SERVICES_ROOT);
    }

    protected void configureSecureActionProvider() {

        configureActionProvider(SAMPLE_SECURE_PROTOCOL,
                SAMPLE_IP,
                SAMPLE_SECURE_PORT,
                SAMPLE_SERVICES_ROOT);
    }

    protected void configureActionProvider(String protocol, String host, String port,
            String contextRoot) {

        setProperty("org.codice.ddf.system.hostname", host);
        setProperty("org.codice.ddf.system.httpPort", port);
        setProperty("org.codice.ddf.system.httpsPort", port);
        setProperty("org.codice.ddf.system.protocol", protocol);
        setProperty("org.codice.ddf.system.rootContext", contextRoot);
    }

    private void setProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
