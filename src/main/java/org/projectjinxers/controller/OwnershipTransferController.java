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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import org.ethereum.crypto.ECKey.ECDSASignature;
import org.projectjinxers.account.Signer;
import org.projectjinxers.model.Document;
import org.projectjinxers.model.GrantedOwnership;
import org.projectjinxers.model.LoaderFactory;
import org.projectjinxers.model.ModelState;
import org.projectjinxers.model.OwnershipRequest;
import org.projectjinxers.model.OwnershipSelection;
import org.projectjinxers.model.Review;
import org.projectjinxers.model.UserState;
import org.projectjinxers.model.Votable;
import org.projectjinxers.model.Voting;
import org.projectjinxers.util.ModelUtility;

/**
 * Handles ownership transfer requests.
 * 
 * @author ProjectJinxers
 */
public class OwnershipTransferController {

    private static final int REQUIRED_RATING = 40;
    public static final long REQUIRED_INACTIVITY = 1000L * 60 * 60 * 24 * 30;
    private static final long MIN_REQUEST_PHASE_DURATION = 1000L * 60 * 60 * 24 * 10;

    private static final String PUBSUB_MESSAGE_OWNERSHIP_REQUEST_MAIN_SEPARATOR = "|";
    private static final String PUBSUB_MESSAGE_OWNERSHIP_REQUEST_REQUEST_SEPARATOR = ".";
    static final String PUBSUB_MESSAGE_OWNERSHIP_REQUEST_MAIN_SEPARATOR_REGEX = "\\|";
    static final String PUBSUB_MESSAGE_OWNERSHIP_REQUEST_REQUEST_SEPARATOR_REGEX = "\\.";
    static final String OWNERSHIP_VOTING_ANONYMOUS = "-";
    private static final String OWNERSHIP_VOTING_NOT_ANONYMOUS = "+";

    public static String composePubMessageRequest(boolean anonymousVoting, String userHash, String documentHash) {
        return (anonymousVoting ? OWNERSHIP_VOTING_ANONYMOUS : OWNERSHIP_VOTING_NOT_ANONYMOUS)
                + PUBSUB_MESSAGE_OWNERSHIP_REQUEST_REQUEST_SEPARATOR + userHash
                + PUBSUB_MESSAGE_OWNERSHIP_REQUEST_REQUEST_SEPARATOR + documentHash;
    }

    static String composePubMessage(String request, ECDSASignature signature) {
        return request + PUBSUB_MESSAGE_OWNERSHIP_REQUEST_MAIN_SEPARATOR + signature.r
                + PUBSUB_MESSAGE_OWNERSHIP_REQUEST_MAIN_SEPARATOR + signature.s
                + PUBSUB_MESSAGE_OWNERSHIP_REQUEST_MAIN_SEPARATOR + signature.v;
    }

    private final String documentHash;
    private final String userHash;
    private final boolean anonymousVoting;
    private IPLDObject<ModelState> modelStateObject;
    private ModelState modelState;
    private final IPLDContext context;
    private final ECDSASignature signature;
    private final long timestamp;

    private IPLDObject<OwnershipRequest> ownershipRequest;
    private IPLDObject<Voting> voting;
    private IPLDObject<UserState> previousOwner;
    private IPLDObject<UserState> newOwner;
    private IPLDObject<Document> document;

    /**
     * Constructor.
     * 
     * @param documentHash    the document hash
     * @param userHash        the user hash
     * @param anonymousVoting indicates whether or not a voting, if necessary, has to be anonymous
     * @param modelState      the current model state
     * @param context         the context
     * @param signature       the signature
     * @param timestamp       the timestamp (including tolerance) when the ownership request has been received
     */
    OwnershipTransferController(String documentHash, String userHash, boolean anonymousVoting,
            IPLDObject<ModelState> modelState, IPLDContext context, ECDSASignature signature, long timestamp) {
        this.documentHash = documentHash;
        this.userHash = userHash;
        this.anonymousVoting = anonymousVoting;
        this.modelStateObject = modelState;
        this.modelState = modelState.getMapped();
        this.context = context;
        this.signature = signature;
        this.timestamp = timestamp;
    }

