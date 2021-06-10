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
import java.lang.reflect.Constructor;

import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.ECKey.ECDSASignature;
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
    private Class<D> dataClass;
    private ECDSASignature signature;

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
     * @param multihash the multihash
     * @param context   the context
     * @param dataClass the class of the data instance
     */
    public IPLDObject(String multihash, IPLDContext context, Class<D> dataClass) {
        this.multihash = multihash;
        this.context = context;
        this.dataClass = dataClass;
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
                Constructor<D> ctor = dataClass.getDeclaredConstructor();
                ctor.setAccessible(true);
                mapped = ctor.newInstance();
                signature = context.loadObject(multihash, mapped);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        return mapped;
    }

    /**
     * Verifies the signature with the given public key.
     * 
     * @param publicKey the public key
     * @return true iff the signature has been verified successfully
     */
    public boolean verifySignature(byte[] publicKey) {
        return signature != null && getMapped() != null && ECKey.verify(mapped.hash(), signature, publicKey);
    }

    /**
     * Writes (serializes) the data instance to IPFS. Stores and returns the signature, if any.
     * 
     * @param writer     takes the single properties by key
     * @param signingKey the key for signing the hash
     * @param context    the context
     * @return the optional signature
     * @throws IOException if writing a single property fails
     */
    public ECDSASignature write(IPLDWriter writer, ECKey signingKey, IPLDContext context) throws IOException {
        this.signature = mapped.write(writer, signingKey, context);
        return signature;
    }

}
