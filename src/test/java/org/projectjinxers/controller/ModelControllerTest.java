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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.projectjinxers.account.ECCSigner;
import org.projectjinxers.account.Signer;
import org.projectjinxers.config.Config;
import org.projectjinxers.model.Document;
import org.projectjinxers.model.ModelState;
import org.projectjinxers.model.UserState;

/**
 * @author ProjectJinxers
 *
 */
class ModelControllerTest {

    private TestIPFSAccess access;

    @BeforeEach
    void setup() {
        this.access = new TestIPFSAccess();
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
        Signer signer = new ECCSigner("user", "pass");
        IPLDObject<UserState> userState = modelState.getUserState(userHash);
        controller.saveDocument(documentObject, signer);
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
        Signer signer = new ECCSigner("user", "pass");
        IPLDObject<UserState> userState = modelState.getUserState(userHash);
        modelStateObject.beginTransaction(controller.getContext()); // causes update to be deferred
        controller.saveDocument(documentObject, signer);
        assertNull(userState.getMapped().getDocument(documentObject.getMultihash()));
        // prepare for simulated message
        String[] newHashes = access.readObjects("model/modelController/saveDocument/simple_rec.json");
        // now we end the transaction
        modelStateObject.commit();
        access.simulateModelStateMessage(config.getIOTAMainAddress(), newHashes[0]);
        // wait a bit
        waitFor(100);
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
        Signer signer = new ECCSigner("user", "pass");
        IPLDObject<UserState> userState = modelState.getUserState(userHash);
        modelStateObject.beginTransaction(controller.getContext()); // causes update to be deferred
        controller.saveDocument(documentObject, signer);
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
        waitFor(100);
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
        Signer signer = new ECCSigner("user", "pass");
        IPLDObject<UserState> userState = modelState.getUserState(userHash);
        userState.beginTransaction(controller.getContext()); // causes update to be deferred
        controller.saveDocument(documentObject, signer);
        assertNull(userState.getMapped().getDocument(documentObject.getMultihash()));
        // prepare for simulated message
        String[] newHashes = access.readObjects("model/modelController/saveDocument/simple_rec.json");
        // now we end the transaction
        userState.commit();
        access.simulateModelStateMessage(config.getIOTAMainAddress(), newHashes[0]);
        // wait a bit
        waitFor(100);
        IPLDObject<ModelState> nextModelState = controller.getCurrentValidatedState();
        IPLDObject<UserState> nextUserState = nextModelState.getMapped().getUserState(userHash);
        assertSame(nextModelState, modelStateObject); // validated model state is not replaced by local operations
        assertSame(nextUserState, userState); // pending user state from first attempt
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
        Signer signer = new ECCSigner("user", "pass");
        IPLDObject<UserState> userState = modelState.getUserState(userHash);
        userState.beginTransaction(controller.getContext()); // causes update to be deferred
        controller.saveDocument(documentObject, signer);
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
        waitFor(100);
        nextModelState = controller.getCurrentValidatedState();
        nextUserState = nextModelState.getMapped().getUserState(userHash);
        assertSame(nextModelState, modelStateObject); // validated model state is not replaced by local operations
        assertSame(nextUserState, userState); // pending user state from first attempt
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
        Signer signer = new ECCSigner("user", "pass");
        // simulate a save failure for everything but the document
        access.addNoSaveFailure(documentObject, controller.getContext(), signer);
        controller.saveDocument(documentObject, signer);
        assertNotNull(documentObject.getMultihash());
        IPLDObject<UserState> nextUserState = modelStateObject.getMapped().getUserState(userHash);
        assertNotSame(nextUserState, userState); // rollback deserializes bytes
        assertNull(nextUserState.getMapped().getDocument(documentObject.getMultihash()));

        // now we allow for saving successfully (and reading from the file)
        access.clearNoSaveFailures();
        // prepare for simulated message
        String[] newHashes = access.readObjects("model/modelController/saveDocument/simple_rec.json");
        access.simulateModelStateMessage(config.getIOTAMainAddress(), newHashes[0]);
        // wait a bit
        waitFor(100);
        IPLDObject<ModelState> nextModelState = controller.getCurrentValidatedState();
        nextUserState = modelStateObject.getMapped().getUserState(userHash);
        assertSame(nextModelState, modelStateObject); // validated model state is not replaced by local operations
        assertSame(nextUserState, userState); // pending user state from first attempt
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
        Signer signer = new ECCSigner("user", "pass");
        // simulate a save failure for everything but the document
        String noSaveFailureHash = access.addNoSaveFailure(documentObject, controller.getContext(), signer);
        controller.saveDocument(documentObject, signer);
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
        waitFor(100);
        IPLDObject<ModelState> nextModelState = controller.getCurrentValidatedState();
        nextUserState = modelStateObject.getMapped().getUserState(userHash);
        assertSame(nextModelState, modelStateObject); // validated model state is not replaced by local operations
        assertSame(nextUserState, userState); // pending user state from first attempt
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
        Signer signer = new ECCSigner("user", "pass");
        IPLDObject<UserState> userState = modelState.getUserState(userHash);
        modelStateObject.beginTransaction(controller.getContext()); // causes update to be deferred
        controller.saveDocument(documentObject, signer);
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
        waitFor(100);
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
        final String modelStateHash = hashes[4];
        final String userHash = hashes[7];
        final String documentHash = hashes[3];
        Config config = Config.getSharedInstance();
        access.saveModelStateHash(config.getIOTAMainAddress(), modelStateHash);
        ModelController controller = new ModelController(access, null);
        IPLDObject<ModelState> modelStateObject = controller.getCurrentValidatedState();
        ModelState modelState = modelStateObject.getMapped();
        assertNotNull(modelState);
        access.simulateOwnershipRequestMessage(config.getIOTAMainAddress(), userHash, documentHash,
                new ECCSigner("newOwner", "newpass"));
        waitFor(100);
    }

    private void waitFor(long millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException e) {

        }
    }

}
