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

/**
 * Interface for all Vote instances.
 * 
 * @author ProjectJinxers
 */
public interface Vote extends IPLDSerializable {

    @Override
    default boolean isSignatureMandatory() {
        return true;
    }

    /**
     * @return the invitationKey
     */
    byte[] getInvitationKey();

    /**
     * @return whether or not the value is supposed to be read from the IOTA Tangle
     */
    boolean isReadValue();

    /**
     * @return the obfuscation parameter of a public hash operation with secret parameters, which hashes the vote value
     */
    int getValueHashObfuscation();

    Object getValue();

}
