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
package org.projectjinxers.model;

import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.ECKey.ECDSASignature;
import org.projectjinxers.ipld.IPLDContext;

/**
 * The base interface for all data model classes, that can be saved as IPLD in IPFS.
 * 
 * @author ProjectJinxers
 */
public interface IPLDSerializable {
	
	/**
	 * Reads (deserializes) the single properties.
	 * 
	 * @param contextForRecursion if non-null, links should be resolved with the help of this context
	 */
	void read(IPLDContext contextForRecursion);
	
	/**
	 * Writes (serializes) the single properties. This default implementation calls
	 * {@link #writeProperties(ECKey, IPLDContext)} and, if that returned a non-null hash, signs that hash with the
	 * given key.
	 * 
	 * @param signingKey the signing key
	 * @param context the context
	 * @return the signature if the hash has been created and signed
	 */
	default ECDSASignature write(ECKey signingKey, IPLDContext context) {
		byte[] hash = writeProperties(signingKey, context);
		return hash == null ? null : signingKey.sign(hash);
	}
	
	/**
	 * Writes (serializes) the single properties.
	 * 
	 * @param signingKey the signing key (for recursion)
	 * @param context the context
	 * @return the optional hash (if null, the data will not be signed)
	 */
	byte[] writeProperties(ECKey signingKey, IPLDContext context);
	
	/**
	 * @return the hash of the serialized data (this is not the multihash for the object)
	 */
	byte[] hash();

}
