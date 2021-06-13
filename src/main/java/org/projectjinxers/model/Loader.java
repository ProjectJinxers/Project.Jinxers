/*
 * Copyright (C) 2021 ProjectJinxers
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program. If not, see
 * <https://www.gnu.org/licenses/>.
 */
package org.projectjinxers.model;

import org.projectjinxers.controller.IPLDReader;

/**
 * Instances create or (in general) provide data object instances. This is useful, because the exact type of a loaded
 * object can only be determined after reading its properties. The data object instances can be their own loaders. That
 * is perfectly legal and absolutely encouraged when the concrete type is known in advance.
 * 
 * @author ProjectJinxers
 */
public interface Loader<D extends IPLDSerializable> {

    /**
     * If the data instance has not been created, yet, or can't be gotten from somewhere else, it is created.
     * 
     * @param reader   the reader (can be queried for the existence of specific keys)
     * @param metadata the metadata
     * @return the data instance (might be this object)
     */
    D getOrCreateDataInstance(IPLDReader reader, Metadata metadata);

    /**
     * @return the loaded data instance (might be this object)
     */
    D getLoaded();

}
