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

import static org.projectjinxers.util.ModelUtility.addProgressListener;
import static org.projectjinxers.util.ModelUtility.dequeue;
import static org.projectjinxers.util.ModelUtility.enqueue;
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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Stream;

import org.ethereum.crypto.ECKey.ECDSASignature;
import org.projectjinxers.account.Signer;
import org.projectjinxers.config.Config;
import org.projectjinxers.config.SecretConfig;
import org.projectjinxers.controller.IPLDObject.ProgressListener;
import org.projectjinxers.controller.IPLDObject.ProgressTask;
import org.projectjinxers.model.Document;
import org.projectjinxers.model.DocumentRemoval;
import org.projectjinxers.model.GrantedOwnership;
import org.projectjinxers.model.GrantedUnban;
import org.projectjinxers.model.ModelState;
import org.projectjinxers.model.OwnershipRequest;
import org.projectjinxers.model.OwnershipSelection;
import org.projectjinxers.model.Review;
import org.projectjinxers.model.SealedDocument;
import org.projectjinxers.model.SettlementRequest;
import org.projectjinxers.model.Tally;
import org.projectjinxers.model.UnbanRequest;
import org.projectjinxers.model.UserState;
import org.projectjinxers.model.Votable;
import org.projectjinxers.model.Voting;
import org.spongycastle.util.encoders.Base64;

/**
 * Coordinates the model states. The constructor tries to find and initialize the most recent state. It also subscribes
 * to the topics for being able to continuously receive model states and ownership requests from peers.
 * 
 * @author ProjectJinxers
 */
public class ModelController {

    public interface ModelControllerListener {

        void handleInvalidSettlement(Set<String> invalidHashes);

        void handleRemoved();

    }

    static class PendingSubMessage {

        final String message;
        final long timestamp;

        PendingSubMessage(String message, long timestamp) {
            this.message = message;
            this.timestamp = timestamp;
        }
    }

    // Currently this is a container for progress listeners only. Could be used as a real progress listener in the
    // future (with subclasses UserStateProgressListener and ModelStateProgressListener).
    private static class ForwardingProgressListener implements ProgressListener {

        private Collection<ProgressListener> listeners;

        @Override
        public boolean isDeterminate() {
            return false;
        }

        @Override
        public void startedTask(ProgressTask task, int steps) {
            for (ProgressListener listener : listeners) {
                listener.startedTask(task, steps);
            }
        }

        @Override
        public void nextStep() {

        }

        @Override
        public void finishedTask(ProgressTask task) {
            for (ProgressListener listener : listeners) {
                listener.finishedTask(task);
            }
        }

        @Override
        public void failedTask(ProgressTask task, String message, Throwable failure) {
            for (ProgressListener listener : listeners) {
                listener.failedTask(task, message, failure);
            }
        }

        @Override
        public void enqueued() {
            for (ProgressListener listener : listeners) {
                listener.enqueued();
            }
        }

        @Override
        public boolean dequeued() {
            for (ProgressListener listener : listeners) {
                if (!listener.dequeued()) {
                    for (ProgressListener other : listeners) {
                        if (other != listener) {
                            other.obsoleted();
                        }
                    }
                    return false;
                }
            }
            return true;
        }

        @Override
        public void obsoleted() {
            for (ProgressListener listener : listeners) {
                listener.obsoleted();
            }
        }

        @Override
        public boolean isCanceled() {
            for (ProgressListener listener : listeners) {
                if (listener.isCanceled()) {
                    for (ProgressListener other : listeners) {
                        if (other != listener) {
                            other.obsoleted();
                        }
                    }
                    return true;
                }
            }
            return false;
        }

    }

    private static final String PUBSUB_SUB_KEY_DATA = "data";
    private static final String PUBSUB_TOPIC_PREFIX_OWNERSHIP_REQUEST = "or";

    private static final Map<String, ModelController> MODEL_CONTROLLERS = new HashMap<>();

    public static ModelController getModelController(Config config) throws Exception {
        return getModelController(config, null);
    }

    public static ModelController getModelController(Config config, SecretConfig secretConfig) throws Exception {
        IPFSAccess access = new IPFSAccess();
        access.configure();
        return getModelController(access, config, secretConfig);
    }

    public static ModelController getModelController(IPFSAccess access, Config config) throws Exception {
        return getModelController(access, config, null);
    }

    public static ModelController getModelController(IPFSAccess access, Config config, SecretConfig secretConfig)
            throws Exception {
        Config cfg = config == null ? Config.getSharedInstance() : config;
        String address = cfg.getIOTAAddress();
        synchronized (MODEL_CONTROLLERS) {
            ModelController modelController = MODEL_CONTROLLERS.get(address);
            if (modelController == null) {
                modelController = new ModelController(access, cfg, secretConfig, cfg.getTimestampTolerance());
                MODEL_CONTROLLERS.put(address, modelController);
            }
            return modelController;
        }
    }

    public static void removeModelController(String address) {
        ModelController removed;
        synchronized (MODEL_CONTROLLERS) {
            removed = MODEL_CONTROLLERS.remove(address);
        }
        if (removed != null && removed.listener != null) {
            removed.listener.handleRemoved();
        }
    }

    private final IPFSAccess access;
    private final SecretConfig secretConfig;
    private final IPLDContext context;
    private long timestampTolerance;

    private final String address;
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

