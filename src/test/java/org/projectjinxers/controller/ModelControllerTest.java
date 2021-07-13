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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.projectjinxers.controller.TestIPFSAccessUtil.DEFAULT_SIGNER;
import static org.projectjinxers.controller.TestIPFSAccessUtil.DEFAULT_SIGNER_SECURITY_LEVEL_1;
import static org.projectjinxers.controller.TestIPFSAccessUtil.NEW_OWNER_SIGNER;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.projectjinxers.account.Users;
import org.projectjinxers.config.Config;
import org.projectjinxers.model.Document;
import org.projectjinxers.model.DocumentContents;
import org.projectjinxers.model.DocumentRemoval;
import org.projectjinxers.model.GrantedOwnership;
import org.projectjinxers.model.ModelState;
import org.projectjinxers.model.SealedDocument;
import org.projectjinxers.model.SettlementRequest;
import org.projectjinxers.model.User;
import org.projectjinxers.model.UserState;
import org.spongycastle.util.encoders.Base64;

/**
 * @author ProjectJinxers
 *
 */
class ModelControllerTest {

    private static final Field MODEL_CONTROLLERS_FIELD;
    static {
        try {
            MODEL_CONTROLLERS_FIELD = ModelController.class.getDeclaredField("MODEL_CONTROLLERS");
            MODEL_CONTROLLERS_FIELD.setAccessible(true);
        }
        catch (NoSuchFieldException | SecurityException e) {
            throw new ExceptionInInitializerError();
        }
    }

    private TestIPFSAccess access;

    @BeforeEach
    void setup() throws IllegalArgumentException, IllegalAccessException {
        this.access = new TestIPFSAccess();
        ((Map<?, ?>) MODEL_CONTROLLERS_FIELD.get(null)).clear();
    }

    @Test
    void testReceiveModelStateWhileValidating() throws Exception {
        String[] hashes = access.readObjects("model/modelController/saveDocument/simple.json");
        final String modelStateHash = hashes[1];
        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAAddress(), modelStateHash);
        ModelController controller = ModelController.getModelController(access, null);
        // prepare for simulated message
        String[] newHashes = access.readObjects("model/modelController/saveDocument/simple_rec.json");
        String[] newHashes2 = access.readObjects("model/modelController/saveDocument/simple_rec2.json");
        access.simulateModelStateMessage(config.getIOTAAddress(), newHashes[0]);
        controller.handleIncomingModelState(Base64.toBase64String(newHashes2[0].getBytes(StandardCharsets.UTF_8)), 0);

