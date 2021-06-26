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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import org.projectjinxers.model.Document;
import org.projectjinxers.model.DocumentRemoval;
import org.projectjinxers.model.GrantedOwnership;
import org.projectjinxers.model.GrantedUnban;
import org.projectjinxers.model.LoaderFactory;
import org.projectjinxers.model.ModelState;
import org.projectjinxers.model.OwnershipRequest;
import org.projectjinxers.model.SealedDocument;
import org.projectjinxers.model.SettlementRequest;
import org.projectjinxers.model.Tally;
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

    // if changed in a running system, all affected model meta versions must be changed as well and validation must be
    // adjusted
    public static final long TIMESTAMP_TOLERANCE = 1000L * 60 * 2;

    private static final Comparator<Long> DESCENDING = new Comparator<>() {
        @Override
        public int compare(Long o1, Long o2) {
            return o2.compareTo(o1);
        }
    };

    private final IPLDContext context; // we might actually not need it (it's used indirectly in getMapped() calls)
    private IPLDObject<ModelState> currentValidLocalState;
    private final Set<String> currentLocalHashes;
    private final long timestamp;
    private final boolean strict;
    private final SettlementController mainSettlementController;

    private SettlementController currentSettlementController;
    private IPLDObject<ModelState> commonStateObject;
    private ModelState commonState;
    private Map<String, IPLDObject<UserState>> commonUserStates = new HashMap<>();

    private Map<Long, IPLDObject<ModelState>> mustKeepModelStates;
    private IPLDObject<ModelState> keptModelState;
    private Map<String, Set<Long>> mustKeepUserStateVersions;
    private Map<String, IPLDObject<UserState>> keptUserStates;

    private Set<String> validated = new TreeSet<>();

    public ValidationContext(IPLDContext context, IPLDObject<ModelState> currentValidLocalState,
            Set<String> currentLocalHashes, long timestamp, boolean strict) {
        this.context = context;
        this.currentValidLocalState = currentValidLocalState;
        this.currentLocalHashes = currentLocalHashes;
        this.timestamp = timestamp;
        this.strict = strict;
        this.mainSettlementController = new SettlementController(true, timestamp);
        this.currentSettlementController = mainSettlementController;
    }

    public SettlementController getMainSettlementController() {
        return mainSettlementController;
    }

    public IPLDObject<ModelState> getPreviousVersion() {
        return keptModelState == null ? commonStateObject : keptModelState;
    }

    public IPLDObject<UserState> getPreviousUserState(String userHash) {
        IPLDObject<UserState> kept = keptUserStates == null ? null : keptUserStates.get(userHash);
        return kept == null ? commonUserStates.get(userHash) : kept;
    }

    public boolean isTrivialMerge() {
        return commonStateObject == currentValidLocalState;
    }

    public boolean isTrivialMerge(String userHash) {
        IPLDObject<UserState> currentValidUserState = currentValidLocalState.getMapped().expectUserState(userHash);
        IPLDObject<UserState> commonUserState = getPreviousUserState(userHash);
        return commonUserState == currentValidUserState || commonUserState != null && currentValidUserState != null
                && commonUserState.getMultihash().equals(currentValidUserState.getMultihash());
    }

    public void validateTimestamp(long timestamp) {
        if (strict && Math.abs(System.currentTimeMillis() - timestamp) > TIMESTAMP_TOLERANCE) {
            throw new ValidationException("Timestamp out of range");
        }
    }

    public void validateModelState(ModelState modelState) {
        findCommonState(modelState);
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
                    validateSettlementRequest(settlementRequest.getMapped(), modelState);
                }
                newSettlementRequestsMap.put(multihash, settlementRequest);
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
                        validateOwnershipRequest(ownershipRequest.getMapped(), modelState);
                    }
                    newOwnershipRequestsMap.put(multihash, ownershipRequest);
                }
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
                    if (currentValidLocalState != null) {
                        if (commonUserState == null) {
                            findCommonUserState(userHash, userStateObject);
                        }
                        else {
                            findBestCommonUserState(userHash, userStateObject, commonUserState);
                        }
                    }
                    validateUserState(userState, modelState, newSettlementRequestsMap, newOwnershipRequestsMap);
                }
            }
        }
        if (newSettlementRequestsMap != null && newSettlementRequestsMap.size() > 0) {
            throw new ValidationException("New settlement requests out of sync (model state v user states)");
        }
        if (newOwnershipRequestsMap != null && newOwnershipRequestsMap.size() > 0) {
            throw new ValidationException("New ownership requests out of sync (model state v user states)");
        }
        validateMustKeepModelStates(modelState.getPreviousVersion());

        // we already prepare the settlement requests required for merging; in order to prevent them from influencing
        // the outcome of the validation, we don't set the flag, that makes main validation include them
        if (currentValidLocalState != null && currentSettlementController == mainSettlementController) {
            Map<String, IPLDObject<SettlementRequest>> newSettlementReqs = currentValidLocalState.getMapped()
                    .getNewSettlementRequests(commonState, true);
            if (newSettlementReqs != null) {
                for (IPLDObject<SettlementRequest> request : newSettlementReqs.values()) {
                    mainSettlementController.checkRequest(request.getMapped(), false);
                }
            }
        }
        // the main settlement controller is prepared for skipping reviews where there are no settlement requests for
        // the documents (including merge); all other settlement controllers won't be used for merging
        Map<String, UserState> affected = new HashMap<>();
        modelState.prepareSettlementValidation(currentSettlementController, affected);

        Map<String, UserState> toUpdate = new HashMap<>();
        for (Entry<String, UserState> entry : affected.entrySet()) {
            String key = entry.getKey();
            IPLDObject<UserState> previousUserState = getPreviousUserState(key);
            if (previousUserState == null) {
                toUpdate.put(key, new UserState(entry.getValue().getUser()));
            }
            else {
                toUpdate.put(key, entry.getValue().settlementCopy());
            }
        }
        Map<String, SealedDocument> sealedDocuments = new HashMap<>();
        if (currentSettlementController.evaluate(sealedDocuments, modelState)) {
            currentSettlementController.update(toUpdate, modelState, sealedDocuments);
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
        Map<String, IPLDObject<SealedDocument>> newSealedDocuments = modelState.getNewSealedDocuments(commonState,
                false);
        if (newSealedDocuments != null) {
            for (Entry<String, IPLDObject<SealedDocument>> entry : newSealedDocuments.entrySet()) {
                IPLDObject<SealedDocument> sealed = entry.getValue();
                SealedDocument expected = sealedDocuments.get(entry.getKey());
                if (!sealed.getMapped().getDocument().getMultihash().equals(expected.getDocument().getMultihash())) {
                    throw new ValidationException("Sealed document inconsistency");
                }
                validated.add(sealed.getMultihash());
            }
        }
        if (sealedDocuments.size() > 0) {
            throw new ValidationException("Unmatched expected sealed documents");
        }
        if (currentSettlementController == mainSettlementController) {
            mainSettlementController.enterMergeMode();
        }
    }

    private void findCommonState(ModelState modelState) {
        if (currentValidLocalState != null) {
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
                    // minor potential for optimization: evaluate if this is valid
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

    private void findCommonUserState(String userHash, IPLDObject<UserState> userState) {
        IPLDObject<UserState> localUserStateObject = currentValidLocalState.getMapped().expectUserState(userHash);
        UserState localUserState = localUserStateObject.getMapped();
        IPLDObject<UserState> remoteUserStateObject = userState;
        UserState remoteUserState = remoteUserStateObject.getMapped();
        long localVersion = localUserState.getVersion();
        long remoteVersion = remoteUserState.getVersion();
        do {
            while (remoteVersion > localVersion) {
                remoteUserStateObject = remoteUserState.getPreviousVersion();
                if (remoteUserStateObject == null) {
                    return;
                }
                remoteUserState = remoteUserStateObject.getMapped();
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

    private void findBestCommonUserState(String userHash, IPLDObject<UserState> userState,
            IPLDObject<UserState> minCommonState) {
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
        boolean hasNewVotes = voting.validateNewVotes(commonState, modelState, context, this);
        IPLDObject<Tally> tally = voting.getTally();
        if (tally != null && validated.add(tally.getMultihash())) {
            voting.validateTally();
        }
        if (!hasNewVotes && (commonState == null || commonState.getVoting(key) == null)) {
            modelState.validateVotingCause(key, commonState == null ? -1 : commonState.getVersion());
        }
    }

    /*
     * verify signature, if previousStates (all the way to common ) contain toggled settlement request, check if payload
     * has been increased; check if document was eligible for settlement at request.userState and there is no
     * conflicting settlement request (w.r.t. timestamp, respecting truth inversion links) -> SettlementController!!!
     */
    private void validateSettlementRequest(SettlementRequest request, ModelState modelState) {

    }

    /*
     * verify signature, ownership transfer controller for model state must produce same ownership request
     */
    private void validateOwnershipRequest(OwnershipRequest request, ModelState modelState) {

    }

    // In contrast to merging, we don't reset the new instance maps or collections, because we are validating a remote
    // state, which, if valid, is never validated again, and if invalid, is dropped immediately.
    private void validateUserState(UserState userState, ModelState modelState,
            Map<String, IPLDObject<SettlementRequest>> newSettlementRequestsMap,
            Map<String, IPLDObject<OwnershipRequest>> newOwnershipRequestsMap) {
        IPLDObject<UserState> common = commonUserStates.get(userState.getUser().getMultihash());
        UserState since = common == null ? null : common.getMapped();
        Collection<IPLDObject<Document>> newDocuments = userState.getNewDocuments(since, false);
        if (newDocuments != null) {
            for (IPLDObject<Document> document : newDocuments) {
                if (validated.add(document.getMultihash())) {
                    validateDocument(document.getMapped(), userState);
                }
            }
        }
        Map<String, IPLDObject<DocumentRemoval>> newRemovedDocuments = userState.getNewRemovedDocuments(since, false);
        if (newRemovedDocuments != null) {
            for (IPLDObject<DocumentRemoval> removal : newRemovedDocuments.values()) {
                if (validated.add(removal.getMultihash())) {
                    validateDocumentRemoval(removal.getMapped());
                }
            }
        }
        Collection<IPLDObject<SettlementRequest>> newSettlementRequests = userState.getNewSettlementRequests(since,
                false);
        if (newSettlementRequests != null) {
            User user = userState.getUser().getMapped();
            for (IPLDObject<SettlementRequest> settlementRequest : newSettlementRequests) {
                String multihash = settlementRequest.getMultihash();
                if (newSettlementRequestsMap.remove(multihash).getMapped().getUserState().getMapped().getUser()
                        .getMapped() != user) {
                    throw new ValidationException("Settlement request inconsistency: expected same user");
                }
                currentSettlementController.checkRequest(settlementRequest.getMapped(), true);
            }
        }
        Collection<IPLDObject<OwnershipRequest>> newOwnershipRequests = userState.getNewOwnershipRequests(since, false);
        if (newOwnershipRequests != null) {
            for (IPLDObject<OwnershipRequest> ownershipRequest : newOwnershipRequests) {
                if (newOwnershipRequestsMap.remove(ownershipRequest.getMultihash()) == null) {
                    throw new ValidationException("Expected ownership request in model state");
                }
            }
        }
        Collection<IPLDObject<UnbanRequest>> newUnbanRequests = userState.getNewUnbanRequests(since, false);
        if (newUnbanRequests != null) {
            for (IPLDObject<UnbanRequest> unbanRequest : newUnbanRequests) {
                if (validated.add(unbanRequest.getMultihash())) {
                    validateUnbanRequest(unbanRequest.getMapped(), userState);
                }
            }
        }
        Collection<IPLDObject<GrantedOwnership>> newGrantedOwnerships = userState.getNewGrantedOwnerships(since, false);
        if (newGrantedOwnerships != null) {
            for (IPLDObject<GrantedOwnership> grantedOwnership : newGrantedOwnerships) {
                if (validated.add(grantedOwnership.getMultihash())) {
                    validateGrantedOwnership(grantedOwnership.getMapped(), modelState);
                }
            }
        }
        Collection<IPLDObject<GrantedUnban>> newGrantedUnbans = userState.getNewGrantedUnbans(since, false);
        if (newGrantedUnbans != null) {
            String userHash = userState.getUser().getMultihash();
            for (IPLDObject<GrantedUnban> grantedUnban : newGrantedUnbans) {
                if (validated.add(grantedUnban.getMultihash())) {
                    validateGrantedUnban(grantedUnban.getMapped(), userState, modelState);
                }
                currentSettlementController.checkGrantedUnban(grantedUnban.getMapped(), userHash);
            }
        }
    }

    /*
     * verify signature, if review, assert that there is no unrelated (i.e. no previous version) review for the same doc
     * assert user is not banned, assert previous version exists and is from same user
     */
    private void validateDocument(Document document, UserState userState) {

    }

    private void validateDocumentRemoval(DocumentRemoval removal) {

    }

    /*
     * verify signature, if previousStates (all the way to common - or request.userState if not strict) contain toggled
     * unban request, check if payload has been increased; check if request.userState was banned, contains a false-entry
     * and does not already contain a granted unban for the document
     */
    private void validateUnbanRequest(UnbanRequest request, UserState currentState) {

    }

    /*
     * Ownership transfer controller for modelState must produce same GrantedOwnership
     */
    private void validateGrantedOwnership(GrantedOwnership granted, ModelState modelState) {

    }

    /*
     * granted.unbanRequest must be contained in user state and there must be a won voting with granted.unbanRequest as
     * subject in model state
     */
    private void validateGrantedUnban(GrantedUnban granted, UserState userState, ModelState modelState) {
        IPLDObject<UnbanRequest> unbanRequest = granted.getUnbanRequest();
        if (unbanRequest != userState.expectUnbanRequest(unbanRequest.getMapped().getDocument().getMultihash())) {
            throw new ValidationException("Expected same unban request reference");
        }
        IPLDObject<Voting> voting = modelState.expectVotingForUnbanRequest(unbanRequest.getMultihash());
        voting.getMapped().expectWinner(Boolean.TRUE);
    }

    public long addMustKeepModelState(IPLDObject<ModelState> modelState) {
        long version = modelState.getMapped().getVersion();
        if (commonState == null || commonState.getVersion() < version) {
            if (mustKeepModelStates == null) {
                mustKeepModelStates = new HashMap<>();
            }
            mustKeepModelStates.put(version, modelState);
            return version;
        }
        return -1;
    }

    private boolean validateMustKeepModelStates(IPLDObject<ModelState> mustKeepStart) {
        if (mustKeepModelStates != null && currentSettlementController == mainSettlementController) {
            outer: do {
                integrateMustKeepUserStates(mustKeepStart);
                List<Long> keys = new ArrayList<>(mustKeepModelStates.keySet());
                Collections.sort(keys, DESCENDING);
                for (Long key : keys) {
                    IPLDObject<ModelState> mustKeep = mustKeepModelStates.remove(key);
                    this.currentSettlementController = new SettlementController(false, timestamp);
                    IPLDObject<ModelState> toValidate = new IPLDObject<>(mustKeep.getMultihash(),
                            LoaderFactory.MODEL_STATE.createLoader(), context, this);
                    int count = mustKeepModelStates.size();
                    toValidate.getMapped();
                    if (keptModelState == null) { // by design highest version first, must not be replaced with lower
                                                  // version
                        keptModelState = mustKeep;
                    }
                    if (mustKeepModelStates.size() > count) {
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

    public long addMustKeepUserState(IPLDObject<UserState> userState) {
        if (mustKeepUserStateVersions == null) {
            mustKeepUserStateVersions = new HashMap<>();
            if (mustKeepModelStates == null) {
                mustKeepModelStates = new HashMap<>();
            }
        }
        UserState u = userState.getMapped();
        long version = u.getVersion();
        String userHash = u.getUser().getMultihash();
        IPLDObject<UserState> commonUserState = commonUserStates.get(userHash);
        if (commonUserState == null || commonUserState.getMapped().getVersion() < version) {
            Set<Long> set = mustKeepUserStateVersions.get(userHash);
            if (set == null) {
                set = new TreeSet<>(DESCENDING);
                mustKeepUserStateVersions.put(userHash, set);
            }
            set.add(version);
            return version;
        }
        return -1;
    }

    private void integrateMustKeepUserStates(IPLDObject<ModelState> mustKeepStart) {
        if (mustKeepUserStateVersions != null) {
            long stop = commonState == null ? -1 : commonState.getVersion();
            for (Entry<String, Set<Long>> entry : mustKeepUserStateVersions.entrySet()) {
                String userHash = entry.getKey();
                Set<Long> set = entry.getValue();
                outer: for (Long version : set) {
                    for (IPLDObject<ModelState> mustKeep : mustKeepModelStates.values()) {
                        IPLDObject<UserState> userState = mustKeep.getMapped().getUserState(userHash);
                        if (userState != null && userState.getMapped().getVersion() == version) {
                            addKeptUserState(userHash, userState);
                            continue outer;
                        }
                    }
                    IPLDObject<ModelState> mustKeep = mustKeepStart;
                    do {
                        ModelState modelState = mustKeep.getMapped();
                        if (!mustKeepModelStates.containsKey(modelState.getVersion())) {
                            IPLDObject<UserState> userState = modelState.getUserState(userHash);
                            if (userState != null && userState.getMapped().getVersion() == version) {
                                mustKeepModelStates.put(modelState.getVersion(), mustKeep);
                                addKeptUserState(userHash, userState);
                                continue outer;
                            }
                        }
                        mustKeep = modelState.getPreviousVersion();
                    }
                    while (mustKeep.getMapped().getVersion() > stop);
                    throw new ValidationException("Did not find model state for must keep user state");
                }
            }
        }
        mustKeepUserStateVersions.clear();
    }

    private void addKeptUserState(String userHash, IPLDObject<UserState> userState) {
        if (keptUserStates == null) {
            keptUserStates = new HashMap<>();
        }
        if (!keptUserStates.containsKey(userHash)) { // by design highest version first, must not be replaced with lower
                                                     // version
            keptUserStates.put(userHash, userState);
        }
    }

    public boolean addValidated(String multihash) {
        return validated.add(multihash);
    }

}
