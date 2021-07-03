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

import static org.projectjinxers.util.ModelUtility.indexOfNonNullEntry;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;

import org.ethereum.crypto.ECKey.ECDSASignature;
import org.projectjinxers.account.Signer;
import org.projectjinxers.config.Config;
import org.projectjinxers.model.Document;
import org.projectjinxers.model.GrantedOwnership;
import org.projectjinxers.model.ModelState;
import org.projectjinxers.model.OwnershipRequest;
import org.projectjinxers.model.Review;
import org.projectjinxers.model.SealedDocument;
import org.projectjinxers.model.SettlementRequest;
import org.projectjinxers.model.UserState;
import org.projectjinxers.model.Voting;
import org.spongycastle.util.encoders.Base64;

/**
 * Coordinates the model states. The constructor tries to find and initialize the most recent state. It also subscribes
 * to the topics for being able to continuously receive model states and ownership requests from peers.
 * 
 * @author ProjectJinxers
 */
public class ModelController {

    static class PendingSubMessage {

        final String message;
        final long timestamp;

        PendingSubMessage(String message, long timestamp) {
            this.message = message;
            this.timestamp = timestamp;
        }
    }

    private static final String PUBSUB_SUB_KEY_DATA = "data";
    private static final String PUBSUB_TOPIC_PREFIX_OWNERSHIP_REQUEST = "or";

    private final IPFSAccess access;
    private final IPLDContext context;
    private long timestampTolerance;

    private final String mainIOTAAddress;
    private IPLDObject<ModelState> currentValidatedState;
    private Map<String, SettlementController> currentLocalHashes = new HashMap<>();

    private ValidationContext currentValidationContext;
    private SettlementController currentSnapshot;

    private boolean validatingModelState;
    private Deque<PendingSubMessage> pendingModelStates;
    private Deque<PendingSubMessage> pendingOwnershipRequests;

    private Map<String, IPLDObject<UserState>> pendingUserStates;
    private Map<String, UserState> appliedSettlementData;
    private Map<String, String[]> pendingNewReviewTableEntries;
    private Map<String, Queue<IPLDObject<Document>>> queuedDocuments;
    private Map<String, Queue<IPLDObject<OwnershipRequest>>> queuedOwnershipRequests;
    private Map<String, Queue<IPLDObject<GrantedOwnership>>> queuedGrantedOwnerships;
    private Map<String, Queue<String>> queuedTransferredDocumentHashes;
    private Queue<OwnershipTransferController> queuedOwnershipTransferControllers;
    private Queue<IPLDObject<Voting>> queuedVotings;
    private Queue<IPLDObject<SettlementRequest>> queuedSettlementRequests;
    private boolean abortLocalChanges;

    /**
     * Constructor. If it returns without throwing an exception, the instance is completely initialized and continuously
     * listens for model states and ownership requests from peers.
     * 
     * @param access             the access to the IPFS API
     * @param config             the config (provides initialization and configuration parameters, pass null for
     *                           defaults)
     * @param timestampTolerance the tolerance when validating deadlines and other timestamps in milliseconds (0 or
     *                           negative disables timestamp validation, does not affect validating relative durations)
     * @throws Exception if initialization failed and the application should not continue running.
     */
    public ModelController(IPFSAccess access, Config config, long timestampTolerance) throws Exception {
        this.access = access;
        if (config == null) {
            config = Config.getSharedInstance();
        }
        this.context = new IPLDContext(access, IPLDEncoding.JSON, IPLDEncoding.CBOR, false);
        this.timestampTolerance = timestampTolerance;
        mainIOTAAddress = config.getIOTAMainAddress();
        String currentModelStateHash;
        try {
            currentModelStateHash = access.readModelStateHash(mainIOTAAddress);
            if (currentModelStateHash != null) {
                this.currentValidatedState = loadModelState(currentModelStateHash, false);
            }
        }
        catch (FileNotFoundException e) {
            do {
                currentModelStateHash = readNextModelStateHashFromTangle(mainIOTAAddress);
                if (currentModelStateHash != null) {
                    try {
                        this.currentValidationContext = new ValidationContext(context, null, null,
                                System.currentTimeMillis() + timestampTolerance, 0);
                        this.currentValidatedState = loadModelState(currentModelStateHash, true);
                        access.saveModelStateHash(mainIOTAAddress, currentModelStateHash);
                        break;
                    }
                    catch (Exception e2) {
                        e2.printStackTrace();
                    }
                }
            }
            while (currentModelStateHash != null);
        }
        subscribeToModelStatesTopic();
        subscribeToOwnershipRequestsTopic();
    }

