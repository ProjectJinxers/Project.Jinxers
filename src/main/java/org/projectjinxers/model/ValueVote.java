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

import java.io.IOException;

import org.projectjinxers.account.Signer;
import org.projectjinxers.controller.ByteCodec;
import org.projectjinxers.controller.IPLDContext;
import org.projectjinxers.controller.IPLDReader;
import org.projectjinxers.controller.IPLDWriter;

/**
 * A ValueVote is a vote, which carries an arbitrary value. Every vote of an anonymous voting is automatically a an
 * instance of this class.
 * 
 * @author ProjectJinxers
 */
public class ValueVote extends AbstractVote {

    private static final String KEY_STRING_VALUE = "s";
    private static final String KEY_BYTES_VALUE = "b";

    private Object value;

    @Override
    public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
            Metadata metadata) {
        super.read(reader, context, validationContext, eager, metadata);
        String stringValue = reader.readString(KEY_STRING_VALUE);
        if (stringValue != null) {
            this.value = stringValue;
        }
        byte[] bytesValue = reader.readByteArray(KEY_BYTES_VALUE, ByteCodec.DEFAULT);
        if (bytesValue != null) {
            this.value = bytesValue;
        }
        else {
            this.value = null;
        }
    }

    @Override
    public void write(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {
        if (value instanceof byte[]) {
            writer.writeByteArray(KEY_BYTES_VALUE, (byte[]) value, ByteCodec.DEFAULT);
        }
        else if (value instanceof String) {
            writer.writeString(KEY_STRING_VALUE, (String) value);
        }
    }

}
