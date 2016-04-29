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
package org.codice.ddf.registry.publication;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Date;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import org.codice.ddf.registry.common.RegistryConstants;
import org.codice.ddf.registry.common.metacard.RegistryObjectMetacardType;
import org.codice.ddf.registry.federationadmin.service.FederationAdminException;
import org.codice.ddf.registry.federationadmin.service.FederationAdminService;
import org.junit.Test;
import org.osgi.service.event.Event;

import ddf.catalog.data.Metacard;
import ddf.catalog.data.impl.MetacardImpl;

public class TestRegistryPublicationHandler {

    @Test
    public void testNullMetacard() {
        FederationAdminService service = mock(FederationAdminService.class);
        RegistryPublicationHandler rph = new RegistryPublicationHandler(service);
        Dictionary<String, Object> eventProperties = new Hashtable<>();
        Event event = new Event("myevent", eventProperties);
        ExecutorService executorService = mock(ExecutorService.class);
        rph.setExecutorService(executorService);
        rph.handleEvent(event);
        verify(executorService, never()).execute(any(Runnable.class));
    }

    @Test
    public void testNonRegistryMetacard() {
        FederationAdminService service = mock(FederationAdminService.class);
        RegistryPublicationHandler rph = new RegistryPublicationHandler(service);
        MetacardImpl mcard = new MetacardImpl();
        mcard.setAttribute(Metacard.TAGS, Metacard.DEFAULT_TAG);
        Dictionary<String, Object> eventProperties = new Hashtable<>();
        eventProperties.put("ddf.catalog.event.metacard", mcard);
        Event event = new Event("myevent", eventProperties);
        ExecutorService executorService = mock(ExecutorService.class);
        rph.setExecutorService(executorService);
        rph.handleEvent(event);
        verify(executorService, never()).execute(any(Runnable.class));
    }

    @Test
    public void testRegistryMetacardExecutorCall() {
        FederationAdminService service = mock(FederationAdminService.class);
        RegistryPublicationHandler rph = new RegistryPublicationHandler(service);
        MetacardImpl mcard = new MetacardImpl();
        mcard.setAttribute(Metacard.TAGS, RegistryConstants.REGISTRY_TAG);
        Dictionary<String, Object> eventProperties = new Hashtable<>();
        eventProperties.put("ddf.catalog.event.metacard", mcard);
        Event event = new Event("myevent", eventProperties);
        ExecutorService executorService = mock(ExecutorService.class);
        rph.setExecutorService(executorService);
        rph.handleEvent(event);
        verify(executorService, times(1)).execute(any(Runnable.class));
    }

    @Test
    public void testProcessUpdateNoPublications() throws Exception {
        FederationAdminService service = mock(FederationAdminService.class);
        RegistryPublicationHandler rph = new RegistryPublicationHandler(service);
        MetacardImpl mcard = new MetacardImpl();
        mcard.setAttribute(RegistryObjectMetacardType.PUBLISHED_LOCATIONS, new ArrayList<>());
        rph.processUpdate(mcard);
        verify(service, never()).updateRegistryEntry(mcard);
    }

    @Test
    public void testProcessUpdateEmptyPublications() throws Exception {
        FederationAdminService service = mock(FederationAdminService.class);
        RegistryPublicationHandler rph = new RegistryPublicationHandler(service);
        MetacardImpl mcard = new MetacardImpl();
        rph.processUpdate(mcard);
        verify(service, never()).updateRegistryEntry(mcard);
    }

    @Test
    public void testProcessUpdateNoLastPublishDate() throws Exception {
        FederationAdminService service = mock(FederationAdminService.class);
        RegistryPublicationHandler rph = new RegistryPublicationHandler(service);
        MetacardImpl mcard = new MetacardImpl();
        mcard.setAttribute(RegistryObjectMetacardType.PUBLISHED_LOCATIONS, "mylocation");
        rph.processUpdate(mcard);
        verify(service, times(1)).updateRegistryEntry(mcard);
    }

    @Test
    public void testProcessUpdateNoUpdateNeeded() throws Exception {
        FederationAdminService service = mock(FederationAdminService.class);
        RegistryPublicationHandler rph = new RegistryPublicationHandler(service);
        MetacardImpl mcard = new MetacardImpl();
        mcard.setAttribute(RegistryObjectMetacardType.PUBLISHED_LOCATIONS, "mylocation");
        Date now = new Date();
        mcard.setModifiedDate(now);
        mcard.setAttribute(RegistryObjectMetacardType.LAST_PUBLISHED, now);
        rph.processUpdate(mcard);
        verify(service, never()).updateRegistryEntry(mcard);
    }

    @Test
    public void testProcessUpdate() throws Exception {
        FederationAdminService service = mock(FederationAdminService.class);
        RegistryPublicationHandler rph = new RegistryPublicationHandler(service);
        MetacardImpl mcard = new MetacardImpl();
        mcard.setAttribute(RegistryObjectMetacardType.PUBLISHED_LOCATIONS, "mylocation");
        Date now = new Date();
        Date before = new Date(now.getTime() - 100000);
        mcard.setModifiedDate(now);
        mcard.setAttribute(RegistryObjectMetacardType.LAST_PUBLISHED, before);
        rph.processUpdate(mcard);
        verify(service, times(1)).updateRegistryEntry(mcard);
        assertThat(mcard.getAttribute(RegistryObjectMetacardType.LAST_PUBLISHED)
                .getValue(), equalTo(now));
    }

    @Test
    public void testProcessUpdateException() throws Exception {
        FederationAdminService service = mock(FederationAdminService.class);
        doThrow(new FederationAdminException("Test Error")).when(service)
                .updateRegistryEntry(any(Metacard.class), any(Set.class));
        RegistryPublicationHandler rph = new RegistryPublicationHandler(service);
        MetacardImpl mcard = new MetacardImpl();
        mcard.setAttribute(RegistryObjectMetacardType.PUBLISHED_LOCATIONS, "mylocation");
        Date now = new Date();
        Date before = new Date(now.getTime() - 100000);
        mcard.setModifiedDate(now);
        mcard.setAttribute(RegistryObjectMetacardType.LAST_PUBLISHED, before);
        rph.processUpdate(mcard);
        verify(service, never()).updateRegistryEntry(mcard);
        assertThat(mcard.getAttribute(RegistryObjectMetacardType.LAST_PUBLISHED)
                .getValue(), equalTo(before));
    }

}
