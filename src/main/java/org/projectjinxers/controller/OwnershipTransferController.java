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

import org.ethereum.crypto.ECKey.ECDSASignature;
import org.projectjinxers.account.Signer;
import org.projectjinxers.model.Document;
import org.projectjinxers.model.LoaderFactory;
import org.projectjinxers.model.ModelState;
import org.projectjinxers.model.OwnershipRequest;
import org.projectjinxers.model.Review;
import org.projectjinxers.model.UserState;
import org.projectjinxers.model.Voting;

/**
 * Handles ownership transfer requests.
 * 
 * @author ProjectJinxers
 */
public class OwnershipTransferController {

    private static final int REQUIRED_RATING = 40;

    private final String documentHash;
    private final String userHash;
    private final IPLDObject<ModelState> modelState;
    private final IPLDContext context;
    private final ECDSASignature signature;

    private IPLDObject<OwnershipRequest> ownershipRequest;
    private IPLDObject<Voting> voting;
    private IPLDObject<UserState> previousOwner;
    private IPLDObject<UserState> newOwner;
    private IPLDObject<Document> document;

    /**
     * Constructor.
     * 
     * @param documentHash the document hash
     * @param userHash     the user hash
     * @param modelState   the current model state
     * @param context      the cntext
     * @param signature    the signature
     */
    OwnershipTransferController(String documentHash, String userHash, IPLDObject<ModelState> modelState,
            IPLDContext context, ECDSASignature signature) {
        this.documentHash = documentHash;
        this.userHash = userHash;
        this.modelState = modelState;
        this.context = context;
        this.signature = signature;
    }

    /**
     * Processes the data, that has been passed to the constructor. If this method returns true, the prepared data can
     * be accessed by calling the getters.
     * 
     * @return true iff the request is valid
     */
    boolean process() {
        IPLDObject<UserState> userState = modelState.getMapped().getUserState(userHash);
        if (userState != null) {
            byte[] hashBase = (userHash + "." + documentHash).getBytes(StandardCharsets.UTF_8);
            if (userState.getMapped().getUser().getMapped().verifySignature(signature, hashBase, Signer.VERIFIER)) {
                IPLDObject<Document> document = new IPLDObject<>(documentHash, LoaderFactory.DOCUMENT.createLoader(),
                        context, null);
                Document loaded = document.getMapped();
                if (loaded != null) {
                    if (userHash.equals(loaded.getFirstVersion().expectUserState().getUser().getMultihash())) { // OP
                        return prepareTransfer(userState, document, true);
                    }
                    else {
                        UserState unwrapped = userState.getMapped();
                        if (unwrapped.getRating() >= REQUIRED_RATING) {
                            if (loaded instanceof Review) {
                                Review review = (Review) loaded;
                                Boolean approve = review.getApprove();
                                if (approve != null) {
                                    if (approve) {
                                        // check if requesting user is the OP or find a non-negative review by them

                                    }
                                    else {
                                        // find a non-positive review by the requesting user
                                    }
                                }
                            }
                            else {
                                // find a non-negative review by requesting user
                            }
                        }
                    }
                }
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
        // check inactivity, single request or voting
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
