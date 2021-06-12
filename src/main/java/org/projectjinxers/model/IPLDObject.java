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

import org.ethereum.crypto.ECKey.ECDSASignature;
import org.projectjinxers.account.Signer;
import org.projectjinxers.ipld.IPLDContext;
import org.projectjinxers.ipld.IPLDWriter;

/**
 * Wrapper class for data model objects, that can be saved as IPLD in IPFS. By default, loading/reading an instance from
 * IPFS does not automatically load the entire (sub)tree. Primitive kinds are mapped. Links are resolved on access.
 * 
 * @author ProjectJinxers
 * @param <D> the type of the data instance
 */
public class IPLDObject<D extends IPLDSerializable> {

    private String multihash;
    private D mapped;
    private IPLDContext context;
    private ValidationContext validationContext;
    private Loader<D> loader;
    private Metadata metadata;

    /**
     * Constructor for locally created objects. Usually the instance will be written to IPFS.
     * 
     * @param data the data instance
     */
    public IPLDObject(D data) {
        this.mapped = data;
    }

    /**
     * Constructor for loaded objects or root objects that are to be loaded.
     * 
     * @param multihash         the multihash
     * @param loader            a wrapper for the data instance (might be the data instance itself, though)
     * @param context           the context
     * @param validationContext the validation context
     */
    public IPLDObject(String multihash, Loader<D> loader, IPLDContext context, ValidationContext validationContext) {
        this.multihash = multihash;
        this.context = context;
        this.validationContext = validationContext;
        this.loader = loader;
    }

    /**
     * @return the multihash
     */
    public String getMultihash() {
        return multihash;
    }

    /**
     * If the data instance has not been resolved, yet, it is loaded with the help of the context, that had been passed
     * to the constructor.
     * 
     * @return the resolved and mapped data instance
     */
    public D getMapped() {
        if (mapped == null && multihash != null) {
            try {
                this.metadata = context.loadObject(multihash, loader, validationContext);
                this.mapped = loader.getLoaded();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        return mapped;
    }

    /**
     * @return the metadata (if this instance has not metadata, yet, {@link #getMapped()} is called)
     */
    public Metadata getMetadata() {
        if (metadata == null) {
            getMapped();
        }
        return metadata;
    }

    /**
     * Writes (serializes) the data instance to IPFS.
     * 
     * @param writer  takes the single properties by key
     * @param signer  the signer for recursion
     * @param context the context
     * @throws IOException if writing a single property fails
     */
    public void write(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {
        mapped.write(writer, signer, context);
    }

    /**
     * This method is called after serializing the data. If the data indicates that signing be mandatory, the data is
     * hashed and signed and the signature is stored in the returned metadata instance, which is stored in this object,
     * as well.
     * 
     * @param signer   the signer (in case signing is mandatory)
     * @param hashBase the data to hash and sign
     * @return the metadata containing the signature and version
     */
    public Metadata signIfMandatory(Signer signer, byte[] hashBase) {
        ECDSASignature signature;
        if (getMapped().isSignatureMandatory()) {
            signature = signer.sign(hashBase);
        }
        else {
            signature = null;
        }
        this.metadata = new Metadata(mapped.getVersion(), signature);
        return metadata;
    }

}
