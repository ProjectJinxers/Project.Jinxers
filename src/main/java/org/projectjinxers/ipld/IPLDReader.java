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

import java.lang.reflect.Array;

import org.projectjinxers.model.IPLDObject;
import org.projectjinxers.model.IPLDSerializable;
import org.projectjinxers.model.Metadata;
import org.projectjinxers.model.ValidationContext;

/**
 * Interface for deserializing data read from IPFS.
 * 
 * @author ProjectJinxers
 */
public interface IPLDReader {

    /**
     * Reads (deserializes) the given data.
     * 
     * @param context           the context
     * @param validationContext the validation context
     * @param bytes             the raw bytes
     * @param emptyInstance     the data model instance (will be populated with the values)
     * @param eager             indicates whether or not links are to be resolved now (as opposed to on-demand)
     * @return the metadata of the read object
     */
    Metadata read(IPLDContext context, ValidationContext validationContext, byte[] bytes,
            IPLDSerializable emptyInstance, boolean eager);

    /**
     * Reads a Boolean value.
     * 
     * @param key the key
     * @return the boolean value for the given key
     */
    Boolean readBoolean(String key);

    /**
     * Reads a Character value.
     * 
     * @param key the key
     * @return the Character value for the given key
     */
    Character readCharacter(String key);

    /**
     * Reads a Number value.
     * 
     * @param key the key
     * @return the Number value for the given key
     */
    Number readNumber(String key);

    /**
     * Reads a string value.
     * 
     * @param key the key
     * @return the String value for the given key
     */
    String readString(String key);

    /**
     * Reads a link value (the link is not resolved).
     * 
     * @param key the key
     * @return the link value for the given key
     */
    String readLink(String key);

    /**
     * Reads a link object value. The link is resolved if eager is true.
     * 
     * @param key               the key
     * @param context           the context (not for recursion only, if null, accessing the data instance will fail)
     * @param validationContext the validation context
     * @param dataClass         the class of the data instances
     * @param eager             indicates whether or not the link is to be resolved now (as opposed to on-demand)
     * @return the link object value for the given key
     */
    default <D extends IPLDSerializable> IPLDObject<D> readLinkObject(String key, IPLDContext context,
            ValidationContext validationContext, Class<D> dataClass, boolean eager) {
        String linkMultihash = readLink(key);
        IPLDObject<D> res = new IPLDObject<D>(linkMultihash, context, validationContext, dataClass);
        if (eager) {
            res.getMapped();
        }
        return res;
    }

    /**
     * Reads a boolean array value.
     * 
     * @param key the key
     * @return the boolean array value for the given key
     */
    boolean[] readBooleanArray(String key);

    /**
     * Reads a char array value.
     * 
     * @param key the key
     * @return the char array value for the given key
     */
    char[] readCharArray(String key);

    /**
     * Reads an int array value.
     * 
     * @param key the key
     * @return the char array value for the given key
     */
    int[] readIntArray(String key);

    /**
     * Reads a long array value.
     * 
     * @param key the key
     * @return the char array value for the given key
     */
    long[] readLongArray(String key);

    /**
     * Reads a Number array value.
     * 
     * @param key the key
     * @return the Number array value for the given key
     */
    Number[] readNumberArray(String key);

    /**
     * Reads a String array value.
     * 
     * @param key the key
     * @return the String array value for the given key
     */
    String[] readStringArray(String key);

    /**
     * Reads a links array value (the links are not resolved).
     * 
     * @param key the key
     * @return the links array value for the given key
     */
    String[] readLinksArray(String key);

    /**
     * Reads a link objects array value. The links are resolved if eager is true. The default implementation is complete
     * and uses a bit of reflection.
     * 
     * @param key               the key
     * @param context           the context (not for recursion only, if null, accessing the data instance of each entry
     *                          will fail)
     * @param validationContext the validation context
     * @param dataClass         the class of the data instances
     * @param eager             indicates whether or not links are to be resolved now (as opposed to on-demand)
     * @return the link objects array value for the given key
     */
    default <D extends IPLDSerializable> IPLDObject<D>[] readLinkObjectsArray(String key, IPLDContext context,
            ValidationContext validationContext, Class<D> dataClass, boolean eager) {
        String[] linksArray = readLinksArray(key);
        @SuppressWarnings("unchecked")
        IPLDObject<D>[] res = (IPLDObject<D>[]) Array.newInstance(IPLDObject.class, linksArray.length);
        int i = 0;
        for (String link : linksArray) {
            IPLDObject<D> linkObject = new IPLDObject<>(link, context, validationContext, dataClass);
            if (eager) {
                linkObject.getMapped();
            }
            res[i++] = linkObject;
        }
        return res;
    }

}
