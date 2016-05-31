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
package org.codice.ddf.registry.rest.endpoint.publication;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.codice.ddf.registry.common.RegistryConstants;
import org.codice.ddf.registry.common.metacard.RegistryObjectMetacardType;
import org.codice.ddf.registry.federationadmin.service.FederationAdminException;
import org.codice.ddf.registry.federationadmin.service.FederationAdminService;
import org.junit.Before;
import org.junit.Test;

import ddf.catalog.data.impl.AttributeImpl;
import ddf.catalog.data.impl.MetacardImpl;

public class RegistryPublicationHelperTest {

    private FederationAdminService federationAdminService;

    private RegistryPublicationHelper publicationHelper;

    private MetacardImpl metacard;

    @Before
    public void setup() {
        federationAdminService = mock(FederationAdminService.class);
        publicationHelper = new RegistryPublicationHelper(federationAdminService);

        metacard = new MetacardImpl(new RegistryObjectMetacardType());
        metacard.setId("mcardId");
        metacard.setTags(Collections.singleton(RegistryConstants.REGISTRY_TAG));
    }

    @Test(expected = FederationAdminException.class)
    public void testPublishInvalidRegistryId() throws Exception {
        when(federationAdminService.getRegistryMetacardsByRegistryIds(any(List.class))).thenReturn(
                Collections.emptyList());
        publicationHelper.publish(null, "sourceId");
    }

    @Test
    public void testPublishAlreadyPublished() throws Exception {
        metacard.setAttribute(new AttributeImpl(RegistryObjectMetacardType.PUBLISHED_LOCATIONS,
                Collections.singletonList("sourceId")));
        when(federationAdminService.getRegistryMetacardsByRegistryIds(any(List.class))).thenReturn(
                Collections.singletonList(metacard));
        publicationHelper.publish("regiId", "sourceId");
        verify(federationAdminService, never()).addRegistryEntry(metacard,
                Collections.singleton("sourceId"));
        verify(federationAdminService, never()).updateRegistryEntry(metacard);
    }

    @Test
    public void testPublishAddAnotherSource() throws Exception {
        metacard.setAttribute(new AttributeImpl(RegistryObjectMetacardType.PUBLISHED_LOCATIONS,
                Collections.singletonList("source1")));
        when(federationAdminService.getRegistryMetacardsByRegistryIds(any(List.class))).thenReturn(
                Collections.singletonList(metacard));
        publicationHelper.publish("regiId", "sourceId");
        verify(federationAdminService).addRegistryEntry(metacard,
                Collections.singleton("sourceId"));
        verify(federationAdminService).updateRegistryEntry(metacard);
    }

    @Test
    public void testPublish() throws Exception {

        when(federationAdminService.getRegistryMetacardsByRegistryIds(any(List.class))).thenReturn(
                Collections.singletonList(metacard));
        publicationHelper.publish("regiId", "sourceId");
        verify(federationAdminService).addRegistryEntry(metacard,
                Collections.singleton("sourceId"));
        verify(federationAdminService).updateRegistryEntry(metacard);
    }

    @Test
    public void testUnpublish() throws Exception {
        metacard.setAttribute(new AttributeImpl(RegistryObjectMetacardType.PUBLISHED_LOCATIONS,
                Collections.singletonList("sourceId")));
        when(federationAdminService.getRegistryMetacardsByRegistryIds(any(List.class))).thenReturn(
                Collections.singletonList(metacard));
        publicationHelper.unpublish("regId", "sourceId");
        verify(federationAdminService).deleteRegistryEntriesByRegistryIds(Collections.singletonList(
                "regId"), Collections.singleton("sourceId"));
        verify(federationAdminService).updateRegistryEntry(metacard);
    }

    @Test
    public void testUnpublishNoCurrentlyPublished() throws Exception {
        metacard.setAttribute(new AttributeImpl(RegistryObjectMetacardType.PUBLISHED_LOCATIONS,
                Collections.emptyList()));
        when(federationAdminService.getRegistryMetacardsByRegistryIds(any(List.class))).thenReturn(
                Collections.singletonList(metacard));
        publicationHelper.unpublish("regId", "sourceId");
        verify(federationAdminService,
                never()).deleteRegistryEntriesByRegistryIds(Collections.singletonList("regId"),
                Collections.singleton("sourceId"));
        verify(federationAdminService, never()).updateRegistryEntry(metacard);
    }
}
