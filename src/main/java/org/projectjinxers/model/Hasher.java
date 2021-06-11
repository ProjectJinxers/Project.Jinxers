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

import static org.ethereum.crypto.HashUtil.sha3;

/**
 * Interface for objects capable of hashing.
 * 
 * @author ProjectJinxers
 */
public interface Hasher {

    /**
     * Creates a hash of the given bytes. The default implementation uses org.ethereum.crypto.HashUtil.sha3.
     * 
     * @param hashBase
     * @return the created hash
     */
    default byte[] hash(byte[] hashBase) {
        return sha3(hashBase);
    }

}
