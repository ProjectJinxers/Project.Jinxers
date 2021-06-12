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

import java.io.IOException;

import org.ethereum.crypto.ECKey;
import org.projectjinxers.account.Signer;
import org.projectjinxers.ipld.IPLDContext;
import org.projectjinxers.ipld.IPLDReader;
import org.projectjinxers.ipld.IPLDWriter;

/**
 * The base interface for all data model classes, that can be saved as IPLD in IPFS.
 * 
 * @author ProjectJinxers
 */
public interface IPLDSerializable {

    /**
     * The version of a stored instance is stored with it in the metadata object. This enables code changes in data
     * model classes, that also change hashes (the ones for signatures and multihashes, as well). Method implementations
     * of {@link #read(IPLDReader, IPLDContext, boolean)} and {@link #write(IPLDWriter, ECKey, IPLDContext)} must
     * respect the version value. The default implementation returns 0.
     * 
     * @return the version (default 0)
     */
    default int getVersion() {
        return 0;
    }

    /**
     * This method is invoked before the metadata block is added to the data, which is to be sent to IPFS. If this
     * method returns true, the data block will be signed and the signature will be added to the metadata block. The
     * default implementation returns false. Override, if the signature is mandatory.
     * 
     * @return true iff the data block must be signed (default: false)
     */
    default boolean isSignatureMandatory() {
        return false;
    }

    /**
     * Reads (deserializes) the single properties.
     * 
     * @param reader            provides the single values by key
     * @param context           the context
     * @param validationContext the validationContext
     * @param eager             if true, links should be resolved with the help of the context (not with the given
     *                          reader!)
     * @param metadata          the metadata (contains the stored version)
     */
    void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
            Metadata metadata);

    /**
     * Writes (serializes) the single properties.
     * 
     * @param writer  takes the single values by key
     * @param signer  the signer (for recursion)
     * @param context the context (for recursion)
     * @throws IOException if writing a single property fails
     */
    void write(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException;

}
