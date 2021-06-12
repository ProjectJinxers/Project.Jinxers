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
package org.projectjinxers.ipld;

import java.io.IOException;

import org.ethereum.crypto.ECKey.ECDSASignature;
import org.projectjinxers.account.Signer;
import org.projectjinxers.model.IPLDObject;
import org.projectjinxers.model.Loader;
import org.projectjinxers.model.Metadata;
import org.projectjinxers.model.ValidationContext;

import io.ipfs.api.MerkleNode;
import io.ipfs.cid.Cid;

/**
 * Context for IPFS operations on IPLD instances.
 * 
 * @author ProjectJinxers
 */
public class IPLDContext {

    private final IPFSAccess access;
    private final IPLDEncoding in;
    private final IPLDEncoding out;
    private final boolean eager;

    /**
     * Constructor.
     * 
     * @param access the access to the IPFS API
     * @param in     the encoding for submitting data to the IPFS node (IPFS' point of view)
     * @param out    the encoding for saving data in and reading data from IPFS (IPFS' point of view)
     * @param eager  indicates whether or not links are to be resolved now (as opposed to on-demand)
     */
    public IPLDContext(IPFSAccess access, IPLDEncoding in, IPLDEncoding out, boolean eager) {
        this.access = access;
        this.in = in;
        this.out = out;
        this.eager = eager;
    }

    /**
     * Serializes and stores the given object in IPFS.
     * 
     * @param object the object to serialize and store
     * @param signer the optional signer key (if present, and the concrete data instance type supports signing, a
     *               signature is created and stored in the metadata of the given object)
     * @return the string form of the multihash for the saved object
     * @throws IOException if a single write operation fails
     */
    public String saveObject(IPLDObject<?> object, Signer signer) throws IOException {
        IPLDWriter writer = in.createWriter();
        byte[] bytes = writer.write(this, object, signer);
        MerkleNode node = access.ipfs.dag.put(in.getIn(), bytes, out.getIn());
        return node.hash.toString();
    }

    /**
     * Reads and deserializes the object addressed under the given multihash.
     * 
     * @param multihash     the multihash of the object to load
     * @param emptyInstance the empty data instance
     * @return the metadata of the read object containing the optional signature (read, no signing happens here)
     * @throws IOException if a single read operation fails
     */
    public Metadata loadObject(String multihash, Loader<?> loader, ValidationContext validationContext)
            throws IOException {
        byte[] bytes = access.ipfs.dag.get(Cid.decode(multihash));
        if (bytes == null) {
            return null;
        }
        IPLDReader reader = out.createReader();
        return reader.read(this, validationContext, bytes, loader, eager);
    }

    /**
     * Verifies the signature of the given object with the given public key.
     * 
     * @param object    the object
     * @param verifier  recreates the hash that had been signed and verifies the signature
     * @param publicKey the public key
     * @return true iff the signature has been verified successfully
     */
    public boolean verifySignature(IPLDObject<?> object, Signer verifier, byte[] publicKey) {
        IPLDWriter writer = in.createWriter();
        try {
            byte[] hashBase = writer.hashBase(this, object.getMapped());
            ECDSASignature signature = object.getMetadata().getSignature();
            return signature != null && verifier.verifySignature(signature, hashBase, publicKey);
        }
        catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

}
