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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;

import org.ethereum.crypto.ECKey.ECDSASignature;
import org.projectjinxers.account.Signer;
import org.projectjinxers.config.Config;
import org.projectjinxers.model.Document;
import org.projectjinxers.model.GrantedOwnership;
import org.projectjinxers.model.ModelState;
import org.projectjinxers.model.OwnershipRequest;
import org.projectjinxers.model.UserState;
import org.projectjinxers.model.ValidationContext;
import org.projectjinxers.model.Voting;
import org.spongycastle.util.encoders.Base64;

/**
 * Coordinates the model states. The constructor tries to find and initialize the most recent state. It also subscribes
 * to the topics for being able to continuously receive model states and ownership requests from peers.
 * 
 * @author ProjectJinxers
 */
public class ModelController {

    private static final String PUBSUB_SUB_KEY_DATA = "data";
    private static final String PUBSUB_TOPIC_PREFIX_OWNERSHIP_REQUEST = "or";

    private final IPFSAccess access;
    private final IPLDContext context;

    private final String mainIOTAAddress;
    private IPLDObject<ModelState> currentValidatedState;

    private ValidationContext currentValidationContext;

    private boolean validatingModelState;
    private Stack<String> pendingModelStates;
    private Stack<String> pendingOwnershipRequests;

    private Map<String, IPLDObject<UserState>> pendingUserStates;
    private Map<String, Queue<IPLDObject<Document>>> queuedDocuments;
    private Map<String, Queue<IPLDObject<OwnershipRequest>>> queuedOwnershipRequests;
    private Map<String, Queue<IPLDObject<GrantedOwnership>>> queuedGrantedOwnerships;
    private Map<String, Queue<String>> queuedTransferredDocumentHashes;
    private Queue<OwnershipTransferController> queuedOwnershipTransferControllers;
    private Queue<IPLDObject<Voting>> queuedVotings;

