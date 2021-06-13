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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;
import java.util.Stack;

import org.projectjinxers.account.Signer;
import org.projectjinxers.config.Config;
import org.projectjinxers.model.Document;
import org.projectjinxers.model.ModelState;
import org.projectjinxers.model.OwnershipRequest;
import org.projectjinxers.model.UserState;
import org.projectjinxers.model.ValidationContext;
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

    private IPFSAccess access = new IPFSAccess();
    private IPLDContext context = new IPLDContext(access, IPLDEncoding.JSON, IPLDEncoding.CBOR, false);

    private String mainIOTAAddress;
    private IPLDObject<ModelState> currentValidatedState;

    private Config config;

    private ValidationContext currentValidationContext;

    private boolean validatingModelState;
    private Stack<String> pendingModelStates;
    private Stack<String> pendingOwnershipRequests;
    private IPLDObject<UserState> pendingUserState;
    private Queue<IPLDObject<Document>> queuedDocuments;
    private Queue<IPLDObject<OwnershipRequest>> queuedOwnershipRequests;

    /**
     * Constructor. If it returns without throwing an exception, the instance is completely initialized and continuously
     * listens for model states and ownership requests from peers.
     * 
     * @throws Exception if initialization failed and the application should not continue running.
     */
    public ModelController() throws Exception {
        config = Config.getSharedInstance();
        mainIOTAAddress = config.getIOTAMainAddress();
        String currentModelStateHash;
        try {
            currentModelStateHash = readModelStateHash(mainIOTAAddress);
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
                        saveModelStateHash(currentModelStateHash);
                        break;
                    }
                    catch (Exception e2) {
                        e2.printStackTrace();
                    }
                }
            }
            while (currentModelStateHash != null);
        }
        access.ipfs.pubsub.sub(mainIOTAAddress).forEach(map -> {
            String pubSubData = (String) map.get(PUBSUB_SUB_KEY_DATA);
            handleIncomingModelState(pubSubData);
        });
        access.ipfs.pubsub.sub(PUBSUB_TOPIC_PREFIX_OWNERSHIP_REQUEST + mainIOTAAddress).forEach(map -> {
            String pubSubData = (String) map.get(PUBSUB_SUB_KEY_DATA);
            handleIncomingOwnershipRequest(pubSubData);
        });
    }

    /**
     * @return the current fully validated (or trusted) model state
     */
    public IPLDObject<ModelState> getCurrentValidatedState() {
        return currentValidatedState;
    }

    private String readModelStateHash(String address) throws IOException {
        File storage = new File(address);
        BufferedReader br = new BufferedReader(new FileReader(storage));
        try {
            return br.readLine();
        }
        finally {
            br.close();
        }
    }

    private String readNextModelStateHashFromTangle(String address) {
        return null;
    }

    private IPLDObject<ModelState> loadModelState(String multihash, boolean validate) {
        ModelState modelState = new ModelState();
        IPLDObject<ModelState> object = new IPLDObject<>(multihash, modelState, context,
                validate ? currentValidationContext : null);
        object.getMapped();
        return object;
    }

    private void saveModelStateHash(String address) throws IOException {
        File storage = new File(address);
        BufferedWriter writer = new BufferedWriter(new FileWriter(storage));
        writer.write(address);
        writer.close();
    }

    boolean handleIncomingModelState(String pubSubData) {
        if (validatingModelState) {
            storePotentialModelStateHash(pubSubData);
            return false;
        }
        validatingModelState = true;
        currentValidationContext = new ValidationContext();
        String multihash = convertPubSubDataToMultihash(pubSubData);
        IPLDObject<ModelState> loaded = loadModelState(multihash, true);
        mergeWithValidated(loaded);
        validatingModelState = false;
        processPending();
        return true;
    }

    boolean handleIncomingOwnershipRequest(String pubSubData) {
        if (validatingModelState) {
            storePotentialOwnershipRequestHash(pubSubData);
            return false;
        }
        validatingModelState = true;
        currentValidationContext = new ValidationContext();
        String multihash = convertPubSubDataToMultihash(pubSubData);
        OwnershipRequest request = new OwnershipRequest();
        IPLDObject<OwnershipRequest> object = new IPLDObject<>(multihash, request, context, currentValidationContext);
        object.getMapped();
        // TODO process ownership request
        validatingModelState = false;
        processPending();
        return true;
    }

    private String convertPubSubDataToMultihash(String pubSubData) {
        byte[] bytes = Base64.decode(pubSubData);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void processPending() {
        try {
            executePendingChanges();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        processPendingModelStates();
        processPendingOwnershipRequests();
    }

    public void saveDocument(IPLDObject<Document> document, Signer signer) throws IOException {
        document.save(context, signer);
        saveLocalChanges(document.getMapped().expectUserState().getUser().getMultihash(), document, null);
    }

    public void issueOwnershipRequest(IPLDObject<OwnershipRequest> ownershipRequest, Signer signer) throws IOException {
        ownershipRequest.save(context, signer);
        saveLocalChanges(ownershipRequest.getMapped().expectUserHash(), null, ownershipRequest);
    }

    private IPLDObject<UserState> getInstanceToSave(IPLDObject<UserState> local) {
        if (pendingUserState != null) {
            synchronized (pendingUserState) {
                if (pendingUserState != null) {
                    IPLDObject<UserState> res = pendingUserState;
                    pendingUserState = null;
                    return res;
                }
            }
        }
        return local;
    }

    private boolean saveLocalChanges(String userHash, IPLDObject<Document> document,
            IPLDObject<OwnershipRequest> ownershipRequest) throws IOException {
        IPLDObject<ModelState> currentModelState = currentValidatedState;
        ModelState modelState = currentModelState.getMapped();
        IPLDObject<UserState> userState = modelState.expectUserState(userHash);
        if (currentModelState.beginTransaction(context)) {
            Collection<IPLDObject<Document>> documents = new ArrayList<>();
            if (queuedDocuments != null) {
                synchronized (queuedDocuments) {
                    documents.addAll(queuedDocuments);
                    queuedDocuments.clear();
                }
            }
            if (document != null) {
                documents.add(document);
            }
            Collection<IPLDObject<OwnershipRequest>> requests = new ArrayList<>();
            if (queuedOwnershipRequests != null) {
                synchronized (queuedOwnershipRequests) {
                    requests.addAll(queuedOwnershipRequests);
                    queuedOwnershipRequests.clear();
                }
            }
            if (ownershipRequest != null) {
                requests.add(ownershipRequest);
            }
            boolean userStateTransactionStarted = false;
            boolean userStateSaved = false;
            userState = getInstanceToSave(userState);
            try {
                if (documents.size() > 0 || requests.size() > 0) {
                    userState.beginTransaction(context);
                    userStateTransactionStarted = true;
                    userState.getMapped().addLinks(documents, requests, userState);
                    userState.save(context, null);
                    userStateSaved = true;
                }
                modelState.updateUserState(userState, currentModelState);
                currentModelState.save(context, null);
                if (userStateTransactionStarted) {
                    userState.commit();
                }
                currentModelState.commit();
            }
            catch (Exception e) {
                currentModelState.rollback(context);
                if (userStateTransactionStarted) {
                    if (userStateSaved) {
                        this.pendingUserState = userState;
                    }
                    else {
                        userState.rollback(context);
                        requeue(documents, requests);
                    }
                }
                else {
                    requeue(documents, requests);
                }
                return false;
            }
            if (requests.size() > 0) {
                String topic = PUBSUB_TOPIC_PREFIX_OWNERSHIP_REQUEST + mainIOTAAddress;
                requests.parallelStream().forEach(request -> {
                    try {
                        access.ipfs.pubsub.pub(topic,
                                URLEncoder.encode(request.getMultihash(), StandardCharsets.UTF_8));
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
            return true;
        }
        if (document != null) {
            enqueueDocument(document);
        }
        else if (ownershipRequest != null) {
            enqueueOwnershipRequest(ownershipRequest);
        }
        return false;
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
            queuedDocuments = new ArrayDeque<>();
        }
        synchronized (queuedDocuments) {
            queuedDocuments.add(document);
        }
    }

    private void enqueueOwnershipRequest(IPLDObject<OwnershipRequest> ownershipRequest) {
        if (queuedOwnershipRequests == null) {
            queuedOwnershipRequests = new ArrayDeque<>();
        }
        synchronized (queuedOwnershipRequests) {
            queuedOwnershipRequests.add(ownershipRequest);
        }
    }

    private void requeue(Collection<IPLDObject<Document>> documents,
            Collection<IPLDObject<OwnershipRequest>> requests) {
        if (queuedDocuments != null) {
            synchronized (queuedDocuments) {
                queuedDocuments.addAll(documents);
            }
        }
        if (queuedOwnershipRequests != null) {
            synchronized (queuedOwnershipRequests) {
                queuedOwnershipRequests.addAll(requests);
            }
        }
    }

    private boolean executePendingChanges() throws IOException {
        if (pendingUserState != null) {
            synchronized (pendingUserState) {
                if (pendingUserState != null) {
                    return saveLocalChanges(pendingUserState.getMapped().getUser().getMultihash(), null, null);
                }
            }
        }
        if (queuedDocuments != null) {
            IPLDObject<Document> first;
            synchronized (queuedDocuments) {
                first = queuedDocuments.peek();
            }
            if (first != null) {
                return saveLocalChanges(first.getMapped().expectUserState().getUser().getMultihash(), null, null);
            }
        }
        if (queuedOwnershipRequests != null) {
            IPLDObject<OwnershipRequest> first;
            synchronized (queuedOwnershipRequests) {
                first = queuedOwnershipRequests.peek();
            }
            if (first != null) {
                return saveLocalChanges(first.getMapped().expectUserHash(), null, null);
            }
        }
        return false;
    }

    private void mergeWithValidated(IPLDObject<ModelState> modelState) {
        // TODO: merge with currentValidatedState
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
                    System.out.println("Couldn't handle received ownership request hash: " + pubSubData);
                    e.printStackTrace();
                }
            }
            while (true);
        }
    }

}
