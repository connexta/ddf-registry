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

import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.codice.ddf.registry.common.RegistryConstants;
import org.codice.ddf.registry.common.metacard.RegistryObjectMetacardType;
import org.codice.ddf.registry.federationadmin.service.FederationAdminException;
import org.codice.ddf.registry.federationadmin.service.FederationAdminService;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ddf.catalog.data.Attribute;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.impl.AttributeImpl;

public class RegistryPublicationHandler implements EventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistryPublicationHandler.class);

    private static final String METACARD_PROPERTY = "ddf.catalog.event.metacard";

    private FederationAdminService adminService;

    private ExecutorService executor;

    private int threadPoolSize = 1;

    public RegistryPublicationHandler(FederationAdminService adminService) {
        this.adminService = adminService;
    }

    public void init() {
        if (executor == null) {
            executor = Executors.newFixedThreadPool(threadPoolSize);
        }
    }

    public void setExecutorService(ExecutorService service) {
        executor = service;
    }

    public void destroy() {
        executor.shutdown();
    }

    @Override
    public void handleEvent(Event event) {
        Metacard mcard = (Metacard) event.getProperty(METACARD_PROPERTY);
        if (mcard == null) {
            return;
        }
        if (mcard.getTags()
                .contains(RegistryConstants.REGISTRY_TAG)) {
            executor.execute(() -> {
                processUpdate(mcard);
            });
        }
    }

    protected void processUpdate(Metacard mcard) {
        Attribute publishedLocations =
                mcard.getAttribute(RegistryObjectMetacardType.PUBLISHED_LOCATIONS);
        if (publishedLocations != null) {
            Attribute lastPublished = mcard.getAttribute(RegistryObjectMetacardType.LAST_PUBLISHED);
            Date datePublished = null;
            if (lastPublished != null) {
                datePublished = (Date) lastPublished.getValue();
            }

            Set<String> locations = new HashSet<>(publishedLocations.getValues()).stream()
                    .map(e -> e.toString())
                    .collect(Collectors.toSet());

            if ((datePublished == null || datePublished.before(mcard.getModifiedDate()))
                    && CollectionUtils.isNotEmpty(locations)) {
                try {
                    adminService.updateRegistryEntry(mcard, locations);
                    mcard.setAttribute(new AttributeImpl(RegistryObjectMetacardType.LAST_PUBLISHED,
                            mcard.getModifiedDate()));
                    adminService.updateRegistryEntry(mcard);
                } catch (FederationAdminException e) {
                    LOGGER.warn("Unable to send update for {} to {}", mcard.getTitle(), locations);
                }
            }
        }
    }
}
