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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.projectjinxers.account.ECCSigner;
import org.projectjinxers.account.Signer;
import org.projectjinxers.account.Users;
import org.projectjinxers.model.Document;
import org.projectjinxers.model.Loader;
import org.projectjinxers.model.LoaderFactory;
import org.projectjinxers.model.ModelState;
import org.projectjinxers.model.Review;
import org.projectjinxers.model.User;
import org.projectjinxers.model.UserState;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;

/**
 * Utility class that contains tests, which print JSON strings of objects, so they can be saved in a file, that can be
 * passed to a {@link TestIPFSAccess} instance. Tests are allowed to fail, since they are excluded from the gradle
 * build.
 * 
 * @author ProjectJinxers
 *
 */
class TestIPFSAccessUtil {

    private static final String[] PRINT_HASHES_FILEPATHS = {
            "model/modelController/transferOwnership/simpleRequest.json" };

    /**
     * Interface for updating models in memory. The updated models will be printed out to the console. Please note, that
     * the JSON files in the resources folder for the tests, don't have to contain a valid overall state. They just
     * represent a good enough state, such that the tests do what they're supposed to do. They might have been edited
     * manually before and/or after using this utility. Reading these files and having certain objects being processed
     * by an updater might lead to crashes.
     * 
     * @author ProjectJinxers
     */
    static interface ModelUpdater {

        /**
         * Updates the model with the given hash in memory. If the model needs to be signed with a different signer,
         * than the main signer in the test method, you can save it here and return null. Non-null return values will be
         * saved using the default signer. Referenced links, that have to be signed with a different signer, must also
         * be signed here. Every updated link must be wrapped in a new IPLDObject instance, otherwise the link will not
         * be saved, since it already has a multihash.
         * 
         * @param hash    the hash
         * @param context the context
         * @return the model to save or null if the model has already been saved or should be removed (if called during
         *         a replace operation)
         * @throws IOException if saving a referenced link or the updated model fails
         */
        IPLDObject<?> update(String hash, IPLDContext context) throws IOException;

    }

    private static final Map<String, ModelUpdater> UPDATERS = new HashMap<>();
    static {
        UPDATERS.put("5f0c79b199ec906bb53db3b856b7c4662a44eb7ce796aea4a41523070f82e707", new ModelUpdater() {
            @Override
            public IPLDObject<?> update(String hash, IPLDContext context) throws IOException {
                Loader<ModelState> loader = LoaderFactory.MODEL_STATE.createLoader();
                IPLDObject<ModelState> modelState = new IPLDObject<>(hash, loader, context, null);
                ModelState mapped = modelState.getMapped();
                IPLDObject<UserState> userState = mapped
                        .expectUserState("4426c8164350e8ec0d2750e2f492aa6016fab43d147810970f25fceb96c69765");
                IPLDObject<Document> document = userState.getMapped()
                        .getDocument("7a54a98b83e5b944578d2da4c17d6e5c673faf8333c2e6ef4f3a5d179a528511");
                Review review = new Review(null, null, null, null, null, null, null, document, Boolean.FALSE, null);
                IPLDObject<UserState> reviewerState = mapped
                        .expectUserState("71b363800b13b92f7bd2262618c192bbfcfd8b21c59ce022f9eb33ea6bfeefa5");
                reviewerState.getMapped().updateLinks(Arrays.asList(new IPLDObject<>(review)), null, null, null);

                mapped.updateUserState(new IPLDObject<>(reviewerState.getMapped()), null, null, null);
                return modelState;
            }
        });
    }

    private static final Signer DEFAULT_SIGNER = new ECCSigner("user", "pass");

    private static final boolean PRETTY_PRINTING = true;
    private static final Gson PRETTY_GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private static final String HEADER = "\n****************************************************************\n%s\n\n";
    private static final String FOOTER = "\n****************************************************************\n";

    private TestIPFSAccess access;
    private IPLDContext context;

    @BeforeEach
    void setup() {
        access = new TestIPFSAccess();
        context = new IPLDContext(access, IPLDEncoding.JSON, IPLDEncoding.JSON, false);
    }

    @Test
    void printHashes() throws FileNotFoundException, IOException {
        for (String filepath : PRINT_HASHES_FILEPATHS) {
            System.out.printf(HEADER, "Hashes of " + filepath);
            String[] hashes = access.readObjects(filepath);
            for (String hash : hashes) {
                System.out.println(hash);
            }
            System.out.println(FOOTER);
        }
    }

