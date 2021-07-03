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

/**
 * Enum for IPLD encodings/codecs for sending and receiving data.
 * 
 * @author ProjectJinxers
 */
public enum IPLDEncoding {

    /**
     * The JSON encoding/codec
     */
    JSON("json", "json") {

        @Override
        public IPLDReader createReader() {
            return new IPLDJsonReader();
        }

        @Override
        public IPLDWriter createWriter() {
            return new IPLDJsonWriter(true);
        }

    },
    /**
     * The CBOR encoding/codec
     */
    CBOR("cbor", "cbor") {

        @Override
        public IPLDReader createReader() {
            return new IPLDJsonReader();
        }

        @Override
        public IPLDWriter createWriter() {
            return new IPLDJsonWriter(true);
        }

    };

    private String in;
    private String out;

    private IPLDEncoding(String in, String out) {
        this.in = in;
        this.out = out;
    }

    /**
     * @return the value for the encoding, that is used for sending data to IPFS.
     */
    public String getIn() {
        return in;
    }

    /**
     * @return the value for the encoding, that is used for storing data in IPFS and reading data from IPFS.
     */
    public String getOut() {
        return out;
    }

    /**
     * @return the reader (corresponds to {@link #getOut()}, IPFS' point of view)
     */
    public abstract IPLDReader createReader();

    /**
     * @return the writer (corresponds to {@link #getIn()}, IPFS' point of view)
     */
    public abstract IPLDWriter createWriter();

}
