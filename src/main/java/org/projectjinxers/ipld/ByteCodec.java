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

import com.github.fzakaria.ascii85.Ascii85;

/**
 * Interface for encoding and decoding bytes. The default implementation use ascii85 (a.k.a. base85), the most compact
 * form we know.
 * 
 * @author ProjectJinxers
 */
public interface ByteCodec {

    /**
     * The default (since most compact) codec: ascii85 (a.k.a. base85).
     */
    public static final ByteCodec DEFAULT = new ByteCodec() {
    };

    /**
     * Encodes the given bytes. The default implementation uses ascii85 (a.k.a. base85).
     * 
     * @param bytes the bytes to encode
     * @return the encoded bytes
     */
    default String encode(byte[] bytes) {
        return bytes == null ? null : Ascii85.encode(bytes);
    }

    /**
     * Decodes the given encoded string. The default implementation uses ascii85 (a.k.a. base85).
     * 
     * @param encoded the encoded string
     * @return the decoded string
     */
    default byte[] decode(String encoded) {
        return encoded == null ? null : Ascii85.decode(encoded);
    }

}