    @Test
    void printFileContentsForSingleObject() throws IOException {
        System.out.printf(HEADER, "single");
        ModelState modelState = new ModelState();
        User user = new User("user", Users.createAccount("user", "pass").getPubKey());
        IPLDObject<User> userObject = new IPLDObject<>(user);
        UserState userState = new UserState(userObject, null);
        IPLDObject<UserState> userStateObject = new IPLDObject<>(userState);
        IPLDObject<ModelState> modelStateObject = new IPLDObject<>(modelState);
        modelState.updateUserState(userStateObject, null, null, null);
        Signer signer = new ECCSigner("user", "pass");
        modelStateObject.save(context, signer);
        byte[][] allObjects = access.getAllObjects();
        if (allObjects.length == 1) {
            byte[] singleObject = allObjects[0];
            String json = getJSONString(singleObject);
            System.out.println(json);
        }
        else {
            printObjects(allObjects);
        }
        System.out.println(FOOTER);
    }

    @Test
    void printFileContentsForMultipleObjects() {
        System.out.printf(HEADER, "multiple");
        printObjects(access.getAllObjects());
        System.out.println(FOOTER);
    }

    @Test
    void updateFileContentsForSingleObject() throws FileNotFoundException, IOException {
        String filepath = "model.json";
        System.out.printf(HEADER, "update " + filepath);
        updateObject(filepath, false);
        System.out.println(FOOTER);
    }

    @Test
    void updateFileContentsForMultipleObjects() throws FileNotFoundException, IOException {
        String filepath = "model/modelController/transferOwnership/reclaim.json";
        System.out.printf(HEADER, "update " + filepath);
        updateObjects(filepath, false);
        System.out.println(FOOTER);
    }

    @Test
    void replaceFileContentsForSingleObject() throws FileNotFoundException, IOException {
        String filepath = "model.json";
        System.out.printf(HEADER, "replace " + filepath);
        updateObject(filepath, true);
        System.out.println(FOOTER);
    }

    @Test
    void replaceFileContentsForMultipleObjects() throws FileNotFoundException, IOException {
        String filepath = "model/modelController/transferOwnership/simple.json";
        System.out.printf(HEADER, "replace " + filepath);
        updateObjects(filepath, true);
        System.out.println(FOOTER);
    }

    private void updateObject(String filepath, boolean replace) throws IOException {
        String[] hashes = access.readObjects(filepath);
        Signer signer = DEFAULT_SIGNER;
        String hash = hashes[0];
        if (!updateObject(hash, signer) && replace) {
            access.removeObject(hash);
        }
        byte[][] allObjects = access.getAllObjects();
        if (allObjects.length == 1) {
            String json = getJSONString(allObjects[0]);
            System.out.println(json);
        }
        else {
            printObjects(allObjects);
        }
    }

    private void updateObjects(String filepath, boolean replace) throws FileNotFoundException, IOException {
        String[] hashes = access.readObjects(filepath);
        Signer signer = DEFAULT_SIGNER;
        Set<String> remove = replace ? new HashSet<>() : null;
        for (String hash : hashes) {
            if (!updateObject(hash, signer) && replace) {
                remove.add(hash);
            }
        }
        if (remove != null) {
            for (String hash : remove) {
                access.removeObject(hash);
            }
        }
        printObjects(access.getAllObjects());
    }

    private void printObjects(byte[][] objects) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append('[');
        boolean first = true;
        for (byte[] object : objects) {
            String json = getJSONString(object);
            if (first) {
                first = false;
            }
            else {
                sb.append(',');
            }
            sb.append(json);
        }
        sb.append(']');
        if (PRETTY_PRINTING) {
            System.out.println(getJSONString(sb.toString().getBytes(StandardCharsets.UTF_8)));
        }
        else {
            System.out.println(sb);
        }
    }

    private boolean updateObject(String hash, Signer signer) throws IOException {
        ModelUpdater updater = UPDATERS.get(hash);
        if (updater != null) {
            IPLDObject<?> updated = updater.update(hash, context);
            if (updated != null) {
                updated.save(context, signer);
                return true;
            }
            return false;
        }
        return true;
    }

    private String getJSONString(byte[] bytes) {
        String compact = new String(bytes, StandardCharsets.UTF_8);
        if (PRETTY_PRINTING) {
            return PRETTY_GSON.toJson(JsonParser.parseString(compact));
        }
        return compact;
    }

}
