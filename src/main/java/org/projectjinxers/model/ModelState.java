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
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
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
    private static final String KEY_SEALED_DOCUMENTS = "d";
    private static final String KEY_OWNERSHIP_REQUESTS = "o";

    private static final KeyProvider<UserState> USER_STATE_KEY_PROVIDER = new KeyProvider<>() {
        @Override
        public String getKey(IPLDObject<UserState> object) {
            return object.getMapped().getUser().getMultihash();
        }
    };

    private static final KeyProvider<Voting> VOTING_KEY_PROVIDER = new KeyProvider<>() {
        @Override
        public String getKey(IPLDObject<Voting> object) {
            return object.getMapped().getSubject().getMultihash();
        }
    };

    private static final KeyProvider<Document> SEALED_DOCUMENT_KEY_PROVIDER = new KeyProvider<>() {

    };

    private static final KeyProvider<OwnershipRequest> OWNERSHIP_REQUESTS_KEY_PROVIDER = new KeyProvider<>() {
        @Override
        public String getKey(IPLDObject<OwnershipRequest> object) {
            return object.getMapped().getDocument().getMultihash();
        }
    };

    private int version;
    private long timestamp;
    private IPLDObject<ModelState> previousVersion;
    private Map<String, IPLDObject<UserState>> userStates;
    private Map<String, IPLDObject<Voting>> votings;
    private Map<String, IPLDObject<Document>> sealedDocuments;
    private Map<String, IPLDObject<OwnershipRequest>[]> ownershipRequests;

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
        this.sealedDocuments = reader.readLinkObjects(KEY_SEALED_DOCUMENTS, context, validationContext,
                LoaderFactory.DOCUMENT, eager, SEALED_DOCUMENT_KEY_PROVIDER);
        this.ownershipRequests = reader.readLinkObjectCollections(KEY_OWNERSHIP_REQUESTS, context, validationContext,
                LoaderFactory.OWNERSHIP_REQUEST, eager, OWNERSHIP_REQUESTS_KEY_PROVIDER);
    }

    @Override
    public void write(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {
        writer.writeNumber(KEY_VERSION, version);
        writer.writeNumber(KEY_TIMESTAMP, timestamp);
        writer.writeLink(KEY_PREVIOUS_VERSION, previousVersion, signer, null);
        writer.writeLinkObjects(KEY_USER_STATES, userStates, signer, context);
        writer.writeLinkObjects(KEY_VOTINGS, votings, signer, context);
        writer.writeLinkObjects(KEY_SEALED_DOCUMENTS, sealedDocuments, signer, null);
        writer.writeLinkObjectArrays(KEY_OWNERSHIP_REQUESTS, ownershipRequests, signer, context);
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
     * Checks if the document with the given hash has been sealed.
     * 
     * @param documentHash the document hash
     * @return true iff the document has been sealed
     */
    public boolean isSealedDocument(String documentHash) {
        return sealedDocuments != null && sealedDocuments.containsKey(documentHash);
    }

    /**
     * @param documentHash the document hash
     * @return the sealed document with the given hash (no null checks!)
     */
    public Document expectSealedDocument(String documentHash) {
        return sealedDocuments.get(documentHash).getMapped();
    }

    /**
     * @param documentHash the hash of the document to check
     * @return true iff there is a voting for transfer of ownership of the document with the given hash
     */
    public boolean hasVotingForOwnershipTransfer(String documentHash) {
        if (votings != null) {
            for (IPLDObject<Voting> voting : votings.values()) {
                Votable votable = voting.getMapped().getSubject().getMapped();
                if (votable instanceof OwnershipSelection) {
                    OwnershipSelection ownershipSelection = (OwnershipSelection) votable;
                    if (documentHash.equals(ownershipSelection.getDocument().getMultihash())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * @param documentHash the document hash
     * @return the ownership requests for the document with the given hash (no null checks!)
     */
    public IPLDObject<OwnershipRequest>[] expectOwnershipRequests(String documentHash) {
        return ownershipRequests.get(documentHash);
    }

    /**
     * Sets a wrapped copy of this instance as the previous version (if current is not null only), updates the user
     * states map by replacing the entry for the key of the updated user state with the updated user state and
     * increments the version if there is a previousVersion (i.e. if current is not null). Should only be called in a
     * transaction, unless this a a completely new instance (no previous version).
     * 
     * @param updated the updated (or new) user state
     * @param current the current wrapper (pass null, if you want to update without setting a previous version and
     *                increasing the version - make sure to call this with the previous version for the first update, if
     *                this is not the first version, as the copies would contain new state objects later)
     */
    public void updateUserState(IPLDObject<UserState> updated,
            Collection<IPLDObject<OwnershipRequest>> ownershipRequests, Collection<IPLDObject<Voting>> votings,
            IPLDObject<ModelState> current) {
        ModelState copy;
        if (current == null) {
            copy = null;
        }
        else {
            copy = new ModelState();
            copy.version = version;
            copy.timestamp = timestamp;
            copy.previousVersion = previousVersion;
            if (this.votings != null) {
                copy.votings = new LinkedHashMap<>(this.votings);
            }
        }
        if (userStates == null) {
            this.userStates = new LinkedHashMap<>();
        }
        else if (copy != null) {
            copy.userStates = new LinkedHashMap<>(userStates);
        }
        if (current != null) {
            this.previousVersion = new IPLDObject<>(current, copy);
            this.version++;
        }
        userStates.put(USER_STATE_KEY_PROVIDER.getKey(updated), updated);
        if (ownershipRequests != null) {
            if (this.ownershipRequests == null) {
                this.ownershipRequests = new LinkedHashMap<>();
            }
            for (IPLDObject<OwnershipRequest> ownershipRequest : ownershipRequests) {
                String key = OWNERSHIP_REQUESTS_KEY_PROVIDER.getKey(ownershipRequest);
                IPLDObject<OwnershipRequest>[] requests = this.ownershipRequests.get(key);
                if (requests == null) {
                    @SuppressWarnings("unchecked")
                    IPLDObject<OwnershipRequest>[] array = (IPLDObject<OwnershipRequest>[]) Array
                            .newInstance(IPLDObject.class, 1);
                    array[0] = ownershipRequest;
                    this.ownershipRequests.put(key, array);
                }
                else {
                    IPLDObject<OwnershipRequest>[] arrayCopy = Arrays.copyOf(requests, requests.length + 1);
                    arrayCopy[requests.length] = ownershipRequest;
                }
            }
        }
        if (votings != null) {
            for (IPLDObject<Voting> voting : votings) {
                this.votings.put(VOTING_KEY_PROVIDER.getKey(voting), voting);
            }
        }
        this.timestamp = System.currentTimeMillis();
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
