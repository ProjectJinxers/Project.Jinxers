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
package org.projectjinxers.ipld;

import java.io.IOException;

import org.ethereum.crypto.ECKey;
import org.projectjinxers.model.IPLDObject;
import org.projectjinxers.model.IPLDSerializable;

/**
 * Interface for writing data to IPFS.
 * 
 * @author ProjectJinxers
 */
public interface IPLDWriter {
	
	/**
	 * Writes (serializes) the given object.
	 * 
	 * @param <D> the type of the data instance to write (contained in object)
	 * @param context the context
	 * @param object the object to write
	 * @param signingKey the optional signing key (if present, and the concrete data instance type supports signing,
	 * a signature is created and stored in the given object)
	 * @return the serialized form of the given object
	 * @throws IOException if single write operations fail
	 */
	<D extends IPLDSerializable> byte[] write(IPLDContext context, IPLDObject<D> object, ECKey signingKey)
	throws IOException;
	
	/**
	 * Writes a boolean property.
	 * 
	 * @param key the key
	 * @param value the value
	 * @throws IOException if writing fails
	 */
	void writeBoolean(String key, boolean value) throws IOException;
	
	/**
	 * Writes a char property.
	 * 
	 * @param key the key
	 * @param value the value
	 * @throws IOException if writing fails
	 */
	void writeChar(String key, char value) throws IOException;
	
	/**
	 * Writes a Number property.
	 * 
	 * @param key the key
	 * @param value the value
	 * @throws IOException if writing fails
	 */
	void writeNumber(String key, Number value) throws IOException;
	
	/**
	 * Writes a String property.
	 * 
	 * @param key the key
	 * @param value the value
	 * @throws IOException if writing fails
	 */
	void writeString(String key, String value) throws IOException;
	
	/**
	 * Writes a link property.
	 * 
	 * @param key the key
	 * @param link the link (multihash)
	 * @throws IOException if writing fails
	 */
	void writeLink(String key, String link) throws IOException;
	
	/**
	 * Writes a boolean array property.
	 * 
	 * @param key the key
	 * @param value the value
	 * @throws IOException if writing fails
	 */
	void writeBooleanArray(String key, boolean[] value) throws IOException;
	
	/**
	 * Writes a char array property.
	 * 
	 * @param key the key
	 * @param value the value
	 * @throws IOException if writing fails
	 */
	void writeCharArray(String key, char[] value) throws IOException;
	
	/**
	 * Writes a Number array property.
	 * 
	 * @param key the key
	 * @param value the value
	 * @throws IOException if writing fails
	 */
	void writeNumberArray(String key, Number[] value) throws IOException;
	
	/**
	 * Writes a String array property.
	 * 
	 * @param key the key
	 * @param value the value
	 * @throws IOException if writing fails
	 */
	void writeStringArray(String key, String[] value) throws IOException;
	
	/**
	 * Writes a link array property.
	 * 
	 * @param key the key
	 * @param links the links (multihashes)
	 * @throws IOException if writing fails
	 */
	void writeLinkArray(String key, String[] links) throws IOException;

}
