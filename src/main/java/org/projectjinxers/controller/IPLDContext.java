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
import java.util.HashMap;
import java.util.Map;

import org.ethereum.crypto.ECKey.ECDSASignature;
import org.projectjinxers.account.Signer;
import org.projectjinxers.model.IPLDSerializable;
import org.projectjinxers.model.Loader;
import org.projectjinxers.model.Metadata;
import org.projectjinxers.model.User;

/**
 * Context for IPFS operations on IPLD instances. Successfully saved or loaded and fully validated objects will be
 * cached.
 * 
 * @author ProjectJinxers
 */
public class IPLDContext {

    private final IPFSAccess access;
    private final IPLDEncoding in;
    private final IPLDEncoding out;
    private final boolean eager;

    private Map<String, IPLDObject<?>> cache = new HashMap<>();

    /**
     * Constructor.
     * 
     * @param access the access to the IPFS API
     * @param in     the encoding for submitting data to the IPFS node (IPFS' point of view)
     * @param out    the encoding for saving data in and reading data from IPFS (IPFS' point of view)
     * @param eager  indicates whether or not links are to be resolved instantly (as opposed to on-demand)
     */
    public IPLDContext(IPFSAccess access, IPLDEncoding in, IPLDEncoding out, boolean eager) {
        this.access = access;
        this.in = in;
        this.out = out;
        this.eager = eager;
    }

    /**
     * Serializes and stores the given object in IPFS. If successful, the given object will also be added to the cache.
     * 
     * @param object the object to serialize and store
     * @param signer the optional signer key (if present, and the concrete data instance type supports signing, a
     *               signature is created and stored in the metadata of the given object)
     * @return the string form of the multihash for the saved object
     * @throws IOException if a single write operation fails
     */
    public String saveObject(IPLDObject<?> object, Signer signer) throws IOException {
        byte[] bytes = serializeObject(object, signer);
        String multihash = access.saveObject(in.getIn(), bytes, out.getIn());
        synchronized (cache) {
            cache.put(multihash, object.withoutContext(null));
        }
        return multihash;
    }

    /**
     * Serializes the given object. The given object won't be cached, saved children will.
     * 
     * @param object the object to serialize
     * @param signer the optional signer key (if present, and the concrete data instance type supports signing, a
     *               signature is created and stored in the metadata of the given object)
     * @return the serialized bytes
     * @throws IOException if a single write operation fails
     */
    public byte[] serializeObject(IPLDObject<?> object, Signer signer) throws IOException {
        IPLDWriter writer = in.createWriter();
        return writer.write(this, object, signer);
    }

    /**
     * Reads and deserializes the object addressed under the given multihash. If the cache contains the multihash, the
     * cached instance is returned instead.
     * 
     * @param multihash the multihash of the object to load
     * @param loader    the loader
     * @return the load result containing either the metadata of the read object containing the optional signature
     *         (read, no signing happens here) or the cached object
     * @throws IOException if a single read operation fails
     */
    public LoadResult loadObject(String multihash, Loader<?> loader, ValidationContext validationContext)
            throws IOException {
        synchronized (cache) {
            IPLDObject<?> fromCache = cache.get(multihash);
            if (fromCache != null) {
                return new LoadResult(fromCache);
            }
        }
        byte[] bytes = access.loadObject(multihash);
        if (bytes == null) {
            return null;
        }
        return new LoadResult(loadObject(bytes, loader, validationContext));
    }

    <D extends IPLDSerializable> LoadResult loadObject(IPLDObject<D> object) throws IOException {
        String multihash = object.getMultihash();
        LoadResult result = loadObject(multihash, object.getLoader(), object.getValidationContext());
        if (result != null && result.getFromCache() == null) {
            synchronized (cache) {
                cache.put(multihash, object.withoutContext(result));
            }
        }
        return result;
    }

    /**
     * Deserializes the given bytes.
     * 
     * @param bytes             the bytes to deserialize
     * @param loader            the loader
     * @param validationContext the optional validation context
     * @return the metadata of the read object containing the optional signature (read, no signing happens here)
     * @throws IOException if a single read operation fails
     */
    public Metadata loadObject(byte[] bytes, Loader<?> loader, ValidationContext validationContext) {
        IPLDReader reader = out.createReader();
        return reader.read(this, validationContext, bytes, loader, eager);
    }

    /**
     * Verifies the signature of the given object with the given user.
     * 
     * @param object   the object
     * @param verifier recreates the hash that had been signed and verifies the signature
     * @param user     the user
     */
    public void verifySignature(IPLDObject<?> object, Signer verifier, User user) {
        IPLDWriter writer = in.createWriter();
        try {
            byte[] hashBase = object.getMapped().hashBase(writer, this);
            ECDSASignature signature = object.getMetadata().getSignature();
            if (signature == null) {
                throw new ValidationException("expected signature");
            }
            user.verifySignature(signature, hashBase, verifier);
        }
        catch (IOException e) {
            throw new ValidationException("failed to verify signature", e);
        }
    }

    /**
     * Verifies the signature of the given object with the given public key.
     * 
     * @param object    the object
     * @param verifier  recreates the hash that had been signed and verifies the signature
     * @param publicKey the public key
     */
    public void verifySignature(IPLDObject<?> object, Signer verifier, byte[] publicKey) {
        IPLDWriter writer = in.createWriter();
        try {
            byte[] hashBase = object.getMapped().hashBase(writer, this);
            ECDSASignature signature = object.getMetadata().getSignature();
            if (signature == null) {
                throw new ValidationException("expected signature");
            }
            verifier.verifySignature(signature, hashBase, publicKey);
        }
        catch (IOException e) {
            throw new ValidationException("failed to verify signature", e);
        }
    }

    /**
     * @param multihash the multihash
     * @return the object with the given multihash from the cache
     */
    public IPLDObject<?> getCachedObject(String multihash) {
        return cache.get(multihash);
    }

    public void clearCache() {
        cache.clear();
    }

}
