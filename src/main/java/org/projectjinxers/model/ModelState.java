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
import java.util.LinkedHashMap;
import java.util.Map;

import org.projectjinxers.account.Signer;
import org.projectjinxers.controller.IPLDContext;
import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.controller.IPLDReader;
import org.projectjinxers.controller.IPLDReader.KeyProvider;
import org.projectjinxers.controller.IPLDWriter;

/**
 * ModelStates are the root instances of a tree, that represents the system at a specific time.
 * 
 * @author ProjectJinxers
 */
public class ModelState implements IPLDSerializable, Loader<ModelState> {

    private static final String KEY_VERSION = "v";
    private static final String KEY_TIMESTAMP = "t";
    private static final String KEY_PREVIOUS_VERSION = "p";
    private static final String KEY_USER_STATES = "u";
    private static final String KEY_VOTINGS = "v";

    private static final KeyProvider<UserState> USER_STATE_KEY_PROVIDER = new KeyProvider<UserState>() {
        @Override
        public String getKey(IPLDObject<UserState> object) {
            return object.getMapped().getUser().getMultihash();
        }
    };

    private static final KeyProvider<Voting> VOTING_KEY_PROVIDER = new KeyProvider<Voting>() {
        @Override
        public String getKey(IPLDObject<Voting> object) {
            return object.getMapped().getSubject().getMultihash();
        }
    };

    private int version;
    private long timestamp;
    private IPLDObject<ModelState> previousVersion;
    private Map<String, IPLDObject<UserState>> userStates;
    private Map<String, IPLDObject<Voting>> votings;

    @Override
    public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
            Metadata metadata) {
        this.version = reader.readNumber(KEY_VERSION).intValue();
        this.timestamp = reader.readNumber(KEY_TIMESTAMP).longValue();
        this.previousVersion = reader.readLinkObject(KEY_PREVIOUS_VERSION, context, validationContext,
                LoaderFactory.MODEL_STATE, false); // we don't want to load the entire tree, do we?
        this.userStates = reader.readLinkObjects(KEY_USER_STATES, context, validationContext, LoaderFactory.USER_STATE,
                eager, USER_STATE_KEY_PROVIDER);
        this.votings = reader.readLinkObjects(KEY_VOTINGS, context, validationContext, LoaderFactory.VOTING, eager,
                VOTING_KEY_PROVIDER);
    }

    @Override
    public void write(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {
        writer.writeNumber(KEY_VERSION, version);
        writer.writeNumber(KEY_TIMESTAMP, timestamp);
        writer.writeLink(KEY_PREVIOUS_VERSION, previousVersion, signer, null);
        writer.writeLinkObjects(KEY_USER_STATES, userStates, signer, context);
        writer.writeLinkObjects(KEY_VOTINGS, votings, signer, context);
    }

    /**
     * Null-safe check if the given userHash is contained in the user states map.
     * 
     * @param userHash the userHash to check
     * @return true iff the given userHash is contained in the user states map
     */
    public boolean containsUserState(String userHash) {
        return userStates == null ? false : userStates.containsKey(userHash);
    }

    /**
     * @param userHash the userHash
     * @return the wrapped user state instance for the given hash (null-safe)
     */
    public IPLDObject<UserState> getUserState(String userHash) {
        return userStates == null ? null : userStates.get(userHash);
    }

    /**
     * @param userHash the userHash
     * @return the wrapped user state instance for the given hash (no null checks!)
     */
    public IPLDObject<UserState> expectUserState(String userHash) {
        return userStates.get(userHash);
    }

    /**
     * Sets a wrapped copy of this instance as the previous version, updates the user states map by replacing the entry
     * for the key of the updated user state with the updated user state and increments the version. Should only be
     * called in a transaction.
     * 
     * @param updated the updated user state
     * @param the     current wrapper
     */
    public void updateUserState(IPLDObject<UserState> updated, IPLDObject<ModelState> wrapper) {
        ModelState copy = new ModelState();
        copy.version = version;
        copy.timestamp = timestamp;
        copy.previousVersion = previousVersion;
        copy.userStates = new LinkedHashMap<>(userStates);
        if (votings != null) {
            copy.votings = new LinkedHashMap<>(votings);
        }
        this.previousVersion = new IPLDObject<>(wrapper, copy);
        userStates.put(USER_STATE_KEY_PROVIDER.getKey(updated), updated);
        this.version++;
    }

    @Override
    public ModelState getOrCreateDataInstance(IPLDReader reader, Metadata metadata) {
        return this;
    }

    @Override
    public ModelState getLoaded() {
        return this;
    }

}