    public OwnershipTransferController(IPLDObject<OwnershipRequest> ownershipRequest, IPLDObject<ModelState> modelState,
            IPLDContext context) {
        OwnershipRequest req = ownershipRequest.getMapped();
        this.documentHash = req.getDocument().getMultihash();
        this.userHash = req.expectUserHash();
        this.anonymousVoting = req.isAnonymousVoting();
        this.modelStateObject = modelState;
        this.modelState = modelStateObject.getMapped();
        this.context = context;
        this.signature = ownershipRequest.getMetadata().getSignature();
        this.timestamp = req.getTimestamp();
        if (!process() || this.ownershipRequest == null) {
            throw new ValidationException("expected ownership request");
        }
    }

    public OwnershipTransferController(IPLDObject<GrantedOwnership> grantedOwnership, IPLDObject<ModelState> modelState,
            String userHash, IPLDContext context, long timestampTolerance) {
        GrantedOwnership granted = grantedOwnership.getMapped();
        this.documentHash = granted.getDocument().getMultihash();
        this.userHash = userHash;
        IPLDObject<ModelState> previousState = modelState.getMapped().getPreviousVersion();
        IPLDObject<OwnershipRequest> ownershipRequest = previousState.getMapped().expectUserState(userHash).getMapped()
                .getOwnershipRequest(documentHash);
        this.anonymousVoting = ownershipRequest == null ? false : ownershipRequest.getMapped().isAnonymousVoting();
        this.modelStateObject = modelState;
        this.modelState = modelState.getMapped();
        this.context = context;
        this.timestamp = this.modelState.getTimestamp() + timestampTolerance;
        if (!process() || this.document == null || this.previousOwner == null
                || !this.newOwner.getMultihash().equals(userHash)) {
            throw new ValidationException("invalid granted ownership");
        }
        this.signature = null;
    }

    public OwnershipTransferController(OwnershipSelection selection, IPLDObject<ModelState> modelState) {
        this.modelStateObject = modelState;
        this.modelState = modelStateObject.getMapped();
        this.documentHash = selection.getDocument().getMultihash();
        this.anonymousVoting = selection.isAnonymous();
        this.timestamp = selection.getDeadline().getTime() - OwnershipSelection.DURATION;
        if (!prepareVoting(Long.MAX_VALUE, false, null, document)) {
            throw new ValidationException("expected voting");
        }
        this.userHash = null;
        this.context = null;
        this.signature = null;
    }

    /**
     * Processes the data, that has been passed to the constructor. If this method returns true, the prepared data can
     * be accessed by calling the getters.
     * 
     * @return true iff the request is valid
     */
    boolean process() {
        return process(modelState);
    }

    boolean processAgain(IPLDObject<ModelState> validState) {
        ownershipRequest = null;
        voting = null;
        previousOwner = null;
        newOwner = null;
        document = null;
        ModelState valid = validState.getMapped();
        if (process(valid)) {
            this.modelStateObject = validState;
            this.modelState = valid;
            return true;
        }
        return false;
    }

    private boolean process(ModelState modelState) {
        if (modelState.isSealedDocument(documentHash)
                || modelState.getVotingForOwnershipTransfer(documentHash) != null) {
            return false;
        }
        IPLDObject<UserState> userState = modelState.getUserState(userHash);
        if (userState != null) {
            byte[] hashBase = composePubMessageRequest(anonymousVoting, userHash, documentHash)
                    .getBytes(StandardCharsets.UTF_8);
            userState.getMapped().getUser().getMapped().verifySignature(signature, hashBase, Signer.VERIFIER);
            IPLDObject<Document> document = new IPLDObject<>(documentHash, LoaderFactory.DOCUMENT.createLoader(),
                    context, null);
            Document loaded = document.getMapped();
            if (loaded != null && !userHash.equals(loaded.expectUserState().getUser().getMultihash())) {
                if (userHash.equals(loaded.getFirstVersion().expectUserState().getUser().getMultihash())) { // OP
                    if (anonymousVoting) {
                        throw new ValidationException(
                                "OP reclaims never result in votings, not even ownership requests "
                                        + "- for future validation the anonymous voting flag must be false");
                    }
                    return prepareTransfer(userState, document, true);
                }
                return processUserDocument(userState, document);
            }
        }
        return false;
    }

