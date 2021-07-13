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
import org.projectjinxers.controller.IPLDObject.ProgressListener;
import org.projectjinxers.controller.IPLDReader;
import org.projectjinxers.controller.IPLDWriter;
import org.projectjinxers.controller.ValidationContext;

/**
 * Abstract base implementation of the Vote interface.
 * 
 * @author ProjectJinxers
 */
public abstract class AbstractVote implements Vote {

    private static final String KEY_INVITATION_KEY = "i";
    private static final String KEY_READ_VALUE = "r";
    private static final String KEY_VALUE_HASH_OBFUSCATION = "o";

    private byte[] invitationKey;
    private boolean readValue;
    private int valueHashObfuscation;

    AbstractVote() {

    }

    protected AbstractVote(byte[] invitationKey, boolean readValue, int valueHashObfuscation) {
        this.invitationKey = invitationKey;
        this.readValue = readValue;
        this.valueHashObfuscation = valueHashObfuscation;
    }

    @Override
    public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
            Metadata metadata) {
        this.invitationKey = reader.readByteArray(KEY_INVITATION_KEY, ByteCodec.DEFAULT);
        this.readValue = Boolean.TRUE.equals(reader.readBoolean(KEY_READ_VALUE));
        this.valueHashObfuscation = reader.readNumber(KEY_VALUE_HASH_OBFUSCATION).intValue();
    }

    @Override
    public void write(IPLDWriter writer, Signer signer, IPLDContext context, ProgressListener progressListener)
            throws IOException {
        writer.writeByteArray(KEY_INVITATION_KEY, invitationKey, ByteCodec.DEFAULT);
        writer.writeIfTrue(KEY_READ_VALUE, readValue);
        writer.writeNumber(KEY_VALUE_HASH_OBFUSCATION, valueHashObfuscation);
    }

    @Override
    public byte[] getInvitationKey() {
        return invitationKey;
    }

    @Override
    public boolean isReadValue() {
        return readValue;
    }

    @Override
    public int getValueHashObfuscation() {
        return valueHashObfuscation;
    }

}
