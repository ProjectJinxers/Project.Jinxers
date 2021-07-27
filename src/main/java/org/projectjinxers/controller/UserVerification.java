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

import java.util.HashMap;
import java.util.Map;

import org.projectjinxers.model.User;

/**
 * @author ProjectJinxers
 *
 */
public class UserVerification {

    public interface UserVerifier {

        public boolean verifyUser(IPLDObject<User> user);

    }

    private static final Map<String, UserVerifier> VERIFIERS = new HashMap<>();
    static {
        VERIFIERS.put("trustAll", (user) -> true); // prototype only, not part of the specification
    }

    public static UserVerifier getVerifier(String key) {
        return VERIFIERS.get(key);
    }

}
