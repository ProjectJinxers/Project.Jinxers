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

import static org.projectjinxers.util.ModelUtility.expectEqual;
import static org.projectjinxers.util.ModelUtility.isEqual;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import org.projectjinxers.account.Signer;
import org.projectjinxers.config.SecretConfig;
import org.projectjinxers.model.Document;
import org.projectjinxers.model.DocumentRemoval;
import org.projectjinxers.model.GrantedOwnership;
import org.projectjinxers.model.GrantedUnban;
import org.projectjinxers.model.LoaderFactory;
import org.projectjinxers.model.ModelState;
import org.projectjinxers.model.OwnershipRequest;
import org.projectjinxers.model.Review;
import org.projectjinxers.model.SealedDocument;
import org.projectjinxers.model.SettlementRequest;
import org.projectjinxers.model.Tally;
import org.projectjinxers.model.ToggleRequest;
import org.projectjinxers.model.UnbanRequest;
import org.projectjinxers.model.User;
import org.projectjinxers.model.UserState;
import org.projectjinxers.model.Voting;

/**
 * Context for validation of unknown objects. The context keeps all visited objects in a cache. Usage of this class
 * requires a strictly defined flow. First the model state has to be mapped. Trivial validation errors (such as missing
 * mandatory values) can be detected while mapping. Mapping implementations should assume that the data be valid.
 * Runtime exceptions during mapping indicate invalid data. They should not be caught, let alone handled by a mapping
 * implementation. After successfully mapping a model state, {@link #validateModelState(ModelState)} should be called.
 * This call sets some internal references, that can be accessed via getters. If those getters are called before the
 * model state has been validated, they will return null. Instances of this class should not be reused for subsequent
 * validations. They should, however, be kept in memory for merging the currently known valid data with the newly
 * validated data.
 * 
 * @author ProjectJinxers
 */
public class ValidationContext {

    private static final Comparator<Long> DESCENDING = new Comparator<>() {
        @Override
        public int compare(Long o1, Long o2) {
            return o2.compareTo(o1);
        }
    };

    private final IPLDContext context;
    private IPLDObject<ModelState> currentValidLocalState;
    private final Set<String> currentLocalHashes;
    private final long timestamp;
    private final long timestampTolerance;
    private final SecretConfig secretConfig;
    private final boolean strict;
    private SettlementController mainSettlementController;

    private SettlementController currentSettlementController;
    private IPLDObject<ModelState> commonStateObject;
    private ModelState commonState;
    private Deque<ModelState> previousStates = new ArrayDeque<>();
    private Map<String, IPLDObject<UserState>> commonUserStates = new HashMap<>();

    private Map<Long, IPLDObject<ModelState>> mustValidateModelStates;
    private IPLDObject<ModelState> validatedPreviousState;
    private Map<String, Set<Long>> mustValidateUserStateVersions;
    private Map<String, IPLDObject<UserState>> validatedUserStates;

    private Set<String> validated = new TreeSet<>();
    private Map<String, Set<String>> obsoleteReviewVersions = new HashMap<>();

    public ValidationContext(IPLDContext context, IPLDObject<ModelState> currentValidLocalState,
            Set<String> currentLocalHashes, long timestamp, long timestampTolerance, SecretConfig secretConfig) {
        this.context = context;
        this.currentValidLocalState = currentValidLocalState;
        this.currentLocalHashes = currentLocalHashes;
        this.timestamp = timestamp;
        this.timestampTolerance = timestampTolerance;
        this.secretConfig = secretConfig;
        this.strict = timestampTolerance > 0;
    }

    public IPLDContext getContext() {
        return context;
    }

