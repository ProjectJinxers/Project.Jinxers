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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.projectjinxers.account.ECCSigner;
import org.projectjinxers.account.Signer;
import org.projectjinxers.config.Config;
import org.projectjinxers.model.Document;
import org.projectjinxers.model.ModelState;
import org.projectjinxers.model.UserState;
import org.spongycastle.util.encoders.Base64;

/**
 * @author ProjectJinxers
 *
 */
class ModelControllerTest {

    private static final Signer DEFAULT_SIGNER = new ECCSigner("user", "pass");
    private static final Signer NEW_OWNER_SIGNER = new ECCSigner("newOwner", "newpass");

    private TestIPFSAccess access;

    @BeforeEach
    void setup() {
        this.access = new TestIPFSAccess();
    }

    @Test
    void testReceiveModelStateWhileValidating() throws Exception {
        String[] hashes = access.readObjects("model/modelController/saveDocument/simple.json");
        final String modelStateHash = hashes[1];
        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null);
        // prepare for simulated message
        String[] newHashes = access.readObjects("model/modelController/saveDocument/simple_rec.json");
        String[] newHashes2 = access.readObjects("model/modelController/saveDocument/simple_rec2.json");
        access.simulateModelStateMessage(config.getIOTAMainAddress(), newHashes[0]);
        controller.handleIncomingModelState(Base64.toBase64String(newHashes2[0].getBytes(StandardCharsets.UTF_8)));

