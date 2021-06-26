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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.projectjinxers.account.Signer;
import org.projectjinxers.controller.IPLDContext;
import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.controller.IPLDReader;
import org.projectjinxers.controller.IPLDReader.KeyProvider;
import org.projectjinxers.controller.IPLDWriter;
import org.projectjinxers.controller.OwnershipTransferController;
import org.projectjinxers.controller.SettlementController;
import org.projectjinxers.controller.ValidationContext;
import org.projectjinxers.controller.ValidationException;
import org.projectjinxers.util.ModelUtility;

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
    private static final String KEY_VOTINGS = "g";
    private static final String KEY_SETTLEMENT_REQUESTS = "s";
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

    private static final KeyProvider<SealedDocument> SEALED_DOCUMENT_KEY_PROVIDER = new KeyProvider<>() {
        public String getKey(IPLDObject<SealedDocument> object) {
            return object.getMapped().getDocument().getMultihash();
        }
    };

    private static final KeyProvider<OwnershipRequest> OWNERSHIP_REQUESTS_KEY_PROVIDER = new KeyProvider<>() {
        @Override
        public String getKey(IPLDObject<OwnershipRequest> object) {
            return object.getMapped().getDocument().getMultihash();
        }
    };

    private long version;
    private long timestamp;
    private IPLDObject<ModelState> previousVersion;
    private Map<String, IPLDObject<UserState>> userStates;
    private Map<String, IPLDObject<Voting>> votings;
    private Map<String, IPLDObject<SettlementRequest>> settlementRequests;
    private Map<String, IPLDObject<SealedDocument>> sealedDocuments;
    private Map<String, IPLDObject<OwnershipRequest>[]> ownershipRequests;

    private Map<String, IPLDObject<UserState>> newUserStates;
    private Map<String, IPLDObject<Voting>> newVotings;
    private Map<String, IPLDObject<SettlementRequest>> newSettlementRequests;
    private Map<String, IPLDObject<SealedDocument>> newSealedDocuments;
    private Map<String, IPLDObject<OwnershipRequest>[]> newOwnershipRequests;

    @Override
    public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
            Metadata metadata) {
        this.version = reader.readNumber(KEY_VERSION).intValue();
        this.timestamp = reader.readNumber(KEY_TIMESTAMP).longValue();
        if (validationContext != null) {
            validationContext.validateTimestamp(timestamp);
        }
        this.previousVersion = reader.readLinkObject(KEY_PREVIOUS_VERSION, context, null, LoaderFactory.MODEL_STATE,
                false); // we don't want to load the entire tree, do we?
        if (validationContext != null && previousVersion != null && previousVersion.getMapped().version >= version) {
            throw new ValidationException("Version must be increased");
        }
        this.userStates = reader.readLinkObjects(KEY_USER_STATES, context, validationContext, LoaderFactory.USER_STATE,
                eager, USER_STATE_KEY_PROVIDER);
        this.votings = reader.readLinkObjects(KEY_VOTINGS, context, validationContext, LoaderFactory.VOTING, eager,
                VOTING_KEY_PROVIDER);
        this.settlementRequests = reader.readLinkObjects(KEY_SETTLEMENT_REQUESTS, context, validationContext,
                LoaderFactory.SETTLEMENT_REQUEST, eager, UserState.SETTLEMENT_REQUEST_KEY_PROVIDER);
        this.sealedDocuments = reader.readLinkObjects(KEY_SEALED_DOCUMENTS, context, validationContext,
                LoaderFactory.SEALED_DOCUMENT, eager, SEALED_DOCUMENT_KEY_PROVIDER);
        this.ownershipRequests = reader.readLinkObjectCollections(KEY_OWNERSHIP_REQUESTS, context, validationContext,
                LoaderFactory.OWNERSHIP_REQUEST, eager, OWNERSHIP_REQUESTS_KEY_PROVIDER);
        if (validationContext != null) {
            validationContext.validateModelState(this);
        }
    }

    @Override
    public void write(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {
        writer.writeNumber(KEY_VERSION, version);
        writer.writeNumber(KEY_TIMESTAMP, timestamp);
        writer.writeLink(KEY_PREVIOUS_VERSION, previousVersion, signer, null);
        writer.writeLinkObjects(KEY_USER_STATES, userStates, signer, context);
        writer.writeLinkObjects(KEY_VOTINGS, votings, signer, context);
        writer.writeLinkObjects(KEY_SETTLEMENT_REQUESTS, settlementRequests, signer, context);
        writer.writeLinkObjects(KEY_SEALED_DOCUMENTS, sealedDocuments, signer, null);
        writer.writeLinkObjectArrays(KEY_OWNERSHIP_REQUESTS, ownershipRequests, signer, context);
    }

    public long getVersion() {
        return version;
    }

    public IPLDObject<ModelState> getPreviousVersion() {
        return previousVersion;
    }

    public Set<String> expectAllUserHashes() {
        return Collections.unmodifiableSet(userStates.keySet());
    }

    public Collection<IPLDObject<UserState>> expectAllUserStates() {
        return Collections.unmodifiableCollection(userStates.values());
    }

    public Set<Entry<String, IPLDObject<UserState>>> getAllUserStateEntries() {
        return userStates == null ? null : Collections.unmodifiableSet(userStates.entrySet());
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

    public boolean isTruthInverted(String documentHash) {
        if (sealedDocuments != null) {
            IPLDObject<SealedDocument> sealed = sealedDocuments.get(documentHash);
            if (sealed != null) {
                return sealed.getMapped().isTruthInverted();
            }
        }
        return false;
    }

    /**
     * @param documentHash the document hash
     * @return the sealed document with the given hash (no null checks!)
     */
    public SealedDocument expectSealedDocument(String documentHash) {
        return sealedDocuments.get(documentHash).getMapped();
    }

    public IPLDObject<Voting> getVoting(String key) {
        return votings == null ? null : votings.get(key);
    }

    /**
     * @param documentHash the hash of the document to check
     * @return the voting for transfer of ownership of the document with the given hash, if any
     */
    public IPLDObject<Voting> getVotingForOwnershipTransfer(String documentHash) {
        if (votings != null) {
            for (IPLDObject<Voting> voting : votings.values()) {
                Votable votable = voting.getMapped().getSubject().getMapped();
                if (votable instanceof OwnershipSelection) {
                    OwnershipSelection ownershipSelection = (OwnershipSelection) votable;
                    if (documentHash.equals(ownershipSelection.getDocument().getMultihash())) {
                        return voting;
                    }
                }
            }
        }
        return null;
    }

    public IPLDObject<Voting> expectVotingForUnbanRequest(String unbanRequestHash) {
        return votings.get(unbanRequestHash);
    }

    /**
     * @param documentHash the document hash
     * @return the ownership requests for the document with the given hash (no null checks!)
     */
    public IPLDObject<OwnershipRequest>[] expectOwnershipRequests(String documentHash) {
        return ownershipRequests.get(documentHash);
    }

    /**
     * Updates this instance or a copy, which depends on the value of the 'current' parameter. It will be a copy if that
     * value is not null. That value will also be the previousVersion of the updated instance. The first invocation of
     * this method in a logical transaction should always be done with a non-null current instance.
     * 
     * @param userState         the updated (or new) user state (can also be null)
     * @param ownershipRequests the new ownership requests (can be null)
     * @param the               new votings (can be null)
     * @param current           the current wrapper (pass null, if you want to update without setting a previous version
     *                          and increasing the version - make sure to call this with the previous version for the
     *                          first update, if this is not the first version, as the copies would contain new state
     *                          objects later)
     * @return the updated instance (can be this instance)
     */
    public ModelState updateUserState(IPLDObject<UserState> userState,
            Collection<IPLDObject<OwnershipRequest>> ownershipRequests, Collection<IPLDObject<Voting>> votings,
            IPLDObject<ModelState> current) {
        ModelState updated;
        if (current == null) {
            if (userState != null && this.userStates == null) {
                this.userStates = new LinkedHashMap<>();
            }
            if (ownershipRequests != null && this.ownershipRequests == null) {
                this.ownershipRequests = new LinkedHashMap<>();
            }
            if (votings != null && this.votings == null) {
                this.votings = new LinkedHashMap<>();
            }
            updated = this;
        }
        else {
            updated = new ModelState();
            updated.version = version + 1;
            updated.previousVersion = current;
            if (this.userStates != null) {
                updated.userStates = new LinkedHashMap<>(this.userStates);
            }
            if (this.votings != null) {
                updated.votings = new LinkedHashMap<>(this.votings);
            }
            else if (votings != null) {
                updated.votings = new LinkedHashMap<>();
            }
            if (this.sealedDocuments != null) {
                updated.sealedDocuments = new LinkedHashMap<>(this.sealedDocuments);
            }
            if (this.ownershipRequests != null) {
                updated.ownershipRequests = new LinkedHashMap<>(this.ownershipRequests);
            }
            else if (ownershipRequests != null) {
                updated.ownershipRequests = new LinkedHashMap<>();
            }
        }
        if (userState != null) {
            updated.userStates.put(USER_STATE_KEY_PROVIDER.getKey(userState), userState);
        }
        if (ownershipRequests != null) {
            for (IPLDObject<OwnershipRequest> ownershipRequest : ownershipRequests) {
                String key = OWNERSHIP_REQUESTS_KEY_PROVIDER.getKey(ownershipRequest);
                IPLDObject<OwnershipRequest>[] requests = updated.ownershipRequests.get(key);
                if (requests == null) {
                    @SuppressWarnings("unchecked")
                    IPLDObject<OwnershipRequest>[] array = (IPLDObject<OwnershipRequest>[]) Array
                            .newInstance(IPLDObject.class, 1);
                    array[0] = ownershipRequest;
                    updated.ownershipRequests.put(key, array);
                }
                else {
                    IPLDObject<OwnershipRequest>[] arrayCopy = Arrays.copyOf(requests, requests.length + 1);
                    arrayCopy[requests.length] = ownershipRequest;
                    updated.ownershipRequests.put(key, arrayCopy);
                }
            }
        }
        if (votings != null) {
            for (IPLDObject<Voting> voting : votings) {
                updated.votings.put(VOTING_KEY_PROVIDER.getKey(voting), voting);
            }
        }
        updated.timestamp = System.currentTimeMillis();
        return updated;
    }

    public Map<String, IPLDObject<UserState>> getNewUserStates(ModelState since, boolean ignoreCached) {
        if (ignoreCached || newUserStates == null) {
            newUserStates = ModelUtility.getNewForeignKeyLinksMap(userStates, since == null ? null : since.userStates);
        }
        return newUserStates;
    }

    public Map<String, IPLDObject<Voting>> getNewVotings(ModelState since, boolean ignoreCached) {
        if (ignoreCached || newVotings == null) {
            newVotings = ModelUtility.getNewForeignKeyLinksMap(votings, since == null ? null : since.votings);
        }
        return newVotings;
    }

    public Map<String, IPLDObject<SettlementRequest>> getNewSettlementRequests(ModelState since, boolean ignoreCached) {
        if (ignoreCached || newSettlementRequests == null) {
            newSettlementRequests = ModelUtility.getNewForeignKeyLinksMap(settlementRequests,
                    since == null ? null : since.settlementRequests);
        }
        return newSettlementRequests;
    }

    public Map<String, IPLDObject<SealedDocument>> getNewSealedDocuments(ModelState since, boolean ignoreCached) {
        if (ignoreCached || newSealedDocuments == null) {
            newSealedDocuments = ModelUtility.getNewLinksMap(sealedDocuments,
                    since == null ? null : since.sealedDocuments);
        }
        return newSealedDocuments;
    }

    public Map<String, IPLDObject<OwnershipRequest>[]> getNewOwnershipRequests(ModelState since, boolean ignoreCached) {
        if (ignoreCached || newOwnershipRequests == null) {
            newOwnershipRequests = ModelUtility.getNewLinkArraysMap(ownershipRequests,
                    since == null ? null : since.ownershipRequests);
        }
        return newOwnershipRequests;
    }

    public void validateVotingCause(String votingKey, long validVersion) {
        ModelState modelState = this;
        IPLDObject<Voting> voting = null;
        while (modelState.previousVersion != null) {
            ModelState prev = modelState.previousVersion.getMapped();
            if (prev.version == validVersion || prev.votings == null) {
                break;
            }
            IPLDObject<Voting> prevVoting = prev.votings.get(votingKey);
            if (prevVoting == null) {
                break;
            }
            modelState = prev;
            voting = prevVoting;
        }
        Voting v = voting.getMapped();
        Votable subject = v.getSubject().getMapped();
        if (subject instanceof OwnershipSelection) {
            OwnershipSelection selection = (OwnershipSelection) subject;
            OwnershipTransferController checkController = new OwnershipTransferController(selection,
                    v.getInitialModelState());
            Voting reconstructedVoting = checkController.getVoting().getMapped();
            selection.validateReconstructed((OwnershipSelection) reconstructedVoting.getSubject().getMapped());
        }
    }

    public long validateUnchangedVote(String voteKey, String multihash, String votingKey, long validVersion) {
        ModelState modelState = this;
        long res = validVersion;
        do {
            if (modelState.version == validVersion) {
                return validVersion;
            }
            if (modelState.votings == null) {
                return res;
            }
            IPLDObject<Voting> votingObject = modelState.votings.get(votingKey);
            if (votingObject == null) {
                return res;
            }
            Voting voting = votingObject.getMapped();
            if (voting.hasVotes()) {
                IPLDObject<Vote> check = voting.getVote(votingKey);
                if (check != null && !check.getMultihash().equals(multihash)) {
                    throw new ValidationException("Found changed vote");
                }
                res = modelState.version;
                modelState = modelState.previousVersion == null ? null : modelState.previousVersion.getMapped();
            }
            else {
                return res;
            }
        }
        while (modelState != null);
        return res;
    }

    public void prepareSettlementValidation(SettlementController controller, Map<String, UserState> affected) {
        if (userStates != null) {
            for (Entry<String, IPLDObject<UserState>> entry : userStates.entrySet()) {
                UserState userState = entry.getValue().getMapped();
                if (userState.prepareSettlementValidation(controller)) {
                    affected.put(entry.getKey(), userState);
                }
            }
        }
    }

    public ModelState mergeWith(IPLDObject<ModelState> otherObject, ValidationContext validationContext) {
        ModelState other = otherObject.getMapped();
        Map<String, IPLDObject<UserState>> newUserStates = other.newUserStates == null ? null
                : new LinkedHashMap<>(other.newUserStates);
        ModelState res = new ModelState();
        res.userStates = new LinkedHashMap<String, IPLDObject<UserState>>();
        Map<String, UserState> mergedUserStates = new HashMap<>();
        // can't be null, otherwise it would be a trivial merge
        for (Entry<String, IPLDObject<UserState>> entry : userStates.entrySet()) {
            String key = entry.getKey();
            if (validationContext.isTrivialMerge(key)) {
                if (newUserStates != null) {
                    newUserStates.remove(key);
                }
                res.userStates.put(key, other.userStates.get(key));
            }
            else {
                IPLDObject<UserState> value = entry.getValue();
                IPLDObject<UserState> remoteUserStateObject = newUserStates == null ? null : newUserStates.remove(key);
                if (remoteUserStateObject == null
                        || remoteUserStateObject.getMultihash().equals(value.getMultihash())) {
                    res.userStates.put(key, value);
                }
                else {
                    UserState merged = value.getMapped().mergeWith(remoteUserStateObject, validationContext);
                    res.userStates.put(key, new IPLDObject<UserState>(merged));
                    mergedUserStates.put(key, merged);
                }
            }
        }
        // new users
        for (UserState userState : mergedUserStates.values()) {
            IPLDObject<UserState> previous = userState.getPreviousVersion();
            if (previous == null) {
                userState.checkSettlementDocuments(validationContext);
            }
            else {
                userState.checkSettlementDocuments(previous.getMapped(), this, validationContext);
            }
        }
        if (newUserStates != null && newUserStates.size() > 0) {
            for (Entry<String, IPLDObject<UserState>> entry : newUserStates.entrySet()) {
                String key = entry.getKey();
                IPLDObject<UserState> userState = entry.getValue();
                UserState u = userState.getMapped();
                if (u.checkSettlementDocuments(validationContext)) {
                    mergedUserStates.put(key, u);
                }
                res.userStates.put(entry.getKey(), userState);
            }
        }

        if (this.votings == null) {
            res.votings = other.votings;
        }
        else {
            res.votings = new LinkedHashMap<>(votings);
            Map<String, IPLDObject<Voting>> newVotings = other.newVotings;
            if (newVotings != null) {
                res.votings.putAll(newVotings);
            }
        }

        if (this.sealedDocuments == null) {
            res.sealedDocuments = other.sealedDocuments;
        }
        else {
            res.sealedDocuments = new LinkedHashMap<>(sealedDocuments);
            Map<String, IPLDObject<SealedDocument>> newSealedDocuments = other.newSealedDocuments;
            if (newSealedDocuments != null) {
                res.sealedDocuments.putAll(newSealedDocuments);
            }
        }

        if (this.ownershipRequests == null) {
            res.ownershipRequests = other.ownershipRequests;
        }
        else if (other.ownershipRequests == null) {
            res.ownershipRequests = new LinkedHashMap<>(ownershipRequests);
        }
        else {
            Map<String, IPLDObject<OwnershipRequest>[]> newOwnershipRequests = other.newOwnershipRequests == null ? null
                    : new LinkedHashMap<>(other.newOwnershipRequests);
            res.ownershipRequests = new LinkedHashMap<>();
            for (Entry<String, IPLDObject<OwnershipRequest>[]> entry : ownershipRequests.entrySet()) {
                String key = entry.getKey();
                IPLDObject<OwnershipRequest>[] otherRequests = newOwnershipRequests == null ? null
                        : newOwnershipRequests.remove(key);
                if (otherRequests == null) {
                    res.ownershipRequests.put(key, entry.getValue());
                }
                else {
                    Map<String, IPLDObject<OwnershipRequest>> merged = new LinkedHashMap<>();
                    for (IPLDObject<OwnershipRequest> request : entry.getValue()) {
                        merged.put(request.getMultihash(), request);
                    }
                    for (IPLDObject<OwnershipRequest> request : otherRequests) {
                        String hash = request.getMultihash();
                        if (!merged.containsKey(hash)) {
                            merged.put(hash, request);
                        }
                    }
                    IPLDObject<OwnershipRequest>[] copy = Arrays.copyOf(otherRequests, merged.size());
                    merged.values().toArray(copy);
                    res.ownershipRequests.put(key, copy);
                }
            }
            res.ownershipRequests.putAll(newOwnershipRequests);
        }

        res.previousVersion = validationContext.getPreviousVersion();
        if (res.previousVersion != null) {
            res.version = res.previousVersion.getMapped().version + 1;
        }
        if (mergedUserStates.size() > 0) {
            SettlementController mainSettlementController = validationContext.getMainSettlementController();
            Map<String, SealedDocument> sealedDocuments = new HashMap<>();
            if (mainSettlementController.evaluate(sealedDocuments, res)) {
                mainSettlementController.update(mergedUserStates, res, sealedDocuments);
                if (sealedDocuments.size() > 0) {
                    if (res.sealedDocuments == null) {
                        res.sealedDocuments = new LinkedHashMap<>();
                    }
                    for (Entry<String, SealedDocument> entry : sealedDocuments.entrySet()) {
                        res.sealedDocuments.put(entry.getKey(), new IPLDObject<>(entry.getValue()));
                    }
                }
                if (mergedUserStates.size() > 0) { // remaining entries have been added by the settlement controller for
                                                   // truth inversion
                    for (Entry<String, UserState> entry : mergedUserStates.entrySet()) {
                        String key = entry.getKey();
                        UserState current = res.expectUserState(key).getMapped();
                        current.applySettlement(entry.getValue());
                    }
                }
            }
        }

        res.timestamp = System.currentTimeMillis();
        return res;
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
