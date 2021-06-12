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
import java.lang.reflect.Array;
import java.util.Map;

import org.projectjinxers.account.Signer;
import org.projectjinxers.model.IPLDObject;
import org.projectjinxers.model.IPLDSerializable;

/**
 * Interface for serializing data for sending it to IPFS. A note on null values: Currently there is no implementation,
 * that needs to read/write null values. That's why the default implementations of the convenience methods all ignore
 * null values. If there should be an implementation in the future, that must read/write null values, the null checks
 * have to be moved to the implementations, that can ignore null values.
 * 
 * @author ProjectJinxers
 */
public interface IPLDWriter {

    /**
     * Writes (serializes) the given object.
     * 
     * @param context the context
     * @param object  the object to write
     * @param signer  the optional signer (if present, and the concrete data instance type supports signing, a signature
     *                is created and stored in the metadata of the given object)
     * @return the serialized form of the given object
     * @throws IOException if single write operations fail
     */
    byte[] write(IPLDContext context, IPLDObject<?> object, Signer signer)
            throws IOException;

    /**
     * Calculates the bytes to hash for creating or verifying a signature.
     * 
     * @param context the context
     * @param data    the data instance
     * @return the bytes to hash
     * @throws IOException if single write operations fail
     */
    byte[] hashBase(IPLDContext context, IPLDSerializable data) throws IOException;

    /**
     * Writes a boolean property.
     * 
     * @param key   the key
     * @param value the value
     * @throws IOException if writing fails
     */
    void writeBoolean(String key, boolean value) throws IOException;

    /**
     * Writes a char property.
     * 
     * @param key   the key
     * @param value the value
     * @throws IOException if writing fails
     */
    void writeChar(String key, char value) throws IOException;

    /**
     * Writes a Number property.
     * 
     * @param key   the key
     * @param value the value
     * @throws IOException if writing fails
     */
    void writeNumber(String key, Number value) throws IOException;

    /**
     * Writes a String property.
     * 
     * @param key   the key
     * @param value the value
     * @throws IOException if writing fails
     */
    void writeString(String key, String value) throws IOException;

    /**
     * Writes a link property.
     * 
     * @param key  the key
     * @param link the link (multihash)
     * @throws IOException if writing fails
     */
    void writeLink(String key, String link) throws IOException;

    /**
     * Writes a link property. If the given link object has no multihash, it will be saved recursively.
     * 
     * @param key     the key
     * @param link    the link
     * @param signer  the signer for recursion
     * @param context the context for recursion
     * @throws IOException if writing fails
     */
    default void writeLink(String key, IPLDObject<?> link, Signer signer, IPLDContext context) throws IOException {
        if (link != null) {
            String multihash = link.getMultihash();
            if (multihash == null) {
                multihash = context.saveObject(link, signer);
            }
            writeLink(key, multihash);
        }
    }

    /**
     * Writes a boolean array property.
     * 
     * @param key   the key
     * @param value the value
     * @throws IOException if writing fails
     */
    void writeBooleanArray(String key, boolean[] value) throws IOException;

    /**
     * Writes a byte array property.
     * 
     * @param key   the key
     * @param value the value
     * @param codec the codec used to encode if a string is actually written
     * @throws IOException if writing fails
     */
    void writeByteArray(String key, byte[] value, ByteCodec codec) throws IOException;

    /**
     * Writes a char array property.
     * 
     * @param key   the key
     * @param value the value
     * @throws IOException if writing fails
     */
    void writeCharArray(String key, char[] value) throws IOException;

    /**
     * Writes a int array property.
     * 
     * @param key   the key
     * @param value the value
     * @throws IOException if writing fails
     */
    void writeIntArray(String key, int[] value) throws IOException;

    /**
     * Writes a long array property.
     * 
     * @param key   the key
     * @param value the value
     * @throws IOException if writing fails
     */
    void writeLongArray(String key, long[] value) throws IOException;

    /**
     * Writes a Number array property.
     * 
     * @param key   the key
     * @param value the value
     * @throws IOException if writing fails
     */
    void writeNumberArray(String key, Number[] value) throws IOException;

    /**
     * Writes a String array property.
     * 
     * @param key   the key
     * @param value the value
     * @throws IOException if writing fails
     */
    void writeStringArray(String key, String[] value) throws IOException;

    /**
     * Writes a link array property.
     * 
     * @param key   the key
     * @param links the links (multihashes)
     * @throws IOException if writing fails
     */
    void writeLinkArray(String key, String[] links) throws IOException;

    /**
     * Recursively writes a link array property. All links without multihash will be saved recursively.
     * 
     * @param key     the key
     * @param links   the links
     * @param signer  the signer for recursion
     * @param context the context for recursion
     * @throws IOException if writing fails
     */
    void writeLinkArray(String key, IPLDObject<?>[] links, Signer signer, IPLDContext context) throws IOException;

    /**
     * Recursively writes a link map property. All links without multihash will be saved recursively. The default
     * implementation serializes the map as an array, as that is a more compact form (the keys don't have to be
     * serialized twice).
     * 
     * @param key     the key
     * @param links   the links
     * @param signer  the signer for recursion
     * @param context the context for recursion
     * @throws IOException if writing fails
     */
    default void writeLinkObjects(String key, Map<String, IPLDObject<?>> links, Signer signer, IPLDContext context)
            throws IOException {
        if (links != null) {
            IPLDObject<?>[] linkArray = (IPLDObject<?>[]) Array.newInstance(IPLDObject.class, links.size());
            writeLinkArray(key, links.values().toArray(linkArray), signer, context);
        }
    }

}