        waitFor(100);
    }

    @Test
    void testSaveDocument() throws Exception {
        String[] hashes = access.readObjects("model/modelController/saveDocument/simple.json");
        final String modelStateHash = hashes[1];
        final String userHash = hashes[0];
        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);
        Document document = new Document("Title", "Subtitle", "Abstract", "Contents", "Version", "Tags", "Source",
                modelState.getUserState(userHash));
        IPLDObject<Document> documentObject = new IPLDObject<Document>(document);
        IPLDObject<UserState> userState = modelState.getUserState(userHash);
        controller.saveDocument(documentObject, DEFAULT_SIGNER);
        IPLDObject<ModelState> nextModelState = controller.getCurrentValidatedState();
        IPLDObject<UserState> nextUserState = modelState.getUserState(userHash);
        assertSame(nextModelState, modelStateObject); // validated model state is not replaced by local operations
        assertSame(nextUserState, userState); // no copy
        Document doc = userState.getMapped().expectDocument(documentObject.getMultihash());
        assertEquals("Title", doc.getTitle());
    }

    @Test
    void testSaveDocumentDeferred() throws Exception {
        String[] hashes = access.readObjects("model/modelController/saveDocument/simple.json");
        final String modelStateHash = hashes[1];
        final String userHash = hashes[0];
        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);
        Document document = new Document("Title", "Subtitle", "Abstract", "Contents", "Version", "Tags", "Source",
                modelState.getUserState(userHash));
        IPLDObject<Document> documentObject = new IPLDObject<Document>(document);
        IPLDObject<UserState> userState = modelState.getUserState(userHash);
        modelStateObject.beginTransaction(controller.getContext()); // causes update to be deferred
        controller.saveDocument(documentObject, DEFAULT_SIGNER);
        assertNull(userState.getMapped().getDocument(documentObject.getMultihash()));
        // prepare for simulated message
        String[] newHashes = access.readObjects("model/modelController/saveDocument/simple_rec.json");
        // now we end the transaction
        modelStateObject.commit();
        access.simulateModelStateMessage(config.getIOTAMainAddress(), newHashes[0]);
        // wait for published hash
        assertNotNull(access.waitForPublishedMessage(config.getIOTAMainAddress(), 100));
        IPLDObject<ModelState> nextModelState = controller.getCurrentValidatedState();
        IPLDObject<UserState> nextUserState = nextModelState.getMapped().getUserState(userHash);
        assertSame(nextModelState, modelStateObject); // validated model state is not replaced by local operations
        assertSame(nextUserState, userState); // pending user state from first attempt
        Document doc = nextUserState.getMapped().expectDocument(documentObject.getMultihash());
        assertEquals("Title", doc.getTitle());
    }

    @Test
    void testSaveDocumentDeferredTwice() throws Exception {
        String[] hashes = access.readObjects("model/modelController/saveDocument/simple.json");
        final String modelStateHash = hashes[1];
        final String userHash = hashes[0];
        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);
        Document document = new Document("Title", "Subtitle", "Abstract", "Contents", "Version", "Tags", "Source",
                modelState.getUserState(userHash));
        IPLDObject<Document> documentObject = new IPLDObject<Document>(document);
        IPLDObject<UserState> userState = modelState.getUserState(userHash);
        modelStateObject.beginTransaction(controller.getContext()); // causes update to be deferred
        controller.saveDocument(documentObject, DEFAULT_SIGNER);
        assertNull(userState.getMapped().getDocument(documentObject.getMultihash()));
        // prepare for simulated message
        String[] newHashes = access.readObjects("model/modelController/saveDocument/simple_rec.json");
        access.simulateModelStateMessage(config.getIOTAMainAddress(), newHashes[0]);
        // wait a bit
        waitFor(100);
        IPLDObject<ModelState> nextModelState = controller.getCurrentValidatedState();
        IPLDObject<UserState> nextUserState = nextModelState.getMapped().getUserState(userHash);
        // we're still in a transaction
        assertNull(userState.getMapped().getDocument(documentObject.getMultihash()));
        // prepare for next simulated message
        newHashes = access.readObjects("model/modelController/saveDocument/simple_rec2.json");
        // now we end the transaction
        modelStateObject.commit();
        access.simulateModelStateMessage(config.getIOTAMainAddress(), newHashes[0]);
        assertNotNull(access.waitForPublishedMessage(config.getIOTAMainAddress(), 100));
        nextModelState = controller.getCurrentValidatedState();
        nextUserState = nextModelState.getMapped().getUserState(userHash);
        assertSame(nextModelState, modelStateObject); // validated model state is not replaced by local operations
        assertSame(nextUserState, userState); // pending user state from first attempt
        Document doc = nextUserState.getMapped().expectDocument(documentObject.getMultihash());
        assertEquals("Title", doc.getTitle());
    }

    @Test
    void testSaveDocumentDeferredByUserStateTransaction() throws Exception {
        String[] hashes = access.readObjects("model/modelController/saveDocument/simple.json");
        final String modelStateHash = hashes[1];
        final String userHash = hashes[0];
        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);
        Document document = new Document("Title", "Subtitle", "Abstract", "Contents", "Version", "Tags", "Source",
                modelState.getUserState(userHash));
        IPLDObject<Document> documentObject = new IPLDObject<Document>(document);
        IPLDObject<UserState> userState = modelState.getUserState(userHash);
        userState.beginTransaction(controller.getContext()); // causes update to be deferred
        controller.saveDocument(documentObject, DEFAULT_SIGNER);
        assertNull(userState.getMapped().getDocument(documentObject.getMultihash()));
        // prepare for simulated message
        String[] newHashes = access.readObjects("model/modelController/saveDocument/simple_rec.json");
        // now we end the transaction
        userState.commit();
        access.simulateModelStateMessage(config.getIOTAMainAddress(), newHashes[0]);
        // wait a bit
        assertNotNull(access.waitForPublishedMessage(config.getIOTAMainAddress(), 200));
        IPLDObject<ModelState> nextModelState = controller.getCurrentValidatedState();
        IPLDObject<UserState> nextUserState = nextModelState.getMapped().getUserState(userHash);
        assertSame(nextModelState, modelStateObject); // validated model state is not replaced by local operations
        assertNotSame(nextUserState, userState); // no pending user state from first attempt
        Document doc = nextUserState.getMapped().expectDocument(documentObject.getMultihash());
        assertEquals("Title", doc.getTitle());
    }

    @Test
    void testSaveDocumentDeferredTwiceByUserStateTransaction() throws Exception {
        String[] hashes = access.readObjects("model/modelController/saveDocument/simple.json");
        final String modelStateHash = hashes[1];
        final String userHash = hashes[0];
        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);
        Document document = new Document("Title", "Subtitle", "Abstract", "Contents", "Version", "Tags", "Source",
                modelState.getUserState(userHash));
        IPLDObject<Document> documentObject = new IPLDObject<Document>(document);
        IPLDObject<UserState> userState = modelState.getUserState(userHash);
        userState.beginTransaction(controller.getContext()); // causes update to be deferred
        controller.saveDocument(documentObject, DEFAULT_SIGNER);
        assertNull(userState.getMapped().getDocument(documentObject.getMultihash()));
        // prepare for simulated message
        String[] newHashes = access.readObjects("model/modelController/saveDocument/simple_rec.json");
        access.simulateModelStateMessage(config.getIOTAMainAddress(), newHashes[0]);
        // wait a bit
        waitFor(100);
        IPLDObject<ModelState> nextModelState = controller.getCurrentValidatedState();
        IPLDObject<UserState> nextUserState = nextModelState.getMapped().getUserState(userHash);
        // we're still in a transaction
        assertNull(userState.getMapped().getDocument(documentObject.getMultihash()));
        // prepare for next simulated message
        newHashes = access.readObjects("model/modelController/saveDocument/simple_rec2.json");
        // now we end the transaction
        userState.commit();
        access.simulateModelStateMessage(config.getIOTAMainAddress(), newHashes[0]);
        assertNotNull(access.waitForPublishedMessage(config.getIOTAMainAddress(), 200));
        nextModelState = controller.getCurrentValidatedState();
        nextUserState = nextModelState.getMapped().getUserState(userHash);
        assertSame(nextModelState, modelStateObject); // validated model state is not replaced by local operations
        assertNotSame(nextUserState, userState); // no pending user state from first attempt
        Document doc = nextUserState.getMapped().expectDocument(documentObject.getMultihash());
        assertEquals("Title", doc.getTitle());
    }

    @Test
    void testSaveDocumentDeferredBySaveFailure() throws Exception {
        String[] hashes = access.readObjects("model/modelController/saveDocument/simple.json");
        final String modelStateHash = hashes[1];
        final String userHash = hashes[0];
        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);
        IPLDObject<UserState> userState = modelState.getUserState(userHash);
        Document document = new Document("Title", "Subtitle", "Abstract", "Contents", "Version", "Tags", "Source",
                userState);
        IPLDObject<Document> documentObject = new IPLDObject<Document>(document);
        // simulate a save failure for everything but the document
        access.addNoSaveFailure(documentObject, controller.getContext(), DEFAULT_SIGNER);
        controller.saveDocument(documentObject, DEFAULT_SIGNER);
        assertNotNull(documentObject.getMultihash());
        IPLDObject<UserState> nextUserState = modelStateObject.getMapped().getUserState(userHash);
        assertNotSame(nextUserState, userState); // rollback deserializes bytes
        assertNull(nextUserState.getMapped().getDocument(documentObject.getMultihash()));

        // now we allow for saving successfully (and reading from the file)
        access.clearNoSaveFailures();
        // prepare for simulated message
        String[] newHashes = access.readObjects("model/modelController/saveDocument/simple_rec.json");
        access.simulateModelStateMessage(config.getIOTAMainAddress(), newHashes[0]);
        // wait for published hash
        assertNotNull(access.waitForPublishedMessage(config.getIOTAMainAddress(), 200));
        IPLDObject<ModelState> nextModelState = controller.getCurrentValidatedState();
        nextUserState = modelStateObject.getMapped().getUserState(userHash);
        assertSame(nextModelState, modelStateObject); // validated model state is not replaced by local operations
        assertNotSame(nextUserState, userState); // no pending user state from first attempt
        Document doc = nextUserState.getMapped().expectDocument(documentObject.getMultihash());
        assertEquals("Title", doc.getTitle());
    }

    @Test
    void testSaveDocumentDeferredTwiceBySaveFailure() throws Exception {
        String[] hashes = access.readObjects("model/modelController/saveDocument/simple.json");
        final String modelStateHash = hashes[1];
        final String userHash = hashes[0];
        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);
        IPLDObject<UserState> userState = modelState.getUserState(userHash);
        Document document = new Document("Title", "Subtitle", "Abstract", "Contents", "Version", "Tags", "Source",
                userState);
        IPLDObject<Document> documentObject = new IPLDObject<Document>(document);
        // simulate a save failure for everything but the document
        String noSaveFailureHash = access.addNoSaveFailure(documentObject, controller.getContext(), DEFAULT_SIGNER);
        controller.saveDocument(documentObject, DEFAULT_SIGNER);
        assertNotNull(documentObject.getMultihash());
        IPLDObject<UserState> nextUserState = modelStateObject.getMapped().getUserState(userHash);
        assertNotSame(nextUserState, userState); // rollback deserializes bytes
        assertNull(nextUserState.getMapped().getDocument(documentObject.getMultihash()));
        // prepare for simulated message
        // temporarily disable save failures
        access.clearNoSaveFailures();
        String[] newHashes = access.readObjects("model/modelController/saveDocument/simple_rec.json");
        // re-enable save failures
        access.addNoSaveFailure(noSaveFailureHash);
        access.simulateModelStateMessage(config.getIOTAMainAddress(), newHashes[0]);
        // wait a bit
        waitFor(100);
        IPLDObject<UserState> nextUserState2 = modelStateObject.getMapped().getUserState(userHash);
        assertNotSame(nextUserState2, nextUserState); // rollback deserializes bytes
        // the save failure simulation is still active
        assertNull(nextUserState2.getMapped().getDocument(documentObject.getMultihash()));
        // prepare for next simulated message
        // now we allow for saving successfully (and reading from the file)
        access.clearNoSaveFailures();
        newHashes = access.readObjects("model/modelController/saveDocument/simple_rec2.json");
        access.simulateModelStateMessage(config.getIOTAMainAddress(), newHashes[0]);
        assertNotNull(access.waitForPublishedMessage(config.getIOTAMainAddress(), 100));
        IPLDObject<ModelState> nextModelState = controller.getCurrentValidatedState();
        nextUserState = modelStateObject.getMapped().getUserState(userHash);
        assertSame(nextModelState, modelStateObject); // validated model state is not replaced by local operations
        assertNotSame(nextUserState, userState); // no pending user state from first attempt
        Document doc = nextUserState.getMapped().expectDocument(documentObject.getMultihash());
        assertEquals("Title", doc.getTitle());
    }

    @Test
    void testSaveDocumentDeferredAfterInvalidModelStateMessage() throws Exception {
        String[] hashes = access.readObjects("model/modelController/saveDocument/simple.json");
        final String modelStateHash = hashes[1];
        final String userHash = hashes[0];
        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);
        Document document = new Document("Title", "Subtitle", "Abstract", "Contents", "Version", "Tags", "Source",
                modelState.getUserState(userHash));
        IPLDObject<Document> documentObject = new IPLDObject<Document>(document);
        IPLDObject<UserState> userState = modelState.getUserState(userHash);
        modelStateObject.beginTransaction(controller.getContext()); // causes update to be deferred
        controller.saveDocument(documentObject, DEFAULT_SIGNER);
        assertNull(userState.getMapped().getDocument(documentObject.getMultihash()));

        // now we end the transaction
        modelStateObject.commit();
        access.simulateModelStateMessage(config.getIOTAMainAddress(), "invalid");
        // wait a bit
        waitFor(100);
        IPLDObject<ModelState> nextModelState = controller.getCurrentValidatedState();
        IPLDObject<UserState> nextUserState = nextModelState.getMapped().getUserState(userHash);
        assertSame(nextModelState, modelStateObject);
        assertSame(nextUserState, userState);
        assertNull(userState.getMapped().getDocument(documentObject.getMultihash()));

        // prepare for valid simulated message
        String[] newHashes = access.readObjects("model/modelController/saveDocument/simple_rec.json");
        access.simulateModelStateMessage(config.getIOTAMainAddress(), newHashes[0]);
        assertNotNull(access.waitForPublishedMessage(config.getIOTAMainAddress(), 100));
        nextModelState = controller.getCurrentValidatedState();
        nextUserState = nextModelState.getMapped().getUserState(userHash);
        assertSame(nextModelState, modelStateObject); // validated model state is not replaced by local operations
        assertSame(nextUserState, userState); // pending user state from first attempt
        Document doc = nextUserState.getMapped().expectDocument(documentObject.getMultihash());
        assertEquals("Title", doc.getTitle());
    }

    @Test
    void testTransferOwnership() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/simple.json");
        final String modelStateHash = hashes[3];
        final String userHash = hashes[5];
        final String documentHash = hashes[2];
        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), userHash, documentHash, false,
                NEW_OWNER_SIGNER);
        // assertNotNull(access.waitForPublishedMessage(config.getIOTAMainAddress(), 200));
    }

    @Test
    void testTransferOwnershipDeferred() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/simple.json");
        final String modelStateHash = hashes[3];
        final String userHash = hashes[5];
        final String documentHash = hashes[2];
        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        modelStateObject.beginTransaction(controller.getContext()); // causes update to be deferred

        Map<String, Object> msg = access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), userHash,
                documentHash, false, NEW_OWNER_SIGNER);
        waitFor(100);
        modelStateObject.commit();

        access.simulateOwnershipRequestMessage(documentHash, msg);
        // assertNotNull(access.waitForPublishedMessage(config.getIOTAMainAddress(), 200));
    }

    @Test
    void testTransferOwnershipDeferredTwice() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/simple.json");
        final String modelStateHash = hashes[3];
        final String userHash = hashes[5];
        final String documentHash = hashes[2];
        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        modelStateObject.beginTransaction(controller.getContext()); // causes update to be deferred

        Map<String, Object> msg = access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), userHash,
                documentHash, false, NEW_OWNER_SIGNER);
        waitFor(100);
        access.simulateOwnershipRequestMessage(documentHash, msg);
        waitFor(100);
        modelStateObject.commit();
        access.simulateOwnershipRequestMessage(documentHash, msg);
        // assertNotNull(access.waitForPublishedMessage(config.getIOTAMainAddress(), 4000));
    }

    @Test
    void testTransferOwnershipReclaim() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/reclaim.json");
        final String modelStateHash = hashes[1];
        final String userHash = hashes[0];
        final String documentHash = hashes[5];
        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), userHash, documentHash, false,
                DEFAULT_SIGNER);
        assertNotNull(access.waitForPublishedMessage(config.getIOTAMainAddress(), 8000));
    }

    @Test
    void testTransferOwnershipReclaimDeferred() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/reclaim.json");
        final String modelStateHash = hashes[1];
        final String userHash = hashes[0];
        final String documentHash = hashes[5];
        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        modelStateObject.beginTransaction(controller.getContext()); // causes update to be deferred

        Map<String, Object> msg = access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), userHash,
                documentHash, false, DEFAULT_SIGNER);
        waitFor(400);

        modelStateObject.commit();
        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), msg);
        assertNotNull(access.waitForPublishedMessage(config.getIOTAMainAddress(), 400));
    }

    @Test
    void testTransferOwnershipReclaimDeferredTwice() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/reclaim.json");
        final String modelStateHash = hashes[1];
        final String userHash = hashes[0];
        final String documentHash = hashes[5];
        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        modelStateObject.beginTransaction(controller.getContext()); // causes update to be deferred

        Map<String, Object> msg = access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), userHash,
                documentHash, false, DEFAULT_SIGNER);
        waitFor(100);

        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), msg);
        waitFor(100);

        modelStateObject.commit();
        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), msg);
        assertNotNull(access.waitForPublishedMessage(config.getIOTAMainAddress(), 400));
    }

    @Test
    void testTransferOwnershipReclaimDeferredByUserStateTransaction() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/reclaim.json");
        final String modelStateHash = hashes[1];
        final String userHash = hashes[0];
        final String documentHash = hashes[5];
        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        IPLDObject<UserState> userState = modelState.expectUserState(hashes[6]);
        userState.beginTransaction(controller.getContext()); // causes update to be deferred

        Map<String, Object> msg = access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), userHash,
                documentHash, false, DEFAULT_SIGNER);
        waitFor(100);
        assertNull(access.getPublishedMessage(config.getIOTAMainAddress()));

        userState.commit();
        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), msg);
        assertNotNull(access.waitForPublishedMessage(config.getIOTAMainAddress(), 400));
    }

    @Test
    void testTransferOwnershipReclaimDeferredTwiceByUserStateTransaction() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/reclaim.json");
        final String modelStateHash = hashes[1];
        final String userHash = hashes[0];
        final String documentHash = hashes[5];
        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        IPLDObject<UserState> userState = modelState.expectUserState(hashes[6]);
        userState.beginTransaction(controller.getContext()); // causes update to be deferred

        Map<String, Object> msg = access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), userHash,
                documentHash, false, DEFAULT_SIGNER);
        waitFor(800);

        assertNull(access.getPublishedMessage(config.getIOTAMainAddress()));
        IPLDObject<UserState> rolledBackUserState = modelState.expectUserState(hashes[6]);
        assertNotSame(rolledBackUserState, userState);
        rolledBackUserState.beginTransaction(controller.getContext());
        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), msg);
        waitFor(800);

        assertNull(access.getPublishedMessage(config.getIOTAMainAddress()));
        IPLDObject<UserState> next = modelState.expectUserState(hashes[6]);
        assertNotSame(next, rolledBackUserState);
        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), msg);
        assertNotNull(access.waitForPublishedMessage(config.getIOTAMainAddress(), 200));
    }

    @Test
    void testTransferOwnershipReclaimDeferredBySaveFailure() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/reclaim.json");
        final String modelStateHash = hashes[1];
        final String userHash = hashes[0];
        final String documentHash = hashes[5];
        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        access.addNoSaveFailure("");

        Map<String, Object> msg = access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), userHash,
                documentHash, false, DEFAULT_SIGNER);
        waitFor(100);

        access.clearNoSaveFailures();
        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), msg);
        assertNotNull(access.waitForPublishedMessage(config.getIOTAMainAddress(), 400));
    }

    @Test
    void testTransferOwnershipReclaimDeferredTwiceBySaveFailure() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/reclaim.json");
        final String modelStateHash = hashes[1];
        final String userHash = hashes[0];
        final String documentHash = hashes[5];
        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        access.addNoSaveFailure("");

        Map<String, Object> msg = access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), userHash,
                documentHash, false, DEFAULT_SIGNER);
        waitFor(100);

        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), msg);
        waitFor(100);

        access.clearNoSaveFailures();
        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), msg);
        assertNotNull(access.waitForPublishedMessage(config.getIOTAMainAddress(), 400));
    }

    @Test
    void testDeclineOwnershipRequestInsufficientRating() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/insufficientRating.json");
        String modelStateHash = hashes[1];
        String reviewerHash = hashes[7];
        String documentHash = hashes[5];

        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), reviewerHash, documentHash, false,
                NEW_OWNER_SIGNER);
        waitFor(100);
        assertNull(modelState.expectUserState(reviewerHash).getMapped().getOwnershipRequest(documentHash));
    }

    @Test
    void testDeclineOwnershipRequestNegReview() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/negReview.json");
        String modelStateHash = hashes[1];
        String reviewerHash = hashes[7];
        String documentHash = hashes[5];

        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), reviewerHash, documentHash, false,
                NEW_OWNER_SIGNER);
        waitFor(100);
        assertNull(modelState.expectUserState(reviewerHash).getMapped().getOwnershipRequest(documentHash));
    }

    @Test
    void testRequestTooEarly() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/reqTooEarly.json");
        String modelStateHash = hashes[1];
        String reviewerHash = hashes[7];
        String documentHash = hashes[5];

        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), reviewerHash, documentHash, false,
                NEW_OWNER_SIGNER);
        waitFor(100);
        assertNull(modelState.expectUserState(reviewerHash).getMapped().getOwnershipRequest(documentHash));
    }

    @Test
    void testSimpleOwnershipRequest() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/simpleRequest.json");
        String modelStateHash = hashes[1];
        String reviewerHash = hashes[7];
        String documentHash = hashes[5];

        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), reviewerHash, documentHash, false,
                NEW_OWNER_SIGNER);
        String published = access.waitForPublishedMessage(config.getIOTAMainAddress(), 16000);
        assertNotNull(published);
        IPLDObject<ModelState> updated = new IPLDObject<>(published, new ModelState(), controller.getContext(), null);
        ModelState updatedState = updated.getMapped();
        assertNotNull(updatedState.expectUserState(reviewerHash).getMapped().getOwnershipRequest(documentHash));
        assertNotNull(updatedState.expectOwnershipRequests(documentHash));
    }

    @Test
    void testSimpleOwnershipRequestDeferred() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/simpleRequest.json");
        String modelStateHash = hashes[1];
        String reviewerHash = hashes[7];
        String documentHash = hashes[5];

        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        modelStateObject.beginTransaction(controller.getContext()); // causes update to be deferred

        Map<String, Object> msg = access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), reviewerHash,
                documentHash, false, NEW_OWNER_SIGNER);
        waitFor(100);
        assertNull(access.getPublishedMessage(config.getIOTAMainAddress()));

        modelStateObject.commit();

        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), msg);

        String published = access.waitForPublishedMessage(config.getIOTAMainAddress(), 200);
        assertNotNull(published);
        IPLDObject<ModelState> updated = new IPLDObject<>(published, new ModelState(), controller.getContext(), null);
        ModelState updatedState = updated.getMapped();
        assertNotNull(updatedState.expectUserState(reviewerHash).getMapped().getOwnershipRequest(documentHash));
        assertNotNull(updatedState.expectOwnershipRequests(documentHash));
    }

    @Test
    void testSimpleOwnershipRequestDeferredTwice() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/simpleRequest.json");
        String modelStateHash = hashes[1];
        String reviewerHash = hashes[7];
        String documentHash = hashes[5];

        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        modelStateObject.beginTransaction(controller.getContext()); // causes update to be deferred

        Map<String, Object> msg = access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), reviewerHash,
                documentHash, false, NEW_OWNER_SIGNER);
        waitFor(100);
        assertNull(access.getPublishedMessage(config.getIOTAMainAddress()));

        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), msg);
        waitFor(100);
        assertNull(access.getPublishedMessage(config.getIOTAMainAddress()));

        modelStateObject.commit();

        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), msg);

        String published = access.waitForPublishedMessage(config.getIOTAMainAddress(), 200);
        assertNotNull(published);
        IPLDObject<ModelState> updated = new IPLDObject<>(published, new ModelState(), controller.getContext(), null);
        ModelState updatedState = updated.getMapped();
        assertNotNull(updatedState.expectUserState(reviewerHash).getMapped().getOwnershipRequest(documentHash));
        assertNotNull(updatedState.expectOwnershipRequests(documentHash));
    }

    @Test
    void testSimpleOwnershipRequestDeferredByUserStateTransaction() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/simpleRequest.json");
        String modelStateHash = hashes[1];
        String reviewerHash = hashes[7];
        String documentHash = hashes[5];

        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        IPLDObject<UserState> reviewerState = modelState.expectUserState(reviewerHash);
        reviewerState.beginTransaction(controller.getContext()); // causes update to be deferred

        Map<String, Object> msg = access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), reviewerHash,
                documentHash, false, NEW_OWNER_SIGNER);
        waitFor(100);
        assertNull(access.getPublishedMessage(config.getIOTAMainAddress()));

        reviewerState.commit();

        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), msg);

        String published = access.waitForPublishedMessage(config.getIOTAMainAddress(), 400);
        assertNotNull(published);
        IPLDObject<ModelState> updated = new IPLDObject<>(published, new ModelState(), controller.getContext(), null);
        ModelState updatedState = updated.getMapped();
        assertNotNull(updatedState.expectUserState(reviewerHash).getMapped().getOwnershipRequest(documentHash));
        assertNotNull(updatedState.expectOwnershipRequests(documentHash));
    }

    @Test
    void testSimpleOwnershipRequestDeferredTwiceByUserStateTransaction() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/simpleRequest.json");
        String modelStateHash = hashes[1];
        String reviewerHash = hashes[7];
        String documentHash = hashes[5];

        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        IPLDObject<UserState> reviewerState = modelState.expectUserState(reviewerHash);
        reviewerState.beginTransaction(controller.getContext()); // causes update to be deferred

        Map<String, Object> msg = access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), reviewerHash,
                documentHash, false, NEW_OWNER_SIGNER);
        waitFor(800);
        assertNull(access.getPublishedMessage(config.getIOTAMainAddress()));

        IPLDObject<UserState> rolledBackReviewerState = modelState.expectUserState(reviewerHash);
        assertNotSame(rolledBackReviewerState, reviewerState);
        rolledBackReviewerState.beginTransaction(controller.getContext());
        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), msg);
        waitFor(800);
        assertNull(access.getPublishedMessage(config.getIOTAMainAddress()));

        IPLDObject<UserState> next = modelState.expectUserState(reviewerHash);
        assertNotSame(next, rolledBackReviewerState);

        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), msg);

        String published = access.waitForPublishedMessage(config.getIOTAMainAddress(), 400);
        assertNotNull(published);
        IPLDObject<ModelState> updated = new IPLDObject<>(published, new ModelState(), controller.getContext(), null);
        ModelState updatedState = updated.getMapped();
        assertNotNull(updatedState.expectUserState(reviewerHash).getMapped().getOwnershipRequest(documentHash));
        assertNotNull(updatedState.expectOwnershipRequests(documentHash));
    }

    private void waitFor(long millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException e) {

        }
    }

}
