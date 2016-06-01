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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.codice.ddf.registry.common.metacard.RegistryObjectMetacardType;
import org.codice.ddf.registry.federationadmin.service.FederationAdminException;
import org.codice.ddf.registry.federationadmin.service.FederationAdminService;

import ddf.catalog.data.Attribute;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.impl.AttributeImpl;

public class RegistryPublicationHelper {

    private FederationAdminService federationAdmin;

    public RegistryPublicationHelper(FederationAdminService federationAdminService) {
        this.federationAdmin = federationAdminService;
    }

    public void publish(String registryId, String sourceId) throws FederationAdminException {
        Metacard mcard = getMetacard(registryId);
        String mcardId = mcard.getId();
        List<Serializable> locations = new ArrayList<>();
        Attribute locAttr = mcard.getAttribute(RegistryObjectMetacardType.PUBLISHED_LOCATIONS);
        if (locAttr != null) {
            locations = new HashSet<>(locAttr.getValues()).stream()
                    .map(Object::toString)
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        if (locations.contains(sourceId)) {
            return;
        }

        federationAdmin.addRegistryEntry(mcard, Collections.singleton(sourceId));

        //need to reset the id since the framework rest it in the groomer plugin
        //and we don't want to update with the wrong id
        mcard.setAttribute(new AttributeImpl(Metacard.ID, mcardId));
        locations.add(sourceId);
        if (locAttr != null) {
            locAttr.getValues()
                    .add(sourceId);
        } else {
            locAttr = new AttributeImpl(RegistryObjectMetacardType.PUBLISHED_LOCATIONS, locations);
        }
        mcard.setAttribute(locAttr);

        federationAdmin.updateRegistryEntry(mcard);

    }

    private Metacard getMetacard(String registryId) throws FederationAdminException {
        List<Metacard> metacards =
                federationAdmin.getRegistryMetacardsByRegistryIds(Collections.singletonList(
                        registryId));
        if (CollectionUtils.isEmpty(metacards)) {
            throw new FederationAdminException(
                    "Could not retrieve metacard with registry-id " + registryId);
        }
        return metacards.get(0);
    }

    public void unpublish(String registryId, String sourceId) throws FederationAdminException {
        Metacard mcard = getMetacard(registryId);

        List<Serializable> locations = new ArrayList<>();
        Attribute locAttr = mcard.getAttribute(RegistryObjectMetacardType.PUBLISHED_LOCATIONS);
        if (locAttr == null || !locAttr.getValues().contains(sourceId)) {
            return;
        }

        federationAdmin.deleteRegistryEntriesByRegistryIds(Collections.singletonList(registryId), Collections.singleton(sourceId));

        locAttr.getValues().remove(sourceId);
        mcard.setAttribute(locAttr);

        federationAdmin.updateRegistryEntry(mcard);
    }
}
