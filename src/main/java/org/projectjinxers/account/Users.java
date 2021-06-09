/*
	Copyright (C) 2021 ProjectJinxers

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.projectjinxers.account;

import java.io.UnsupportedEncodingException;

import org.ethereum.crypto.ECKey;

import static org.ethereum.crypto.HashUtil.sha3;

/**
 * Utility class for handling user accounts.
 * 
 * @author ProjectJinxers
 */
public class Users {
	
	/**
	 * Creates an ECKey instance from a deterministic concatenation of the given username and password.
	 * 
	 * @param username the username
	 * @param password the password
	 * @return the created key
	 */
	public static ECKey createAccount(String username, String password) {      
        try {
            byte[] privKeyBytes = (username + "#" + password).getBytes("UTF-8");
            privKeyBytes = sha3(privKeyBytes);
            return ECKey.fromPrivate(privKeyBytes);
        }
        catch (UnsupportedEncodingException e) {
            throw new Error(e);
        }
    }

}
