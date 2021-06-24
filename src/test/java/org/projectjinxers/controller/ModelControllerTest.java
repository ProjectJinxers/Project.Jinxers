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
        ModelController controller = new ModelController(access, null, 0);
        // prepare for simulated message
        String[] newHashes = access.readObjects("model/modelController/saveDocument/simple_rec.json");
        String[] newHashes2 = access.readObjects("model/modelController/saveDocument/simple_rec2.json");
        access.simulateModelStateMessage(config.getIOTAMainAddress(), newHashes[0]);
        controller.handleIncomingModelState(Base64.toBase64String(newHashes2[0].getBytes(StandardCharsets.UTF_8)), 0);

        waitFor(100);
    }

    @Test
    void testSaveDocument() throws Exception {
        String[] hashes = access.readObjects("model/modelController/saveDocument/simple.json");
        final String modelStateHash = hashes[1];
        final String userHash = hashes[0];
        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null, 0);
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
    void testSaveDocumentDeferredBySaveFailure() throws Exception {
        String[] hashes = access.readObjects("model/modelController/saveDocument/simple.json");
        final String modelStateHash = hashes[1];
        final String userHash = hashes[0];
        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null, 0);
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
        ModelController controller = new ModelController(access, null, 0);
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
        // prepare for simulated message
        // temporarily disable save failures
        access.clearNoSaveFailures();
        String[] newHashes = access.readObjects("model/modelController/saveDocument/simple_rec.json");
        // re-enable save failures
        access.failSaveIn(2); // should be model state
        access.simulateModelStateMessage(config.getIOTAMainAddress(), newHashes[0]);
        // random ownership request (not part of this test, code coverage only - should be effective after merge has
        // been implemented)
        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), userHash, "invalid", false, DEFAULT_SIGNER);
        assertNull(access.waitForPublishedMessage(config.getIOTAMainAddress(), 100));
        // prepare for next simulated message
        newHashes = access.readObjects("model/modelController/saveDocument/simple_rec2.json");
        access.simulateModelStateMessage(config.getIOTAMainAddress(), newHashes[0]);
        do { // the model controller publishes the merged model state first; if we're too slow, the hash will be the
             // merged model state's hash
            String hash = access.waitForPublishedMessage(config.getIOTAMainAddress(), 100);
            IPLDObject<ModelState> nextModelState = new IPLDObject<>(hash, new ModelState(), controller.getContext(),
                    null);
            nextUserState = nextModelState.getMapped().getUserState(userHash);
        }
        while (nextUserState.getMapped().getDocument(documentObject.getMultihash()) == null);
        assertNotSame(nextUserState, userState); // copy of pending user state from first attempt
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
        ModelController controller = new ModelController(access, null, 0);
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
    void testTransferOwnershipReclaim() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/reclaim.json");
        final String modelStateHash = hashes[1];
        final String userHash = hashes[0];
        final String documentHash = hashes[5];
        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null, 0);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), userHash, documentHash, false,
                DEFAULT_SIGNER);
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
        ModelController controller = new ModelController(access, null, 0);
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
        ModelController controller = new ModelController(access, null, 0);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        access.addNoSaveFailure("");

        Map<String, Object> msg = access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), userHash,
                documentHash, false, DEFAULT_SIGNER);
        assertNull(access.waitForPublishedMessage(config.getIOTAMainAddress(), 200));

        access.clearNoSaveFailures();
        access.failSaveIn(3); // should be user state
        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), msg);
        assertNull(access.waitForPublishedMessage(config.getIOTAMainAddress(), 200));

        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), msg);
        assertNotNull(access.waitForPublishedMessage(config.getIOTAMainAddress(), 200));
    }

    @Test
    void testDeclineOwnershipRequestInsufficientRating() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/insufficientRating.json");
        String modelStateHash = hashes[1];
        String reviewerHash = hashes[7];
        String documentHash = hashes[5];

        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null, 0);
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
        ModelController controller = new ModelController(access, null, 0);
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
        ModelController controller = new ModelController(access, null, 0);
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
        ModelController controller = new ModelController(access, null, 0);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), reviewerHash, documentHash, false,
                NEW_OWNER_SIGNER);

        String published = access.waitForPublishedMessage(config.getIOTAMainAddress(), 400);
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
        ModelController controller = new ModelController(access, null, 0);
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
        ModelController controller = new ModelController(access, null, 0);
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

        access.clearNoSaveFailures();
        access.failSaveIn(3); // should be user state
        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), msg);
        waitFor(100);
        assertNull(access.getPublishedMessage(config.getIOTAMainAddress()));

        IPLDObject<UserState> afterSecondFailure = modelState.expectUserState(reviewerHash);
        assertSame(afterSecondFailure, afterFistFailure);

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
        ModelController controller = new ModelController(access, null, 0);
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
        ModelController controller = new ModelController(access, null, 0);
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

    @Test
    void testExistingOwnershipRequestNoContestantsDeferredBySaveFailure() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/existingReq.json");
        String modelStateHash = hashes[2];
        String userHash = hashes[4];
        String documentHash = hashes[10];

        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null, 0);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        access.addNoSaveFailure("");
        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), userHash, documentHash, false,
                NEW_OWNER_SIGNER);
        assertNull(access.waitForPublishedMessage(config.getIOTAMainAddress(), 100));

        access.clearNoSaveFailures();

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

    @Test
    void testExistingOwnershipRequestNoContestantsDeferredTwiceBySaveFailure() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/existingReq.json");
        String modelStateHash = hashes[2];
        String userHash = hashes[4];
        String documentHash = hashes[10];

        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null, 0);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        access.addNoSaveFailure("");
        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), userHash, documentHash, false,
                NEW_OWNER_SIGNER);
        assertNull(access.waitForPublishedMessage(config.getIOTAMainAddress(), 100));

        access.clearNoSaveFailures();
        access.failSaveIn(4); // should be model state

        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), userHash, documentHash, false,
                NEW_OWNER_SIGNER);
        assertNull(access.waitForPublishedMessage(config.getIOTAMainAddress(), 100));

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

    /**
     * c18afbb976f8f247df60f510ff423f2339783bc602c01efe13b815a8256dc53c
     * 4426c8164350e8ec0d2750e2f492aa6016fab43d147810970f25fceb96c69765
     * fe80c488b6972d6f62e2f1a6bc5f9f458e6bbbb65d5c61f4b7d9be38d2aff9ff
     * 2b4a67b1561d030e90659f75e782189f16a93d0118a71070047d80f6149ad5ea
     * c26324283499d6db7799f6625d4ece1dd995a4d91a5f956bdf7429211a2cd176
     * 71b363800b13b92f7bd2262618c192bbfcfd8b21c59ce022f9eb33ea6bfeefa5
     * 29668aebf20e5a3996df3c49744a1845f012891df59633f1efe910f8d83a55ea
     * ffed413a96602bead1ac0403f20c6ee5a11864b86805958b564900ac450e667c
     * 1b97d858818d3891e406a3b7adcac4cb7198aaf43bad9663dd71954d14733f06
     * b144ebcf4f6ebd47881a15f8c17b3c6712abd84ea5960bbe3566630618273618
     * c850f91a7e427f81bef839960b0f85f94d8039ca132dc436464d2600ff699c23
     * b792365bb534de0294402bbbcf6a07df67e57a3c5c72e5c68a4f4531e0aa3b30
     * 109538171df26adb1f1e7ff0e55b777f6e52de8190db13cb39e6c87383c82e96
     * 3bc8fd34da22696d3b51304718a7bab7c6cd013490a70d061a8d9cce8e03fc64
     * ea13ce9d0779365eb2e7b86a54d97b4810a5c06603337945d89d1c0cf295bdbe
     * a9c4cd4686e45f8f019a3d0274aebf4c573200ccccd0517587119c23eacaa449
     * 
     * @throws Exception
     */
    @Test
    void testExistingOwnershipRequestOneActiveContestant() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/existingReqs.json");
        String modelStateHash = hashes[15];
        String userHash = hashes[5];
        String documentHash = hashes[12];

        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null, 0);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), userHash, documentHash, false,
                NEW_OWNER_SIGNER);
        String newHash = access.waitForPublishedMessage(config.getIOTAMainAddress(), 200);
        assertNotNull(newHash);
        IPLDObject<ModelState> newLocalState = new IPLDObject<>(newHash, new ModelState(), controller.getContext(),
                null);
        ModelState newLocal = newLocalState.getMapped();
        assertNotNull(newLocal.getVotingForOwnershipTransfer(documentHash));
    }

    @Test
    void testExistingOwnershipRequestOneActiveContestantDeferredBySaveFailure() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/existingReqs.json");
        String modelStateHash = hashes[15];
        String userHash = hashes[5];
        String documentHash = hashes[12];

        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null, 0);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        access.addNoSaveFailure("");
        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), userHash, documentHash, false,
                NEW_OWNER_SIGNER);
        assertNull(access.waitForPublishedMessage(config.getIOTAMainAddress(), 100));

        access.clearNoSaveFailures();
        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), userHash, documentHash, false,
                NEW_OWNER_SIGNER);

        String newHash = access.waitForPublishedMessage(config.getIOTAMainAddress(), 200);
        assertNotNull(newHash);
        IPLDObject<ModelState> newLocalState = new IPLDObject<>(newHash, new ModelState(), controller.getContext(),
                null);
        ModelState newLocal = newLocalState.getMapped();
        assertNotNull(newLocal.getVotingForOwnershipTransfer(documentHash));
    }

    @Test
    void testExistingOwnershipRequestOneActiveContestantDeferredTwiceBySaveFailure() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/existingReqs.json");
        String modelStateHash = hashes[15];
        String userHash = hashes[5];
        String documentHash = hashes[12];

        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null, 0);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        access.addNoSaveFailure("");
        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), userHash, documentHash, false,
                NEW_OWNER_SIGNER);
        assertNull(access.waitForPublishedMessage(config.getIOTAMainAddress(), 100));

        access.clearNoSaveFailures();
        access.failSaveIn(5); // should be model state
        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), userHash, documentHash, false,
                NEW_OWNER_SIGNER);
        assertNull(access.waitForPublishedMessage(config.getIOTAMainAddress(), 100));

        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), userHash, documentHash, false,
                NEW_OWNER_SIGNER);
        String newHash = access.waitForPublishedMessage(config.getIOTAMainAddress(), 200);
        assertNotNull(newHash);
        IPLDObject<ModelState> newLocalState = new IPLDObject<>(newHash, new ModelState(), controller.getContext(),
                null);
        ModelState newLocal = newLocalState.getMapped();
        assertNotNull(newLocal.getVotingForOwnershipTransfer(documentHash));
    }

    private void waitFor(long millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException e) {

        }
    }

}
