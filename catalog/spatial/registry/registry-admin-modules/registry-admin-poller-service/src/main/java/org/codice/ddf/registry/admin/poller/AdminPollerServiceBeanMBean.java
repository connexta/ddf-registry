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

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import ddf.catalog.federation.FederationException;
import ddf.catalog.source.SourceUnavailableException;
import ddf.catalog.source.UnsupportedQueryException;

public interface AdminPollerServiceBeanMBean {
    boolean sourceStatus(String servicePID);

    /**
     * Returns a list of source metatype mappings and registry metacard mappings for the given service.
     *
     * If the registry id of the source metatype matches a registry metacard mapping, they will be paired
     * together in a Map
     *
     * @return List of source/service metatype mappings.
     */
    List<Map<String, Object>> allSourceInfo();

    /**
     * Returns a list of registry metatype mappings for the given service.
     *
     * @return List of registry metatype mappings.
     */
    List<Map<String, Object>> allRegistryInfo();

    /**
     * @param source       - The id of the source that will be published to or unpublished
     *                     from the following destinations
     * @param destinations - List of ids of catalog stores that the source will be
     *                     published to or unpublished from
     * @return the list of currently published locations after attempting to perform
     * the publish/unpublishes
     */
    List<Serializable> updatePublications(String source, List<String> destinations)
            throws UnsupportedQueryException, SourceUnavailableException, FederationException;

}
