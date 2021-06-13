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
package org.projectjinxers.account;

import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.ECKey.ECDSASignature;
import org.projectjinxers.model.Hasher;

/**
 * Interface for objects capable of creating and verifying ECC signatures in addition to hashing.
 * 
 * @author ProjectJinxers
 */
public interface Signer extends Hasher {

    /**
     * A special Signer that uses default implementations for hashing and verifying. Signing is not supported.
     */
    public static final Signer VERIFIER = new Signer() {
        @Override
        public ECDSASignature signHash(byte[] hash) {
            return null;
        }
    };

    /**
     * Signs the given bytes after hashing them. The default implementation calls {@link #signHash(byte[])} passing the
     * result of {@link #hash(byte[])}.
     * 
     * @param hashBase the bytes to hash and sign
     * @return the signature
     */
    default ECDSASignature sign(byte[] hashBase) {
        return signHash(hash(hashBase));
    }

    /**
     * Signs the given hash bytes.
     * 
     * @param hash the hash bytes to sign
     * @return the signature
     */
    ECDSASignature signHash(byte[] hash);

    /**
     * Verifies the given signature.
     * 
     * @param signature the signature
     * @param hashBase  the bytes to verify the signature against after hashing
     * @param publicKey the public key, which is the counterpart to the private key, that had been used to sign the hash
     * @return true iff the signature is valid
     */
    default boolean verifySignature(ECDSASignature signature, byte[] hashBase, byte[] publicKey) {
        byte[] hash = hash(hashBase);
        return ECKey.verify(hash, signature, publicKey);
    }

}