    void subscribeToModelStatesTopic() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    access.subscribe(mainIOTAAddress).forEach(map -> {
                        try {
                            String pubSubData = (String) map.get(PUBSUB_SUB_KEY_DATA);
                            handleIncomingModelState(pubSubData, System.currentTimeMillis());
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                }
                catch (Exception e) {
                    e.printStackTrace();
                    subscribeToModelStatesTopic();
                }
            }
        }).start();
    }

    void subscribeToOwnershipRequestsTopic() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    access.subscribe(PUBSUB_TOPIC_PREFIX_OWNERSHIP_REQUEST + mainIOTAAddress).forEach(map -> {
                        try {
                            String pubSubData = (String) map.get(PUBSUB_SUB_KEY_DATA);
                            handleIncomingOwnershipRequest(pubSubData, System.currentTimeMillis() + timestampTolerance);
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                }
                catch (Exception e) {
                    e.printStackTrace();
                    subscribeToOwnershipRequestsTopic();
                }
            }
        }).start();
    }

    /**
     * @return the context
     */
    public IPLDContext getContext() {
        return context;
    }

    /**
     * @return the current fully validated (or trusted) model state
     */
    public IPLDObject<ModelState> getCurrentValidatedState() {
        return currentValidatedState;
    }

    private String readNextModelStateHashFromTangle(String address) {
        return null;
    }

    private IPLDObject<ModelState> loadModelState(String multihash, boolean validate) {
        ModelState modelState = new ModelState();
        IPLDObject<ModelState> object = new IPLDObject<>(multihash, modelState, context,
                validate ? currentValidationContext : null);
        return object.getMapped() == null ? null : object;
    }

    boolean handleIncomingModelState(String pubSubData, long timestamp) {
        if (validatingModelState) {
            storePotentialModelStateHash(pubSubData, timestamp);
            return false;
        }
        validatingModelState = true;
        try {
            String multihash = convertPubSubDataToOriginal(pubSubData);
            if (currentLocalHashes.containsKey(multihash)) {
                if (currentValidatedState == null || !multihash.equals(currentValidatedState.getMultihash())) {
                    this.currentValidatedState = loadModelState(multihash, false);
                    SettlementController localSettlement = currentLocalHashes.get(multihash);
                    if (localSettlement != null) {
                        this.currentSnapshot = localSettlement;
                    }
                }
            }
            else {
                currentValidationContext = new ValidationContext(context, currentValidatedState,
                        currentLocalHashes.keySet(), timestamp, timestampTolerance);
                IPLDObject<ModelState> loaded = loadModelState(multihash, true);
                mergeWithValidated(loaded);
            }
        }
        catch (Exception e) {
            // ignore
        }
        finally {
            validatingModelState = false;
        }
        processPending();
        return true;
    }

    boolean handleIncomingOwnershipRequest(String pubSubData, long timestamp) {
        if (validatingModelState) {
            storePotentialOwnershipRequestHash(pubSubData, timestamp);
            return false;
        }
        String decoded = convertPubSubDataToOriginal(pubSubData);
        String[] parts = decoded
                .split(OwnershipTransferController.PUBSUB_MESSAGE_OWNERSHIP_REQUEST_MAIN_SEPARATOR_REGEX);
        String[] requestParts = parts[0]
                .split(OwnershipTransferController.PUBSUB_MESSAGE_OWNERSHIP_REQUEST_REQUEST_SEPARATOR_REGEX);
        BigInteger r = new BigInteger(parts[1]);
        BigInteger s = new BigInteger(parts[2]);
        byte v = Byte.parseByte(parts[3]);
        ECDSASignature signature = new ECDSASignature(r, s);
        signature.v = v;

        IPLDObject<ModelState> currentModelState = currentValidatedState;
        OwnershipTransferController controller = new OwnershipTransferController(requestParts[2], requestParts[1],
                OwnershipTransferController.OWNERSHIP_VOTING_ANONYMOUS.equals(requestParts[0]), currentModelState,
                context, signature, timestamp);
        if (controller.process()) {
            try {
                saveLocalChanges(null, null, controller);
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    private String convertPubSubDataToOriginal(String pubSubData) {
        byte[] bytes = Base64.decode(pubSubData);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void processPending() {
        try {
            executePendingChanges();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        processPendingModelStates();
        processPendingOwnershipRequests();
    }

    /**
     * Saves the document and updates the local state. If successful, the resulting state will be published to other
     * peers for validation. The validated model state is not changed (no new instance) during this call. It will be
     * changed after one of the peers, that validated the published model state, published the new state.
     * 
     * @param document the document to save
     * @param signer   the signer for creating mandatory signatures
     * @throws IOException if writing the properties fails
     */
    public void saveDocument(IPLDObject<Document> document, Signer signer) throws IOException {
        document.save(context, signer);
        saveLocalChanges(document, null, null);
    }

    public void issueSettlementRequest(IPLDObject<SettlementRequest> settlementRequest, Signer signer)
            throws IOException {
        settlementRequest.save(context, signer);
        saveLocalChanges(null, settlementRequest, null);
    }

    /**
     * Composes a new message, which represents the ownership request, and publishes it for the current main IOTA
     * address. The request part of the message is signed and the signature is appended to the message.
     * 
     * @param documentHash the hash of the document the user wants to request ownership for
     * @param userHash     the hash of the user, who wants to request ownership
     * @param signer       the signer (it is recommended to not keep this in memory for ever, instead ask the user for
     *                     their credentials or keep them (not the signer) in a secure keystore and create a new signer
     *                     for this call and discard it right after)
     * @throws IOException if publishing the ownership request fails
     */
    public void issueOwnershipRequest(String documentHash, String userHash, boolean anonymousVoting, Signer signer)
            throws IOException {
        String topic = PUBSUB_TOPIC_PREFIX_OWNERSHIP_REQUEST + mainIOTAAddress;
        String request = OwnershipTransferController.composePubMessageRequest(anonymousVoting, userHash, documentHash);
        byte[] requestBytes = request.getBytes(StandardCharsets.UTF_8);
        ECDSASignature signature = signer.sign(requestBytes);
        try {
            access.publish(topic, OwnershipTransferController.composePubMessage(request, signature));
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private IPLDObject<UserState> getInstanceToSave(IPLDObject<UserState> local) {
        if (pendingUserStates != null) {
            IPLDObject<UserState> res = null;
            String userHash = null;
            synchronized (pendingUserStates) {
                if (pendingUserStates != null) {
                    userHash = local.getMapped().getUser().getMultihash();
                    res = pendingUserStates.remove(userHash);
                }
            }
            if (res != null) {
                UserState applied = null;
                if (appliedSettlementData != null) {
                    synchronized (appliedSettlementData) {
                        applied = appliedSettlementData.remove(userHash);
                    }
                }
                if (applied != null) {
                    res.getMapped().revertSettlement(applied);
                }
                return res;
            }
        }
        return local;
    }

    /*
     * TODO: document removals, unban requests
     */
    private boolean saveLocalChanges(IPLDObject<Document> document, IPLDObject<SettlementRequest> settlementRequest,
            OwnershipTransferController ownershipTransferController) throws IOException {
        IPLDObject<ModelState> currentModelState = currentValidatedState;
        ModelState modelState = currentModelState == null ? new ModelState() : currentModelState.getMapped();
        SettlementController settlementController = currentSnapshot == null ? null
                : currentSnapshot.createPreEvaluationSnapshot(System.currentTimeMillis());
        Queue<IPLDObject<SettlementRequest>> settlementRequests = null;
        if (settlementController != null) {
            boolean settlementChanged = false;
            if (queuedSettlementRequests != null) {
                synchronized (queuedSettlementRequests) {
                    if (queuedSettlementRequests.size() > 0) {
                        settlementRequests = new ArrayDeque<>(queuedSettlementRequests);
                    }
                }
                if (settlementRequests != null) {
                    for (IPLDObject<SettlementRequest> request : settlementRequests) {
                        settlementChanged = settlementController.checkRequest(request.getMapped().getDocument(), false,
                                true) || settlementChanged;
                    }
                }
            }
            if (settlementRequest != null) {
                if (settlementController.checkRequest(settlementRequest.getMapped().getDocument(), false, true)) {
                    settlementChanged = true;
                    if (settlementRequests == null) {
                        settlementRequests = new ArrayDeque<>();
                    }
                    settlementRequests.add(settlementRequest);
                }
            }
            settlementChanged = settlementController.applyNewTimestamp() || settlementChanged;
            if (settlementChanged) {
                settlementController.enterMergeMode();
            }
            else {
                settlementController = null;
            }
        }
        Set<String> userHashes = new LinkedHashSet<>();
        Map<String, Queue<IPLDObject<Document>>> documents = new HashMap<>();
        Map<String, Queue<IPLDObject<OwnershipRequest>>> ownershipRequests = new HashMap<>();
        Map<String, Queue<IPLDObject<GrantedOwnership>>> grantedOwnerships = new HashMap<>();
        Map<String, Queue<String>> transferredDocumentHashes = new HashMap<>();
        Collection<OwnershipTransferController> transferControllers = new ArrayList<>();
        Collection<IPLDObject<Voting>> votings = null;
        Map<String, String[]> newReviewTableEntries = null;
        if (queuedOwnershipTransferControllers != null) {
            synchronized (queuedOwnershipTransferControllers) {
                transferControllers.addAll(queuedOwnershipTransferControllers);
            }
        }
        if (queuedVotings != null) {
            synchronized (queuedVotings) {
                if (queuedVotings.size() > 0) {
                    votings = new ArrayList<>(queuedVotings);
                }
            }
        }
        if (ownershipTransferController != null) {
            transferControllers.add(ownershipTransferController);
        }
        if (transferControllers.size() > 0) {
            for (OwnershipTransferController controller : transferControllers) {
                if (abortLocalChanges) {
                    return false;
                }
                IPLDObject<OwnershipRequest> ownershipRequest = controller.getOwnershipRequest();
                if (ownershipRequest == null) {
                    IPLDObject<Document> transferredDocument = controller.getDocument();
                    if (transferredDocument == null) {
                        IPLDObject<Voting> voting = controller.getVoting();
                        try {
                            voting.save(context, null);
                        }
                        catch (Exception e) {
                            handleOwnershipTransferControllerException(e, ownershipTransferController, document);
                            return false;
                        }
                        if (votings == null) {
                            votings = new ArrayList<>();
                        }
                        votings.add(voting);
                    }
                    else {
                        Document transferredDoc = transferredDocument.getMapped();
                        IPLDObject<UserState> previousOwner = controller.getPreviousOwner();
                        String key = previousOwner.getMapped().getUser().getMultihash();
                        Queue<String> transferredDocHashes = transferredDocumentHashes.get(key);
                        if (transferredDocHashes == null) {
                            transferredDocHashes = new ArrayDeque<>();
                            transferredDocumentHashes.put(key, transferredDocHashes);
                        }
                        String firstVersionHash = transferredDocument.getMapped().getFirstVersionHash();
                        if (firstVersionHash == null) {
                            firstVersionHash = transferredDocument.getMultihash();
                        }
                        transferredDocHashes.add(firstVersionHash);
                        userHashes.add(key);
                        IPLDObject<UserState> newOwner = controller.getNewOwner();
                        IPLDObject<Document> transferred = transferredDoc.transferTo(newOwner, transferredDocument,
                                controller.getSignature());
                        try {
                            String transferredHash = transferred.save(context, null);
                            String[] reviewTableEntries = modelState
                                    .getReviewTableEntries(transferredDocument.getMultihash());
                            if (reviewTableEntries != null) {
                                if (newReviewTableEntries == null) {
                                    newReviewTableEntries = new LinkedHashMap<>();
                                }
                                newReviewTableEntries.put(transferredHash, reviewTableEntries);
                            }
                            key = newOwner.getMapped().getUser().getMultihash();
                            Queue<IPLDObject<Document>> docs = documents.get(key);
                            if (docs == null) {
                                docs = new ArrayDeque<>();
                                documents.put(key, docs);
                            }
                            docs.add(transferred);
                            if (settlementController != null) {
                                settlementController.checkDocument(transferredDocument, true, null, null);
                            }
                            userHashes.add(key);
                        }
                        catch (Exception e) {
                            handleOwnershipTransferControllerException(e, ownershipTransferController, document);
                            return false;
                        }
                        Queue<IPLDObject<GrantedOwnership>> granted = grantedOwnerships.get(key);
                        if (granted == null) {
                            granted = new ArrayDeque<>();
                            grantedOwnerships.put(key, granted);
                        }
                        granted.add(new IPLDObject<>(new GrantedOwnership(transferred, currentModelState)));
                    }
                }
                else {
                    String key = ownershipRequest.getMapped().expectUserHash();
                    try {
                        ownershipRequest.save(context, null);
                        Queue<IPLDObject<OwnershipRequest>> ownershipReqs = ownershipRequests.get(key);
                        if (ownershipReqs == null) {
                            ownershipReqs = new ArrayDeque<>();
                            ownershipRequests.put(key, ownershipReqs);
                        }
                        ownershipReqs.add(ownershipRequest);
                        userHashes.add(key);
                    }
                    catch (Exception e) {
                        handleOwnershipTransferControllerException(e, ownershipTransferController, document);
                        return false;
                    }
                }
            }
        }
        if (queuedOwnershipTransferControllers != null) {
            synchronized (queuedOwnershipTransferControllers) {
                queuedOwnershipTransferControllers.clear();
            }
        }
        if (queuedDocuments != null) {
            synchronized (queuedDocuments) {
                for (Entry<String, Queue<IPLDObject<Document>>> entry : queuedDocuments.entrySet()) {
                    String key = entry.getKey();
                    userHashes.add(key);
                    Queue<IPLDObject<Document>> docs = documents.get(key);
                    if (docs == null) {
                        docs = new ArrayDeque<>();
                        documents.put(key, docs);
                    }
                    if (settlementController == null) {
                        docs.addAll(entry.getValue());
                    }
                    else {
                        for (IPLDObject<Document> doc : entry.getValue()) {
                            docs.add(doc);
                            settlementController.checkDocument(doc, true, null, null);
                        }
                    }
                }
            }
        }
        if (settlementController != null && queuedSettlementRequests != null) {
            synchronized (queuedSettlementRequests) {
                queuedSettlementRequests.clear();
            }
        }
        if (queuedOwnershipRequests != null) {
            synchronized (queuedOwnershipRequests) {
                for (Entry<String, Queue<IPLDObject<OwnershipRequest>>> entry : queuedOwnershipRequests.entrySet()) {
                    String key = entry.getKey();
                    userHashes.add(key);
                    Queue<IPLDObject<OwnershipRequest>> reqs = ownershipRequests.get(key);
                    if (reqs == null) {
                        reqs = new ArrayDeque<>();
                        ownershipRequests.put(key, reqs);
                    }
                    reqs.addAll(entry.getValue());
                }
            }
        }
        if (queuedGrantedOwnerships != null) {
            synchronized (queuedGrantedOwnerships) {
                for (Entry<String, Queue<IPLDObject<GrantedOwnership>>> entry : queuedGrantedOwnerships.entrySet()) {
                    String key = entry.getKey();
                    userHashes.add(key);
                    Queue<IPLDObject<GrantedOwnership>> granted = grantedOwnerships.get(key);
                    if (granted == null) {
                        granted = new ArrayDeque<>();
                        grantedOwnerships.put(key, granted);
                    }
                    granted.addAll(entry.getValue());
                }
            }
        }
        if (queuedTransferredDocumentHashes != null) {
            synchronized (queuedTransferredDocumentHashes) {
                for (Entry<String, Queue<String>> entry : queuedTransferredDocumentHashes.entrySet()) {
                    String key = entry.getKey();
                    userHashes.add(key);
                    Queue<String> hashes = transferredDocumentHashes.get(key);
                    if (hashes == null) {
                        hashes = new ArrayDeque<>();
                        transferredDocumentHashes.put(key, hashes);
                    }
                    hashes.addAll(entry.getValue());
                }
            }
        }
        if (document != null) {
            String userHash = document.getMapped().expectUserState().getUser().getMultihash();
            userHashes.add(userHash);
            Queue<IPLDObject<Document>> queue = documents.get(userHash);
            if (queue == null) {
                queue = new ArrayDeque<>();
                documents.put(userHash, queue);
            }
            queue.add(document);
            if (settlementController != null) {
                settlementController.checkDocument(document, true, null, null);
            }
        }
        if (queuedVotings != null) {
            synchronized (queuedVotings) {
                queuedVotings.clear();
            }
        }
        Map<String, IPLDObject<UserState>> updatedUserStates = new LinkedHashMap<>();
        if (pendingUserStates != null) {
            synchronized (pendingUserStates) {
                updatedUserStates.putAll(pendingUserStates);
            }
        }
        if (pendingNewReviewTableEntries != null) {
            synchronized (pendingNewReviewTableEntries) {
                if (newReviewTableEntries == null) {
                    newReviewTableEntries = new LinkedHashMap<>(pendingNewReviewTableEntries);
                }
                else { // preserve order
                    Map<String, String[]> tmp = newReviewTableEntries;
                    newReviewTableEntries = new LinkedHashMap<>(pendingNewReviewTableEntries);
                    newReviewTableEntries.putAll(tmp);
                }
            }
        }
        Map<String, UserState> settlementStates;
        Collection<IPLDObject<SealedDocument>> sealedDocuments;
        if (settlementController == null) {
            settlementStates = null;
            sealedDocuments = null;
        }
        else {
            Map<String, SealedDocument> sealedDocs = new HashMap<>();
            if (settlementController.evaluate(sealedDocs, modelState)) {
                settlementStates = new HashMap<>();
                settlementController.update(settlementStates, modelState, sealedDocs);
                sealedDocuments = new ArrayList<>();
                for (SealedDocument doc : sealedDocs.values()) {
                    sealedDocuments.add(new IPLDObject<>(doc));
                }
                userHashes.addAll(settlementStates.keySet());
            }
            else {
                settlementStates = null;
                sealedDocuments = null;
            }
        }
        String ownerHash;
        if (settlementRequests == null) {
            ownerHash = null;
        }
        else {
            ownerHash = settlementRequests.peek().getMapped().getUserState().getMapped().getUser().getMultihash();
            userHashes.add(ownerHash);
        }
        for (String userHash : userHashes) {
            IPLDObject<UserState> userState = modelState.getUserState(userHash);
            if (userState == null && document != null) {
                userState = document.getMapped().getUserState();
            }
            Queue<IPLDObject<Document>> docs = documents.get(userHash);
            if (queuedDocuments != null) {
                synchronized (queuedDocuments) {
                    queuedDocuments.remove(userHash);
                }
            }
            Queue<IPLDObject<OwnershipRequest>> oreqs = ownershipRequests.get(userHash);
            if (queuedOwnershipRequests != null) {
                synchronized (queuedOwnershipRequests) {
                    queuedOwnershipRequests.remove(userHash);
                }
            }
            Queue<IPLDObject<GrantedOwnership>> granted = grantedOwnerships.get(userHash);
            if (queuedGrantedOwnerships != null) {
                synchronized (queuedGrantedOwnerships) {
                    queuedGrantedOwnerships.remove(userHash);
                }
            }
            Queue<String> hashes = transferredDocumentHashes.get(userHash);
            if (queuedTransferredDocumentHashes != null) {
                synchronized (queuedTransferredDocumentHashes) {
                    queuedTransferredDocumentHashes.remove(userHash);
                }
            }
            Queue<IPLDObject<SettlementRequest>> sreqs = settlementRequests != null && userHash.equals(ownerHash)
                    ? settlementRequests
                    : null;
            UserState settlementValues = settlementStates == null ? null : settlementStates.get(userHash);
            if (abortLocalChanges) {
                requeue(userHash, null, docs, settlementRequests, oreqs, granted, hashes, votings);
                handleUserStateUnsaved(updatedUserStates, settlementStates, document, newReviewTableEntries);
                return false;
            }
            IPLDObject<UserState> toSave = getInstanceToSave(userState);
            if (docs != null || sreqs != null || oreqs != null || granted != null || hashes != null
                    || settlementValues != null) {
                try {
                    UserState updated = toSave.getMapped().updateLinks(docs, sreqs, oreqs, granted, hashes,
                            settlementValues, toSave);
                    IPLDObject<UserState> updatedObject = new IPLDObject<>(updated);
                    updatedObject.save(context, null);
                    updatedUserStates.put(userHash, updatedObject);
                }
                catch (Exception e) {
                    e.printStackTrace();
                    requeue(userHash, toSave == userState ? null : toSave, docs, settlementRequests, oreqs, granted,
                            hashes, votings);
                    handleUserStateUnsaved(updatedUserStates, settlementStates, document, newReviewTableEntries);
                    return false;
                }
                if (docs != null && docs.size() > 0) {
                    for (IPLDObject<Document> doc : docs) {
                        newReviewTableEntries = updateNewReviewTableEntries(doc, newReviewTableEntries);
                    }
                }
            }
        }
        if (abortLocalChanges) {
            handleLocalModelStateUnsaved(updatedUserStates, votings, settlementRequests, settlementStates,
                    newReviewTableEntries);
            return false;
        }
        long timestamp = settlementController == null ? System.currentTimeMillis()
                : settlementController.getTimestamp();
        if (updatedUserStates.isEmpty()) {
            if (votings == null) {
                return false;
            }
            modelState = modelState.updateUserState(null, null, null, votings, null, newReviewTableEntries,
                    currentModelState, timestamp);
        }
        else {
            boolean first = true;
            for (Entry<String, IPLDObject<UserState>> entry : updatedUserStates.entrySet()) {
                String key = entry.getKey();
                if (first) {
                    modelState = modelState.updateUserState(entry.getValue(),
                            key.equals(ownerHash) ? settlementRequests : null, ownershipRequests.get(key), votings,
                            sealedDocuments, newReviewTableEntries, currentModelState, timestamp);
                    first = false;
                }
                else {
                    modelState = modelState.updateUserState(entry.getValue(),
                            key.equals(ownerHash) ? settlementRequests : null, ownershipRequests.get(key), null, null,
                            null, null, timestamp);
                }
            }
        }
        IPLDObject<ModelState> newLocalState = new IPLDObject<ModelState>(modelState);
        if (abortLocalChanges) {
            handleLocalModelStateUnsaved(updatedUserStates, votings, settlementRequests, settlementStates,
                    newReviewTableEntries);
            return false;
        }
        try {
            currentLocalHashes.put(newLocalState.save(context, null), settlementController);
        }
        catch (Exception e) {
            e.printStackTrace();
            handleLocalModelStateUnsaved(updatedUserStates, votings, settlementRequests, settlementStates,
                    newReviewTableEntries);
            return false;
        }
        publishLocalState(newLocalState);
        return true;
    }

    private void handleOwnershipTransferControllerException(Exception e, OwnershipTransferController controller,
            IPLDObject<Document> document) {
        e.printStackTrace();
        if (controller != null) {
            enqueueOwnershipTransferController(controller);
        }
        if (document != null) {
            enqueueDocument(document);
        }
    }

    private Map<String, String[]> updateNewReviewTableEntries(IPLDObject<Document> document,
            Map<String, String[]> reviewTableEntries) {
        Map<String, String[]> res = reviewTableEntries;
        Document doc = document.getMapped();
        if (doc instanceof Review) {
            String reviewHash = document.getMultihash();
            String documentHash = ((Review) doc).getDocument().getMultihash();
            String[] currentEntries;
            if (res == null) {
                res = new LinkedHashMap<>();
                currentEntries = null;
            }
            else {
                currentEntries = res.get(documentHash);
            }
            if (currentEntries == null) {
                res.put(documentHash, new String[] { reviewHash });
            }
            else {
                String previousVersionHash = doc.getPreviousVersionHash();
                int index = previousVersionHash == null ? -1 : indexOfNonNullEntry(currentEntries, previousVersionHash);
                String[] copy;
                if (index < 0) {
                    copy = new String[currentEntries.length + 1];
                    System.arraycopy(currentEntries, 0, copy, 0, currentEntries.length);
                    copy[currentEntries.length] = reviewHash;
                }
                else { // we still copy (since pendingNewReviewTableEntries copy is/was shallow)
                    copy = new String[currentEntries.length];
                    System.arraycopy(currentEntries, 0, copy, 0, currentEntries.length);
                    copy[index] = reviewHash;
                }
                res.put(documentHash, copy);
            }
        }
        return res;
    }

    private void handleUserStateUnsaved(Map<String, IPLDObject<UserState>> updatedUserStates,
            Map<String, UserState> settlementStates, IPLDObject<Document> document,
            Map<String, String[]> newReviewTableEntries) {
        if (updatedUserStates.size() > 0) {
            if (pendingUserStates == null) {
                pendingUserStates = new LinkedHashMap<>();
            }
            synchronized (pendingUserStates) {
                pendingUserStates.putAll(updatedUserStates);
            }
            updateAppliedSettlementData(settlementStates, updatedUserStates);
        }
        this.pendingNewReviewTableEntries = newReviewTableEntries;
        // at this point everything but the document parameter has been handled if its owner has not been
        // processed
        if (document != null) {
            if (pendingUserStates == null || !pendingUserStates
                    .containsKey(document.getMapped().expectUserState().getUser().getMultihash())) {
                enqueueDocument(document);
            }
        }
    }

    private void updateAppliedSettlementData(Map<String, UserState> settlementStates,
            Map<String, IPLDObject<UserState>> updatedUserStates) {
        if (settlementStates != null) {
            if (appliedSettlementData == null) {
                appliedSettlementData = new HashMap<>();
            }
            synchronized (appliedSettlementData) {
                for (Entry<String, IPLDObject<UserState>> entry : updatedUserStates.entrySet()) {
                    String key = entry.getKey();
                    UserState update = settlementStates.get(key);
                    if (update != null) {
                        UserState applied = appliedSettlementData.get(key);
                        if (applied == null) {
                            appliedSettlementData.put(key, update);
                        }
                        else {
                            applied.applySettlement(update);
                        }
                    }
                }
            }
        }
    }

    private void handleLocalModelStateUnsaved(Map<String, IPLDObject<UserState>> updatedUserStates,
            Collection<IPLDObject<Voting>> votings, Collection<IPLDObject<SettlementRequest>> settlementRequests,
            Map<String, UserState> settlementStates, Map<String, String[]> newReviewTableEntries) {
        if (updatedUserStates.size() > 0) {
            if (pendingUserStates == null) {
                pendingUserStates = new LinkedHashMap<>();
            }
            synchronized (pendingUserStates) {
                pendingUserStates.putAll(updatedUserStates);
            }
        }
        if (votings != null) {
            if (queuedVotings == null) {
                queuedVotings = new ArrayDeque<>();
            }
            queuedVotings.addAll(votings);
        }
        if (settlementRequests != null) {
            if (queuedSettlementRequests == null) {
                queuedSettlementRequests = new ArrayDeque<>();
            }
            queuedSettlementRequests.addAll(settlementRequests);
        }
        updateAppliedSettlementData(settlementStates, updatedUserStates);
        this.pendingNewReviewTableEntries = newReviewTableEntries;
    }

    private void storePotentialModelStateHash(String pubSubData, long timestamp) {
        if (pendingModelStates == null) {
            pendingModelStates = new ArrayDeque<>();
        }
        synchronized (pendingModelStates) {
            pendingModelStates.push(new PendingSubMessage(pubSubData, timestamp));
        }
    }

    private void storePotentialOwnershipRequestHash(String pubSubData, long timestamp) {
        if (pendingOwnershipRequests == null) {
            pendingOwnershipRequests = new ArrayDeque<>();
        }
        synchronized (pendingOwnershipRequests) {
            pendingOwnershipRequests.push(new PendingSubMessage(pubSubData, timestamp));
        }
    }

    private void enqueueDocument(IPLDObject<Document> document) {
        if (queuedDocuments == null) {
            queuedDocuments = new HashMap<>();
        }
        String key = document.getMapped().expectUserState().getUser().getMultihash();
        synchronized (queuedDocuments) {
            Queue<IPLDObject<Document>> queue = queuedDocuments.get(key);
            if (queue == null) {
                queue = new ArrayDeque<>();
                queuedDocuments.put(key, queue);
            }
            queue.add(document);
        }
    }

    private void enqueueOwnershipTransferController(OwnershipTransferController controller) {
        if (queuedOwnershipTransferControllers == null) {
            queuedOwnershipTransferControllers = new ArrayDeque<>();
        }
        synchronized (queuedOwnershipTransferControllers) {
            queuedOwnershipTransferControllers.add(controller);
        }
    }

    private void requeue(String userHash, IPLDObject<UserState> pending, Queue<IPLDObject<Document>> documents,
            Queue<IPLDObject<SettlementRequest>> sreqs, Queue<IPLDObject<OwnershipRequest>> oreqs,
            Queue<IPLDObject<GrantedOwnership>> granted, Queue<String> transferredOwnershipHashes,
            Collection<IPLDObject<Voting>> votings) {
        boolean queueDocuments = documents != null;
        if (queueDocuments) {
            if (queuedDocuments == null) {
                queuedDocuments = new HashMap<>();
            }
            synchronized (queuedDocuments) {
                Queue<IPLDObject<Document>> queue = queuedDocuments.get(userHash);
                if (queue == null) {
                    queuedDocuments.put(userHash, documents);
                }
                else {
                    queue.addAll(documents);
                }
            }
        }
        if (sreqs != null) {
            if (queuedSettlementRequests == null) {
                queuedSettlementRequests = new ArrayDeque<>();
            }
            synchronized (queuedSettlementRequests) {
                queuedSettlementRequests.addAll(sreqs);
            }
        }
        if (oreqs != null) {
            if (queuedOwnershipRequests == null) {
                queuedOwnershipRequests = new HashMap<>();
            }
            synchronized (queuedOwnershipRequests) {
                Queue<IPLDObject<OwnershipRequest>> queue = queuedOwnershipRequests.get(userHash);
                if (queue == null) {
                    queuedOwnershipRequests.put(userHash, oreqs);
                }
                else {
                    queue.addAll(oreqs);
                }
            }
        }
        if (granted != null) {
            if (queuedGrantedOwnerships == null) {
                queuedGrantedOwnerships = new HashMap<>();
            }
            synchronized (queuedGrantedOwnerships) {
                Queue<IPLDObject<GrantedOwnership>> queue = queuedGrantedOwnerships.get(userHash);
                if (queue == null) {
                    queuedGrantedOwnerships.put(userHash, granted);
                }
                else {
                    queue.addAll(granted);
                }
            }
        }
        if (transferredOwnershipHashes != null) {
            if (queuedTransferredDocumentHashes == null) {
                queuedTransferredDocumentHashes = new HashMap<>();
            }
            synchronized (queuedTransferredDocumentHashes) {
                Queue<String> queue = queuedTransferredDocumentHashes.get(userHash);
                if (queue == null) {
                    queuedTransferredDocumentHashes.put(userHash, transferredOwnershipHashes);
                }
                else {
                    queue.addAll(transferredOwnershipHashes);
                }
            }
        }
        if (votings != null) {
            if (queuedVotings == null) {
                queuedVotings = new ArrayDeque<>();
            }
            synchronized (queuedVotings) {
                queuedVotings.addAll(votings);
            }
        }
        if (pending != null) {
            synchronized (pendingUserStates) {
                pendingUserStates.put(userHash, pending);
            }
        }
    }

    private boolean executePendingChanges() throws IOException {
        if (pendingUserStates != null && pendingUserStates.size() > 0
                || queuedDocuments != null && queuedDocuments.size() > 0
                || queuedOwnershipRequests != null && queuedOwnershipRequests.size() > 0
                || queuedGrantedOwnerships != null && queuedGrantedOwnerships.size() > 0
                || queuedTransferredDocumentHashes != null && queuedTransferredDocumentHashes.size() > 0
                || queuedOwnershipTransferControllers != null && queuedOwnershipTransferControllers.size() > 0
                || queuedVotings != null && queuedVotings.size() > 0
                || queuedSettlementRequests != null && queuedSettlementRequests.size() > 0) {
            return saveLocalChanges(null, null, null);
        }
        return false;
    }

    private boolean mergeWithValidated(IPLDObject<ModelState> validated) {
        IPLDObject<ModelState> nextValidatedState;
        ModelState localMergeBase;
        if (currentValidationContext.isTrivialMerge()) {
            ModelState localRoot = validated.getMapped();
            checkPendingUserStatesAndQueues(localRoot);
            Map<String, Set<String>> obsoleteReviewVersions = currentValidationContext.getObsoleteReviewVersions();
            if (obsoleteReviewVersions.size() == 0) {
                nextValidatedState = validated;
                this.currentValidatedState = nextValidatedState;
                this.currentSnapshot = currentValidationContext.getMainSettlementController()
                        .createPreEvaluationSnapshot(0);
                return true;
            }
            localRoot.removeObsoleteReviewVersions(obsoleteReviewVersions);
            localMergeBase = localRoot;
        }
        else {
            ModelState localRoot = currentValidatedState.getMapped();
            localMergeBase = localRoot.mergeWith(validated, currentValidationContext);
        }
        nextValidatedState = new IPLDObject<ModelState>(localMergeBase);
        try {
            String newHash = nextValidatedState.save(context, null);
            SettlementController currentSnapshot = currentValidationContext.getMainSettlementController()
                    .createPreEvaluationSnapshot(0);
            currentLocalHashes.put(newHash, currentSnapshot);
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        publishLocalState(nextValidatedState);
        checkPendingUserStatesAndQueues(localMergeBase);
        return true;
    }

    private void checkPendingUserStatesAndQueues(ModelState localRoot) {
        // TODO: check local instances and adjust or remove accordingly
        // idea: get new documents, settlement requests, ownership requests and granted ownerships and re-queue valid
        // instances, drop everything else (especially locally computed voting results, settlements etc.)
        // better idea: let client code handle it (it knows which documents etc. it sent to this controller)
    }

    private void processPendingModelStates() {
        if (pendingModelStates != null) {
            do {
                PendingSubMessage pending;
                synchronized (pendingModelStates) {
                    pending = pendingModelStates.isEmpty() ? null : pendingModelStates.pop();
                }
                if (pending == null) {
                    break;
                }
                try {
                    if (!handleIncomingModelState(pending.message, pending.timestamp)) {
                        break;
                    }
                }
                catch (Exception e) {
                    System.out.println("Couldn't handle received model state hash: " + pending.message);
                    e.printStackTrace();
                }
            }
            while (true);
        }
    }

    private void processPendingOwnershipRequests() {
        if (pendingOwnershipRequests != null) {
            do {
                PendingSubMessage pending;
                synchronized (pendingOwnershipRequests) {
                    pending = pendingOwnershipRequests.isEmpty() ? null : pendingOwnershipRequests.pop();
                }
                if (pending == null) {
                    break;
                }
                try {
                    if (!handleIncomingOwnershipRequest(pending.message, pending.timestamp)) {
                        break;
                    }
                }
                catch (Exception e) {
                    System.out.println("Couldn't handle received ownership request: " + pending.message);
                    e.printStackTrace();
                }
            }
            while (true);
        }
    }

    private void publishLocalState(IPLDObject<ModelState> localState) {
        try {
            access.publish(mainIOTAAddress, localState.getMultihash());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
