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
package org.projectjinxers.data;

import static org.projectjinxers.util.ModelUtility.loadObjects;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import org.projectjinxers.account.Signer;
import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.controller.ModelController;
import org.projectjinxers.model.IPLDSerializable;
import org.projectjinxers.model.ModelState;
import org.projectjinxers.model.UserState;
import org.projectjinxers.model.Votable;
import org.projectjinxers.model.Voting;
import org.projectjinxers.util.ModelUtility.CompletionHandler;

/**
 * @author ProjectJinxers
 *
 */
public class OwnershipRequest extends Document {

    private IPLDObject<org.projectjinxers.model.OwnershipRequest>[] requestObjects;
    private String userHash;
    private boolean anonymousVotingRequested;

    private CompletionHandler ownershipRequestsCompletionHandler;
    private boolean loadingRequests;
    private String requestsFailureMessage;

    private IPLDObject<Voting> voting;
    private CompletionHandler votingCompletionHandler;
    private boolean loadingVoting;
    private String votingFailureMessage;

    public OwnershipRequest(Group group, String documentHash,
            IPLDObject<org.projectjinxers.model.OwnershipRequest>[] requestObjects) {
        super(group, documentHash);
        this.requestObjects = requestObjects;
    }

    public OwnershipRequest(Group group, IPLDObject<org.projectjinxers.model.Document> document, User user,
            boolean anynmousVoting) {
        super(group, user, document, null, null);
        this.userHash = user.getMultihash();
        this.anonymousVotingRequested = anynmousVoting;
    }

    @Override
    public IPLDObject<org.projectjinxers.model.Document> getDocumentObject() {
        IPLDObject<org.projectjinxers.model.Document> documentObject = super.getDocumentObject();
        if (documentObject == null && requestObjects != null) {
            for (IPLDObject<org.projectjinxers.model.OwnershipRequest> request : requestObjects) {
                if (request.isMapped()) {
                    return request.getMapped().getDocument();
                }
            }
        }
        return null;
    }

    public IPLDObject<org.projectjinxers.model.OwnershipRequest>[] getRequestObjects() {
        return requestObjects;
    }

    public boolean isAnonymousVotingRequested() {
        return anonymousVotingRequested;
    }

    public boolean isLoadingRequests() {
        return loadingRequests;
    }

    public String getRequestsFailureMessage() {
        return requestsFailureMessage;
    }

    public boolean isLoadingVoting() {
        return loadingVoting;
    }

    public String getVotingFailureMessage() {
        return votingFailureMessage;
    }

    public IPLDObject<org.projectjinxers.model.OwnershipRequest>[] getOrLoadRequestObjects(
            CompletionHandler completionHandler) {
        this.ownershipRequestsCompletionHandler = completionHandler;
        if (requestObjects != null && !loadingRequests) {
            updateRequestObjects();
        }
        return requestObjects;
    }

    public IPLDObject<Voting> getOrLoadVoting(CompletionHandler completionHandler) {
        this.votingCompletionHandler = completionHandler;
        if ((voting == null || !voting.isMapped()) && !loadingVoting && requestObjects != null) {
            if (voting == null) {
                voting = getGroup().getController().getCurrentValidatedState().getMapped()
                        .getVotingForOwnershipTransfer(getMultihash());
            }
            if (voting != null) {
                updateVoting();
            }
        }
        return voting;
    }

    @Override
    public boolean save(ModelController controller, Signer signer) {
        startOperation(() -> save(controller, signer));
        new Thread(() -> {
            try {
                controller.issueOwnershipRequest(getMultihash(), userHash, anonymousVotingRequested, signer);
            }
            catch (IOException e) {

            }
        }).start();
        return true;
    }

    @Override
    public void groupUpdated(Group group, IPLDObject<ModelState> valid) {
        if (group == getGroup()) {
            super.groupUpdated(group, valid);
            if (requestObjects != null) {
                String multihash = getMultihash();
                if (multihash != null) {
                    requestObjects = valid.getMapped().expectOwnershipRequests(multihash);
                }
                updateRequestObjects();
                voting = valid.getMapped().getVotingForOwnershipTransfer(multihash);
                if (voting != null) {
                    updateVoting();
                }
            }
        }
    }

    private void updateRequestObjects() {
        loadRequests(false, 0);
    }

    private void loadRequests(boolean completeFailure, int attemptCountAfterLastSuccess) {
        if (completeFailure && attemptCountAfterLastSuccess == 3) {
            loadingRequests = false;
            requestsFailureMessage = "Loading requests canceled after 3 complete failures.";
            if (ownershipRequestsCompletionHandler != null) {
                ownershipRequestsCompletionHandler.completed(0);
            }
        }
        else {
            Collection<IPLDObject<? extends IPLDSerializable>> toLoad = new ArrayList<>();
            boolean anonymousVoting = false;
            for (IPLDObject<org.projectjinxers.model.OwnershipRequest> request : requestObjects) {
                if (request.isMapped()) {
                    org.projectjinxers.model.OwnershipRequest req = request.getMapped();
                    if (req.isAnonymousVoting()) {
                        anonymousVoting = true;
                    }
                    IPLDObject<UserState> userState = req.getUserState();
                    if (userState.isMapped()) {
                        IPLDObject<org.projectjinxers.model.User> user = userState.getMapped().getUser();
                        if (!user.isMapped()) {
                            toLoad.add(user);
                        }
                    }
                    else {
                        toLoad.add(userState);
                    }
                }
                else {
                    toLoad.add(request);
                }
            }
            this.anonymousVotingRequested = anonymousVoting;
            if (toLoad.size() > 0) {
                this.loadingRequests = true;
                loadObjects(toLoad, (successCount) -> {
                    if (ownershipRequestsCompletionHandler != null) {
                        ownershipRequestsCompletionHandler.completed(successCount);
                    }
                    loadRequests(successCount == 0, successCount == 0 ? attemptCountAfterLastSuccess + 1 : 0);
                });
            }
            else {
                this.loadingRequests = false;
                if (ownershipRequestsCompletionHandler != null) {
                    ownershipRequestsCompletionHandler.completed(0);
                }
            }
        }
    }

    private void updateVoting() {
        loadVoting(false, 0);
    }

    private void loadVoting(boolean completeFailure, int attemptCountAfterLastSuccess) {
        if (completeFailure && attemptCountAfterLastSuccess == 3) {
            loadingVoting = false;
            votingFailureMessage = "Loading voting canceled after 3 unsuccessful attempts.";
            if (votingCompletionHandler != null) {
                votingCompletionHandler.completed(0);
            }
        }
        else {
            Collection<IPLDObject<? extends IPLDSerializable>> toLoad = new ArrayList<>();
            if (voting.isMapped()) {
                IPLDObject<Votable> subject = voting.getMapped().getSubject();
                if (!subject.isMapped()) {
                    toLoad.add(subject);
                }
            }
            else {
                toLoad.add(voting);
            }
            if (toLoad.size() > 0) {
                loadObjects(toLoad, (successCount) -> {
                    if (votingCompletionHandler != null) {
                        votingCompletionHandler.completed(successCount);
                    }
                    loadVoting(successCount == 0, successCount == 0 ? attemptCountAfterLastSuccess + 1 : 0);
                });
            }
            else {
                this.loadingVoting = false;
                if (votingCompletionHandler != null) {
                    votingCompletionHandler.completed(0);
                }
            }
        }
    }

}