    /**
     * Constructor. If it returns without throwing an exception, the instance is completely initialized and continuously
     * listens for model states and ownership requests from peers.
     * 
     * @param access the access to the IPFS API
     * @param config the config (provides initialization and configuration parameters, pass null for defaults)
     * @throws Exception if initialization failed and the application should not continue running.
     */
    public ModelController(IPFSAccess access, Config config) throws Exception {
        this.access = access;
        if (config == null) {
            config = Config.getSharedInstance();
        }
        this.context = new IPLDContext(access, IPLDEncoding.JSON, IPLDEncoding.CBOR, false);
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
                        this.currentValidationContext = new ValidationContext();
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
                            handleIncomingModelState(pubSubData);
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
                            handleIncomingOwnershipRequest(pubSubData);
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

    boolean handleIncomingModelState(String pubSubData) {
        if (validatingModelState) {
            storePotentialModelStateHash(pubSubData);
            return false;
        }
        validatingModelState = true;
        try {
            currentValidationContext = new ValidationContext();
            String multihash = convertPubSubDataToOriginal(pubSubData);
            IPLDObject<ModelState> loaded = loadModelState(multihash, true);
            mergeWithValidated(loaded);
        }
        finally {
            validatingModelState = false;
        }
        processPending();
        return true;
    }

    boolean handleIncomingOwnershipRequest(String pubSubData) {
        if (validatingModelState) {
            storePotentialOwnershipRequestHash(pubSubData);
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
                context, signature);
        if (controller.process()) {
            try {
                saveLocalChanges(null, controller);
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
        saveLocalChanges(document, null);
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
            synchronized (pendingUserStates) {
                if (pendingUserStates != null) {
                    IPLDObject<UserState> res = pendingUserStates.remove(local.getMapped().getUser().getMultihash());
                    if (res != null) {
                        return res;
                    }
                }
            }
        }
        return local;
    }

    private boolean saveLocalChanges(IPLDObject<Document> document,
            OwnershipTransferController ownershipTransferController) throws IOException {
        IPLDObject<ModelState> currentModelState = currentValidatedState;
        ModelState modelState = currentModelState.getMapped();
        Set<String> userHashes = new LinkedHashSet<>();
        Map<String, Queue<IPLDObject<Document>>> documents = new HashMap<>();
        Map<String, Queue<IPLDObject<OwnershipRequest>>> ownershipRequests = new HashMap<>();
        Map<String, Queue<IPLDObject<GrantedOwnership>>> grantedOwnerships = new HashMap<>();
        Map<String, Queue<String>> transferredDocumentHashes = new HashMap<>();
        Collection<OwnershipTransferController> transferControllers = new ArrayList<>();
        Collection<IPLDObject<Voting>> votings = new ArrayList<>();
        if (queuedOwnershipTransferControllers != null) {
            synchronized (queuedOwnershipTransferControllers) {
                transferControllers.addAll(queuedOwnershipTransferControllers);
            }
        }
        if (queuedVotings != null) {
            synchronized (queuedVotings) {
                votings.addAll(queuedVotings);
            }
        }
        if (ownershipTransferController != null) {
            transferControllers.add(ownershipTransferController);
        }
        if (transferControllers.size() > 0) {
            for (OwnershipTransferController controller : transferControllers) {
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
                        transferredDocHashes.add(transferredDocument.getMultihash());
                        userHashes.add(key);
                        IPLDObject<UserState> newOwner = controller.getNewOwner();
                        key = newOwner.getMapped().getUser().getMultihash();
                        Queue<IPLDObject<Document>> docs = documents.get(key);
                        if (docs == null) {
                            docs = new ArrayDeque<>();
                            documents.put(key, docs);
                        }
                        IPLDObject<Document> transferred = transferredDoc.transferTo(newOwner, transferredDocument,
                                controller.getSignature());
                        try {
                            transferred.save(context, null);
                            docs.add(transferred);
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
                    docs.addAll(entry.getValue());
                    documents.put(key, docs);
                }
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
        for (String userHash : userHashes) {
            IPLDObject<UserState> userState = modelState.expectUserState(userHash);
            Queue<IPLDObject<Document>> docs = documents.get(userHash);
            if (queuedDocuments != null) {
                synchronized (queuedDocuments) {
                    queuedDocuments.remove(userHash);
                }
            }
            Queue<IPLDObject<OwnershipRequest>> requests = ownershipRequests.get(userHash);
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
            userState = getInstanceToSave(userState);
            if (docs != null && docs.size() > 0 || requests != null && requests.size() > 0
                    || granted != null && granted.size() > 0 || hashes != null && hashes.size() > 0) {
                try {
                    UserState updated = userState.getMapped().updateLinks(docs, requests, granted, hashes, userState);
                    IPLDObject<UserState> updatedObject = new IPLDObject<>(updated);
                    updatedObject.save(context, null);
                    updatedUserStates.put(userHash, updatedObject);
                }
                catch (Exception e) {
                    e.printStackTrace();
                    requeue(userHash, docs, requests, granted, hashes, votings);

                    if (updatedUserStates.size() > 0) {
                        if (pendingUserStates == null) {
                            pendingUserStates = new LinkedHashMap<>();
                        }
                        synchronized (pendingUserStates) {
                            pendingUserStates.putAll(updatedUserStates);
                        }
                    }
                    // at this point everything but the document parameter has been handled if its owner has not been
                    // processed
                    if (document != null) {
                        String ownerHash = document.getMapped().expectUserState().getUser().getMultihash();
                        if (pendingUserStates == null || !pendingUserStates.containsKey(ownerHash)) {
                            enqueueDocument(document);
                        }
                    }
                    return false;
                }
            }
        }
        if (updatedUserStates.isEmpty()) { // must be voting(s)
            modelState = modelState.updateUserState(null, null, votings, currentModelState);
        }
        else {
            boolean first = true;
            for (Entry<String, IPLDObject<UserState>> entry : updatedUserStates.entrySet()) {
                modelState = modelState.updateUserState(entry.getValue(), ownershipRequests.get(entry.getKey()),
                        first ? votings : null, first ? currentModelState : null);
                first = false;
            }
        }
        IPLDObject<ModelState> newLocalState = new IPLDObject<ModelState>(modelState);
        try {
            newLocalState.save(context, null);
        }
        catch (Exception e) {
            e.printStackTrace();
            if (updatedUserStates.size() > 0) {
                if (pendingUserStates == null) {
                    pendingUserStates = new LinkedHashMap<>();
                }
                synchronized (pendingUserStates) {
                    pendingUserStates.putAll(updatedUserStates);
                }
            }
            if (votings.size() > 0) {
                if (queuedVotings == null) {
                    queuedVotings = new ArrayDeque<>();
                }
                queuedVotings.addAll(votings);
            }
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

    private void storePotentialModelStateHash(String pubSubData) {
        if (pendingModelStates == null) {
            pendingModelStates = new Stack<>();
        }
        synchronized (pendingModelStates) {
            pendingModelStates.push(pubSubData);
        }
    }

    private void storePotentialOwnershipRequestHash(String pubSubData) {
        if (pendingOwnershipRequests == null) {
            pendingOwnershipRequests = new Stack<>();
        }
        synchronized (pendingOwnershipRequests) {
            pendingOwnershipRequests.push(pubSubData);
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

    private void requeue(String userHash, Queue<IPLDObject<Document>> documents,
            Queue<IPLDObject<OwnershipRequest>> requests, Queue<IPLDObject<GrantedOwnership>> granted,
            Queue<String> transferredOwnershipHashes, Collection<IPLDObject<Voting>> votings) {
        if (documents != null && documents.size() > 0) {
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
        if (requests != null && requests.size() > 0) {
            if (queuedOwnershipRequests == null) {
                queuedOwnershipRequests = new HashMap<>();
            }
            synchronized (queuedOwnershipRequests) {
                Queue<IPLDObject<OwnershipRequest>> queue = queuedOwnershipRequests.get(userHash);
                if (queue == null) {
                    queuedOwnershipRequests.put(userHash, requests);
                }
                else {
                    queue.addAll(requests);
                }
            }
        }
        if (granted != null && granted.size() > 0) {
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
        if (transferredOwnershipHashes != null && transferredOwnershipHashes.size() > 0) {
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
        if (votings != null && votings.size() > 0) {
            if (queuedVotings == null) {
                queuedVotings = new ArrayDeque<>();
            }
            synchronized (queuedVotings) {
                queuedVotings.addAll(votings);
            }
        }
    }

    private boolean executePendingChanges() throws IOException {
        if (pendingUserStates != null && pendingUserStates.size() > 0
                || queuedDocuments != null && queuedDocuments.size() > 0
                || queuedOwnershipRequests != null && queuedOwnershipRequests.size() > 0
                || queuedOwnershipTransferControllers != null && queuedOwnershipTransferControllers.size() > 0) {
            return saveLocalChanges(null, null);
        }
        return false;
    }

    private void mergeWithValidated(IPLDObject<ModelState> modelState) {
        modelState.getMapped();
        // TODO: merge with currentValidatedState
        try {
            Thread.sleep(10);
        }
        catch (InterruptedException e) {

        }
    }

    private void processPendingModelStates() {
        if (pendingModelStates != null) {
            do {
                String pubSubData;
                synchronized (pendingModelStates) {
                    pubSubData = pendingModelStates.isEmpty() ? null : pendingModelStates.pop();
                }
                if (pubSubData == null) {
                    break;
                }
                try {
                    if (!handleIncomingModelState(pubSubData)) {
                        break;
                    }
                }
                catch (Exception e) {
                    System.out.println("Couldn't handle received model state hash: " + pubSubData);
                    e.printStackTrace();
                }
            }
            while (true);
        }
    }

    private void processPendingOwnershipRequests() {
        if (pendingOwnershipRequests != null) {
            do {
                String pubSubData;
                synchronized (pendingOwnershipRequests) {
                    pubSubData = pendingOwnershipRequests.isEmpty() ? null : pendingOwnershipRequests.pop();
                }
                if (pubSubData == null) {
                    break;
                }
                try {
                    if (!handleIncomingOwnershipRequest(pubSubData)) {
                        break;
                    }
                }
                catch (Exception e) {
                    System.out.println("Couldn't handle received ownership request: " + pubSubData);
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