        waitFor(100);
    }

    @Test
    void testFirstActionEver() throws Exception {
        ModelController controller = ModelController.getModelController(access, null);
        User user = new User("user", Users.createAccount("user", "pass", 1).getPubKey());
        UserState userState = new UserState(new IPLDObject<>(user));
        DocumentContents contents = new DocumentContents("Abstract", "Contents");
        Document document = new Document("Title", null, null, null, null, new IPLDObject<>(contents),
                new IPLDObject<>(userState));
        IPLDObject<Document> documentObject = new IPLDObject<Document>(document);
        controller.saveDocument(documentObject, DEFAULT_SIGNER_SECURITY_LEVEL_1);
        String newHash = waitForPublishedAndSimulateDirectConfirmation(100, 100);
        IPLDObject<ModelState> firstEverModelState = new IPLDObject<>(newHash, new ModelState(),
                controller.getContext(), new ValidationContext(controller.getContext(), null, null,
                        System.currentTimeMillis(), Config.DEFAULT_TIMESTAMP_TOLERANCE, null));
        controller.getContext().clearCache();
        ModelState firstEver = firstEverModelState.getMapped();
        assertNull(firstEver.getPreviousVersion());
        Set<Entry<String, IPLDObject<UserState>>> allUserStateEntries = firstEver.getAllUserStateEntries();
        assertEquals(1, allUserStateEntries.size());
        Entry<String, IPLDObject<UserState>> singleEntry = allUserStateEntries.iterator().next();
        UserState loadedUserState = singleEntry.getValue().getMapped();
        Map<String, IPLDObject<Document>> allDocuments = loadedUserState.getAllDocuments();
        assertEquals(1, allDocuments.size());
        Document loadedDocument = allDocuments.values().iterator().next().getMapped();
        assertNull(loadedDocument.getPreviousVersion());
        assertNull(loadedDocument.getFirstVersionHash());
        assertEquals("Title", loadedDocument.getTitle());
        assertNotNull(loadedDocument.getContents());
    }

    @Test
    void testSaveDocument() throws Exception {
        String[] hashes = access.readObjects("model/modelController/saveDocument/simple.json");
        final String modelStateHash = hashes[1];
        final String userHash = hashes[0];
        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAAddress(), modelStateHash);
        ModelController controller = ModelController.getModelController(access, null);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);
        Document document = new Document("Title", "Subtitle", "Version", "Tags", "Source", null,
                modelState.getUserState(userHash));
        IPLDObject<Document> documentObject = new IPLDObject<Document>(document);
        IPLDObject<UserState> userState = modelState.getUserState(userHash);
        controller.saveDocument(documentObject, DEFAULT_SIGNER);
        String newHash = access.waitForPublishedMessage(config.getIOTAAddress(), 100);
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
        access.saveModelStateHash(config.getIOTAAddress(), modelStateHash);
        ModelController controller = ModelController.getModelController(access, null);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);
        IPLDObject<UserState> userState = modelState.getUserState(userHash);
        Document document = new Document("Title", "Subtitle", "Version", "Tags", "Source", null, userState);
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
        access.simulateModelStateMessage(config.getIOTAAddress(), newHashes[0]);
        // wait for published hash
        String newHash = access.waitForPublishedMessage(config.getIOTAAddress(), 200);
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
        access.saveModelStateHash(config.getIOTAAddress(), modelStateHash);
        ModelController controller = ModelController.getModelController(access, null);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);
        IPLDObject<UserState> userState = modelState.getUserState(userHash);
        Document document = new Document("Title", "Subtitle", "Version", "Tags", "Source", null, userState);
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
        access.simulateModelStateMessage(config.getIOTAAddress(), newHashes[0]);
        // random ownership request (not part of this test, code coverage only)
        access.simulateOwnershipRequestMessage(config.getIOTAAddress(), userHash, "invalid", false, DEFAULT_SIGNER);
        assertNull(access.waitForPublishedMessage(config.getIOTAAddress(), 100));
        // prepare for next simulated message
        newHashes = access.readObjects("model/modelController/saveDocument/simple_rec2.json");
        access.simulateModelStateMessage(config.getIOTAAddress(), newHashes[0]);
        String hash = access.waitForPublishedMessage(config.getIOTAAddress(), 100);
        IPLDObject<ModelState> nextModelState = new IPLDObject<>(hash, new ModelState(), controller.getContext(), null);
        nextUserState = nextModelState.getMapped().getUserState(userHash);
        assertNull(nextUserState.getMapped().getDocument(documentObject.getMultihash())); // merge state without local
                                                                                          // changes
        assertNull(access.waitForPublishedMessage(config.getIOTAAddress(), 400)); // pending cleared after merge
    }

    @Test
    void testSaveDocumentDeferredAfterInvalidModelStateMessage() throws Exception {
        String[] hashes = access.readObjects("model/modelController/saveDocument/simple.json");
        final String modelStateHash = hashes[1];
        final String userHash = hashes[0];
        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAAddress(), modelStateHash);
        ModelController controller = ModelController.getModelController(access, null);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);
        Document document = new Document("Title", "Subtitle", "Version", "Tags", "Source", null,
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

        access.simulateModelStateMessage(config.getIOTAAddress(), "invalid");
        assertNull(access.waitForPublishedMessage(config.getIOTAAddress(), 200));

        // now we restore normal save behavior
        access.clearNoSaveFailures();
        // prepare for valid simulated message
        String[] newHashes = access.readObjects("model/modelController/saveDocument/simple_rec.json");
        access.simulateModelStateMessage(config.getIOTAAddress(), newHashes[0]);
        String newHash = access.waitForPublishedMessage(config.getIOTAAddress(), 100);
        IPLDObject<ModelState> nextModelState = new IPLDObject<>(newHash, new ModelState(), controller.getContext(),
                null);
        IPLDObject<UserState> nextUserState = nextModelState.getMapped().getUserState(userHash);
        assertNotSame(nextUserState, userState); // copy of pending user state from first attempt
        Document doc = nextUserState.getMapped().expectDocument(documentObject.getMultihash());
        assertEquals("Title", doc.getTitle());
    }

    @Test
    void testDocumentRemoval() throws Exception {
        String[] hashes = access.readObjects("model/modelController/removeDocument/twoUsers.json");
        final String modelStateHash = hashes[9];
        final String userHash = hashes[0];
        final String documentHash = hashes[2];
        ModelController controller = ModelController.getModelController(access, null);
        assertNull(validateInitialModelState(modelStateHash, 400));
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);
        IPLDObject<UserState> userState = modelState.expectUserState(userHash);
        IPLDObject<Document> document = userState.getMapped().expectDocumentObject(documentHash);
        controller.requestDocumentRemoval(new IPLDObject<>(new DocumentRemoval(document)), DEFAULT_SIGNER);
    }

    @Test
    void testEligibleSettlementRequest() throws Exception {
        String[] validHashes = access.readObjects("model/modelController/baseStates/twentyfourUsers.json");
        final String validModelStateHash = validHashes[12];
        Config config = Config.getSharedInstance();
        ModelController controller = ModelController.getModelController(access, config);
        assertNull(validateInitialModelState(validModelStateHash, 400));
        String[] hashes = access.readObjects("model/modelController/settlement/eligible.json");
        final String modelStateHash = hashes[27];
        final String userHash = hashes[16];
        final String documentHash = hashes[20];
        access.simulateModelStateMessage(config.getIOTAAddress(), modelStateHash);

        waitForPublishedAndSimulateDirectConfirmation(400, 100);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        IPLDObject<UserState> userState = modelState.expectUserState(userHash);
        IPLDObject<Document> document = userState.getMapped().expectDocumentObject(documentHash);
        SettlementRequest request = new SettlementRequest(System.currentTimeMillis(), document, userState);
        controller.issueSettlementRequest(new IPLDObject<>(request), DEFAULT_SIGNER);

        assertNotNull(access.waitForPublishedMessage(config.getIOTAAddress(), 100));
    }

    @Test
    void testEligibleSettlementRequestDeferredBySaveFailure() throws Exception {
        String[] validHashes = access.readObjects("model/modelController/baseStates/twentyfourUsers.json");
        final String validModelStateHash = validHashes[12];
        Config config = Config.getSharedInstance();
        ModelController controller = ModelController.getModelController(access, config);
        assertNull(validateInitialModelState(validModelStateHash, 400));
        String[] hashes = access.readObjects("model/modelController/settlement/eligible.json");
        final String modelStateHash = hashes[27];
        final String userHash = hashes[16];
        final String documentHash = hashes[20];
        access.simulateModelStateMessage(config.getIOTAAddress(), modelStateHash);
        String newHash = waitForPublishedAndSimulateDirectConfirmation(800, 100);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        IPLDObject<UserState> userState = modelState.expectUserState(userHash);
        IPLDObject<Document> document = userState.getMapped().expectDocumentObject(documentHash);
        SettlementRequest request = new SettlementRequest(System.currentTimeMillis(), document, userState);

        access.failSaveIn(2);
        controller.issueSettlementRequest(new IPLDObject<>(request), DEFAULT_SIGNER);

        assertNull(access.waitForPublishedMessage(config.getIOTAAddress(), 100));

        // trigger next local save (shouldn't change anything prior)
        access.simulateModelStateMessage(config.getIOTAAddress(), newHash);
        assertNotNull(access.waitForPublishedMessage(config.getIOTAAddress(), 100));
    }

    @Test
    void testEligibleSettlementRequestDeferredTwiceBySaveFailure() throws Exception {
        String[] validHashes = access.readObjects("model/modelController/baseStates/twentyfourUsers.json");
        final String validModelStateHash = validHashes[12];
        Config config = Config.getSharedInstance();
        ModelController controller = ModelController.getModelController(access, config);
        assertNull(validateInitialModelState(validModelStateHash, 400));
        String[] hashes = access.readObjects("model/modelController/settlement/eligible.json");
        final String modelStateHash = hashes[27];
        final String userHash = hashes[16];
        final String documentHash = hashes[20];
        access.simulateModelStateMessage(config.getIOTAAddress(), modelStateHash);
        String newHash = waitForPublishedAndSimulateDirectConfirmation(400, 100);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        IPLDObject<UserState> userState = modelState.expectUserState(userHash);
        IPLDObject<Document> document = userState.getMapped().expectDocumentObject(documentHash);
        SettlementRequest request = new SettlementRequest(System.currentTimeMillis(), document, userState);

        access.failSaveIn(2);
        controller.issueSettlementRequest(new IPLDObject<>(request), DEFAULT_SIGNER);

        assertNull(access.waitForPublishedMessage(config.getIOTAAddress(), 100));

        access.failSaveIn(1);
        // trigger next local save (shouldn't change anything prior)
        access.simulateModelStateMessage(config.getIOTAAddress(), newHash);
        assertNull(access.waitForPublishedMessage(config.getIOTAAddress(), 100));

        // trigger next local save (shouldn't change anything prior)
        access.simulateModelStateMessage(config.getIOTAAddress(), newHash);
        assertNotNull(access.waitForPublishedMessage(config.getIOTAAddress(), 100));
    }

    @Test
    void testExecutedSettlementRequest() throws Exception {
        String[] hashes = access.readObjects("model/modelController/settlement/validRequest.json");
        final String validModelStateHash = hashes[17];
        Config config = Config.getSharedInstance();
        ModelController controller = ModelController.getModelController(access, config);
        validateInitialModelState(validModelStateHash, 400);

        hashes = access.readObjects("model/modelController/settlement/sealed_20_3.json");
        final String modelStateHash = hashes[104];
        final String documentHash = hashes[69];
        access.simulateModelStateMessage(config.getIOTAAddress(), modelStateHash);
        assertNull(access.waitForPublishedMessage(config.getIOTAAddress(), 400)); // trivial merge
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);
        assertNotNull(modelState.expectSealedDocument(documentHash));
    }

    @Test
    void testTruthInversion() throws Exception { // 03cc091eab564683138a2134eb19107ba082c4865a36f8d218d0af4c0821e236
        String[] validHashes = access.readObjects("model/modelController/settlement/validRequest.json");
        final String validModelStateHash = validHashes[17];
        Config config = Config.getSharedInstance();
        ModelController controller = ModelController.getModelController(access, config);
        validateInitialModelState(validModelStateHash, 400);
        // 1625204576909
        String[] hashes = access.readObjects("model/modelController/settlement/sealed_20_3_1.json");
        String modelStateHash = hashes[40];
        final String documentHash = hashes[72];
        access.simulateModelStateMessage(config.getIOTAAddress(), modelStateHash);
        assertNull(access.waitForPublishedMessage(config.getIOTAAddress(), 400)); // trivial merge
        hashes = access.readObjects("model/modelController/settlement/validTruthInversion.json");
        modelStateHash = hashes[155];
        access.simulateModelStateMessage(config.getIOTAAddress(), modelStateHash);
        waitForPublishedAndSimulateDirectConfirmation(400, 0); // obsolete review versions

        hashes = access.readObjects("model/modelController/settlement/sealedTruthInversion.json");
        modelStateHash = hashes[161]; // final String userHash = hashes[29]; final String documentHash = hashes[66];
        access.simulateModelStateMessage(config.getIOTAAddress(), modelStateHash);

        assertNull(access.waitForPublishedMessage(config.getIOTAAddress(), 400));

        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        SealedDocument sealed = modelStateObject.getMapped().expectSealedDocument(documentHash);
        assertTrue(sealed.isTruthInverted());
    }

    @Test
    void testBlockedSettlementRequest() throws Exception {
        String[] hashes = access.readObjects("model/modelController/settlement/validRequest.json");
        final String validModelStateHash = hashes[17];
        Config config = Config.getSharedInstance();
        ModelController controller = ModelController.getModelController(access, config);
        validateInitialModelState(validModelStateHash, 400);

        hashes = access.readObjects("model/modelController/settlement/blocked_20_3_1.json");
        final String modelStateHash = hashes[4];
        final String documentHash = hashes[68];
        access.simulateModelStateMessage(config.getIOTAAddress(), modelStateHash);
        assertNull(access.waitForPublishedMessage(config.getIOTAAddress(), 400)); // trivial merge
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        assertNotEquals(validModelStateHash, modelStateObject.getMultihash());
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);
        assertFalse(modelState.isSealedDocument(documentHash));
    }

    @Test
    void testTransferOwnershipReclaim() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/reclaim.json");
        final String modelStateHash = hashes[0];
        final String userHash = hashes[1];
        final String documentHash = hashes[6];
        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAAddress(), modelStateHash);
        ModelController controller = ModelController.getModelController(access, config);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        access.simulateOwnershipRequestMessage(config.getIOTAAddress(), userHash, documentHash, false, DEFAULT_SIGNER);
        assertNotNull(access.waitForPublishedMessage(config.getIOTAAddress(), 400));
    }

    @Test
    void testTransferOwnershipReclaimDeferredBySaveFailure() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/reclaim.json");
        final String modelStateHash = hashes[0];
        final String userHash = hashes[1];
        final String documentHash = hashes[6];
        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAAddress(), modelStateHash);
        ModelController controller = ModelController.getModelController(access, config);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        access.addNoSaveFailure("");

        Map<String, Object> msg = access.simulateOwnershipRequestMessage(config.getIOTAAddress(), userHash,
                documentHash, false, DEFAULT_SIGNER);
        waitFor(100);

        access.clearNoSaveFailures();
        access.simulateOwnershipRequestMessage(config.getIOTAAddress(), msg);
        assertNotNull(access.waitForPublishedMessage(config.getIOTAAddress(), 400));
    }

    @Test
    void testTransferOwnershipReclaimDeferredTwiceBySaveFailure() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/reclaim.json");
        final String modelStateHash = hashes[0];
        final String userHash = hashes[1];
        final String documentHash = hashes[6];
        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAAddress(), modelStateHash);
        ModelController controller = ModelController.getModelController(access, config);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        access.addNoSaveFailure("");

        Map<String, Object> msg = access.simulateOwnershipRequestMessage(config.getIOTAAddress(), userHash,
                documentHash, false, DEFAULT_SIGNER);
        assertNull(access.waitForPublishedMessage(config.getIOTAAddress(), 200));

        access.clearNoSaveFailures();
        access.failSaveIn(3); // should be user state
        access.simulateOwnershipRequestMessage(config.getIOTAAddress(), msg);
        assertNull(access.waitForPublishedMessage(config.getIOTAAddress(), 200));

        access.simulateOwnershipRequestMessage(config.getIOTAAddress(), msg);
        assertNotNull(access.waitForPublishedMessage(config.getIOTAAddress(), 200));
    }

    @Test
    void testDeclineOwnershipRequestInsufficientRating() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/insufficientRating.json");
        String modelStateHash = hashes[1];
        String reviewerHash = hashes[7];
        String documentHash = hashes[5];

        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAAddress(), modelStateHash);
        ModelController controller = ModelController.getModelController(access, config);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        access.simulateOwnershipRequestMessage(config.getIOTAAddress(), reviewerHash, documentHash, false,
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
        access.saveModelStateHash(config.getIOTAAddress(), modelStateHash);
        ModelController controller = ModelController.getModelController(access, config);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        access.simulateOwnershipRequestMessage(config.getIOTAAddress(), reviewerHash, documentHash, false,
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
        access.saveModelStateHash(config.getIOTAAddress(), modelStateHash);
        ModelController controller = ModelController.getModelController(access, config);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        access.simulateOwnershipRequestMessage(config.getIOTAAddress(), reviewerHash, documentHash, false,
                NEW_OWNER_SIGNER);
        waitFor(100);
        assertNull(modelState.expectUserState(reviewerHash).getMapped().getOwnershipRequest(documentHash));
    }

    @Test
    void testSimpleOwnershipRequest() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/simpleRequest.json");
        String modelStateHash = hashes[8];
        String reviewerHash = hashes[6];
        String documentHash = hashes[0];

        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAAddress(), modelStateHash);
        ModelController controller = ModelController.getModelController(access, config);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        access.simulateOwnershipRequestMessage(config.getIOTAAddress(), reviewerHash, documentHash, false,
                NEW_OWNER_SIGNER);

        String published = access.waitForPublishedMessage(config.getIOTAAddress(), 400);
        assertNotNull(published);
        IPLDObject<ModelState> updated = new IPLDObject<>(published, new ModelState(), controller.getContext(), null);
        ModelState updatedState = updated.getMapped();
        assertNotNull(updatedState.expectUserState(reviewerHash).getMapped().getOwnershipRequest(documentHash));
        assertNotNull(updatedState.expectOwnershipRequests(documentHash));
    }

    @Test
    void testSimpleOwnershipRequestDeferredBySaveFailure() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/simpleRequest.json");
        String modelStateHash = hashes[8];
        String reviewerHash = hashes[6];
        String documentHash = hashes[0];

        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAAddress(), modelStateHash);
        ModelController controller = ModelController.getModelController(access, config);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        access.addNoSaveFailure(""); // causes update to be deferred

        Map<String, Object> msg = access.simulateOwnershipRequestMessage(config.getIOTAAddress(), reviewerHash,
                documentHash, false, NEW_OWNER_SIGNER);
        waitFor(100);
        assertNull(access.getPublishedMessage(config.getIOTAAddress()));

        access.clearNoSaveFailures();

        access.simulateOwnershipRequestMessage(config.getIOTAAddress(), msg);

        String published = access.waitForPublishedMessage(config.getIOTAAddress(), 400);
        assertNotNull(published);
        IPLDObject<ModelState> updated = new IPLDObject<>(published, new ModelState(), controller.getContext(), null);
        ModelState updatedState = updated.getMapped();
        assertNotNull(updatedState.expectUserState(reviewerHash).getMapped().getOwnershipRequest(documentHash));
        assertNotNull(updatedState.expectOwnershipRequests(documentHash));
    }

    @Test
    void testSimpleOwnershipRequestDeferredTwiceBySaveFailure() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/simpleRequest.json");
        String modelStateHash = hashes[8];
        String reviewerHash = hashes[6];
        String documentHash = hashes[0];

        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAAddress(), modelStateHash);
        ModelController controller = ModelController.getModelController(access, config);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        IPLDObject<UserState> reviewerState = modelState.expectUserState(reviewerHash);
        access.addNoSaveFailure(""); // causes update to be deferred

        Map<String, Object> msg = access.simulateOwnershipRequestMessage(config.getIOTAAddress(), reviewerHash,
                documentHash, false, NEW_OWNER_SIGNER);
        waitFor(100);
        assertNull(access.getPublishedMessage(config.getIOTAAddress()));

        IPLDObject<UserState> afterFistFailure = modelState.expectUserState(reviewerHash);
        assertSame(afterFistFailure, reviewerState);

        access.clearNoSaveFailures();
        access.failSaveIn(3); // should be user state
        access.simulateOwnershipRequestMessage(config.getIOTAAddress(), msg);
        waitFor(100);
        assertNull(access.getPublishedMessage(config.getIOTAAddress()));

        IPLDObject<UserState> afterSecondFailure = modelState.expectUserState(reviewerHash);
        assertSame(afterSecondFailure, afterFistFailure);

        access.simulateOwnershipRequestMessage(config.getIOTAAddress(), msg);

        String published = access.waitForPublishedMessage(config.getIOTAAddress(), 400);
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
        access.saveModelStateHash(config.getIOTAAddress(), modelStateHash);
        ModelController controller = ModelController.getModelController(access, config);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        access.simulateOwnershipRequestMessage(config.getIOTAAddress(), userHash, documentHash, false,
                NEW_OWNER_SIGNER);
        assertNull(access.waitForPublishedMessage(config.getIOTAAddress(), 200));
    }

    @Test
    void testExistingOwnershipRequestNoContestants() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/existingReq.json");
        String modelStateHash = hashes[2];
        String userHash = hashes[4];
        String documentHash = hashes[10];

        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAAddress(), modelStateHash);
        ModelController controller = ModelController.getModelController(access, config);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        access.simulateOwnershipRequestMessage(config.getIOTAAddress(), userHash, documentHash, false,
                NEW_OWNER_SIGNER);
        String newHash = access.waitForPublishedMessage(config.getIOTAAddress(), 400);
        assertNotNull(newHash);
        IPLDObject<ModelState> newLocalState = new IPLDObject<>(newHash, new ModelState(), controller.getContext(),
                null);
        ModelState newLocal = newLocalState.getMapped();
        IPLDObject<UserState> previousOwnerState = newLocal.expectUserState(hashes[0]);
        assertNull(previousOwnerState.getMapped().getDocument(documentHash));
        IPLDObject<UserState> userState = newLocal.expectUserState(userHash);
        IPLDObject<GrantedOwnership> grantedOwnership = userState.getMapped().getGrantedOwnership(documentHash);
        assertNotNull(userState.getMapped()
                .expectDocument(grantedOwnership.getMapped().getDocument().getMapped().getFirstVersionHash()));
    }

    @Test
    void testExistingOwnershipRequestNoContestantsDeferredBySaveFailure() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/existingReq.json");
        String modelStateHash = hashes[2];
        String userHash = hashes[4];
        String documentHash = hashes[10];

        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAAddress(), modelStateHash);
        ModelController controller = ModelController.getModelController(access, config);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        access.addNoSaveFailure("");
        access.simulateOwnershipRequestMessage(config.getIOTAAddress(), userHash, documentHash, false,
                NEW_OWNER_SIGNER);
        assertNull(access.waitForPublishedMessage(config.getIOTAAddress(), 100));

        access.clearNoSaveFailures();

        access.simulateOwnershipRequestMessage(config.getIOTAAddress(), userHash, documentHash, false,
                NEW_OWNER_SIGNER);
        String newHash = access.waitForPublishedMessage(config.getIOTAAddress(), 400);
        assertNotNull(newHash);
        IPLDObject<ModelState> newLocalState = new IPLDObject<>(newHash, new ModelState(), controller.getContext(),
                null);
        ModelState newLocal = newLocalState.getMapped();
        IPLDObject<UserState> previousOwnerState = newLocal.expectUserState(hashes[0]);
        assertNull(previousOwnerState.getMapped().getDocument(documentHash));
        IPLDObject<UserState> userState = newLocal.expectUserState(userHash);
        IPLDObject<GrantedOwnership> grantedOwnership = userState.getMapped().getGrantedOwnership(documentHash);
        assertNotNull(userState.getMapped()
                .expectDocument(grantedOwnership.getMapped().getDocument().getMapped().getFirstVersionHash()));
    }

    @Test
    void testExistingOwnershipRequestNoContestantsDeferredTwiceBySaveFailure() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/existingReq.json");
        String modelStateHash = hashes[2];
        String userHash = hashes[4];
        String documentHash = hashes[10];

        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAAddress(), modelStateHash);
        ModelController controller = ModelController.getModelController(access, config);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        access.addNoSaveFailure("");
        access.simulateOwnershipRequestMessage(config.getIOTAAddress(), userHash, documentHash, false,
                NEW_OWNER_SIGNER);
        assertNull(access.waitForPublishedMessage(config.getIOTAAddress(), 100));

        access.clearNoSaveFailures();
        access.failSaveIn(6); // should be model state

        access.simulateOwnershipRequestMessage(config.getIOTAAddress(), userHash, documentHash, false,
                NEW_OWNER_SIGNER);
        assertNull(access.waitForPublishedMessage(config.getIOTAAddress(), 100));

        access.simulateOwnershipRequestMessage(config.getIOTAAddress(), userHash, documentHash, false,
                NEW_OWNER_SIGNER);

        String newHash = access.waitForPublishedMessage(config.getIOTAAddress(), 400);
        assertNotNull(newHash);
        IPLDObject<ModelState> newLocalState = new IPLDObject<>(newHash, new ModelState(), controller.getContext(),
                null);
        ModelState newLocal = newLocalState.getMapped();
        IPLDObject<UserState> previousOwnerState = newLocal.expectUserState(hashes[0]);
        assertNull(previousOwnerState.getMapped().getDocument(documentHash));
        IPLDObject<UserState> userState = newLocal.expectUserState(userHash);
        IPLDObject<GrantedOwnership> grantedOwnership = userState.getMapped().getGrantedOwnership(documentHash);
        assertNotNull(userState.getMapped()
                .expectDocument(grantedOwnership.getMapped().getDocument().getMapped().getFirstVersionHash()));
    }

    @Test
    void testExistingOwnershipRequestOneActiveContestant() throws Exception {
        String[] hashes = access.readObjects("model/modelController/transferOwnership/existingReqs.json");
        String modelStateHash = hashes[15];
        String userHash = hashes[5];
        String documentHash = hashes[12];

        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAAddress(), modelStateHash);
        ModelController controller = ModelController.getModelController(access, config);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        access.simulateOwnershipRequestMessage(config.getIOTAAddress(), userHash, documentHash, false,
                NEW_OWNER_SIGNER);
        String newHash = access.waitForPublishedMessage(config.getIOTAAddress(), 200);
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
        access.saveModelStateHash(config.getIOTAAddress(), modelStateHash);
        ModelController controller = ModelController.getModelController(access, config);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        access.addNoSaveFailure("");
        access.simulateOwnershipRequestMessage(config.getIOTAAddress(), userHash, documentHash, false,
                NEW_OWNER_SIGNER);
        assertNull(access.waitForPublishedMessage(config.getIOTAAddress(), 100));

        access.clearNoSaveFailures();
        access.simulateOwnershipRequestMessage(config.getIOTAAddress(), userHash, documentHash, false,
                NEW_OWNER_SIGNER);

        String newHash = access.waitForPublishedMessage(config.getIOTAAddress(), 200);
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
        access.saveModelStateHash(config.getIOTAAddress(), modelStateHash);
        ModelController controller = ModelController.getModelController(access, config);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);

        access.addNoSaveFailure("");
        access.simulateOwnershipRequestMessage(config.getIOTAAddress(), userHash, documentHash, false,
                NEW_OWNER_SIGNER);
        assertNull(access.waitForPublishedMessage(config.getIOTAAddress(), 100));

        access.clearNoSaveFailures();
        access.failSaveIn(5); // should be model state
        access.simulateOwnershipRequestMessage(config.getIOTAAddress(), userHash, documentHash, false,
                NEW_OWNER_SIGNER);
        assertNull(access.waitForPublishedMessage(config.getIOTAAddress(), 100));

        access.simulateOwnershipRequestMessage(config.getIOTAAddress(), userHash, documentHash, false,
                NEW_OWNER_SIGNER);
        String newHash = access.waitForPublishedMessage(config.getIOTAAddress(), 200);
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

    private String validateInitialModelState(String hash, long waitAfter) {
        waitFor(100);
        String address = Config.getSharedInstance().getIOTAAddress();
        access.simulateModelStateMessage(address, hash);
        return access.waitForPublishedMessage(address, waitAfter);
    }

    private String waitForPublishedAndSimulateDirectConfirmation(long waitForPublished, long waitAfterConfirmation) {
        String address = Config.getSharedInstance().getIOTAAddress();
        String newHash = access.waitForPublishedMessage(address, waitForPublished);
        access.simulateModelStateMessage(address, newHash);
        if (waitAfterConfirmation > 0) {
            waitFor(waitAfterConfirmation);
        }
        return newHash;
    }

}