    private ModelControllerListener listener;

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
    private ModelController(IPFSAccess access, Config config, SecretConfig secretConfig, long timestampTolerance)
            throws Exception {
        this.access = access;
        Config cfg = config == null ? Config.getSharedInstance() : config;
        this.secretConfig = secretConfig == null ? SecretConfig.getSharedInstance() : secretConfig;
        this.context = new IPLDContext(access, IPLDEncoding.JSON, IPLDEncoding.CBOR, false);
        this.timestampTolerance = timestampTolerance;
        address = cfg.getIOTAAddress();
        String currentModelStateHash;
        try {
            currentModelStateHash = access.readModelStateHash(address);
            if (currentModelStateHash != null) {
                this.currentValidatedState = loadModelState(currentModelStateHash, false);
            }
        }
        catch (FileNotFoundException e) {
            currentModelStateHash = cfg.getValidHash(address);
            if (currentModelStateHash != null) {
                this.currentValidatedState = loadModelState(currentModelStateHash, false);
            }
            do {
                currentModelStateHash = readNextModelStateHashFromTangle(address);
                if (currentModelStateHash != null) {
                    try {
                        this.currentValidationContext = new ValidationContext(context, null, null,
                                System.currentTimeMillis() + timestampTolerance, 0, this.secretConfig);
                        this.currentValidatedState = loadModelState(currentModelStateHash, true);
                        access.saveModelStateHash(address, currentModelStateHash);
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
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                boolean subscribedSuccessfully = false;
                try {
                    Stream<Map<String, Object>> stream = access.subscribe(address);
                    subscribedSuccessfully = true;
                    stream.forEach(map -> {
                        try {
                            String pubSubData = (String) map.get(PUBSUB_SUB_KEY_DATA);
                            System.out.println("Received pubsub message: " + map);
                            handleIncomingModelState(pubSubData, System.currentTimeMillis());
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                }
                catch (Exception e) {
                    e.printStackTrace();
                    if (subscribedSuccessfully) {
                        subscribeToModelStatesTopic();
                    }
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    void subscribeToOwnershipRequestsTopic() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                boolean subscribedSuccessfully = false;
                try {
                    Stream<Map<String, Object>> stream = access
                            .subscribe(PUBSUB_TOPIC_PREFIX_OWNERSHIP_REQUEST + address);
                    subscribedSuccessfully = true;
                    stream.forEach(map -> {
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
                    if (subscribedSuccessfully) {
                        subscribeToOwnershipRequestsTopic();
                    }
                }
            }
        });
        t.setDaemon(true);
        t.start();
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

    public void setListener(ModelControllerListener listener) {
        this.listener = listener;
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
                    checkPendingUserStatesAndQueues(currentValidatedState);
                }
            }
            else {
                currentValidationContext = new ValidationContext(context, currentValidatedState,
                        currentLocalHashes.keySet(), timestamp, timestampTolerance, secretConfig);
                IPLDObject<ModelState> loaded = loadModelState(multihash, true);
                mergeWithValidated(loaded);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
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
            saveLocalChanges(null, null, null, null, controller, null, System.currentTimeMillis());
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
        document.save(context, signer, document.getProgressListener());
        saveLocalChanges(document, null, null, null, null, null, System.currentTimeMillis());
    }

    public void requestDocumentRemoval(IPLDObject<DocumentRemoval> removal, Signer signer) throws IOException {
        removal.save(context, signer, removal.getProgressListener());
        saveLocalChanges(null, removal, null, null, null, null, System.currentTimeMillis());
    }

    public void issueSettlementRequest(IPLDObject<SettlementRequest> settlementRequest, Signer signer)
            throws IOException {
        settlementRequest.save(context, signer, settlementRequest.getProgressListener());
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
        String topic = PUBSUB_TOPIC_PREFIX_OWNERSHIP_REQUEST + address;
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
        unbanRequest.save(context, signer, unbanRequest.getProgressListener());
        saveLocalChanges(null, null, null, unbanRequest, null, null, System.currentTimeMillis());
    }

    public void addVoting(IPLDObject<Voting> voting, Signer signer) throws IOException {
        voting.save(context, signer, voting.getProgressListener());
        saveLocalChanges(null, null, null, null, null, voting, System.currentTimeMillis());
    }

    public boolean addVote(IPLDObject<Voting> voting, String userHash, int valueIndex, int valueHashObfuscation,
            Signer signer) throws IOException {
        ProgressListener progressListener = voting.getProgressListener();
        if (progressListener != null) {
            progressListener.startedTask(ProgressTask.INIT, 0);
        }
        long timestamp = System.currentTimeMillis();
        Voting updated = voting.getMapped().addVote(userHash, valueIndex, valueHashObfuscation, timestamp,
                timestampTolerance, secretConfig);
        if (progressListener != null) {
            progressListener.finishedTask(ProgressTask.INIT);
        }
        if (updated != null) {
            IPLDObject<Voting> updatedObject = new IPLDObject<>(updated);
            if (progressListener != null) {
                updatedObject.setProgressListener(progressListener);
            }
            updatedObject.save(context, signer, progressListener);
            saveLocalChanges(null, null, null, null, null, updatedObject, timestamp);
            return true;
        }
        return false;
    }

    public boolean tally(IPLDObject<Voting> voting) throws IOException {
        ProgressListener progressListener = voting.getProgressListener();
        if (progressListener != null) {
            progressListener.startedTask(ProgressTask.INIT, voting.getMapped().getProgressSteps());
        }
        long timestamp = System.currentTimeMillis();
        Voting updated = voting.getMapped().tally(timestamp, timestampTolerance, secretConfig, progressListener);
        if (progressListener != null) {
            progressListener.finishedTask(ProgressTask.INIT);
        }
        if (updated != null) {
            IPLDObject<Voting> updatedObject = new IPLDObject<>(updated);
            if (progressListener != null) {
                updatedObject.setProgressListener(progressListener);
            }
            updatedObject.save(context, null, progressListener);
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
            OwnershipTransferController ownershipTransferController, IPLDObject<Voting> voting, long timestamp) {
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
        Map<String, Collection<ProgressListener>> userHashes = new LinkedHashMap<>();
        Map<String, Map<String, IPLDObject<DocumentRemoval>>> documentRemovals = new HashMap<>();
        if (queuedDocumentRemovals != null) {
            dequeue(queuedDocumentRemovals, documentRemovals, UserState.DOCUMENT_REMOVAL_KEY_PROVIDER, userHashes,
                    false);
        }
        if (documentRemoval != null) {
            String userHash = documentRemoval.getMapped().getDocument().getMapped().expectUserState().getUser()
                    .getMultihash();
            if (!addProgressListener(documentRemoval, userHash, userHashes, documentRemovals,
                    UserState.DOCUMENT_REMOVAL_KEY_PROVIDER)) {
                documentRemoval = null;
            }
        }
        SettlementController settlementController = currentSnapshot == null ? null
                : currentSnapshot.createPreEvaluationSnapshot(timestamp);
        Map<String, Map<String, IPLDObject<SettlementRequest>>> settlementRequests = new HashMap<>();
        if (settlementController != null) {
            for (Map<String, IPLDObject<DocumentRemoval>> removals : documentRemovals.values()) {
                for (IPLDObject<DocumentRemoval> removal : removals.values()) {
                    settlementController.checkRemovedDocument(removal.getMapped().getDocument(), currentState);
                }
            }
            boolean settlementChanged = false;
            if (queuedSettlementRequests != null) {
                dequeue(queuedSettlementRequests, settlementRequests, UserState.SETTLEMENT_REQUEST_KEY_PROVIDER,
                        userHashes, false);
                if (settlementRequests.size() > 0) {
                    SettlementRequest sameTimestamp = settlementRequest == null ? null : settlementRequest.getMapped();
                    for (Map<String, IPLDObject<SettlementRequest>> queue : settlementRequests.values()) {
                        for (IPLDObject<SettlementRequest> request : queue.values()) {
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
                String userHash = req.getUserState().getMapped().getUser().getMultihash();
                if (addProgressListener(settlementRequest, userHash, userHashes, settlementRequests,
                        UserState.SETTLEMENT_REQUEST_KEY_PROVIDER)) {
                    if (settlementController.checkRequest(req.getDocument(), false, true)) {
                        settlementChanged = true;
                    }
                }
                else {
                    settlementRequest = null;
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
        Map<String, Map<String, IPLDObject<Document>>> documents = new HashMap<>();
        Map<String, Map<String, IPLDObject<UnbanRequest>>> unbanRequests = new HashMap<>();
        Map<String, Queue<IPLDObject<GrantedUnban>>> grantedUnbans = new HashMap<>();
        Map<String, Queue<IPLDObject<OwnershipRequest>>> ownershipRequests = new HashMap<>();
        Map<String, Map<String, IPLDObject<GrantedOwnership>>> grantedOwnerships = new HashMap<>();
        Map<String, Queue<String>> transferredDocumentHashes = new HashMap<>();
        Collection<OwnershipTransferController> transferControllers = new ArrayList<>();
        Map<String, IPLDObject<Voting>> votings = null;
        Map<String, String[]> newReviewTableEntries = null;
        if (queuedOwnershipTransferControllers != null) {
            synchronized (queuedOwnershipTransferControllers) {
                transferControllers.addAll(queuedOwnershipTransferControllers);
            }
        }
        Collection<ProgressListener> modelStateProgressListeners = new ArrayList<>();
        if (queuedVotings != null) {
            synchronized (queuedVotings) {
                if (queuedVotings.size() > 0) {
                    votings = new HashMap<>();
                    Iterator<Entry<String, IPLDObject<Voting>>> it = queuedVotings.entrySet().iterator();
                    while (it.hasNext()) {
                        Entry<String, IPLDObject<Voting>> entry = it.next();
                        String key = entry.getKey();
                        IPLDObject<Voting> value = entry.getValue();
                        ProgressListener progressListener = value.getProgressListener();
                        if (progressListener == null) {
                            votings.put(key, value);
                        }
                        else if (progressListener.dequeued()) {
                            modelStateProgressListeners.add(progressListener);
                            votings.put(key, value);
                        }
                        else {
                            it.remove();
                        }
                    }
                    if (votings.isEmpty()) {
                        votings = null;
                    }
                }
            }
        }
        if (voting != null) {
            if (addProgressListener(voting, modelStateProgressListeners, false)) {
                if (votings == null) {
                    votings = new HashMap<>();
                }
                votings.put(ModelState.VOTING_KEY_PROVIDER.getKey(voting), voting);
            }
            else {
                voting = null;
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
                        IPLDObject<Voting> ownershipVoting = controller.getVoting();
                        try {
                            ownershipVoting.save(context, null, null);
                        }
                        catch (Exception e) {
                            handleOwnershipTransferControllerException(e, ownershipTransferController, document,
                                    documentRemoval, settlementRequest, unbanRequest, voting);
                            return false;
                        }
                        if (votings == null) {
                            votings = new HashMap<>();
                        }
                        votings.put(ModelState.VOTING_KEY_PROVIDER.getKey(ownershipVoting), ownershipVoting);
                    }
                    else {
                        try {
                            newReviewTableEntries = transferDocument(transferredDocument, controller.getPreviousOwner(),
                                    controller.getNewOwner(), controller.getSignature(), documents,
                                    transferredDocumentHashes, grantedOwnerships, userHashes, newReviewTableEntries,
                                    currentModelState, settlementController);
                        }
                        catch (Exception e) {
                            handleOwnershipTransferControllerException(e, ownershipTransferController, document,
                                    documentRemoval, settlementRequest, unbanRequest, voting);
                            return false;
                        }
                    }
                }
                else {
                    String key = ownershipRequest.getMapped().expectUserHash();
                    try {
                        ownershipRequest.save(context, null, null);
                        Queue<IPLDObject<OwnershipRequest>> ownershipReqs = ownershipRequests.get(key);
                        if (ownershipReqs == null) {
                            ownershipReqs = new ArrayDeque<>();
                            ownershipRequests.put(key, ownershipReqs);
                        }
                        ownershipReqs.add(ownershipRequest);
                        if (!userHashes.containsKey(key)) {
                            userHashes.put(key, null);
                        }
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
            Collection<IPLDObject<Document>> docs = dequeue(queuedDocuments, documents, UserState.DOCUMENT_KEY_PROVIDER,
                    userHashes, settlementController != null);
            if (docs != null) {
                for (IPLDObject<Document> doc : docs) {
                    settlementController.checkDocument(doc, true, null, null, null);
                }
            }
        }
        if (document != null) {
            String userHash = document.getMapped().expectUserState().getUser().getMultihash();
            if (addProgressListener(document, userHash, userHashes, documents, UserState.DOCUMENT_KEY_PROVIDER)) {
                if (settlementController != null) {
                    settlementController.checkDocument(document, true, null, null, null);
                }
            }
            else {
                document = null;
            }
        }
        if (votings != null) {
            for (IPLDObject<Voting> votingObject : votings.values()) {
                Voting vot = votingObject.getMapped();
                IPLDObject<Tally> tally = vot.getTally();
                if (tally != null) {
                    int[] counts = tally.getMapped().getCounts();
                    IPLDObject<Votable> subjectObject = vot.getSubject();
                    Votable subject = subjectObject.getMapped();
                    if (subject instanceof UnbanRequest) {
                        UnbanRequest ureq = (UnbanRequest) subject;
                        if (ureq.isGranted(counts)) {
                            String userHash = ureq.getUserState().getMapped().getUser().getMultihash();
                            @SuppressWarnings("unchecked")
                            GrantedUnban grantedUnban = new GrantedUnban((IPLDObject<UnbanRequest>) subject);
                            Queue<IPLDObject<GrantedUnban>> granted = grantedUnbans.get(userHash);
                            if (granted == null) {
                                granted = new ArrayDeque<>();
                                grantedUnbans.put(userHash, granted);
                            }
                            granted.add(new IPLDObject<>(grantedUnban));
                        }
                    }
                    else if (subject instanceof OwnershipSelection) {
                        OwnershipSelection selection = (OwnershipSelection) subject;
                        IPLDObject<OwnershipRequest> winnerObject = selection.getWinner(counts, vot);
                        OwnershipRequest winner = winnerObject.getMapped();
                        String userHash = winner.getUserState().getMapped().getUser().getMultihash();
                        IPLDObject<Document> docObj = winner.getDocument();
                        Document doc = docObj.getMapped();
                        Map<String, IPLDObject<GrantedOwnership>> granted = grantedOwnerships.get(userHash);
                        if (granted == null || !granted.containsKey(docObj.getMultihash())) {
                            try {
                                newReviewTableEntries = transferDocument(docObj, doc.getUserState(),
                                        modelState.expectUserState(userHash), winnerObject.getForeignSignature(),
                                        documents, transferredDocumentHashes, grantedOwnerships, userHashes,
                                        newReviewTableEntries, currentModelState, settlementController);
                            }
                            catch (Exception e) {
                                handleNoUserStatesSaved(document, documentRemoval, settlementRequest, unbanRequest,
                                        ownershipTransferController, voting);
                                throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
                            }
                        }
                    }
                }
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
                for (String userHash : settlementStates.keySet()) {
                    if (!userHashes.containsKey(userHash)) {
                        userHashes.put(userHash, null);
                    }
                }
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
                    ModelControllerListener listener = this.listener;
                    if (listener != null) {
                        listener.handleInvalidSettlement(invalidSettlementRequests);
                    }
                    return false;
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
                    if (!userHashes.containsKey(key)) {
                        userHashes.put(key, null);
                    }
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
                    if (!userHashes.containsKey(key)) {
                        userHashes.put(key, null);
                    }
                    Map<String, IPLDObject<GrantedOwnership>> granted = grantedOwnerships.get(key);
                    if (granted == null) {
                        granted = new LinkedHashMap<>();
                        grantedOwnerships.put(key, granted);
                    }
                    for (IPLDObject<GrantedOwnership> grantedOwnership : entry.getValue()) {
                        IPLDObject<Document> doc = grantedOwnership.getMapped().getDocument();
                        granted.put(doc.getMapped().getPreviousVersionHash(), grantedOwnership);
                    }
                }
            }
        }
        if (queuedTransferredDocumentHashes != null) {
            synchronized (queuedTransferredDocumentHashes) {
                for (Entry<String, Queue<String>> entry : queuedTransferredDocumentHashes.entrySet()) {
                    String key = entry.getKey();
                    if (!userHashes.containsKey(key)) {
                        userHashes.put(key, null);
                    }
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
            Iterator<Entry<String, IPLDObject<UserState>>> it = updatedUserStates.entrySet().iterator();
            while (it.hasNext()) {
                Entry<String, IPLDObject<UserState>> entry = it.next();
                ProgressListener progressListener = entry.getValue().getProgressListener();
                if (progressListener != null && !progressListener.dequeued()) {
                    checkPendingUserStatesAndQueues(null);
                    return false;
                }
            }
        }
        Map<String, String[]> pendingNewReviewTableEntries = this.pendingNewReviewTableEntries;
        if (pendingNewReviewTableEntries != null) {
            if (newReviewTableEntries == null) {
                newReviewTableEntries = new LinkedHashMap<>(pendingNewReviewTableEntries);
            }
            else { // preserve order
                Map<String, String[]> tmp = newReviewTableEntries;
                newReviewTableEntries = new LinkedHashMap<>(pendingNewReviewTableEntries);
                newReviewTableEntries.putAll(tmp);
            }
            this.pendingNewReviewTableEntries = null;
        }
        if (queuedUnbanRequests != null) {
            dequeue(queuedUnbanRequests, unbanRequests, UserState.UNBAN_REQUEST_KEY_PROVIDER, userHashes, false);
        }
        for (Entry<String, Collection<ProgressListener>> entry : userHashes.entrySet()) {
            String userHash = entry.getKey();
            Collection<ProgressListener> userProgressListeners = entry.getValue();
            if (userProgressListeners == null) {
                userProgressListeners = new ArrayList<>();
            }
            IPLDObject<UserState> userState = modelState.getUserState(userHash);
            if (userState == null && document != null) {
                userState = document.getMapped().getUserState();
            }
            Map<String, IPLDObject<Document>> docs = documents.get(userHash);
            if (queuedDocuments != null) {
                synchronized (queuedDocuments) {
                    queuedDocuments.remove(userHash);
                }
            }
            Map<String, IPLDObject<DocumentRemoval>> removals = documentRemovals.get(userHash);
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
            Map<String, IPLDObject<GrantedOwnership>> granted = grantedOwnerships.get(userHash);
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
            Map<String, IPLDObject<SettlementRequest>> sreqs = settlementRequests.get(userHash);
            if (queuedSettlementRequests != null) {
                synchronized (queuedSettlementRequests) {
                    queuedSettlementRequests.remove(userHash);
                }
            }
            Map<String, IPLDObject<UnbanRequest>> ureqs = unbanRequests.get(userHash);
            if (queuedUnbanRequests != null) {
                synchronized (queuedUnbanRequests) {
                    queuedUnbanRequests.remove(userHash);
                }
            }
            Queue<IPLDObject<GrantedUnban>> unbans = grantedUnbans.get(userHash);

            UserState settlementValues = settlementStates == null ? null : settlementStates.get(userHash);
            if (abortLocalChanges) {
                requeue(userHash, null, docs, removals, sreqs, oreqs, granted, hashes, ureqs, votings);
                handleUserStateUnsaved(updatedUserStates, settlementStates, document, documentRemoval, unbanRequest,
                        newReviewTableEntries);
                return false;
            }
            IPLDObject<UserState> toSave = getInstanceToSave(userState);
            if (docs != null || sreqs != null || oreqs != null || granted != null || hashes != null
                    || settlementValues != null || ureqs != null || unbans != null) {
                try {
                    if (toSave != userState) {
                        ProgressListener progressListener = toSave.getProgressListener();
                        if (progressListener instanceof ForwardingProgressListener) {
                            userProgressListeners.addAll(((ForwardingProgressListener) progressListener).listeners);
                        }
                    }
                    for (ProgressListener listener : userProgressListeners) {
                        listener.startedTask(ProgressTask.LINK_USER, 0); // could check determinate flag and count?
                    }
                    UserState updated = toSave.getMapped().updateLinks(docs, removals, sreqs, oreqs, granted, hashes,
                            sealedDocumentHashes, settlementValues, ureqs, unbans, toSave, userProgressListeners);
                    IPLDObject<UserState> updatedObject = new IPLDObject<>(updated);
                    updatedObject.save(context, null, null);
                    if (userProgressListeners.size() > 0) {
                        try {
                            for (ProgressListener listener : userProgressListeners) {
                                listener.finishedTask(ProgressTask.LINK_USER);
                            }
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                        ForwardingProgressListener listener = new ForwardingProgressListener();
                        listener.listeners = userProgressListeners;
                        // save progress listeners in case of an error later
                        updatedObject.setProgressListener(listener);
                        modelStateProgressListeners.add(listener);
                    }
                    updatedUserStates.put(userHash, updatedObject);
                }
                catch (Exception e) {
                    e.printStackTrace();
                    requeue(userHash, toSave == userState ? null : toSave, docs, removals, sreqs, oreqs, granted,
                            hashes, ureqs, votings);
                    handleUserStateUnsaved(updatedUserStates, settlementStates, document, documentRemoval, unbanRequest,
                            newReviewTableEntries);
                    return false;
                }
                if (docs != null && docs.size() > 0) {
                    for (IPLDObject<Document> doc : docs.values()) {
                        newReviewTableEntries = updateNewReviewTableEntries(doc, newReviewTableEntries);
                    }
                }
            }
        }
        if (abortLocalChanges) {
            handleLocalModelStateUnsaved(updatedUserStates, votings, settlementStates, newReviewTableEntries);
            return false;
        }
        for (ProgressListener progressListener : modelStateProgressListeners) {
            progressListener.startedTask(ProgressTask.LINK_MODEL, 0);
        }
        if (updatedUserStates.isEmpty()) {
            if (votings == null) {
                return false;
            }
            modelState = modelState.updateUserState(null, null, null, votings, null, newReviewTableEntries,
                    currentModelState, timestamp, modelStateProgressListeners);
        }
        else {
            boolean first = true;
            for (Entry<String, IPLDObject<UserState>> entry : updatedUserStates.entrySet()) {
                String key = entry.getKey();
                if (first) {
                    modelState = modelState.updateUserState(entry.getValue(), settlementRequests.get(key),
                            ownershipRequests.get(key), votings, sealedDocuments, newReviewTableEntries,
                            currentModelState, timestamp, modelStateProgressListeners);
                    first = false;
                }
                else {
                    modelState = modelState.updateUserState(entry.getValue(), settlementRequests.get(key),
                            ownershipRequests.get(key), null, null, null, null, timestamp, modelStateProgressListeners);
                }
            }
        }
        IPLDObject<ModelState> newLocalState = new IPLDObject<ModelState>(modelState);
        if (abortLocalChanges) {
            handleLocalModelStateUnsaved(updatedUserStates, votings, settlementStates, newReviewTableEntries);
            return false;
        }
        try {
            currentLocalHashes.put(newLocalState.save(context, null, null), settlementController);
            if (pendingUserStates != null) {
                synchronized (pendingUserStates) {
                    pendingUserStates.clear();
                    if (appliedSettlementData != null) {
                        appliedSettlementData.clear();
                    }
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            handleLocalModelStateUnsaved(updatedUserStates, votings, settlementStates, newReviewTableEntries);
            return false;
        }
        for (ProgressListener progressListener : modelStateProgressListeners) {
            progressListener.finishedTask(ProgressTask.LINK_MODEL);
        }
        publishLocalState(newLocalState);
        return true;
    }

    private Map<String, String[]> transferDocument(IPLDObject<Document> transferredDocument,
            IPLDObject<UserState> previousOwner, IPLDObject<UserState> newOwner, ECDSASignature foreignSignature,
            Map<String, Map<String, IPLDObject<Document>>> documents,
            Map<String, Queue<String>> transferredDocumentHashes,
            Map<String, Map<String, IPLDObject<GrantedOwnership>>> grantedOwnerships,
            Map<String, Collection<ProgressListener>> userHashes, final Map<String, String[]> newReviewTableEntries,
            IPLDObject<ModelState> currentModelState, SettlementController settlementController) throws IOException {
        Document transferredDoc = transferredDocument.getMapped();
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
        if (!userHashes.containsKey(key)) {
            userHashes.put(key, null);
        }
        IPLDObject<Document> transferred = transferredDoc.transferTo(newOwner, transferredDocument, foreignSignature);
        String transferredHash = transferred.save(context, null, null);
        String[] reviewTableEntries = currentModelState.getMapped()
                .getReviewTableEntries(transferredDocument.getMultihash());
        Map<String, String[]> res = newReviewTableEntries;
        if (reviewTableEntries != null) {
            if (res == null) {
                res = new LinkedHashMap<>();
            }
            res.put(transferredHash, reviewTableEntries);
        }
        key = newOwner.getMapped().getUser().getMultihash();
        Map<String, IPLDObject<Document>> docs = documents.get(key);
        if (docs == null) {
            docs = new LinkedHashMap<>();
            documents.put(key, docs);
        }
        docs.put(UserState.DOCUMENT_KEY_PROVIDER.getKey(transferred), transferred);
        if (settlementController != null) {
            settlementController.checkDocument(transferredDocument, true, null, null, null);
        }
        if (!userHashes.containsKey(key)) {
            userHashes.put(key, null);
        }
        Map<String, IPLDObject<GrantedOwnership>> granted = grantedOwnerships.get(key);
        if (granted == null) {
            granted = new LinkedHashMap<>();
            grantedOwnerships.put(key, granted);
        }
        granted.put(firstVersionHash, new IPLDObject<>(new GrantedOwnership(transferred, currentModelState)));
        return res;
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
            Map<String, IPLDObject<UserState>> queue = enqueue(updatedUserStates, pendingUserStates, true);
            if (queue == null) {
                checkPendingUserStatesAndQueues(null);
                return;
            }
            if (pendingUserStates == null) {
                pendingUserStates = queue;
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
                        appliedSettlementData.put(key, update);
                    }
                }
            }
        }
    }

    private void handleLocalModelStateUnsaved(Map<String, IPLDObject<UserState>> updatedUserStates,
            Map<String, IPLDObject<Voting>> votings, Map<String, UserState> settlementStates,
            Map<String, String[]> newReviewTableEntries) {
        if (updatedUserStates.size() > 0) {
            Map<String, IPLDObject<UserState>> queue = enqueue(updatedUserStates, pendingUserStates, true);
            if (queue == null) {
                checkPendingUserStatesAndQueues(null);
                return;
            }
            if (pendingUserStates == null) {
                pendingUserStates = queue;
            }
            updateAppliedSettlementData(settlementStates, updatedUserStates);
        }
        if (votings != null) {
            queuedVotings = enqueue(votings, queuedVotings, false);
        }
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
        String key = document.getMapped().expectUserState().getUser().getMultihash();
        queuedDocuments = enqueue(document, queuedDocuments, key);
    }

    private void enqueueDocumentRemoval(IPLDObject<DocumentRemoval> documentRemoval) {
        String key = documentRemoval.getMapped().getDocument().getMapped().expectUserState().getUser().getMultihash();
        queuedDocumentRemovals = enqueue(documentRemoval, queuedDocumentRemovals, key);
    }

    private void enqueueSettlementRequest(IPLDObject<SettlementRequest> settlementRequest) {
        String key = settlementRequest.getMapped().getUserState().getMapped().getUser().getMultihash();
        queuedSettlementRequests = enqueue(settlementRequest, queuedSettlementRequests, key);
    }

    private void enqueueUnbanRequest(IPLDObject<UnbanRequest> unbanRequest) {
        String key = unbanRequest.getMapped().getUserState().getMapped().getUser().getMultihash();
        queuedUnbanRequests = enqueue(unbanRequest, queuedUnbanRequests, key);
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
            ProgressListener progressListener = voting.getProgressListener();
            if (progressListener == null) {
                queuedVotings.put(ModelState.VOTING_KEY_PROVIDER.getKey(voting), voting);
            }
            else if (!progressListener.isCanceled()) {
                queuedVotings.put(ModelState.VOTING_KEY_PROVIDER.getKey(voting), voting);
                progressListener.enqueued();
            }
        }
    }

    private void requeue(String userHash, IPLDObject<UserState> pending, Map<String, IPLDObject<Document>> documents,
            Map<String, IPLDObject<DocumentRemoval>> removals, Map<String, IPLDObject<SettlementRequest>> sreqs,
            Queue<IPLDObject<OwnershipRequest>> oreqs, Map<String, IPLDObject<GrantedOwnership>> granted,
            Queue<String> transferredOwnershipHashes, Map<String, IPLDObject<UnbanRequest>> unbans,
            Map<String, IPLDObject<Voting>> votings) {
        if (pending != null) {
            synchronized (pendingUserStates) {
                ProgressListener progressListener = pending.getProgressListener();
                if (progressListener == null) {
                    pendingUserStates.put(userHash, pending);
                }
                else if (progressListener.isCanceled()) {
                    checkPendingUserStatesAndQueues(null);
                    return;
                }
                else {
                    pendingUserStates.put(userHash, pending);
                    progressListener.enqueued();
                }
            }
        }
        if (documents != null) {
            queuedDocuments = enqueue(documents, queuedDocuments, userHash);
        }
        if (removals != null) {
            queuedDocumentRemovals = enqueue(removals, queuedDocumentRemovals, userHash);
        }
        if (sreqs != null) {
            queuedSettlementRequests = enqueue(sreqs, queuedSettlementRequests, userHash);
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
                    queuedGrantedOwnerships.put(userHash, new ArrayDeque<>(granted.values()));
                }
                else {
                    queue.addAll(granted.values());
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
            queuedUnbanRequests = enqueue(unbans, queuedUnbanRequests, userHash);
        }
        if (votings != null) {
            if (queuedVotings == null) {
                queuedVotings = new HashMap<>();
            }
            synchronized (queuedVotings) {
                for (Entry<String, IPLDObject<Voting>> entry : votings.entrySet()) {
                    IPLDObject<Voting> value = entry.getValue();
                    ProgressListener progressListener = value.getProgressListener();
                    if (progressListener == null) {
                        queuedVotings.put(entry.getKey(), value);
                    }
                    else if (!progressListener.isCanceled()) {
                        queuedVotings.put(entry.getKey(), value);
                        progressListener.enqueued();
                    }
                }
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
            Map<String, Set<String>> obsoleteReviewVersions = currentValidationContext.getObsoleteReviewVersions();
            SettlementController snapshot = currentValidationContext.getMainSettlementController()
                    .createPreEvaluationSnapshot(0);
            currentLocalHashes.put(validated.getMultihash(), snapshot);
            if (obsoleteReviewVersions.size() == 0 || !localRoot.removeObsoleteReviewVersions(obsoleteReviewVersions)) {
                nextValidatedState = validated;
                this.currentValidatedState = nextValidatedState;
                this.currentSnapshot = snapshot;
                checkPendingUserStatesAndQueues(validated);
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
            String newHash = nextValidatedState.save(context, null, null);
            SettlementController currentSnapshot = currentValidationContext.getMainSettlementController()
                    .createPreEvaluationSnapshot(0);
            currentLocalHashes.put(newHash, currentSnapshot);
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        publishLocalState(nextValidatedState);
        checkPendingUserStatesAndQueues(validated);
        return true;
    }

    private void checkPendingUserStatesAndQueues(IPLDObject<ModelState> validated) {
        if (pendingUserStates != null) {
            synchronized (pendingUserStates) {
                for (IPLDObject<UserState> userState : pendingUserStates.values()) {
                    ProgressListener progressListener = userState.getProgressListener();
                    if (progressListener != null) {
                        progressListener.obsoleted();
                    }
                }
                pendingUserStates.clear();
            }
            if (appliedSettlementData != null) {
                appliedSettlementData.clear();
            }
        }
        pendingNewReviewTableEntries = null;
        if (validated != null) {
            ModelState valid = validated.getMapped();
            if (queuedSettlementRequests != null) {
                synchronized (queuedSettlementRequests) {
                    Iterator<Entry<String, Queue<IPLDObject<SettlementRequest>>>> it = queuedSettlementRequests
                            .entrySet().iterator();
                    while (it.hasNext()) {
                        Entry<String, Queue<IPLDObject<SettlementRequest>>> entry = it.next();
                        Queue<IPLDObject<SettlementRequest>> value = entry.getValue();
                        Iterator<IPLDObject<SettlementRequest>> innerIt = value.iterator();
                        while (innerIt.hasNext()) {
                            IPLDObject<SettlementRequest> settlementRequest = innerIt.next();
                            String documentHash = settlementRequest.getMapped().getDocument().getMultihash();
                            if (valid.getSettlementRequest(documentHash) != null) {
                                innerIt.remove();
                                ProgressListener progressListener = settlementRequest.getProgressListener();
                                if (progressListener != null) {
                                    progressListener.obsoleted();
                                }
                            }
                        }
                        if (value.isEmpty()) {
                            it.remove();
                        }
                    }
                }
            }
            if (queuedUnbanRequests != null) {
                synchronized (queuedUnbanRequests) {
                    Iterator<Entry<String, Queue<IPLDObject<UnbanRequest>>>> it = queuedUnbanRequests.entrySet()
                            .iterator();
                    while (it.hasNext()) {
                        Entry<String, Queue<IPLDObject<UnbanRequest>>> entry = it.next();
                        String userHash = entry.getKey();
                        UserState userState = valid.expectUserState(userHash).getMapped();
                        if (userState.checkRequiredRating()) {
                            for (IPLDObject<UnbanRequest> unbanRequest : entry.getValue()) {
                                ProgressListener progressListener = unbanRequest.getProgressListener();
                                if (progressListener != null) {
                                    progressListener.obsoleted();
                                }
                            }
                            it.remove();
                        }
                    }
                }
            }
            if (queuedOwnershipRequests != null) {
                synchronized (queuedOwnershipRequests) {
                    Iterator<Entry<String, Queue<IPLDObject<OwnershipRequest>>>> it = queuedOwnershipRequests.entrySet()
                            .iterator();
                    while (it.hasNext()) {
                        Entry<String, Queue<IPLDObject<OwnershipRequest>>> entry = it.next();
                        Queue<IPLDObject<OwnershipRequest>> value = entry.getValue();
                        Iterator<IPLDObject<OwnershipRequest>> innerIt = value.iterator();
                        while (innerIt.hasNext()) {
                            IPLDObject<OwnershipRequest> ownershipRequest = innerIt.next();
                            try {
                                new OwnershipTransferController(ownershipRequest, validated, context);
                            }
                            catch (Exception e) {
                                innerIt.remove();
                            }
                        }
                        if (value.isEmpty()) {
                            it.remove();
                        }
                    }
                }
            }
            if (queuedGrantedOwnerships != null) {
                synchronized (queuedGrantedOwnerships) {
                    Map<String, String[]> restoredReviewTableEntries = null;
                    Iterator<Entry<String, Queue<IPLDObject<GrantedOwnership>>>> it = queuedGrantedOwnerships.entrySet()
                            .iterator();
                    while (it.hasNext()) {
                        Entry<String, Queue<IPLDObject<GrantedOwnership>>> entry = it.next();
                        String userHash = entry.getKey();
                        Queue<IPLDObject<GrantedOwnership>> value = entry.getValue();
                        Iterator<IPLDObject<GrantedOwnership>> innerIt = value.iterator();
                        while (innerIt.hasNext()) {
                            IPLDObject<GrantedOwnership> grantedOwnership = innerIt.next();
                            try {
                                new OwnershipTransferController(grantedOwnership, validated, userHash, context,
                                        timestampTolerance);
                                // restore reviewTableEntries for still valid granted ownership
                                GrantedOwnership granted = grantedOwnership.getMapped();
                                IPLDObject<Document> document = granted.getDocument();
                                Document doc = document.getMapped();
                                String[] reviewTableEntries = valid.getReviewTableEntries(doc.getPreviousVersionHash());
                                if (reviewTableEntries != null) {
                                    if (restoredReviewTableEntries == null) {
                                        restoredReviewTableEntries = new LinkedHashMap<>();
                                    }
                                    restoredReviewTableEntries.put(document.getMultihash(), reviewTableEntries);
                                }
                            }
                            catch (Exception e) {
                                GrantedOwnership granted = grantedOwnership.getMapped();
                                IPLDObject<Document> document = granted.getDocument();
                                Document doc = document.getMapped();
                                if (queuedTransferredDocumentHashes != null) {
                                    synchronized (queuedTransferredDocumentHashes) {
                                        String prevOwnerHash = doc.getPreviousVersion().expectUserState().getUser()
                                                .getMultihash();
                                        Queue<String> queue = queuedTransferredDocumentHashes.get(prevOwnerHash);
                                        if (queue != null) {
                                            queue.remove(doc.getFirstVersionHash());
                                        }
                                    }
                                }
                                if (queuedDocuments != null) {
                                    synchronized (queuedDocuments) {
                                        String newOwnerHash = doc.expectUserState().getUser().getMultihash();
                                        Queue<IPLDObject<Document>> queue = queuedDocuments.get(newOwnerHash);
                                        queue.remove(document);
                                    }
                                }
                                innerIt.remove();
                            }
                        }
                        if (value.isEmpty()) {
                            it.remove();
                        }
                    }
                    if (restoredReviewTableEntries != null) {
                        pendingNewReviewTableEntries = restoredReviewTableEntries;
                    }
                }
            }
            if (queuedVotings != null) {
                synchronized (queuedVotings) {
                    Iterator<Entry<String, IPLDObject<Voting>>> it = queuedVotings.entrySet().iterator();
                    while (it.hasNext()) {
                        Entry<String, IPLDObject<Voting>> entry = it.next();
                        String key = entry.getKey();
                        IPLDObject<Voting> validVoting = valid.getVoting(key);
                        boolean remove = validVoting != null;
                        if (!remove) {
                            IPLDObject<Voting> value = entry.getValue();
                            Voting voting = value.getMapped();
                            if (voting.hasVotes()) {
                                remove = true;
                            }
                            else {
                                Votable subject = voting.getSubject().getMapped();
                                if (subject instanceof OwnershipSelection) {
                                    try {
                                        new OwnershipTransferController((OwnershipSelection) subject, validated);
                                    }
                                    catch (Exception e) {
                                        remove = true;
                                    }
                                }
                            }
                        }
                        if (remove) {
                            it.remove();
                            IPLDObject<Voting> value = entry.getValue();
                            ProgressListener progressListener = value.getProgressListener();
                            if (progressListener != null) {
                                progressListener.obsoleted();
                            }
                        }
                    }
                }
            }
            if (queuedOwnershipTransferControllers != null) {
                synchronized (queuedOwnershipTransferControllers) {
                    for (Iterator<OwnershipTransferController> it = queuedOwnershipTransferControllers.iterator(); it
                            .hasNext();) {
                        if (!it.next().processAgain(validated)) {
                            it.remove();
                        }
                    }
                }
            }
        }
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
            access.publish(address, localState.getMultihash());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
