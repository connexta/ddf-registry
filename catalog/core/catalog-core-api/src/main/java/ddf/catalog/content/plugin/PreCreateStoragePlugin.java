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
package ddf.catalog.content.plugin;

import ddf.catalog.content.operation.CreateStorageRequest;
import ddf.catalog.plugin.PluginExecutionException;

/**
 * Services implementing this interface are called immediately before an item is created in the
 * content repository.
 * <p/>
 * <p>
 * <b> This code is experimental. While this interface is functional and tested, it may change or be
 * removed in a future version of the library. </b>
 * </p>
 */
public interface PreCreateStoragePlugin {
    /**
     * Processes the {@link ddf.catalog.content.operation.CreateStorageRequest}.
     *
     * @param input the {@link CreateStorageRequest} to process
     * @return the processed {@link CreateStorageRequest} to pass to the next {@link PreCreateStoragePlugin}
     * @throws PluginExecutionException if an error occurs during processing
     */
    CreateStorageRequest process(CreateStorageRequest input) throws PluginExecutionException;
}
