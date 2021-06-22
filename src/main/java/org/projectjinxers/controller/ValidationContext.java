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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.projectjinxers.model.GrantedOwnership;
import org.projectjinxers.model.GrantedUnban;
import org.projectjinxers.model.ModelState;
import org.projectjinxers.model.OwnershipRequest;
import org.projectjinxers.model.SealedDocument;
import org.projectjinxers.model.SettlementRequest;
import org.projectjinxers.model.UnbanRequest;
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

    private static final long TIMESTAMP_TOLERANCE = 1000L * 60 * 60 * 4;

    private IPLDContext context; // we might actually not need it (it's used indirectly in getMapped() calls)
    private IPLDObject<ModelState> currentValidLocalState;
    private Set<String> currentLocalHashes;
    private boolean strict;

    private IPLDObject<ModelState> commonStateObject;
    private ModelState commonState;
    private Map<String, IPLDObject<UserState>> commonUserStates = new HashMap<>();

    public ValidationContext(IPLDContext context, IPLDObject<ModelState> currentValidLocalState,
            Set<String> currentLocalHashes, boolean strict) {
        this.context = context;
        this.currentValidLocalState = currentValidLocalState;
        this.currentLocalHashes = currentLocalHashes;
        this.strict = strict;
    }

    public IPLDObject<ModelState> getCommonStateObject() {
        return commonStateObject;
    }

    public IPLDObject<UserState> getCommonUserState(String userHash) {
        return commonUserStates.get(userHash);
    }

    public boolean isTrivialMerge() {
        return commonStateObject == currentValidLocalState;
    }

    public boolean isTrivialMerge(String userHash) {
        IPLDObject<UserState> currentValidUserState = currentValidLocalState.getMapped().expectUserState(userHash);
        IPLDObject<UserState> commonUserState = commonUserStates.get(userHash);
        return commonUserState == currentValidUserState;
    }

    public void validateTimestamp(long timestamp) {
        if (strict && Math.abs(System.currentTimeMillis() - timestamp) > TIMESTAMP_TOLERANCE) {
            throw new ValidationException("Timestamp out of range");
        }
    }

    public void validateModelState(ModelState modelState) {
        findCommonState(modelState);
        Map<String, IPLDObject<Voting>> newVotings = modelState.getNewVotings(commonState);
        if (newVotings != null) {
            for (Entry<String, IPLDObject<Voting>> entry : newVotings.entrySet()) {
                validateNewVoting(entry.getKey(), entry.getValue().getMapped(), modelState);
            }
        }
        Map<String, IPLDObject<SealedDocument>> newSealedDocuments = modelState.getNewSealedDocuments(commonState);
        if (newSealedDocuments != null) {
            ModelState requestState = strict ? commonState : modelState.getPreviousVersion().getMapped();
            for (Entry<String, IPLDObject<SealedDocument>> entry : newSealedDocuments.entrySet()) {
                validateSealedDocument(entry.getKey(), entry.getValue().getMapped(), requestState);
            }
        }
        Map<String, IPLDObject<OwnershipRequest>> newOwnershipRequestsMap;
        Collection<IPLDObject<OwnershipRequest>> newOwnershipRequests = modelState.getNewOwnershipRequests(commonState);
        if (newOwnershipRequests == null) {
            newOwnershipRequestsMap = null;
        }
        else {
            newOwnershipRequestsMap = new HashMap<>();
            for (IPLDObject<OwnershipRequest> ownershipRequest : newOwnershipRequests) {
                validateOwnershipRequest(ownershipRequest.getMapped(), modelState);
                newOwnershipRequestsMap.put(ownershipRequest.getMultihash(), ownershipRequest);
            }
        }
        Collection<IPLDObject<UserState>> newUserStates = modelState.getNewUserStates(commonState);
        if (newUserStates != null) {
            for (IPLDObject<UserState> userStateObject : newUserStates) {
                UserState userState = userStateObject.getMapped();
                String userHash = userState.getUser().getMultihash();
                IPLDObject<UserState> commonUserState = commonState == null ? null : commonState.getUserState(userHash);
                if (currentValidLocalState != null) {
                    if (commonUserState == null) {
                        findCommonUserState(userHash, userStateObject);
                    }
                    else {
                        findBestCommonUserState(userHash, userStateObject, commonUserState);
                    }
                }
                validateUserState(userState, modelState, newOwnershipRequestsMap);
            }
        }
        if (newOwnershipRequestsMap != null && newOwnershipRequestsMap.size() > 0) {
            throw new ValidationException("New ownership requests out of sync (model state v user states)");
        }
    }

    private void findCommonState(ModelState modelState) {
        if (currentValidLocalState != null) {
            IPLDObject<ModelState> localStateObject = currentValidLocalState;
            ModelState localState = localStateObject.getMapped();
            IPLDObject<ModelState> remoteStateObject = null;
            ModelState remoteState = modelState;
            int localVersion = localState.getVersion();
            int remoteVersion = remoteState.getVersion();
            do {
                while (remoteVersion > localVersion) {
                    remoteStateObject = remoteState.getPreviousVersion();
                    if (remoteStateObject == null) {
                        return;
                    }
                    remoteState = remoteStateObject.getMapped();
                    remoteVersion = remoteState.getVersion();
                    if (currentLocalHashes.contains(remoteStateObject.getMultihash())) {

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
                        commonStateObject = localStateObject; // important for reference equality check
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
        int localVersion = localUserState.getVersion();
        int remoteVersion = remoteUserState.getVersion();
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
                    commonUserStates.put(userHash, localUserStateObject); // important for reference equality check
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
        int minCommonVersion = minCommonState.getMapped().getVersion();
        int localVersion = localUserState.getVersion();
        int remoteVersion = remoteUserState.getVersion();

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
        voting.validateNewVotes(commonState, modelState, context);
        if (voting.getTally() != null) {
            voting.validateTally();
        }
    }

    /*
     * requestState.userState[document.userState.user.multihash] must be document.userState or contain
     * document.settlementRequest
     */
    private void validateSealedDocument(String key, SealedDocument document, ModelState requestState) {
        String userHash = document.getUserState().getMapped().getUser().getMultihash();
        IPLDObject<UserState> userState = requestState.expectUserState(userHash);
        if (userState != document.getUserState() && !userState.getMapped().expectContainsSettlementRequest(key)) {
            throw new ValidationException("Expected settlement request");
        }
    }

    /*
     * verify signature, ownership transfer controller for model state must produce same ownership request
     */
    private void validateOwnershipRequest(OwnershipRequest request, ModelState modelState) {

    }

    private void validateUserState(UserState userState, ModelState modelState,
            Map<String, IPLDObject<OwnershipRequest>> newOwnershipRequestsMap) {
        IPLDObject<UserState> common = commonUserStates.get(userState.getUser().getMultihash());
        UserState since = common == null ? null : common.getMapped();
        IPLDObject<UserState> prev = userState.getPreviousVersion();
        UserState previousState = prev == null ? null : prev.getMapped();
        Collection<IPLDObject<SettlementRequest>> newSettlementRequests = userState.getNewSettlementRequests(since);
        if (newSettlementRequests != null) {
            for (IPLDObject<SettlementRequest> settlementRequest : newSettlementRequests) {
                validateSettlementRequest(settlementRequest.getMapped(), previousState);
            }
        }
        Collection<IPLDObject<OwnershipRequest>> newOwnershipRequests = userState.getNewOwnershipRequests(since);
        if (newOwnershipRequests != null) {
            for (IPLDObject<OwnershipRequest> ownershipRequest : newOwnershipRequests) {
                if (newOwnershipRequestsMap.remove(ownershipRequest.getMultihash()) == null) {
                    throw new ValidationException("Expected ownership request in model state");
                }
            }
        }
        Collection<IPLDObject<UnbanRequest>> newUnbanRequests = userState.getNewUnbanRequests(since);
        if (newUnbanRequests != null) {
            for (IPLDObject<UnbanRequest> unbanRequest : newUnbanRequests) {
                validateUnbanRequest(unbanRequest.getMapped(), previousState);
            }
        }
        Collection<IPLDObject<GrantedOwnership>> newGrantedOwnerships = userState.getNewGrantedOwnerships(since);
        if (newGrantedOwnerships != null) {
            for (IPLDObject<GrantedOwnership> grantedOwnership : newGrantedOwnerships) {
                validateGrantedOwnership(grantedOwnership.getMapped(), modelState);
            }
        }
        Collection<IPLDObject<GrantedUnban>> newGrantedUnbans = userState.getNewGrantedUnbans(since);
        if (newGrantedUnbans != null) {
            for (IPLDObject<GrantedUnban> grantedUnban : newGrantedUnbans) {
                validateGrantedUnban(grantedUnban.getMapped(), userState, modelState);
            }
        }
    }

    /*
     * verify signature, if previousStates (all the way to common - or request.userState if not strict) contain toggled
     * settlement request, check if payload has been increased; check if document was eligible for settlement at
     * request.userState and there is no conflicting settlement request (w.r.t. timestamp) -> SettlementController!!!
     */
    private void validateSettlementRequest(SettlementRequest request, UserState previousState) {

    }

    /*
     * verify signature, if previousStates (all the way to common - or request.userState if not strict) contain toggled
     * unban request, check if payload has been increased; check if request.userState was banned
     */
    private void validateUnbanRequest(UnbanRequest request, UserState previousState) {

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

}
