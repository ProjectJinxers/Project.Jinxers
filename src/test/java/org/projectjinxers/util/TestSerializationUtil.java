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
package org.projectjinxers.util;

import org.ethereum.crypto.ECKey.ECDSASignature;

/**
 * Utility class for handling IPLD JSON in test cases.
 * 
 * @author ProjectJinxers
 */
public class TestSerializationUtil {

    /**
     * Wraps the given fragment and adds the metadata block, where the version is 0, as a sibling.
     * 
     * @param dataFragment the data fragment
     * @return the complete JSON string
     */
    public static String jsonString(String dataFragment) {
        return jsonString(0, dataFragment);
    }

    /**
     * Wraps the given fragment and adds the metadata block, where the version is the given version, as a sibling.
     * 
     * @param version      the data model class version
     * @param dataFragment the data fragment
     * @return the complete JSON string
     */
    public static String jsonString(int version, String dataFragment) {
        return "{\"data\":" + dataFragment + ",\"meta\":{\"version\":" + version + "}}";
    }

    /**
     * Wraps the given fragment and adds the metadata block, where the version is 0, as a sibling.
     * 
     * @param signature    if not null, the metadata block will contain the r, s and v values, as well
     * @param dataFragment the data fragment
     * @return the complete JSON string
     */
    public static String jsonString(ECDSASignature signature, String dataFragment) {
        return jsonString(0, signature, dataFragment);
    }

    /**
     * Wraps the given fragment and adds the metadata block, where the version is the given version, as a sibling.
     * 
     * @param version      the data model class version
     * @param signature    if not null, the metadata block will contain the r, s and v values, as well
     * @param dataFragment the data fragment
     * @return the complete JSON string
     */
    public static String jsonString(int version, ECDSASignature signature, String dataFragment) {
        if (signature == null) {
            return jsonString(version, dataFragment);
        }
        return "{\"data\":" + dataFragment + ",\"meta\":{\"version\":" + version + ",\"r\":" + signature.r + ",\"s\":"
                + signature.s + ",\"v\":" + signature.v + "}}";
    }

    /**
     * Wraps the given fragment and adds the metadata block, where the version is 0, as a sibling.
     * 
     * @param dataFragment the data fragment
     * @return the complete JSON string converted to bytes
     */
    public static byte[] jsonBytes(String dataFragment) {
        return jsonBytes(0, dataFragment);
    }

    /**
     * Wraps the given fragment and adds the metadata block, where the version is the given version, as a sibling.
     * 
     * @param version      the data model class version
     * @param dataFragment the data fragment
     * @return the complete JSON string converted to bytes
     */
    public static byte[] jsonBytes(int version, String dataFragment) {
        return jsonString(version, dataFragment).getBytes();
    }

    /**
     * Wraps the given fragment and adds the metadata block, where the version is 0, as a sibling.
     * 
     * @param signature    if not null, the metadata block will contain the r, s and v values, as well
     * @param dataFragment the data fragment
     * @return the complete JSON string converted to bytes
     */
    public static byte[] jsonBytes(ECDSASignature signature, String dataFragment) {
        if (signature == null) {
            return jsonBytes(dataFragment);
        }
        return jsonBytes(0, signature, dataFragment);
    }

    /**
     * Wraps the given fragment and adds the metadata block, where the version is the given version, as a sibling.
     * 
     * @param version      the data model class version
     * @param signature    if not null, the metadata block will contain the r, s and v values, as well
     * @param dataFragment the data fragment
     * @return the complete JSON string converted to bytes
     */
    public static byte[] jsonBytes(int version, ECDSASignature signature, String dataFragment) {
        if (signature == null) {
            return jsonBytes(version, dataFragment);
        }
        return jsonString(version, signature, dataFragment).getBytes();
    }

}
