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
import java.util.HashSet;
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
import org.projectjinxers.model.DocumentRemoval;
import org.projectjinxers.model.GrantedOwnership;
import org.projectjinxers.model.ModelState;
import org.projectjinxers.model.OwnershipRequest;
import org.projectjinxers.model.Review;
import org.projectjinxers.model.SealedDocument;
import org.projectjinxers.model.SettlementRequest;
import org.projectjinxers.model.UnbanRequest;
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
    private Map<String, Queue<IPLDObject<DocumentRemoval>>> queuedDocumentRemovals;
    private Map<String, Queue<IPLDObject<SettlementRequest>>> queuedSettlementRequests;
    private Map<String, Queue<IPLDObject<UnbanRequest>>> queuedUnbanRequests;
    private Map<String, Queue<IPLDObject<OwnershipRequest>>> queuedOwnershipRequests;
    private Map<String, Queue<IPLDObject<GrantedOwnership>>> queuedGrantedOwnerships;
    private Map<String, Queue<String>> queuedTransferredDocumentHashes;
    private Queue<OwnershipTransferController> queuedOwnershipTransferControllers;
    private Map<String, IPLDObject<Voting>> queuedVotings;
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
                saveLocalChanges(null, null, null, null, controller, null, System.currentTimeMillis());
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
        saveLocalChanges(document, null, null, null, null, null, System.currentTimeMillis());
    }

    public void requestDocumentRemoval(IPLDObject<DocumentRemoval> removal, Signer signer) throws IOException {
        removal.save(context, signer);
        saveLocalChanges(null, removal, null, null, null, null, System.currentTimeMillis());
    }

    public void issueSettlementRequest(IPLDObject<SettlementRequest> settlementRequest, Signer signer)
            throws IOException {
        settlementRequest.save(context, signer);
        saveLocalChanges(null, null, settlementRequest, null, null, null, settlementRequest.getMapped().getTimestamp());
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

    public void issueUnbanRequest(IPLDObject<UnbanRequest> unbanRequest, Signer signer) throws IOException {
        unbanRequest.save(context, signer);
        saveLocalChanges(null, null, null, unbanRequest, null, null, System.currentTimeMillis());
    }

    public void addVoting(IPLDObject<Voting> voting, Signer signer) throws IOException {
        voting.save(context, signer);
        saveLocalChanges(null, null, null, null, null, voting, System.currentTimeMillis());
    }

    public boolean addVote(IPLDObject<Voting> voting, String userHash, int valueIndex, long seed, Signer signer)
            throws IOException {
        long timestamp = System.currentTimeMillis();
        Voting updated = voting.getMapped().addVote(userHash, valueIndex, seed, timestamp, timestampTolerance);
        if (updated != null) {
            IPLDObject<Voting> updatedObject = new IPLDObject<>(updated);
            updatedObject.save(context, signer);
            saveLocalChanges(null, null, null, null, null, updatedObject, timestamp);
            return true;
        }
        return false;
    }

    public boolean tally(IPLDObject<Voting> voting) throws IOException {
        long timestamp = System.currentTimeMillis();
        Voting updated = voting.getMapped().tally(timestamp, timestampTolerance);
        if (updated != null) {
            IPLDObject<Voting> updatedObject = new IPLDObject<>(updated);
            updatedObject.save(context, null);
            saveLocalChanges(null, null, null, null, null, updatedObject, timestamp);
            return true;
        }
        return false;
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

    private boolean saveLocalChanges(IPLDObject<Document> document, IPLDObject<DocumentRemoval> documentRemoval,
            IPLDObject<SettlementRequest> settlementRequest, IPLDObject<UnbanRequest> unbanRequest,
            OwnershipTransferController ownershipTransferController, IPLDObject<Voting> voting, long timestamp)
            throws IOException {
        IPLDObject<ModelState> currentModelState = currentValidatedState;
        ModelState currentState;
        ModelState modelState;
        if (currentModelState == null) {
            currentState = null;
            modelState = new ModelState();
        }
        else {
            currentState = currentModelState.getMapped();
            modelState = currentState;
        }
        Set<String> userHashes = new LinkedHashSet<>();
        Map<String, Queue<IPLDObject<DocumentRemoval>>> documentRemovals = new HashMap<>();
        if (queuedDocumentRemovals != null) {
            synchronized (queuedDocumentRemovals) {
                for (Entry<String, Queue<IPLDObject<DocumentRemoval>>> entry : queuedDocumentRemovals.entrySet()) {
                    String key = entry.getKey();
                    userHashes.add(key);
                    Queue<IPLDObject<DocumentRemoval>> removals = documentRemovals.get(key);
                    if (removals == null) {
                        removals = new ArrayDeque<>();
                        documentRemovals.put(key, removals);
                    }
                    removals.addAll(entry.getValue());
                }
            }
        }
        if (documentRemoval != null) {
            String userHash = documentRemoval.getMapped().getDocument().getMapped().expectUserState().getUser()
                    .getMultihash();
            userHashes.add(userHash);
            Queue<IPLDObject<DocumentRemoval>> queue = documentRemovals.get(userHash);
            if (queue == null) {
                queue = new ArrayDeque<>();
                documentRemovals.put(userHash, queue);
            }
            queue.add(documentRemoval);
        }
        SettlementController settlementController = currentSnapshot == null ? null
                : currentSnapshot.createPreEvaluationSnapshot(timestamp);
        Map<String, Queue<IPLDObject<SettlementRequest>>> settlementRequests = new HashMap<>();
        if (settlementController != null) {
            for (Queue<IPLDObject<DocumentRemoval>> removals : documentRemovals.values()) {
                for (IPLDObject<DocumentRemoval> removal : removals) {
                    settlementController.checkRemovedDocument(removal.getMapped().getDocument(), currentState);
                }
            }
            boolean settlementChanged = false;
            if (queuedSettlementRequests != null) {
                synchronized (queuedSettlementRequests) {
                    if (queuedSettlementRequests.size() > 0) {
                        for (Entry<String, Queue<IPLDObject<SettlementRequest>>> entry : queuedSettlementRequests
                                .entrySet()) {
                            String key = entry.getKey();
                            userHashes.add(key);
                            Queue<IPLDObject<SettlementRequest>> reqs = new ArrayDeque<>(entry.getValue());
                            settlementRequests.put(key, reqs);
                        }
                    }
                }
                if (settlementRequests.size() > 0) {
                    SettlementRequest sameTimestamp = settlementRequest == null ? null : settlementRequest.getMapped();
                    for (Queue<IPLDObject<SettlementRequest>> queue : settlementRequests.values()) {
                        for (IPLDObject<SettlementRequest> request : queue) {
                            SettlementRequest req = request.getMapped();
                            req.synchronizeTimestamp(sameTimestamp);
                            sameTimestamp = req;
                            settlementChanged = settlementController.checkRequest(req.getDocument(), false, true)
                                    || settlementChanged;
                        }
                    }
                }
            }
            if (settlementRequest != null) {
                SettlementRequest req = settlementRequest.getMapped();
                if (settlementController.checkRequest(req.getDocument(), false, true)) {
                    settlementChanged = true;
                }
                String userHash = req.getUserState().getMapped().getUser().getMultihash();
                userHashes.add(userHash);
                Queue<IPLDObject<SettlementRequest>> queue = settlementRequests.get(userHash);
                if (queue == null) {
                    queue = new ArrayDeque<>();
                    settlementRequests.put(userHash, queue);
                }
                queue.add(settlementRequest);
            }
            settlementChanged = settlementController.applyNewTimestamp() || settlementChanged;
            if (settlementChanged) {
                settlementController.enterMergeMode();
            }
            else {
                settlementController = null;
            }
        }
        Map<String, Queue<IPLDObject<Document>>> documents = new HashMap<>();
        Map<String, Queue<IPLDObject<UnbanRequest>>> unbanRequests = new HashMap<>();
        Map<String, Queue<IPLDObject<OwnershipRequest>>> ownershipRequests = new HashMap<>();
        Map<String, Queue<IPLDObject<GrantedOwnership>>> grantedOwnerships = new HashMap<>();
        Map<String, Queue<String>> transferredDocumentHashes = new HashMap<>();
        Collection<OwnershipTransferController> transferControllers = new ArrayList<>();
        Map<String, IPLDObject<Voting>> votings = null;
        Map<String, String[]> newReviewTableEntries = null;
        if (queuedOwnershipTransferControllers != null) {
            synchronized (queuedOwnershipTransferControllers) {
                transferControllers.addAll(queuedOwnershipTransferControllers);
            }
        }
        if (queuedVotings != null) {
            synchronized (queuedVotings) {
                if (queuedVotings.size() > 0) {
                    votings = new HashMap<>(queuedVotings);
                }
            }
        }
        if (voting != null) {
            if (votings == null) {
                votings = new HashMap<>();
            }
            votings.put(voting.getMapped().getSubject().getMultihash(), voting);
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
                        IPLDObject<Voting> ownershipVoting = controller.getVoting();
                        try {
                            ownershipVoting.save(context, null);
                        }
                        catch (Exception e) {
                            handleOwnershipTransferControllerException(e, ownershipTransferController, document,
                                    documentRemoval, settlementRequest, unbanRequest, voting);
                            return false;
                        }
                        if (votings == null) {
                            votings = new HashMap<>();
                        }
                        votings.put(ownershipVoting.getMapped().getSubject().getMultihash(), ownershipVoting);
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
                                settlementController.checkDocument(transferredDocument, true, null, null, null);
                            }
                            userHashes.add(key);
                        }
                        catch (Exception e) {
                            handleOwnershipTransferControllerException(e, ownershipTransferController, document,
                                    documentRemoval, settlementRequest, unbanRequest, voting);
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
                        handleOwnershipTransferControllerException(e, ownershipTransferController, document,
                                documentRemoval, settlementRequest, unbanRequest, voting);
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
                            settlementController.checkDocument(doc, true, null, null, null);
                        }
                    }
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
                settlementController.checkDocument(document, true, null, null, null);
            }
        }
        Map<String, UserState> settlementStates;
        Collection<IPLDObject<SealedDocument>> sealedDocuments;
        Collection<String> sealedDocumentHashes;
        if (settlementController == null) {
            settlementStates = null;
            sealedDocuments = null;
            sealedDocumentHashes = null;
        }
        else {
            Map<String, SealedDocument> sealedDocs = new HashMap<>();
            Set<String> invalidSettlementRequests = new HashSet<>();
            if (settlementController.evaluate(sealedDocs, invalidSettlementRequests, modelState)) {
                settlementStates = new HashMap<>();
                settlementController.update(settlementStates, modelState, sealedDocs);
                sealedDocuments = new ArrayList<>();
                sealedDocumentHashes = new ArrayList<>();
                for (SealedDocument doc : sealedDocs.values()) {
                    sealedDocuments.add(new IPLDObject<>(doc));
                    sealedDocumentHashes.add(doc.getDocument().getMultihash());
                }
                userHashes.addAll(settlementStates.keySet());
                if (queuedSettlementRequests != null) {
                    synchronized (queuedSettlementRequests) {
                        queuedSettlementRequests.clear();
                    }
                }
            }
            else {
                if (invalidSettlementRequests.size() > 0) {
                    handleNoUserStatesSaved(document, documentRemoval, settlementRequest, unbanRequest,
                            ownershipTransferController, voting);
                    // TODO: replace exception with client notification and return false (idea: client can set delete
                    // flag on queued objects which is checked when objects are dequeued)
                    throw new ValidationException("settlement request for document with too few reviews");
                }
                settlementStates = null;
                sealedDocuments = null;
                sealedDocumentHashes = null;
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
        if (queuedUnbanRequests != null) {
            synchronized (queuedUnbanRequests) {
                for (Entry<String, Queue<IPLDObject<UnbanRequest>>> entry : queuedUnbanRequests.entrySet()) {
                    String key = entry.getKey();
                    userHashes.add(key);
                    unbanRequests.put(key, new ArrayDeque<>(entry.getValue()));
                }
            }
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
            Queue<IPLDObject<DocumentRemoval>> removals = documentRemovals.get(userHash);
            if (queuedDocumentRemovals != null) {
                synchronized (queuedDocumentRemovals) {
                    queuedDocumentRemovals.remove(userHash);
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
            Queue<IPLDObject<SettlementRequest>> sreqs = settlementRequests.get(userHash);
            if (queuedSettlementRequests != null) {
                synchronized (queuedSettlementRequests) {
                    queuedSettlementRequests.remove(userHash);
                }
            }
            Queue<IPLDObject<UnbanRequest>> unbans = unbanRequests.get(userHash);
            if (queuedUnbanRequests != null) {
                synchronized (queuedUnbanRequests) {
                    queuedUnbanRequests.remove(userHash);
                }
            }
            UserState settlementValues = settlementStates == null ? null : settlementStates.get(userHash);
            if (abortLocalChanges) {
                requeue(userHash, null, docs, removals, sreqs, oreqs, granted, hashes, unbans, votings);
                handleUserStateUnsaved(updatedUserStates, settlementStates, document, documentRemoval, unbanRequest,
                        newReviewTableEntries);
                return false;
            }
            IPLDObject<UserState> toSave = getInstanceToSave(userState);
            if (docs != null || sreqs != null || oreqs != null || granted != null || hashes != null
                    || settlementValues != null || unbans != null) {
                try {
                    UserState updated = toSave.getMapped().updateLinks(docs, removals, sreqs, oreqs, granted, hashes,
                            sealedDocumentHashes, settlementValues, unbans, toSave);
                    IPLDObject<UserState> updatedObject = new IPLDObject<>(updated);
                    updatedObject.save(context, null);
                    updatedUserStates.put(userHash, updatedObject);
                }
                catch (Exception e) {
                    e.printStackTrace();
                    requeue(userHash, toSave == userState ? null : toSave, docs, removals, sreqs, oreqs, granted,
                            hashes, unbans, votings);
                    handleUserStateUnsaved(updatedUserStates, settlementStates, document, documentRemoval, unbanRequest,
                            newReviewTableEntries);
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
            handleLocalModelStateUnsaved(updatedUserStates, votings, settlementStates, newReviewTableEntries);
            return false;
        }
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
                    modelState = modelState.updateUserState(entry.getValue(), settlementRequests.get(key),
                            ownershipRequests.get(key), votings, sealedDocuments, newReviewTableEntries,
                            currentModelState, timestamp);
                    first = false;
                }
                else {
                    modelState = modelState.updateUserState(entry.getValue(), settlementRequests.get(key),
                            ownershipRequests.get(key), null, null, null, null, timestamp);
                }
            }
        }
        IPLDObject<ModelState> newLocalState = new IPLDObject<ModelState>(modelState);
        if (abortLocalChanges) {
            handleLocalModelStateUnsaved(updatedUserStates, votings, settlementStates, newReviewTableEntries);
            return false;
        }
        try {
            currentLocalHashes.put(newLocalState.save(context, null), settlementController);
        }
        catch (Exception e) {
            e.printStackTrace();
            handleLocalModelStateUnsaved(updatedUserStates, votings, settlementStates, newReviewTableEntries);
            return false;
        }
        publishLocalState(newLocalState);
        return true;
    }

    private void handleOwnershipTransferControllerException(Exception e, OwnershipTransferController controller,
            IPLDObject<Document> document, IPLDObject<DocumentRemoval> documentRemoval,
            IPLDObject<SettlementRequest> settlementRequest, IPLDObject<UnbanRequest> unbanRequest,
            IPLDObject<Voting> voting) {
        e.printStackTrace();
        handleNoUserStatesSaved(document, documentRemoval, settlementRequest, unbanRequest, controller, voting);
    }

    private void handleNoUserStatesSaved(IPLDObject<Document> document, IPLDObject<DocumentRemoval> documentRemoval,
            IPLDObject<SettlementRequest> settlementRequest, IPLDObject<UnbanRequest> unbanRequest,
            OwnershipTransferController controller, IPLDObject<Voting> voting) {
        if (document != null) {
            enqueueDocument(document);
        }
        if (documentRemoval != null) {
            enqueueDocumentRemoval(documentRemoval);
        }
        if (settlementRequest != null) {
            enqueueSettlementRequest(settlementRequest);
        }
        if (unbanRequest != null) {
            enqueueUnbanRequest(unbanRequest);
        }
        if (controller != null) {
            enqueueOwnershipTransferController(controller);
        }
        if (voting != null) {
            enqueueVoting(voting);
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
            Map<String, UserState> settlementStates, IPLDObject<Document> document, IPLDObject<DocumentRemoval> removal,
            IPLDObject<UnbanRequest> unbanRequest, Map<String, String[]> newReviewTableEntries) {
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
        // at this point everything but the single reference parameters has been handled if their owner has not been
        // processed
        if (document != null) {
            if (pendingUserStates == null || !pendingUserStates
                    .containsKey(document.getMapped().expectUserState().getUser().getMultihash())) {
                enqueueDocument(document);
            }
        }
        if (removal != null) {
            if (pendingUserStates == null || !pendingUserStates.containsKey(
                    removal.getMapped().getDocument().getMapped().expectUserState().getUser().getMultihash())) {
                enqueueDocumentRemoval(removal);
            }
        }
        if (unbanRequest != null) {
            if (pendingUserStates == null || !pendingUserStates
                    .containsKey(unbanRequest.getMapped().getUserState().getMapped().getUser().getMultihash())) {
                enqueueUnbanRequest(unbanRequest);
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
            Map<String, IPLDObject<Voting>> votings, Map<String, UserState> settlementStates,
            Map<String, String[]> newReviewTableEntries) {
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
                queuedVotings = new HashMap<>();
            }
            queuedVotings.putAll(votings);
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

    private void enqueueDocumentRemoval(IPLDObject<DocumentRemoval> documentRemoval) {
        if (queuedDocumentRemovals == null) {
            queuedDocumentRemovals = new HashMap<>();
        }
        String key = documentRemoval.getMapped().getDocument().getMapped().expectUserState().getUser().getMultihash();
        synchronized (queuedDocumentRemovals) {
            Queue<IPLDObject<DocumentRemoval>> queue = queuedDocumentRemovals.get(key);
            if (queue == null) {
                queue = new ArrayDeque<>();
                queuedDocumentRemovals.put(key, queue);
            }
            queue.add(documentRemoval);
        }
    }

    private void enqueueSettlementRequest(IPLDObject<SettlementRequest> settlementRequest) {
        if (queuedSettlementRequests == null) {
            queuedSettlementRequests = new HashMap<>();
        }
        String key = settlementRequest.getMapped().getUserState().getMapped().getUser().getMultihash();
        synchronized (queuedSettlementRequests) {
            Queue<IPLDObject<SettlementRequest>> queue = queuedSettlementRequests.get(key);
            if (queue == null) {
                queue = new ArrayDeque<>();
                queuedSettlementRequests.put(key, queue);
            }
            queue.add(settlementRequest);
        }
    }

    private void enqueueUnbanRequest(IPLDObject<UnbanRequest> unbanRequest) {
        if (queuedUnbanRequests == null) {
            queuedUnbanRequests = new HashMap<>();
        }
        String key = unbanRequest.getMapped().getUserState().getMapped().getUser().getMultihash();
        synchronized (queuedUnbanRequests) {
            Queue<IPLDObject<UnbanRequest>> queue = queuedUnbanRequests.get(key);
            if (queue == null) {
                queue = new ArrayDeque<>();
                queuedUnbanRequests.put(key, queue);
            }
            queue.add(unbanRequest);
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

    private void enqueueVoting(IPLDObject<Voting> voting) {
        if (queuedVotings == null) {
            queuedVotings = new HashMap<String, IPLDObject<Voting>>();
        }
        synchronized (queuedVotings) {
            queuedVotings.put(voting.getMapped().getSubject().getMultihash(), voting);
        }
    }

    private void requeue(String userHash, IPLDObject<UserState> pending, Queue<IPLDObject<Document>> documents,
            Queue<IPLDObject<DocumentRemoval>> removals, Queue<IPLDObject<SettlementRequest>> sreqs,
            Queue<IPLDObject<OwnershipRequest>> oreqs, Queue<IPLDObject<GrantedOwnership>> granted,
            Queue<String> transferredOwnershipHashes, Queue<IPLDObject<UnbanRequest>> unbans,
            Map<String, IPLDObject<Voting>> votings) {
        if (documents != null) {
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
        if (removals != null) {
            if (queuedDocumentRemovals == null) {
                queuedDocumentRemovals = new HashMap<>();
            }
            synchronized (queuedDocumentRemovals) {
                Queue<IPLDObject<DocumentRemoval>> queue = queuedDocumentRemovals.get(userHash);
                if (queue == null) {
                    queuedDocumentRemovals.put(userHash, removals);
                }
                else {
                    queue.addAll(removals);
                }
            }
        }
        if (sreqs != null) {
            if (queuedSettlementRequests == null) {
                queuedSettlementRequests = new HashMap<>();
            }
            synchronized (queuedSettlementRequests) {
                Queue<IPLDObject<SettlementRequest>> queue = queuedSettlementRequests.get(userHash);
                if (queue == null) {
                    queuedSettlementRequests.put(userHash, sreqs);
                }
                else {
                    queue.addAll(sreqs);
                }
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
        if (unbans != null) {
            if (queuedUnbanRequests == null) {
                queuedUnbanRequests = new HashMap<>();
            }
            synchronized (queuedUnbanRequests) {
                Queue<IPLDObject<UnbanRequest>> queue = queuedUnbanRequests.get(userHash);
                if (queue == null) {
                    queuedUnbanRequests.put(userHash, unbans);
                }
                else {
                    queue.addAll(unbans);
                }
            }
        }
        if (votings != null) {
            if (queuedVotings == null) {
                queuedVotings = new HashMap<>();
            }
            synchronized (queuedVotings) {
                queuedVotings.putAll(votings);
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
                || queuedDocumentRemovals != null && queuedDocumentRemovals.size() > 0
                || queuedOwnershipRequests != null && queuedOwnershipRequests.size() > 0
                || queuedGrantedOwnerships != null && queuedGrantedOwnerships.size() > 0
                || queuedTransferredDocumentHashes != null && queuedTransferredDocumentHashes.size() > 0
                || queuedOwnershipTransferControllers != null && queuedOwnershipTransferControllers.size() > 0
                || queuedVotings != null && queuedVotings.size() > 0
                || queuedSettlementRequests != null && queuedSettlementRequests.size() > 0
                || queuedUnbanRequests != null && queuedUnbanRequests.size() > 0) {
            return saveLocalChanges(null, null, null, null, null, null, System.currentTimeMillis());
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
            SettlementController snapshot = currentValidationContext.getMainSettlementController()
                    .createPreEvaluationSnapshot(0);
            currentLocalHashes.put(validated.getMultihash(), snapshot);
            if (obsoleteReviewVersions.size() == 0 || !localRoot.removeObsoleteReviewVersions(obsoleteReviewVersions)) {
                nextValidatedState = validated;
                this.currentValidatedState = nextValidatedState;
                this.currentSnapshot = snapshot;
                return true;
            }
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
