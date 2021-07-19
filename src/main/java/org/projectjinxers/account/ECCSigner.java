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

import java.util.Arrays;

import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.ECKey.ECDSASignature;

/**
 * Signs hashes with an {@link ECKey} instance, that is created from a username and a password. It is recommended to not
 * keep this instance longer than necessary in memory. The best approach w.r.t security is to have the user enter their
 * credentials every time an operation is performed that requires the user's signature.
 * 
 * @author ProjectJinxers
 */
public class ECCSigner implements Signer {

    private ECKey key;

    /**
     * Constructor.
     * 
     * @param username the username
     * @param password the password
     */
    public ECCSigner(String username, String password) {
        this(username, password, 0);
    }

    public ECCSigner(String username, String password, int securityLevel) {
        this.key = Users.createAccount(username, password, securityLevel);
    }

    @Override
    public ECDSASignature signHash(byte[] hash) {
        return key.sign(hash);
    }

    public boolean checkPublicKey(byte[] publicKey) {
        return Arrays.equals(key.getPubKey(), publicKey);
    }

}
