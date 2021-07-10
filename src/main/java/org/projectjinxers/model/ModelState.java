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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import org.projectjinxers.account.Signer;
import org.projectjinxers.controller.IPLDContext;
import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.controller.IPLDReader;
import org.projectjinxers.controller.IPLDReader.KeyProvider;
import org.projectjinxers.controller.IPLDWriter;
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
    private static final String KEY_REVIEW_TABLE = "r";

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

        @Override
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

    public static final KeyCollector<ModelState> USER_STATE_KEY_COLLECTOR = new KeyCollector<>() {

        @Override
        public java.util.Set<String> getHashes(ModelState instance) {
            return instance.userStates == null ? null : instance.userStates.keySet();
        }

    };

    public static final KeyCollector<ModelState> VOTING_KEY_COLLECTOR = new KeyCollector<>() {

        @Override
        public Set<String> getHashes(ModelState instance) {
            return instance.votings == null ? null : instance.votings.keySet();
        };

    };

    static final KeyCollector<ModelState> SEALED_DOCUMENT_KEY_COLLECTOR = new KeyCollector<>() {

        @Override
        public Set<String> getHashes(ModelState instance) {
            return instance.sealedDocuments == null ? null : instance.sealedDocuments.keySet();
        }

    };

    public static final KeyCollector<ModelState> SETTLEMENT_KEY_COLLECTOR = new KeyCollector<>() {

        @Override
        public Set<String> getHashes(ModelState instance) {
            return getSplitHashes(instance);
        }

        @Override
        public Set<String> getFirstHashes(ModelState instance) {
            return instance.settlementRequests == null ? null : instance.settlementRequests.keySet();
        }

        @Override
        public Set<String> getSecondHashes(ModelState instance) {
            return SEALED_DOCUMENT_KEY_COLLECTOR.getHashes(instance);
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
    private Map<String, String[]> reviewTable;

    private Map<String, IPLDObject<UserState>> newUserStates;
    private Map<String, IPLDObject<Voting>> newVotings;
    private Map<String, IPLDObject<SettlementRequest>> newSettlementRequests;
    private Map<String, IPLDObject<SealedDocument>> newSealedDocuments;
    private Map<String, IPLDObject<OwnershipRequest>[]> newOwnershipRequests;
    private Map<String, String[]> newReviewTableEntries;

    private long newUserStatesSince;
    private long newVotingsSince;
    private long newSettlementRequestsSince;
    private long newSealedDocumentsSince;
    private long newOwnershipRequestsSince;
    private long newReviewTableEntriesSince;

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
            throw new ValidationException("version must be increased");
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
        this.reviewTable = reader.readLinkCollections(KEY_REVIEW_TABLE);
        if (validationContext != null) {
            validationContext.validateModelState(this);
        }
    }

    @Override
    public void write(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {
        writer.writeNumber(KEY_VERSION, version);
        writer.writeNumber(KEY_TIMESTAMP, timestamp);
        writer.writeLink(KEY_PREVIOUS_VERSION, previousVersion, signer, context);
        writer.writeLinkObjects(KEY_USER_STATES, userStates, signer, context);
        writer.writeLinkObjects(KEY_VOTINGS, votings, signer, context);
        writer.writeLinkObjects(KEY_SETTLEMENT_REQUESTS, settlementRequests, signer, context);
        writer.writeLinkObjects(KEY_SEALED_DOCUMENTS, sealedDocuments, signer, context);
        writer.writeLinkObjectArrays(KEY_OWNERSHIP_REQUESTS, ownershipRequests, signer, context);
        writer.writeLinkArrays(KEY_REVIEW_TABLE, reviewTable);
    }

    public long getVersion() {
        return version;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public IPLDObject<ModelState> getPreviousVersion() {
        return previousVersion;
    }

    public Set<Entry<String, IPLDObject<SettlementRequest>>> getAllSettlementRequestEntries() {
        return settlementRequests == null ? null : settlementRequests.entrySet();
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

    public IPLDObject<SettlementRequest> getSettlementRequest(String documentHash) {
        return settlementRequests == null ? null : settlementRequests.get(documentHash);
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

    /**
     * @param documentHash the document hash
     * @return the ownership requests for the document with the given hash (no null checks!)
     */
    public IPLDObject<OwnershipRequest>[] expectOwnershipRequests(String documentHash) {
        return ownershipRequests.get(documentHash);
    }

    public String[] getReviewTableEntries(String documentHash) {
        return reviewTable == null ? null : reviewTable.get(documentHash);
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
            Collection<IPLDObject<SettlementRequest>> settlementRequests,
            Collection<IPLDObject<OwnershipRequest>> ownershipRequests, Collection<IPLDObject<Voting>> votings,
            Collection<IPLDObject<SealedDocument>> sealedDocuments, Map<String, String[]> reviewTable,
            IPLDObject<ModelState> current, long timestamp) {
        ModelState updated;
        if (current == null) {
            if (userState != null && this.userStates == null) {
                this.userStates = new LinkedHashMap<>();
            }
            if (settlementRequests != null && this.settlementRequests == null) {
                this.settlementRequests = new LinkedHashMap<>();
            }
            if (ownershipRequests != null && this.ownershipRequests == null) {
                this.ownershipRequests = new LinkedHashMap<>();
            }
            if (votings != null && this.votings == null) {
                this.votings = new LinkedHashMap<>();
            }
            if (sealedDocuments != null && this.sealedDocuments == null) {
                this.sealedDocuments = new LinkedHashMap<>();
            }
            if (reviewTable != null) {
                if (this.reviewTable == null) {
                    this.reviewTable = new LinkedHashMap<>(reviewTable);
                }
                else {
                    Map<String, String[]> temp = new LinkedHashMap<>();
                    mergeStringArrayMaps(this.reviewTable, reviewTable, temp);
                    this.reviewTable = temp;
                }
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
            if (this.settlementRequests != null) {
                updated.settlementRequests = new LinkedHashMap<>(this.settlementRequests);
            }
            else if (settlementRequests != null) {
                updated.settlementRequests = new LinkedHashMap<>();
            }
            if (this.sealedDocuments != null) {
                updated.sealedDocuments = new LinkedHashMap<>(this.sealedDocuments);
            }
            else if (sealedDocuments != null) {
                updated.sealedDocuments = new LinkedHashMap<>();
            }
            if (this.ownershipRequests != null) {
                updated.ownershipRequests = new LinkedHashMap<>(this.ownershipRequests);
            }
            else if (ownershipRequests != null) {
                updated.ownershipRequests = new LinkedHashMap<>();
            }
            if (reviewTable == null) {
                if (this.reviewTable != null) {
                    updated.reviewTable = new LinkedHashMap<>(this.reviewTable);
                }
            }
            else {
                if (this.reviewTable == null) {
                    updated.reviewTable = new LinkedHashMap<>(reviewTable);
                }
                else {
                    updated.reviewTable = new LinkedHashMap<>();
                    // merging retains 'links' to older versions of reviews, this is part of the contract (checking for
                    // older versions would defy the purpose of this table) - validation must allow for the presence of
                    // older versions, but can clean them up, if easily achievable
                    mergeStringArrayMaps(this.reviewTable, reviewTable, updated.reviewTable);
                }
            }
        }
        if (userState != null) {
            updated.userStates.put(USER_STATE_KEY_PROVIDER.getKey(userState), userState);
        }
        if (settlementRequests != null) {
            for (IPLDObject<SettlementRequest> settlementRequest : settlementRequests) {
                updated.settlementRequests.put(UserState.SETTLEMENT_REQUEST_KEY_PROVIDER.getKey(settlementRequest),
                        settlementRequest);
            }
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
        if (sealedDocuments != null) {
            for (IPLDObject<SealedDocument> sealed : sealedDocuments) {
                String key = SEALED_DOCUMENT_KEY_PROVIDER.getKey(sealed);
                updated.sealedDocuments.put(key, sealed);
                if (updated.settlementRequests != null) { // null happens when sealed is not an original
                    updated.settlementRequests.remove(key);
                }
            }
        }
        updated.timestamp = timestamp;
        return updated;
    }

    public Map<String, IPLDObject<UserState>> getNewUserStates(ModelState since, boolean ignoreCached) {
        if (ignoreCached || newUserStates == null || since == null && newUserStatesSince >= 0
                || since != null && since.getVersion() != newUserStatesSince) {
            if (since == null) {
                newUserStates = ModelUtility.getNewForeignKeyLinksMap(userStates, null);
                newUserStatesSince = -1;
            }
            else {
                newUserStates = ModelUtility.getNewForeignKeyLinksMap(userStates, since.userStates);
                newUserStatesSince = since.getVersion();
            }
        }
        return newUserStates;
    }

    public Map<String, IPLDObject<Voting>> getNewVotings(ModelState since, boolean ignoreCached) {
        if (ignoreCached || newVotings == null || since == null && newVotingsSince >= 0
                || since != null && since.getVersion() != newVotingsSince) {
            if (since == null) {
                newVotings = ModelUtility.getNewForeignKeyLinksMap(votings, null);
                newVotingsSince = -1;
            }
            else {
                newVotings = ModelUtility.getNewForeignKeyLinksMap(votings, since.votings);
                newVotingsSince = since.getVersion();
            }
        }
        return newVotings;
    }

    public Map<String, IPLDObject<SettlementRequest>> getNewSettlementRequests(ModelState since, boolean ignoreCached) {
        if (ignoreCached || newSettlementRequests == null || since == null && newSettlementRequestsSince >= 0
                || since != null && since.getVersion() != newSettlementRequestsSince) {
            if (since == null) {
                newSettlementRequests = ModelUtility.getNewForeignKeyLinksMap(settlementRequests, null);
                newSettlementRequestsSince = -1;
            }
            else {
                newSettlementRequests = ModelUtility.getNewForeignKeyLinksMap(settlementRequests,
                        since.settlementRequests);
                newSettlementRequestsSince = since.getVersion();
            }
        }
        return newSettlementRequests;
    }

    public Map<String, IPLDObject<SealedDocument>> getNewSealedDocuments(ModelState since, boolean ignoreCached) {
        if (ignoreCached || newSealedDocuments == null || since == null && newSealedDocumentsSince >= 0
                || since != null && since.getVersion() != newSealedDocumentsSince) {
            if (since == null) {
                newSealedDocuments = ModelUtility.getNewForeignKeyLinksMap(sealedDocuments, null);
                newSealedDocumentsSince = -1;
            }
            else {
                newSealedDocuments = ModelUtility.getNewForeignKeyLinksMap(sealedDocuments, since.sealedDocuments);
                newSealedDocumentsSince = since.getVersion();
            }
        }
        return newSealedDocuments;
    }

    public Map<String, IPLDObject<OwnershipRequest>[]> getNewOwnershipRequests(ModelState since, boolean ignoreCached) {
        if (ignoreCached || newOwnershipRequests == null || since == null && newOwnershipRequestsSince >= 0
                || since != null && since.getVersion() != newOwnershipRequestsSince) {
            if (since == null) {
                newOwnershipRequests = ModelUtility.getNewLinkArraysMap(ownershipRequests, null);
                newOwnershipRequestsSince = -1;
            }
            else {
                newOwnershipRequests = ModelUtility.getNewLinkArraysMap(ownershipRequests, since.ownershipRequests);
                newOwnershipRequestsSince = since.getVersion();
            }
        }
        return newOwnershipRequests;
    }

    public Map<String, String[]> getNewReviewTableEntries(ModelState since, boolean ignoreCached) {
        if (ignoreCached || newReviewTableEntries == null || since == null && newReviewTableEntriesSince >= 0
                || since != null && since.getVersion() != newReviewTableEntriesSince) {
            if (since == null) {
                newReviewTableEntries = ModelUtility.getNewLinksArraysMap(reviewTable, null);
                newReviewTableEntriesSince = -1;
            }
            else {
                newReviewTableEntries = ModelUtility.getNewLinksArraysMap(reviewTable, since.reviewTable);
                newReviewTableEntriesSince = since.getVersion();
            }
        }
        return newReviewTableEntries;
    }

    public void validateVotingCause(String votingKey, long validVersion, ValidationContext validationContext) {
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
        v.getSubject().getMapped().validate(v, validationContext);
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
                    throw new ValidationException("found changed vote");
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

    public void prepareSettlementValidation(SettlementController controller, Set<String> newReviewTableKeys,
            Map<String, Set<String>> newReviewTableValues, Map<String, Map<String, String>> reviewers,
            Map<String, UserState> affected) {
        if (userStates != null) {
            for (Entry<String, IPLDObject<UserState>> entry : userStates.entrySet()) {
                UserState userState = entry.getValue().getMapped();
                if (userState.prepareSettlementValidation(controller, newReviewTableKeys, newReviewTableValues,
                        reviewers)) {
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
        if (this.settlementRequests == null) {
            res.settlementRequests = other.settlementRequests;
        }
        else {
            res.settlementRequests = new LinkedHashMap<>(settlementRequests);
            Map<String, IPLDObject<SettlementRequest>> newSettlementRequests = other.newSettlementRequests;
            if (newSettlementRequests != null) {
                res.settlementRequests.putAll(newSettlementRequests);
            }
        }
        if (this.sealedDocuments == null) {
            res.sealedDocuments = other.sealedDocuments;
        }
        else {
            res.sealedDocuments = new LinkedHashMap<>(sealedDocuments);
            Map<String, IPLDObject<SealedDocument>> newSealedDocuments = other.newSealedDocuments;
            if (newSealedDocuments != null) {
                for (Entry<String, IPLDObject<SealedDocument>> entry : newSealedDocuments.entrySet()) {
                    String key = entry.getKey();
                    res.sealedDocuments.put(key, entry.getValue());
                    res.settlementRequests.remove(key);
                }
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
                    copy = merged.values().toArray(copy);
                    res.ownershipRequests.put(key, copy);
                }
            }
            res.ownershipRequests.putAll(newOwnershipRequests);
        }

        if (this.reviewTable == null) {
            res.reviewTable = other.reviewTable;
        }
        else if (other.reviewTable == null) {
            res.reviewTable = new LinkedHashMap<>(reviewTable);
        }
        else {
            Map<String, String[]> newReviewTableEntries = other.newReviewTableEntries == null ? null
                    : new LinkedHashMap<>(other.newReviewTableEntries);
            res.reviewTable = new LinkedHashMap<>();
            mergeStringArrayMaps(reviewTable, newReviewTableEntries, res.reviewTable);
        }

        res.previousVersion = validationContext.getPreviousVersion();
        if (res.previousVersion != null) {
            res.version = res.previousVersion.getMapped().version + 1;
        }
        if (mergedUserStates.size() > 0) {
            SettlementController mainSettlementController = validationContext.getMainSettlementController();
            Map<String, SealedDocument> sealedDocuments = new HashMap<>();
            Set<String> invalidSettlementRequests = new HashSet<>();
            if (mainSettlementController.evaluate(sealedDocuments, invalidSettlementRequests, res)) {
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
            if (invalidSettlementRequests.size() > 0) {
                for (String key : invalidSettlementRequests) {
                    res.settlementRequests.remove(key);
                }
            }
        }

        res.timestamp = validationContext.getMainSettlementController().getTimestamp();
        return res;
    }

    public boolean removeObsoleteReviewVersions(Map<String, Set<String>> obsoleteReviewVersions) {
        boolean res = false;
        for (Entry<String, Set<String>> entry : obsoleteReviewVersions.entrySet()) {
            String key = entry.getKey();
            String[] reviewHashes = reviewTable.get(key);
            Set<String> obsolete = entry.getValue();
            Collection<String> coll = new ArrayList<>();
            for (String reviewHash : reviewHashes) {
                if (!obsolete.contains(reviewHash)) {
                    coll.add(reviewHash);
                }
            }
            if (reviewHashes.length > coll.size()) {
                String[] cleaned = new String[0];
                cleaned = coll.toArray(cleaned);
                reviewTable.put(key, cleaned);
                res = true;
            }
        }
        return res;
    }

    private void mergeStringArrayMaps(Map<String, String[]> map1, Map<String, String[]> map2,
            Map<String, String[]> merged) {
        for (Entry<String, String[]> entry : map1.entrySet()) {
            String key = entry.getKey();
            String[] otherEntries = map2 == null ? null : map2.get(key);
            if (otherEntries == null) {
                merged.put(key, entry.getValue());
            }
            else {
                Set<String> unique = new TreeSet<>();
                Collection<String> coll = new ArrayList<>();
                for (String s : entry.getValue()) {
                    unique.add(s);
                    coll.add(s);
                }
                for (String s : otherEntries) {
                    if (!unique.contains(s)) {
                        coll.add(s);
                    }
                }
                String[] copy = new String[0];
                copy = coll.toArray(copy);
                merged.put(key, copy);
            }
        }
        for (Entry<String, String[]> entry : map2.entrySet()) {
            String key = entry.getKey();
            if (!merged.containsKey(key)) {
                merged.put(key, entry.getValue());
            }
        }
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
