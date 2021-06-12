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
import java.util.Date;

import org.projectjinxers.account.Signer;
import org.projectjinxers.controller.IPLDContext;
import org.projectjinxers.ipld.ByteCodec;
import org.projectjinxers.ipld.IPLDReader;
import org.projectjinxers.ipld.IPLDWriter;

/**
 * In contrast to {@link UserState} instances, instances of this class don't represent the state of a user at a specific
 * time, but the users themselves. They are immutable.
 * 
 * @author ProjectJinxers
 */
public class User implements IPLDSerializable, Loader<User> {

    private static final String KEY_USERNAME = "u";
    private static final String KEY_CREATED_AT = "c";
    private static final String KEY_PUBLIC_KEY = "p";

    private String username;
    private Date createdAt;
    private byte[] publicKey;

    @Override
    public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
            Metadata metadata) {
        this.username = reader.readString(KEY_USERNAME);
        this.createdAt = new Date(reader.readNumber(KEY_CREATED_AT).longValue());
        this.publicKey = reader.readByteArray(KEY_PUBLIC_KEY, ByteCodec.DEFAULT);
    }

    @Override
    public void write(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {
        writer.writeString(KEY_USERNAME, username);
        writer.writeNumber(KEY_CREATED_AT, createdAt.getTime());
        writer.writeByteArray(KEY_PUBLIC_KEY, publicKey, ByteCodec.DEFAULT);
    }

    @Override
    public boolean isSignatureMandatory() {
        return true;
    }

    @Override
    public User getOrCreateDataInstance(IPLDReader reader, Metadata metadata) {
        return this;
    }

    @Override
    public User getLoaded() {
        return this;
    }

}
