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
import org.projectjinxers.model.GrantedOwnership;
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
        String newHash = access.waitForPublishedMessage(config.getIOTAMainAddress(), 0);
        IPLDObject<ModelState> nextModelState = new IPLDObject<>(newHash, new ModelState(), controller.getContext(),
                null);
        IPLDObject<UserState> nextUserState = nextModelState.getMapped().getUserState(userHash);
        assertNotSame(nextUserState, userState); // copy
        Document doc = nextUserState.getMapped().expectDocument(documentObject.getMultihash());
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
        String newHash = access.waitForPublishedMessage(config.getIOTAMainAddress(), 100);
        IPLDObject<ModelState> nextModelState = new IPLDObject<ModelState>(newHash, new ModelState(),
                controller.getContext(), null);
        IPLDObject<UserState> nextUserState = nextModelState.getMapped().getUserState(userHash);
        assertNotSame(nextUserState, userState); // copy of pending user state from first attempt
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
        assertSame(nextUserState, userState);
        assertNull(nextUserState.getMapped().getDocument(documentObject.getMultihash()));

        // now we allow for saving successfully (and reading from the file)
        access.clearNoSaveFailures();
        // prepare for simulated message
        String[] newHashes = access.readObjects("model/modelController/saveDocument/simple_rec.json");
        access.simulateModelStateMessage(config.getIOTAMainAddress(), newHashes[0]);
        // wait for published hash
        String newHash = access.waitForPublishedMessage(config.getIOTAMainAddress(), 200);
        IPLDObject<ModelState> nextModelState = new IPLDObject<>(newHash, new ModelState(), controller.getContext(),
                null);
        nextUserState = nextModelState.getMapped().getUserState(userHash);
        assertNotSame(nextUserState, userState);
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
        assertSame(nextUserState, userState);
        assertNull(nextUserState.getMapped().getDocument(documentObject.getMultihash()));
        // prepare for simulated message
        // temporarily disable save failures
        access.clearNoSaveFailures();
        String[] newHashes = access.readObjects("model/modelController/saveDocument/simple_rec.json");
        // re-enable save failures
        access.addNoSaveFailure(noSaveFailureHash);
        access.simulateModelStateMessage(config.getIOTAMainAddress(), newHashes[0]);
        // random ownership request (not part of this test, code coverage only - should be effective after merge has
        // been implemented)
        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), userHash, "invalid", false, DEFAULT_SIGNER);
        // wait a bit
        waitFor(100);
        IPLDObject<UserState> nextUserState2 = modelStateObject.getMapped().getUserState(userHash);
        // the save failure simulation is still active
        assertNull(nextUserState2.getMapped().getDocument(documentObject.getMultihash()));
        // prepare for next simulated message
        // now we allow for saving successfully (and reading from the file)
        access.clearNoSaveFailures();
        newHashes = access.readObjects("model/modelController/saveDocument/simple_rec2.json");
        access.simulateModelStateMessage(config.getIOTAMainAddress(), newHashes[0]);
        String newHash = access.waitForPublishedMessage(config.getIOTAMainAddress(), 100);
        IPLDObject<ModelState> nextModelState = new IPLDObject<>(newHash, new ModelState(), controller.getContext(),
                null);
        nextUserState = nextModelState.getMapped().getUserState(userHash);
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

        access.addNoSaveFailure(documentObject, controller.getContext(), DEFAULT_SIGNER); // causes update to be
                                                                                          // deferred
        try {
            controller.saveDocument(documentObject, DEFAULT_SIGNER);
        }
        catch (Exception e) {

        }
        assertNull(userState.getMapped().getDocument(documentObject.getMultihash()));

        // now we restore normal save behavior
        access.clearNoSaveFailures();
        access.simulateModelStateMessage(config.getIOTAMainAddress(), "invalid");
        assertNull(access.waitForPublishedMessage(config.getIOTAMainAddress(), 200));

        // prepare for valid simulated message
        String[] newHashes = access.readObjects("model/modelController/saveDocument/simple_rec.json");
        access.simulateModelStateMessage(config.getIOTAMainAddress(), newHashes[0]);
        String newHash = access.waitForPublishedMessage(config.getIOTAMainAddress(), 100);
        IPLDObject<ModelState> nextModelState = new IPLDObject<>(newHash, new ModelState(), controller.getContext(),
                null);
        IPLDObject<UserState> nextUserState = nextModelState.getMapped().getUserState(userHash);
        assertNotSame(nextUserState, userState); // copy of pending user state from first attempt
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
        assertNotNull(access.waitForPublishedMessage(config.getIOTAMainAddress(), 16000));
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

        String published = access.waitForPublishedMessage(config.getIOTAMainAddress(), 32000);
        assertNotNull(published);
        IPLDObject<ModelState> updated = new IPLDObject<>(published, new ModelState(), controller.getContext(), null);
        ModelState updatedState = updated.getMapped();
        assertNotNull(updatedState.expectUserState(reviewerHash).getMapped().getOwnershipRequest(documentHash));
        assertNotNull(updatedState.expectOwnershipRequests(documentHash));
    }

    @Test
    void testSimpleOwnershipRequestDeferredBySaveFailure() throws Exception {
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

        access.addNoSaveFailure(""); // causes update to be deferred

        Map<String, Object> msg = access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), reviewerHash,
                documentHash, false, NEW_OWNER_SIGNER);
        waitFor(100);
        assertNull(access.getPublishedMessage(config.getIOTAMainAddress()));

        access.clearNoSaveFailures();

        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), msg);

        String published = access.waitForPublishedMessage(config.getIOTAMainAddress(), 400);
        assertNotNull(published);
        IPLDObject<ModelState> updated = new IPLDObject<>(published, new ModelState(), controller.getContext(), null);
        ModelState updatedState = updated.getMapped();
        assertNotNull(updatedState.expectUserState(reviewerHash).getMapped().getOwnershipRequest(documentHash));
        assertNotNull(updatedState.expectOwnershipRequests(documentHash));
    }

    @Test
    void testSimpleOwnershipRequestDeferredTwiceBySaveFailure() throws Exception {
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
        access.addNoSaveFailure(""); // causes update to be deferred

        Map<String, Object> msg = access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), reviewerHash,
                documentHash, false, NEW_OWNER_SIGNER);
        waitFor(100);
        assertNull(access.getPublishedMessage(config.getIOTAMainAddress()));

        IPLDObject<UserState> afterFistFailure = modelState.expectUserState(reviewerHash);
        assertSame(afterFistFailure, reviewerState);
        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), msg);
        waitFor(100);
        assertNull(access.getPublishedMessage(config.getIOTAMainAddress()));

        IPLDObject<UserState> afterSecondFailure = modelState.expectUserState(reviewerHash);
        assertSame(afterSecondFailure, afterFistFailure);

        access.clearNoSaveFailures();
        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), msg);

        String published = access.waitForPublishedMessage(config.getIOTAMainAddress(), 400);
        assertNotNull(published);
        IPLDObject<ModelState> updated = new IPLDObject<>(published, new ModelState(), controller.getContext(), null);
        ModelState updatedState = updated.getMapped();
        assertNotNull(updatedState.expectUserState(reviewerHash).getMapped().getOwnershipRequest(documentHash));
        assertNotNull(updatedState.expectOwnershipRequests(documentHash));
    }

    @Test
    void testExistingOwnershipRequestTooEarly() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/existingReqTooEarly.json");
        String modelStateHash = hashes[2];
        String userHash = hashes[4];
        String documentHash = hashes[10];

        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), userHash, documentHash, false,
                NEW_OWNER_SIGNER);
        assertNull(access.waitForPublishedMessage(config.getIOTAMainAddress(), 200));
    }

    @Test
    void testExistingOwnershipRequestNoContestants() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/existingReq.json");
        String modelStateHash = hashes[2];
        String userHash = hashes[4];
        String documentHash = hashes[10];

        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), userHash, documentHash, false,
                NEW_OWNER_SIGNER);
        String newHash = access.waitForPublishedMessage(config.getIOTAMainAddress(), 400);
        assertNotNull(newHash);
        IPLDObject<ModelState> newLocalState = new IPLDObject<>(newHash, new ModelState(), controller.getContext(),
                null);
        ModelState newLocal = newLocalState.getMapped();
        IPLDObject<UserState> previousOwnerState = newLocal.expectUserState(hashes[0]);
        assertNull(previousOwnerState.getMapped().getDocument(documentHash));
        IPLDObject<UserState> userState = newLocal.expectUserState(userHash);
        IPLDObject<GrantedOwnership> grantedOwnership = userState.getMapped().getGrantedOwnership(documentHash);
        assertNotNull(userState.getMapped().expectDocument(grantedOwnership.getMapped().getDocument().getMultihash()));
    }

    private void waitFor(long millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException e) {

        }
    }

}
