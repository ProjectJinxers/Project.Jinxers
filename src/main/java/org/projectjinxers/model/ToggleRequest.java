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
import org.projectjinxers.controller.IPLDContext;
import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.controller.IPLDReader;
import org.projectjinxers.controller.IPLDWriter;
import org.projectjinxers.controller.ValidationContext;
import org.projectjinxers.controller.ValidationException;

/**
 * Base class for user requests, where the active state can be toggled. In order to prevent other users from re-posting
 * an old ToggleRequest, payload property has to be increased in comparison to the current version of the request.
 * 
 * @author ProjectJinxers
 */
public abstract class ToggleRequest implements IPLDSerializable {

    private static final String KEY_ACTIVE = "a";
    private static final String KEY_PAYLOAD = "p";
    private static final String KEY_USER_STATE = "u";

    private boolean active;
    private int payload;
    private IPLDObject<UserState> userState;

    protected ToggleRequest() {

    }

    /**
     * Constructor for a new toggle request.
     * 
     * @param userState the userState (at creation time)
     */
    protected ToggleRequest(IPLDObject<UserState> userState) {
        this.active = true;
        this.userState = userState;
    }

    @Override
    public boolean isSignatureMandatory() {
        return true;
    }

    @Override
    public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
            Metadata metadata) {
        this.active = Boolean.TRUE.equals(reader.readBoolean(KEY_ACTIVE));
        this.payload = reader.readNumber(KEY_PAYLOAD).intValue();
        this.userState = reader.readLinkObject(KEY_USER_STATE, context, validationContext, LoaderFactory.USER_STATE,
                eager);
        if (validationContext != null) {
            validationContext.addMustValidateUserState(userState);
        }
    }

    @Override
    public void write(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {
        writer.writeIfTrue(KEY_ACTIVE, active);
        writer.writeNumber(KEY_PAYLOAD, payload);
        writer.writeLink(KEY_USER_STATE, userState, null, null);
    }

    /**
     * @return whether or not this request is (currently) active
     */
    public boolean isActive() {
        return active;
    }

    public IPLDObject<UserState> getUserState() {
        return userState;
    }

    /**
     * @return the unwrapped user
     */
    public User expectUser() {
        return userState.getMapped().getUser().getMapped();
    }

    /**
     * @return the multihash for the user (if user is null a NullPointerException will be thrown)
     */
    public String expectUserHash() {
        return userState.getMapped().getUser().getMultihash();
    }

    /**
     * Creates a new valid instance where the active property's value is toggled.
     * 
     * @return the toggled copy
     */
    public ToggleRequest toggle() {
        ToggleRequest copy = createCopyInstance();
        copy.active = !active;
        copy.payload = payload + 1;
        copy.userState = userState;
        return copy;
    }

    /**
     * @return a new instance of this class, where all necessary properties defined in the subclasses have been set.
     */
    protected abstract ToggleRequest createCopyInstance();

    public void validate(ToggleRequest previous) {
        if (this != previous && this.active != previous.active && this.payload <= previous.payload) {
            throw new ValidationException("payload not increased");
        }
    }

}
