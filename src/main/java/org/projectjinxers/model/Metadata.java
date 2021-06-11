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

import org.ethereum.crypto.ECKey.ECDSASignature;

/**
 * Contains metadata of saved data instances. This is stored along with the data.
 * 
 * @author ProjectJinxers
 */
public class Metadata {

    private int version;
    private ECDSASignature signature;

    /**
     * Constructor.
     * 
     * @param version   the data model class version
     * @param signature the optional signature of the data model's hash
     */
    public Metadata(int version, ECDSASignature signature) {
        this.version = version;
        this.signature = signature;
    }

    /**
     * @return the data model class version
     */
    public int getVersion() {
        return version;
    }

    /**
     * @return the optional signature of the data model's hash
     */
    public ECDSASignature getSignature() {
        return signature;
    }

}
