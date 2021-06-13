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
package org.projectjinxers.controller;

import java.io.IOException;

import org.ethereum.crypto.ECKey.ECDSASignature;
import org.projectjinxers.account.Signer;
import org.projectjinxers.model.IPLDSerializable;
import org.projectjinxers.model.Loader;
import org.projectjinxers.model.Metadata;
import org.projectjinxers.model.ValidationContext;

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

    private String rollbackMultihash;
    private byte[] rollbackBytes;

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
        this.loader = loader;
        this.context = context;
        this.validationContext = validationContext;
    }

    /**
     * Copy constructor for previousVersion links.
     * 
     * @param source the source object
     * @param data   the copy of the data instance
     */
    public IPLDObject(IPLDObject<D> source, D data) {
        this.multihash = source.multihash;
        this.mapped = data;
        this.metadata = source.metadata;
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
                LoadResult result = context.loadObject(multihash, loader, validationContext);
                if (result != null) {
                    IPLDObject<?> fromCache = result.getFromCache();
                    if (fromCache == null) {
                        this.metadata = result.getLoadedMetadata();
                        this.mapped = loader.getLoaded();
                    }
                    else {
                        this.metadata = fromCache.getMetadata();
                        @SuppressWarnings("unchecked") // obviously correct
                        D mapped = (D) fromCache.getMapped();
                        this.mapped = mapped;
                    }
                }
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
     * Stores this instance in IPFS. This is a recursive operation. Referenced objects, that have not been saved, yet,
     * will also be saved automatically by calling this method. If one child save operation fails, the root save
     * operation fails, as well. Of course, if you retry with this exact instance, the successfully saved children won't
     * be saved again.
     * 
     * @param context the context
     * @param signer  the signer
     * @return the multihash with which the object can be retrieved
     * @throws IOException if saving fails
     */
    String save(IPLDContext context, Signer signer) throws IOException {
        this.multihash = context.saveObject(this, signer);
        return multihash;
    }

    /**
     * Writes (serializes) the data instance to IPFS. Referenced objects, that have not been saved, yet, will also be
     * saved automatically by calling this method. If one child save operation fails, the root save operation fails, as
     * well. Of course, if you retry with this exact instance, the successfully saved children won't be saved again.
     * 
     * @param writer  takes the single properties by key
     * @param signer  the signer for recursion
     * @param context the context
     * @throws IOException if writing a single property fails
     */
    void write(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {
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
    Metadata signIfMandatory(Signer signer, byte[] hashBase) {
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

    /**
     * Saves the current state, so it can be restored in case the transaction has to be rolled back. If there is a
     * transaction, however, the current rollback data will not be replaced.
     * 
     * @param context the context for saving the current state
     * @return true iff a new transaction has been started (false is returned if there already is a transaction)
     * @throws IOException if saving the current state fails
     */
    boolean beginTransaction(IPLDContext context) throws IOException {
        if (rollbackBytes == null) {
            this.rollbackBytes = context.serializeObject(this, null);
            this.rollbackMultihash = multihash;
            return true;
        }
        return false;
    }

    /**
     * Rolls back to the previous save point marked by the most recent successful invocation of
     * {@link #beginTransaction(IPLDContext)}.
     * 
     * @param context the context
     */
    void rollback(IPLDContext context) {
        this.metadata = context.loadObject(rollbackBytes, loader, null);
        this.mapped = loader.getLoaded();
        this.multihash = rollbackMultihash;
        this.rollbackMultihash = null;
        this.rollbackBytes = null;
    }

    /**
     * Clears the save point, so new transactions can be started.
     */
    void commit() {
        this.rollbackBytes = null;
        this.rollbackMultihash = null;
    }

}