    private boolean processUserDocument(IPLDObject<UserState> userState, IPLDObject<Document> document) {
        UserState unwrapped = userState.getMapped();
        if (unwrapped.getRating() >= REQUIRED_RATING) {
            Document doc = document.getMapped();
            if (doc instanceof Review) {
                Review review = (Review) doc;
                Boolean approve = review.getApprove();
                if (approve != null) {
                    if (approve) {
                        // check if requesting user is the OP or find a non-negative review by them
                        Document firstVersion = review.getDocument().getMapped().getFirstVersion();
                        if (userHash.equals(firstVersion.expectUserState().getUser().getMultihash())) {
                            return true;
                        }
                        return unwrapped.checkNonNegativeReview(documentHash)
                                && prepareTransfer(userState, document, false);
                    }
                    // find a non-positive review by the requesting user
                    return unwrapped.checkNonPositiveReview(documentHash)
                            && prepareTransfer(userState, document, false);
                }
                // why would someone ever want to request ownership of an abandoned neutral review?!?
            }
            else {
                return unwrapped.checkNonNegativeReview(documentHash) && prepareTransfer(userState, document, false);
            }
        }
        return false;
    }

    private boolean prepareTransfer(IPLDObject<UserState> resolvedUser, IPLDObject<Document> resolvedDocument,
            boolean op) {
        if (op) { // OP
            this.document = resolvedDocument;
            this.previousOwner = resolvedDocument.getMapped().getUserState();
            this.newOwner = resolvedUser;
            return true;
        }
        IPLDObject<OwnershipRequest> request = resolvedUser.getMapped().getOwnershipRequest(documentHash);
        if (request == null) {
            Date lastActivityDate = modelState.getLastActivityDate(resolvedDocument);
            if (lastActivityDate != null) {
                long inactivity = timestamp - lastActivityDate.getTime();
                if (inactivity >= REQUIRED_INACTIVITY) {
                    this.ownershipRequest = new IPLDObject<>(
                            new OwnershipRequest(resolvedUser, resolvedDocument, anonymousVoting), signature);
                    return true;
                }
            }
            return false;
        }
        OwnershipRequest or = request.getMapped();
        return prepareVoting(or.getTimestamp(), or.isActive(), resolvedUser, resolvedDocument);
    }

    private boolean prepareVoting(long minTimestamp, boolean active, IPLDObject<UserState> resolvedUser,
            IPLDObject<Document> resolvedDocument) {
        IPLDObject<OwnershipRequest>[] ownershipRequests = modelState.expectOwnershipRequests(documentHash);
        Collection<IPLDObject<OwnershipRequest>> activeRequests = new ArrayList<>();
        boolean anonymous = false;
        for (IPLDObject<OwnershipRequest> or : ownershipRequests) {
            OwnershipRequest req = or.getMapped();
            if (req.isActive()) {
                activeRequests.add(or);
                anonymous |= req.isAnonymousVoting();
                long timestamp = req.getTimestamp();
                if (timestamp < minTimestamp) {
                    minTimestamp = timestamp;
                }
            }
        }
        long duration = timestamp - minTimestamp;
        if (duration >= MIN_REQUEST_PHASE_DURATION) {
            if (activeRequests.size() > 1) {
                this.voting = new IPLDObject<Voting>(new Voting(
                        new IPLDObject<Votable>(
                                new OwnershipSelection(resolvedDocument, activeRequests, anonymous, timestamp)),
                        modelStateObject, ModelUtility.CURRENT_HASH_OBFUSCATION_VERSION));
                return true;
            }
            if (active) {
                this.document = resolvedDocument;
                this.previousOwner = resolvedDocument.getMapped().getUserState();
                this.newOwner = resolvedUser;
                return true;
            }
        }
        return false;
    }

    /**
     * @return the ownership request instance, that has been prepared but not saved
     */
    public IPLDObject<OwnershipRequest> getOwnershipRequest() {
        return ownershipRequest;
    }

    /**
     * @return the voting, that has been prepared but not saved
     */
    public IPLDObject<Voting> getVoting() {
        return voting;
    }

    /**
     * @return the previous owner
     */
    public IPLDObject<UserState> getPreviousOwner() {
        return previousOwner;
    }

    /**
     * @return the new owner
     */
    public IPLDObject<UserState> getNewOwner() {
        return newOwner;
    }

    /**
     * @return the document
     */
    public IPLDObject<Document> getDocument() {
        return document;
    }

    /**
     * @return the signature of the pub message
     */
    public ECDSASignature getSignature() {
        return signature;
    }

}