    public IPLDObject<ModelState> getCurrentValidLocalState() {
        return currentValidLocalState;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public SecretConfig getSecretConfig() {
        return secretConfig;
    }

    public SettlementController getMainSettlementController() {
        return mainSettlementController;
    }

    public Map<String, Set<String>> getObsoleteReviewVersions() {
        return obsoleteReviewVersions;
    }

    public IPLDObject<ModelState> getPreviousVersion() {
        return validatedPreviousState == null ? commonStateObject : validatedPreviousState;
    }

    public IPLDObject<UserState> getPreviousUserState(String userHash) {
        IPLDObject<UserState> validated = validatedUserStates == null ? null : validatedUserStates.get(userHash);
        return validated == null ? commonUserStates.get(userHash) : validated;
    }

    public boolean isTrivialMerge() {
        return isEqual(commonStateObject, currentValidLocalState);
    }

    public boolean isTrivialMerge(String userHash) {
        IPLDObject<UserState> currentValidUserState = currentValidLocalState.getMapped().expectUserState(userHash);
        IPLDObject<UserState> commonUserState = getPreviousUserState(userHash);
        return commonUserState == currentValidUserState || commonUserState != null && currentValidUserState != null
                && commonUserState.getMultihash().equals(currentValidUserState.getMultihash());
    }

    public void validateTimestamp(long timestamp) {
        if (strict && currentSettlementController == mainSettlementController
                && Math.abs(this.timestamp - timestamp) > timestampTolerance) {
            throw new ValidationException("timestamp out of range");
        }
    }

    public void validateModelState(ModelState modelState) {
        findCommonState(modelState);
        if (mainSettlementController == null) {
            mainSettlementController = new SettlementController(modelState.getPreviousVersion(), commonState, this,
                    true, modelState.getTimestamp(), timestampTolerance);
            currentSettlementController = mainSettlementController;
        }
        ModelState.USER_STATE_KEY_COLLECTOR.validateUndeletableEntries(previousStates);
        ModelState.VOTING_KEY_COLLECTOR.validateUndeletableEntries(previousStates);
        ModelState.SETTLEMENT_KEY_COLLECTOR.validateMoveOnceUndeletableEntries(previousStates);
        Map<String, IPLDObject<Voting>> newVotings = modelState.getNewVotings(commonState, false);
        if (newVotings != null) {
            for (Entry<String, IPLDObject<Voting>> entry : newVotings.entrySet()) {
                IPLDObject<Voting> voting = entry.getValue();
                if (validated.add(voting.getMultihash())) {
                    validateNewVoting(entry.getKey(), voting.getMapped(), modelState);
                }
            }
        }
        Map<String, IPLDObject<SettlementRequest>> newSettlementRequestsMap;
        Map<String, IPLDObject<SettlementRequest>> newSettlementRequests = modelState
                .getNewSettlementRequests(commonState, false);
        if (newSettlementRequests == null) {
            newSettlementRequestsMap = null;
        }
        else {
            newSettlementRequestsMap = new HashMap<>();
            for (IPLDObject<SettlementRequest> settlementRequest : newSettlementRequests.values()) {
                String multihash = settlementRequest.getMultihash();
                if (validated.add(multihash)) {
                    validateSettlementRequest(settlementRequest, modelState);
                }
                newSettlementRequestsMap.put(multihash, settlementRequest);
            }
        }
        Map<String, IPLDObject<SealedDocument>> newSealedDocuments = modelState.getNewSealedDocuments(commonState,
                false);
        if (newSealedDocuments != null) {
            for (IPLDObject<SealedDocument> sealed : newSealedDocuments.values()) {
                SealedDocument mapped = sealed.getMapped();
                if (mapped.isOriginal()) {
                    currentSettlementController.checkRequest(mapped.getDocument(), true, true);
                }
            }
        }
        Map<String, IPLDObject<OwnershipRequest>> newOwnershipRequestsMap;
        Map<String, IPLDObject<OwnershipRequest>[]> newOwnershipRequests = modelState
                .getNewOwnershipRequests(commonState, false);
        if (newOwnershipRequests == null) {
            newOwnershipRequestsMap = null;
        }
        else {
            newOwnershipRequestsMap = new HashMap<>();
            for (IPLDObject<OwnershipRequest>[] ownershipRequests : newOwnershipRequests.values()) {
                for (IPLDObject<OwnershipRequest> ownershipRequest : ownershipRequests) {
                    String multihash = ownershipRequest.getMultihash();
                    if (validated.add(multihash)) {
                        validateOwnershipRequest(ownershipRequest, modelState);
                    }
                    newOwnershipRequestsMap.put(multihash, ownershipRequest);
                }
            }
        }
        Map<String, Set<String>> newReviewTableValues;
        Set<String> newReviewTableKeysSettlement;
        Map<String, Set<String>> newReviewTableValuesSettlement;
        Map<String, String[]> newReviewTableEntries = modelState.getNewReviewTableEntries(commonState, false);
        if (newReviewTableEntries == null) {
            newReviewTableValues = null;
            newReviewTableKeysSettlement = null;
            newReviewTableValuesSettlement = null;
        }
        else {
            newReviewTableKeysSettlement = new TreeSet<>();
            newReviewTableValues = new HashMap<>();
            newReviewTableValuesSettlement = new HashMap<>();
            for (Entry<String, String[]> entry : newReviewTableEntries.entrySet()) {
                String key = entry.getKey();
                newReviewTableKeysSettlement.add(key);
                Set<String> values = new TreeSet<>();
                for (String link : entry.getValue()) {
                    values.add(link);
                }
                newReviewTableValues.put(key, values);
                newReviewTableValuesSettlement.put(key, new TreeSet<>(values));
            }
        }
        Map<String, IPLDObject<UserState>> newUserStates = modelState.getNewUserStates(commonState, false);
        if (newUserStates != null) {
            for (Entry<String, IPLDObject<UserState>> entry : newUserStates.entrySet()) {
                IPLDObject<UserState> userStateObject = entry.getValue();
                if (validated.add(userStateObject.getMultihash())) {
                    UserState userState = userStateObject.getMapped();
                    String userHash = entry.getKey();
                    IPLDObject<UserState> commonUserState = commonState == null ? null
                            : commonState.getUserState(userHash);
                    Deque<UserState> previousStates = new ArrayDeque<>();
                    if (commonUserState == null) {
                        findCommonUserState(userHash, userStateObject, previousStates);
                    }
                    else {
                        findBestCommonUserState(userHash, userStateObject, commonUserState, previousStates);
                    }
                    UserState.DOCUMENT_KEY_COLLECTOR.validateMoveOnceUndeletableEntries(previousStates);
                    UserState.SETTLEMENT_KEY_COLLECTOR.validateUndeletableEntries(previousStates, this.previousStates);
                    UserState.OWNERSHIP_KEY_COLLECTOR.validateMoveOnceUndeletableEntries(previousStates);
                    UserState.UNBAN_KEY_COLLECTOR.validateMoveOnceUndeletableEntries(previousStates);
                    validateUserState(userState, modelState, newSettlementRequestsMap, newOwnershipRequestsMap,
                            newReviewTableValues, previousStates);
                }
            }
        }
        if (newSettlementRequestsMap != null && newSettlementRequestsMap.size() > 0) {
            throw new ValidationException("new settlement requests out of sync (model state v user states)");
        }
        if (newOwnershipRequestsMap != null && newOwnershipRequestsMap.size() > 0) {
            throw new ValidationException("new ownership requests out of sync (model state v user states)");
        }
        if (newReviewTableValues != null && newReviewTableValues.size() > 0) {
            throw new ValidationException("new review table values out of sync (too many)");
        }
        IPLDObject<ModelState> previousVersion = modelState.getPreviousVersion();
        if (previousVersion != null) {
            validateMustValidateModelStates(previousVersion);
        }

        // we already prepare the settlement requests required for merging; in order to prevent them from influencing
        // the outcome of the validation, we don't set the flag, that makes main validation include them
        if (currentValidLocalState != null && currentSettlementController == mainSettlementController) {
            ModelState validState = currentValidLocalState.getMapped();
            Map<String, IPLDObject<SettlementRequest>> newSettlementReqs = validState
                    .getNewSettlementRequests(commonState, true);
            if (newSettlementReqs != null) {
                for (IPLDObject<SettlementRequest> request : newSettlementReqs.values()) {
                    mainSettlementController.checkRequest(request.getMapped().getDocument(), false, false);
                }
            }
            Map<String, IPLDObject<SealedDocument>> newSealedDocs = validState.getNewSealedDocuments(commonState, true);
            if (newSealedDocs != null) {
                for (IPLDObject<SealedDocument> sealed : newSealedDocs.values()) {
                    mainSettlementController.checkRequest(sealed.getMapped().getDocument(), true, false);
                }
            }
        }
        // the main settlement controller is prepared for skipping reviews where there are no settlement requests for
        // the documents (including merge); all other settlement controllers won't be used for merging
        Map<String, UserState> affected = new HashMap<>();
        Map<String, Map<String, String>> reviewers = new HashMap<>();
        modelState.prepareSettlementValidation(currentSettlementController, newReviewTableKeysSettlement,
                newReviewTableValuesSettlement, reviewers, affected);
        if (newReviewTableKeysSettlement != null
                && (newReviewTableKeysSettlement.size() > 0 || newReviewTableValuesSettlement.size() > 0)) {
            throw new ValidationException("review table inconsistency (too many new entries)");
        }

        Map<String, UserState> toUpdate = new HashMap<>();
        for (Entry<String, UserState> entry : affected.entrySet()) {
            String key = entry.getKey();
            IPLDObject<UserState> previousUserState = getPreviousUserState(key);
            if (previousUserState == null) {
                toUpdate.put(key, new UserState(entry.getValue().getUser()));
            }
            else {
                toUpdate.put(key, previousUserState.getMapped().settlementCopy());
            }
        }
        Map<String, SealedDocument> sealedDocuments = new HashMap<>();
        if (currentSettlementController.evaluate(sealedDocuments, null, modelState)) {
            currentSettlementController.update(toUpdate,
                    currentValidLocalState == null ? null : currentValidLocalState.getMapped(), sealedDocuments);
        }
        for (Entry<String, UserState> entry : affected.entrySet()) {
            String key = entry.getKey();
            entry.getValue().validateSettlement(toUpdate.remove(key));
        }
        if (toUpdate.size() > 0) { // remaining entries have been added by the settlement controller for truth inversion
            for (Entry<String, UserState> entry : toUpdate.entrySet()) {
                String key = entry.getKey();
                UserState current = modelState.expectUserState(key).getMapped();
                IPLDObject<UserState> previous = getPreviousUserState(key);
                if (previous == null) {
                    current.validateSettlement(entry.getValue());
                }
                else {
                    UserState copy = previous.getMapped().settlementCopy();
                    copy.applySettlement(entry.getValue());
                    current.validateSettlement(copy);
                }
            }
        }

        if (newSealedDocuments != null) {
            for (Entry<String, IPLDObject<SealedDocument>> entry : newSealedDocuments.entrySet()) {
                IPLDObject<SealedDocument> sealed = entry.getValue();
                SealedDocument expected = sealedDocuments.remove(entry.getKey());
                expectEqual(sealed.getMapped().getDocument(), expected.getDocument(), "sealed document inconsistency");
                validated.add(sealed.getMultihash());
            }
        }
        if (sealedDocuments.size() > 0) {
            throw new ValidationException("unmatched expected sealed documents");
        }
        if (currentSettlementController == mainSettlementController) {
            mainSettlementController.enterMergeMode();
        }
    }

    private void findCommonState(ModelState modelState) {
        if (currentValidLocalState == null) {
            if (strict) {
                IPLDObject<ModelState> remoteStateObject = modelState.getPreviousVersion();
                while (remoteStateObject != null) {
                    ModelState remoteState = remoteStateObject.getMapped();
                    previousStates.push(remoteState);
                    remoteStateObject = remoteState.getPreviousVersion();
                }
            }
        }
        else {
            IPLDObject<ModelState> localStateObject = currentValidLocalState;
            ModelState localState = localStateObject.getMapped();
            IPLDObject<ModelState> remoteStateObject = null;
            ModelState remoteState = modelState;
            long localVersion = localState.getVersion();
            long remoteVersion = remoteState.getVersion();
            do {
                while (remoteVersion > localVersion) {
                    remoteStateObject = remoteState.getPreviousVersion();
                    if (remoteStateObject == null) {
                        return;
                    }
                    remoteState = remoteStateObject.getMapped();
                    previousStates.push(remoteState);
                    remoteVersion = remoteState.getVersion();
                    if (currentLocalHashes.contains(remoteStateObject.getMultihash())) {
                        commonStateObject = remoteStateObject;
                        commonState = remoteState;
                        return;
                    }
                }
                while (localVersion > remoteVersion) {
                    localStateObject = localState.getPreviousVersion();
                    if (localStateObject == null) {
                        return;
                    }
                    localState = localStateObject.getMapped();
                    localVersion = localState.getVersion();
                }
                if (localVersion == remoteVersion) {
                    if (remoteStateObject == null) {
                        return;
                    }
                    if (localStateObject.getMultihash().equals(remoteStateObject.getMultihash())) {
                        commonStateObject = localStateObject;
                        commonState = localState;
                        return;
                    }
                    localStateObject = localState.getPreviousVersion();
                    if (localStateObject == null) {
                        return;
                    }
                    localState = localStateObject.getMapped();
                    localVersion = localState.getVersion();
                }
            }
            while (true);
        }
    }

    private void findCommonUserState(String userHash, IPLDObject<UserState> userState,
            Deque<UserState> previousStates) {
        IPLDObject<UserState> remoteUserStateObject = userState;
        UserState remoteUserState = remoteUserStateObject.getMapped();
        if (currentValidLocalState == null) {
            if (strict) {
                remoteUserStateObject = remoteUserState.getPreviousVersion();
                while (remoteUserStateObject != null) {
                    remoteUserState = remoteUserStateObject.getMapped();
                    previousStates.push(remoteUserState);
                    remoteUserStateObject = remoteUserState.getPreviousVersion();
                }
            }
        }
        else {
            IPLDObject<UserState> localUserStateObject = currentValidLocalState.getMapped().expectUserState(userHash);
            UserState localUserState = localUserStateObject.getMapped();
            long localVersion = localUserState.getVersion();
            long remoteVersion = remoteUserState.getVersion();
            do {
                while (remoteVersion > localVersion) {
                    remoteUserStateObject = remoteUserState.getPreviousVersion();
                    if (remoteUserStateObject == null) {
                        return;
                    }
                    remoteUserState = remoteUserStateObject.getMapped();
                    previousStates.push(remoteUserState);
                    remoteVersion = remoteUserState.getVersion();
                }
                while (localVersion > remoteVersion) {
                    localUserStateObject = localUserState.getPreviousVersion();
                    if (localUserStateObject == null) {
                        return;
                    }
                    localUserState = localUserStateObject.getMapped();
                    localVersion = localUserState.getVersion();
                }
                if (localVersion == remoteVersion) {
                    if (localUserStateObject.getMultihash().equals(remoteUserStateObject.getMultihash())) {
                        commonUserStates.put(userHash, localUserStateObject);
                        return;
                    }
                    localUserStateObject = localUserState.getPreviousVersion();
                    if (localUserStateObject == null) {
                        return;
                    }
                    localUserState = localUserStateObject.getMapped();
                    localVersion = localUserState.getVersion();
                }
            }
            while (true);
        }
    }

    private void findBestCommonUserState(String userHash, IPLDObject<UserState> userState,
            IPLDObject<UserState> minCommonState, Deque<UserState> previousStates) {
        IPLDObject<UserState> localUserStateObject = currentValidLocalState.getMapped().expectUserState(userHash);
        UserState localUserState = localUserStateObject.getMapped();
        IPLDObject<UserState> remoteUserStateObject = userState;
        UserState remoteUserState = remoteUserStateObject.getMapped();
        long minCommonVersion = minCommonState.getMapped().getVersion();
        long localVersion = localUserState.getVersion();
        long remoteVersion = remoteUserState.getVersion();

        while (remoteVersion > minCommonVersion) {
            while (localVersion > remoteVersion) {
                localUserStateObject = localUserState.getPreviousVersion();
                if (localUserStateObject == null) {
                    break;
                }
                localUserState = localUserStateObject.getMapped();
                localVersion = localUserState.getVersion();
            }
            if (localVersion == remoteVersion) {
                if (localUserStateObject.getMultihash().equals(remoteUserStateObject.getMultihash())) {
                    commonUserStates.put(userHash, localUserStateObject);
                    return;
                }
            }
            remoteUserStateObject = remoteUserState.getPreviousVersion();
            if (remoteUserStateObject == null) {
                break;
            }
            remoteUserState = remoteUserStateObject.getMapped();
            previousStates.push(remoteUserState);
            remoteVersion = remoteUserState.getVersion();
        }
        commonUserStates.put(userHash, minCommonState);
    }

    /*
     * if voting.subject is OwnershipSelection, ownership transfer controller for modelState must produce same voting,
     * else if voting.subject is UnbanRequest, modelState.userStates[voting.subject.userState.user.multihash] must be
     * voting.subject.userState or contain unban request; unban request must be active
     */
    private void validateNewVoting(String key, Voting voting, ModelState modelState) {
        if (previousStates.size() > 0) {
            Collection<Voting> previousVersions = new ArrayList<>();
            for (ModelState previous : previousStates) {
                IPLDObject<Voting> previousVoting = previous.getVoting(key);
                if (previousVoting != null) {
                    previousVersions.add(previousVoting.getMapped());
                }
            }
            Voting.VOTE_KEY_COLLECTOR.validateUndeletableEntries(previousVersions);
        }
        boolean hasNewVotes = voting.validateNewVotes(commonState, modelState, this);
        IPLDObject<Tally> tally = voting.getTally();
        if (tally != null && validated.add(tally.getMultihash())) {
            voting.validateTally(secretConfig);
        }
        if (!hasNewVotes && (commonState == null || commonState.getVoting(key) == null)) {
            modelState.validateVotingCause(key, commonState == null ? -1 : commonState.getVersion(), this);
        }
    }

    /*
     * verify signature, required rating and requestor's relation to document - settlement controller is responsible for
     * remaining validations
     */
    private void validateSettlementRequest(IPLDObject<SettlementRequest> request, ModelState modelState) {
        SettlementRequest req = request.getMapped();
        UserState userState = req.getUserState().getMapped();
        userState.validateRequiredRating();
        IPLDObject<User> user = userState.getUser();
        if (currentValidLocalState != null) {
            String userHash = user.getMultihash();
            user = currentValidLocalState.getMapped().expectUserState(userHash).getMapped().getUser();
        }
        context.verifySignature(request, Signer.VERIFIER, user.getMapped());
        IPLDObject<Document> document = req.getDocument();
        String documentHash = document.getMapped().getFirstVersionHash();
        if (documentHash == null) {
            documentHash = document.getMultihash();
        }
        userState.validateRelatedDocument(documentHash, modelState.getReviewTableEntries(documentHash));
    }

    /*
     * verify signature, ownership transfer controller for first model state without request must produce same ownership
     * request
     */
    private void validateOwnershipRequest(IPLDObject<OwnershipRequest> request, ModelState modelState) {
        String userHash = validateToggleRequest(request);
        OwnershipRequest req = request.getMapped();
        String documentHash = req.getDocument().getMultihash();
        IPLDObject<ModelState> withoutRequest = null;
        IPLDObject<ModelState> previousState = modelState.getPreviousVersion();
        while (previousState != null) {
            ModelState state = previousState.getMapped();
            IPLDObject<UserState> userStateObject = state.expectUserState(userHash);
            UserState userState = userStateObject.getMapped();
            if (userState.getOwnershipRequest(documentHash) != null) {
                userState.validateRequiredRating();
                withoutRequest = previousState;
                break;
            }
            previousState = state.getPreviousVersion();
        }
        new OwnershipTransferController(request, withoutRequest, context);
    }

    private <D extends ToggleRequest> String validateToggleRequest(IPLDObject<D> request) {
        ToggleRequest req = request.getMapped();
        UserState userState = req.getUserState().getMapped();
        IPLDObject<User> user = userState.getUser();
        String userHash = user.getMultihash();
        if (currentValidLocalState != null) {
            user = currentValidLocalState.getMapped().expectUserState(userHash).getMapped().getUser();
        }
        context.verifySignature(request, Signer.VERIFIER, user.getMapped());
        return userHash;
    }

    // In contrast to merging, we don't reset the new instance maps or collections, because we are validating a remote
    // state, which, if valid, is never validated again, and if invalid, is dropped immediately.
    private void validateUserState(UserState userState, ModelState modelState,
            Map<String, IPLDObject<SettlementRequest>> newSettlementRequestsMap,
            Map<String, IPLDObject<OwnershipRequest>> newOwnershipRequestsMap, Map<String, Set<String>> reviewHashes,
            Collection<UserState> previousStates) {
        IPLDObject<UserState> common = commonUserStates.get(userState.getUser().getMultihash());
        UserState since = common == null ? null : common.getMapped();
        IPLDObject<User> userObject = userState.getUser();
        String userHash = userObject.getMultihash();
        if (currentValidLocalState != null) {
            userObject = currentValidLocalState.getMapped().expectUserState(userHash).getMapped().getUser();
        }
        User user = userObject.getMapped();
        Collection<IPLDObject<Document>> newDocuments;
        Map<String, IPLDObject<DocumentRemoval>> newRemovedDocuments;
        if (reviewHashes == null) {
            newDocuments = userState.getNewDocuments(since, false);
            newRemovedDocuments = userState.getNewRemovedDocuments(since, false);
        }
        else {
            newDocuments = userState.getNewDocuments(since, reviewHashes, obsoleteReviewVersions);
            newRemovedDocuments = userState.getNewRemovedDocuments(since, reviewHashes, obsoleteReviewVersions);
        }
        if (newDocuments != null) {
            for (IPLDObject<Document> document : newDocuments) {
                if (validated.add(document.getMultihash())) {
                    validateDocument(document, userState, user);
                }
            }
        }
        if (newRemovedDocuments != null) {
            for (IPLDObject<DocumentRemoval> removal : newRemovedDocuments.values()) {
                if (validated.add(removal.getMultihash())) {
                    validateDocumentRemoval(removal, modelState, userState, user);
                }
                currentSettlementController.checkRemovedDocument(removal.getMapped().getDocument(), null);
            }
        }
        Collection<IPLDObject<SettlementRequest>> newSettlementRequests = userState.getNewSettlementRequests(since,
                false);
        if (newSettlementRequests != null) {
            for (IPLDObject<SettlementRequest> settlementRequest : newSettlementRequests) {
                String multihash = settlementRequest.getMultihash();
                expectEqual(newSettlementRequestsMap.remove(multihash).getMapped().getUserState().getMapped().getUser(),
                        userObject, "settlement request inconsistency: expected same user");
                currentSettlementController.checkRequest(settlementRequest.getMapped().getDocument(), false, true);
            }
        }
        Collection<IPLDObject<OwnershipRequest>> newOwnershipRequests = userState.getNewOwnershipRequests(since, false);
        if (newOwnershipRequests != null) {
            for (IPLDObject<OwnershipRequest> ownershipRequest : newOwnershipRequests) {
                if (newOwnershipRequestsMap.remove(ownershipRequest.getMultihash()) == null) {
                    throw new ValidationException("expected ownership request in model state");
                }
            }
        }
        Collection<IPLDObject<UnbanRequest>> newUnbanRequests = userState.getNewUnbanRequests(since, false);
        if (newUnbanRequests != null) {
            for (IPLDObject<UnbanRequest> unbanRequest : newUnbanRequests) {
                if (validated.add(unbanRequest.getMultihash())) {
                    validateUnbanRequest(unbanRequest, userState, previousStates, user);
                }
            }
        }
        Collection<IPLDObject<GrantedOwnership>> newGrantedOwnerships = userState.getNewGrantedOwnerships(since, false);
        if (newGrantedOwnerships != null) {
            for (IPLDObject<GrantedOwnership> grantedOwnership : newGrantedOwnerships) {
                if (validated.add(grantedOwnership.getMultihash())) {
                    validateGrantedOwnership(grantedOwnership, modelState, userHash);
                }
            }
        }
        Collection<IPLDObject<GrantedUnban>> newGrantedUnbans = userState.getNewGrantedUnbans(since, false);
        if (newGrantedUnbans != null) {
            for (IPLDObject<GrantedUnban> grantedUnban : newGrantedUnbans) {
                if (validated.add(grantedUnban.getMultihash())) {
                    validateGrantedUnban(grantedUnban.getMapped(), userState, modelState);
                }
                currentSettlementController.checkGrantedUnban(grantedUnban.getMapped(), userObject);
            }
        }
    }

    /*
     * verify signature, if review, assert that there is no unrelated (i.e. no previous version) review for the same doc
     * assert user is not banned
     */
    private void validateDocument(IPLDObject<Document> document, UserState userState, User user) {
        userState.validateRequiredRating();
        Document doc = document.getMapped();
        if (doc instanceof Review) {
            Review review = (Review) doc;
            if (currentValidLocalState != null && !review.isInvertTruth()
                    && currentValidLocalState.getMapped().isSealedDocument(review.getDocument().getMultihash())) {
                throw new ValidationException("reviewing sealed document (no truth inversion)");
            }
        }
        context.verifySignature(document, Signer.VERIFIER, user);
    }

    /*
     * verify signature, assert same user, assert no review of settlement-requested document
     */
    private void validateDocumentRemoval(IPLDObject<DocumentRemoval> removal, ModelState modelState,
            UserState userState, User user) {
        userState.validateRequiredRating();
        DocumentRemoval docRem = removal.getMapped();
        IPLDObject<Document> document = docRem.getDocument();
        Document doc = document.getMapped();
        expectEqual(userState.getUser(), doc.expectUserState().getUser(), "not document owner");
        if (doc instanceof Review) {
            Review review = (Review) doc;
            IPLDObject<Document> reviewedDocument = review.getDocument();
            IPLDObject<SettlementRequest> settlementRequest = modelState
                    .getSettlementRequest(reviewedDocument.getMultihash());
            if (settlementRequest != null) {
                throw new ValidationException(
                        "can't remove a review of a document with a valid pending settlement request");
            }
        }
        context.verifySignature(removal, Signer.VERIFIER, user);
    }

    /*
     * verify signature, if previousStates (all the way to common - or request.userState if not strict) contain toggled
     * unban request, check if payload has been increased; check if request.userState was banned, contains a false-entry
     * and does not already contain a granted unban for the document
     */
    private void validateUnbanRequest(IPLDObject<UnbanRequest> request, UserState userState,
            Collection<UserState> previousStates, User user) {
        validateToggleRequest(request);
        String documentHash = request.getMapped().getDocument().getMultihash();
        if (userState.getGrantedUnban(documentHash) != null) {
            throw new ValidationException("user has already been unbanned for the document");
        }
        Collection<UserState> prev;
        if (currentValidLocalState != null || strict) {
            prev = previousStates;
        }
        else {
            Deque<UserState> deque = new ArrayDeque<>();
            IPLDObject<UserState> requestUserState = request.getMapped().getUserState();
            String stop = requestUserState.getMultihash();
            IPLDObject<UserState> previous = userState.getPreviousVersion();
            do {
                UserState state = previous.getMapped();
                deque.push(state);
                previous = state.getPreviousVersion();
            }
            while (previous != null && previous != requestUserState && !previous.getMultihash().equals(stop));
            deque.push(requestUserState.getMapped());
            prev = deque;
        }
        boolean found = false;
        UnbanRequest previousRequest = null;
        for (UserState previous : prev) {
            UnbanRequest unbanRequest = previous.getUnbanRequest(documentHash);
            if (previousRequest != null) {
                unbanRequest.validate(previousRequest);
            }
            if (!found && unbanRequest != null) { // first occurrence - validate prerequisites
                found = true;
                UserState atRequestTime = unbanRequest.getUserState().getMapped();
                atRequestTime.validateUnbanRequest(documentHash);
            }
            previousRequest = unbanRequest;
        }
        if (previousRequest == null) {
            userState.validateUnbanRequest(documentHash);
        }
        else {
            request.getMapped().validate(previousRequest);
        }
    }

    /*
     * Ownership transfer controller for modelState without granted ownership must produce same output that causes the
     * controller to create the granted ownership
     */
    private void validateGrantedOwnership(IPLDObject<GrantedOwnership> granted, ModelState modelState,
            String userHash) {
        IPLDObject<ModelState> withoutGranted = null;
        IPLDObject<ModelState> previousState = modelState.getPreviousVersion();
        String documentHash = granted.getMapped().getDocument().getMultihash();
        while (previousState != null) {
            ModelState state = previousState.getMapped();
            IPLDObject<UserState> userState = state.expectUserState(userHash);
            if (userState.getMapped().getGrantedOwnership(documentHash) == null) {
                withoutGranted = previousState;
                break;
            }
            previousState = state.getPreviousVersion();
        }
        new OwnershipTransferController(granted, withoutGranted, userHash, context, timestampTolerance);
    }

    /*
     * granted.unbanRequest must be contained in user state and there must be a won voting with granted.unbanRequest as
     * subject in model state
     */
    private void validateGrantedUnban(GrantedUnban granted, UserState userState, ModelState modelState) {
        IPLDObject<UnbanRequest> unbanRequest = granted.getUnbanRequest();
        expectEqual(unbanRequest, userState.expectUnbanRequest(unbanRequest.getMapped().getDocument().getMultihash()));
        IPLDObject<Voting> voting = modelState.expectVotingForUnbanRequest(unbanRequest.getMultihash());
        voting.getMapped().expectWinner(Boolean.TRUE);
    }

    public long addMustValidateModelState(IPLDObject<ModelState> modelState) {
        long version = modelState.getMapped().getVersion();
        if (commonState == null || commonState.getVersion() < version) {
            if (mustValidateModelStates == null) {
                mustValidateModelStates = new HashMap<>();
            }
            mustValidateModelStates.put(version, modelState);
            return version;
        }
        return -1;
    }

    public long addMustValidateModelState(IPLDObject<ModelState> modelState, String settlementDocumentHash) {
        IPLDObject<ModelState> stateObject = modelState;
        ModelState state = stateObject.getMapped();
        IPLDObject<SettlementRequest> settlementRequest = state.getSettlementRequest(settlementDocumentHash);
        long version = state.getVersion();
        do {
            IPLDObject<ModelState> prevObject = state.getPreviousVersion();
            if (prevObject == null) {
                if (settlementRequest == null) {
                    throw new ValidationException("expected settlement request");
                }
                if (mustValidateModelStates == null) {
                    mustValidateModelStates = new HashMap<>();
                }
                mustValidateModelStates.put(version, stateObject);
                return version;
            }
            else {
                state = prevObject.getMapped();
                IPLDObject<SettlementRequest> req = state.getSettlementRequest(settlementDocumentHash);
                if (req == null) {
                    if (settlementRequest != null) {
                        if (mustValidateModelStates == null) {
                            mustValidateModelStates = new HashMap<>();
                        }
                        mustValidateModelStates.put(version, stateObject);
                        return version;
                    }
                }
                else {
                    settlementRequest = req;
                }
                version = state.getVersion();
            }
        }
        while (commonState == null || commonState.getVersion() < version);
        return -1;
    }

    private boolean validateMustValidateModelStates(IPLDObject<ModelState> mustKeepStart) {
        if (mustValidateModelStates != null && currentSettlementController == mainSettlementController) {
            outer: do {
                integrateMustValidateUserStates(mustKeepStart);
                List<Long> keys = new ArrayList<>(mustValidateModelStates.keySet());
                Collections.sort(keys, DESCENDING);
                for (Long key : keys) {
                    IPLDObject<ModelState> mustValidateState = mustValidateModelStates.remove(key);
                    ModelState mustValidate = mustValidateState.getMapped();
                    this.currentSettlementController = new SettlementController(mustValidate.getPreviousVersion(),
                            commonState, this, false, mustValidate.getTimestamp(), timestampTolerance);
                    IPLDObject<ModelState> toValidate = new IPLDObject<>(mustValidateState.getMultihash(),
                            LoaderFactory.MODEL_STATE.createLoader(), context, this);
                    int count = mustValidateModelStates.size();
                    toValidate.getMapped();
                    if (validatedPreviousState == null) { // by design highest version first, must not be replaced with
                                                          // lower version
                        validatedPreviousState = mustValidateState;
                    }
                    if (mustValidateModelStates.size() > count) {
                        continue outer;
                    }
                }
                this.currentSettlementController = mainSettlementController;
                return true;
            }
            while (true);
        }
        return false;
    }

    public long addMustValidateUserState(IPLDObject<UserState> userState) {
        if (mustValidateUserStateVersions == null) {
            mustValidateUserStateVersions = new HashMap<>();
            if (mustValidateModelStates == null) {
                mustValidateModelStates = new HashMap<>();
            }
        }
        UserState u = userState.getMapped();
        long version = u.getVersion();
        String userHash = u.getUser().getMultihash();
        IPLDObject<UserState> commonUserState = commonUserStates.get(userHash);
        if (commonUserState == null || commonUserState.getMapped().getVersion() < version) {
            Set<Long> set = mustValidateUserStateVersions.get(userHash);
            if (set == null) {
                set = new TreeSet<>(DESCENDING);
                mustValidateUserStateVersions.put(userHash, set);
            }
            set.add(version);
            return version;
        }
        return -1;
    }

    private void integrateMustValidateUserStates(IPLDObject<ModelState> mustKeepStart) {
        if (mustValidateUserStateVersions != null) {
            long stop = commonState == null ? -1 : commonState.getVersion();
            for (Entry<String, Set<Long>> entry : mustValidateUserStateVersions.entrySet()) {
                String userHash = entry.getKey();
                Set<Long> set = entry.getValue();
                outer: for (Long version : set) {
                    for (IPLDObject<ModelState> mustKeep : mustValidateModelStates.values()) {
                        IPLDObject<UserState> userState = mustKeep.getMapped().getUserState(userHash);
                        if (userState != null && userState.getMapped().getVersion() == version) {
                            addValidatedUserState(userHash, userState);
                            continue outer;
                        }
                    }
                    IPLDObject<ModelState> mustKeep = mustKeepStart;
                    do {
                        ModelState modelState = mustKeep.getMapped();
                        if (!mustValidateModelStates.containsKey(modelState.getVersion())) {
                            IPLDObject<UserState> userState = modelState.getUserState(userHash);
                            if (userState != null && userState.getMapped().getVersion() == version) {
                                mustValidateModelStates.put(modelState.getVersion(), mustKeep);
                                addValidatedUserState(userHash, userState);
                                continue outer;
                            }
                        }
                        mustKeep = modelState.getPreviousVersion();
                    }
                    while (mustKeep.getMapped().getVersion() > stop);
                    throw new ValidationException("Did not find model state for must validate user state");
                }
            }
            mustValidateUserStateVersions.clear();
        }
    }

    private void addValidatedUserState(String userHash, IPLDObject<UserState> userState) {
        if (validatedUserStates == null) {
            validatedUserStates = new HashMap<>();
        }
        if (!validatedUserStates.containsKey(userHash)) { // by design highest version first, must not be replaced with
                                                          // lower
            // version
            validatedUserStates.put(userHash, userState);
        }
    }

    public boolean addValidated(String multihash) {
        return validated.add(multihash);
    }

}
