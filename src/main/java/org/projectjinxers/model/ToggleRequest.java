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
import org.projectjinxers.ipld.IPLDContext;
import org.projectjinxers.ipld.IPLDReader;
import org.projectjinxers.ipld.IPLDWriter;

/**
 * Base class for user requests, where the active state can be toggled. In order to prevent other users from re-posting
 * an old ToggleRequest, payload property has to be increased in comparison to the current version of the request.
 * 
 * @author ProjectJinxers
 */
public abstract class ToggleRequest implements IPLDSerializable {

    private static final String KEY_ACTIVE = "a";
    private static final String KEY_PAYLOAD = "p";
    private static final String KEY_USER = "u";

    private boolean active;
    private int payload;
    private IPLDObject<User> user;

    @Override
    public boolean isSignatureMandatory() {
        return true;
    }

    @Override
    public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
            Metadata metadata) {
        this.active = Boolean.TRUE.equals(reader.readBoolean(KEY_ACTIVE));
        this.payload = reader.readNumber(KEY_PAYLOAD).intValue();
        this.user = reader.readLinkObject(KEY_USER, context, validationContext, LoaderFactory.USER, eager);
    }

    @Override
    public void write(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {
        writer.writeIfTrue(KEY_ACTIVE, active);
        writer.writeNumber(KEY_PAYLOAD, payload);
        writer.writeLink(KEY_USER, user, signer, null);
    }

    /**
     * @return the unwrapped user
     */
    public User getUser() {
        return user == null ? null : user.getMapped();
    }

}
