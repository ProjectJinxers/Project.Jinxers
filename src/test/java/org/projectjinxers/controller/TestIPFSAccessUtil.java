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
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.projectjinxers.account.ECCSigner;
import org.projectjinxers.account.Signer;
import org.projectjinxers.account.Users;
import org.projectjinxers.model.Document;
import org.projectjinxers.model.IPLDSerializable;
import org.projectjinxers.model.LoaderFactory;
import org.projectjinxers.model.ModelState;
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
            "model/modelController/baseStates/fourUsers.json" };

    private static final String UPDATE_FILE_CONTENTS_SINGLE_PATH = null;
    private static final String UPDATE_FILE_CONTENTS_MULTIPLE_PATH = "model/modelController/baseStates/fourUsers.json";
    private static final String REPLACE_FILE_CONTENTS_SINGLE_PATH = null;
    private static final String REPLACE_FILE_CONTENTS_MULTIPLE_PATH = null;

    /**
     * If not null, and this hash is encountered during replacing, the in memory models will be cleared and re-populated
     * by saving the root again (after resetting all multihashes).
     */
    private static final String REPLACE_ROOT_HASH = "aad7535fc00a12a500224335e42f502fcaf809754b620324a3f0df3966aaa529";
    private static final LoaderFactory<?> ROOT_LOADER_FACTORY = LoaderFactory.MODEL_STATE;

    private static Field DOCUMENT_DATE_FIELD;
    {
        try {
            DOCUMENT_DATE_FIELD = Document.class.getDeclaredField("date");
            DOCUMENT_DATE_FIELD.setAccessible(true);
        }
        catch (NoSuchFieldException | SecurityException e) {
            e.printStackTrace();
        }
    }

    private static final long REQUIRED_OWNERSHIP_TRANSFER_INACTIVITY = 1000L * 60 * 60 * 24 * 30;
    private static final Date ELIGIBLE_FOR_OWNERSHIP_TRANSFER = new Date(
            System.currentTimeMillis() - REQUIRED_OWNERSHIP_TRANSFER_INACTIVITY);

    public static final Signer DEFAULT_SIGNER = new ECCSigner("user", "pass");
    public static final Signer NEW_OWNER_SIGNER = new ECCSigner("newOwner", "newpass");

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
         * be signed here. If the root replacement feature of this class is not used, every updated link must be wrapped
         * in a new IPLDObject instance, otherwise the link will not be saved, since it already has a multihash. The
         * returned instance should always be a new instance without multihash, regardless of the root replacement
         * feature.
         * 
         * @param hash    the hash
         * @param context the context
         * @return the model to save or null if the model has already been saved or should be removed (if called during
         *         a replace operation)
         * @throws IOException              if saving a referenced link or the updated model fails
         * @throws IllegalAccessException   if access by reflection fails
         * @throws IllegalArgumentException if access by reflection fails
         */
        IPLDObject<?> update(String hash, IPLDContext context, Set<String> removeHashes)
                throws IOException, IllegalArgumentException, IllegalAccessException;

    }

    private static final Map<String, ModelUpdater> UPDATERS = new HashMap<>();
    static {
        UPDATERS.put("c1cf8466bcb5a624138e843755193822c75c0bb1347b60073766ec42bf3ad060", new ModelUpdater() {

            @Override
            public IPLDObject<?> update(String hash, IPLDContext context, Set<String> removeHashes)
                    throws IOException, IllegalArgumentException, IllegalAccessException {
                IPLDObject<ModelState> modelState = new IPLDObject<>(hash, new ModelState(), context, null);
                IPLDObject<ModelState> updatedState = null;
                for (int i = 5; i < 25; i++) {
                    String username = "user" + i;
                    String password = "pass" + i;
                    User user = new User(username, Users.createAccount(username, password).getPubKey());
                    UserState userState = new UserState(new IPLDObject<User>(user));
                    IPLDObject<UserState> userStateObject = new IPLDObject<UserState>(userState);
                    userStateObject.save(context, new ECCSigner(username, password));
                    ModelState updated = modelState.getMapped().updateUserState(userStateObject, null, null, null, null,
                            null, i == 5 ? modelState : null, System.currentTimeMillis());
                    if (updated != modelState.getMapped()) {
                        updatedState = new IPLDObject<>(updated);
                    }
                }
                updatedState.save(context, null);
                return modelState;
            }

        });
    }

    private static final boolean PRETTY_PRINTING = true;
    private static final Gson PRETTY_GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private static final String HEADER = "\n****************************************************************\n%s\n\n";
    private static final String FOOTER = "\n****************************************************************\n";

    private TestIPFSAccess access;
    private IPLDContext context;

    private Set<String> removeHashes = new HashSet<>();

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
        UserState userState = new UserState(userObject);
        IPLDObject<UserState> userStateObject = new IPLDObject<>(userState);
        IPLDObject<ModelState> modelStateObject = new IPLDObject<>(modelState);
        modelState.updateUserState(userStateObject, null, null, null, null, null, null, System.currentTimeMillis());
        Signer signer = DEFAULT_SIGNER;
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
    void updateFileContentsForSingleObject()
            throws FileNotFoundException, IOException, IllegalArgumentException, IllegalAccessException {
        String filepath = UPDATE_FILE_CONTENTS_SINGLE_PATH;
        boolean nonEmpty = filepath != null && !"".equals(filepath.trim());
        System.out.printf(HEADER, "update single " + (nonEmpty ? filepath : "<n/a>"));
        if (nonEmpty) {
            updateObject(filepath, false);
        }
        System.out.println(FOOTER);
    }

    @Test
    void updateFileContentsForMultipleObjects()
            throws FileNotFoundException, IOException, IllegalArgumentException, IllegalAccessException {
        String filepath = UPDATE_FILE_CONTENTS_MULTIPLE_PATH;
        boolean nonEmpty = filepath != null && !"".equals(filepath.trim());
        System.out.printf(HEADER, "update multiple " + (nonEmpty ? filepath : "<n/a>"));
        if (nonEmpty) {
            updateObjects(filepath, false);
        }
        System.out.println(FOOTER);
    }

    @Test
    void replaceFileContentsForSingleObject()
            throws FileNotFoundException, IOException, IllegalArgumentException, IllegalAccessException {
        String filepath = REPLACE_FILE_CONTENTS_SINGLE_PATH;
        boolean nonEmpty = filepath != null && !"".equals(filepath.trim());
        System.out.printf(HEADER, "replace single " + (nonEmpty ? filepath : "<n/a>"));
        if (nonEmpty) {
            updateObject(filepath, true);
        }
        System.out.println(FOOTER);
    }

    @Test
    void replaceFileContentsForMultipleObjects()
            throws FileNotFoundException, IOException, IllegalArgumentException, IllegalAccessException {
        String filepath = REPLACE_FILE_CONTENTS_MULTIPLE_PATH;
        boolean nonEmpty = filepath != null && !"".equals(filepath.trim());
        System.out.printf(HEADER, "replace multiple " + (nonEmpty ? filepath : "<n/a>"));
        if (nonEmpty) {
            updateObjects(filepath, true);
        }
        System.out.println(FOOTER);
    }

    private void updateObject(String filepath, boolean replace)
            throws IOException, IllegalArgumentException, IllegalAccessException {
        String[] hashes = access.readObjects(filepath);
        Signer signer = DEFAULT_SIGNER;
        String hash = hashes[0];
        if (replace && REPLACE_ROOT_HASH != null && REPLACE_ROOT_HASH.equals(hash)) {
            replaceRoot(hash);
        }
        else {
            if (!updateObject(hash, signer) && replace) {
                access.removeObject(hash);
            }
            for (String removeHash : removeHashes) {
                access.removeObject(removeHash);
            }
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

    private void updateObjects(String filepath, boolean replace)
            throws FileNotFoundException, IOException, IllegalArgumentException, IllegalAccessException {
        String[] hashes = access.readObjects(filepath);
        Signer signer = DEFAULT_SIGNER;
        Set<String> remove = replace ? new HashSet<>() : null;
        String rootHash = null;
        if (REPLACE_ROOT_HASH != null) {
            for (String hash : hashes) {
                if (REPLACE_ROOT_HASH.equals(hash)) {
                    rootHash = hash;
                    break;
                }
            }
        }
        if (rootHash == null) {
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
            for (String removeHash : removeHashes) {
                access.removeObject(removeHash);
            }
        }
        else {
            replaceRoot(rootHash);
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

    private boolean updateObject(String hash, Signer signer)
            throws IOException, IllegalArgumentException, IllegalAccessException {
        ModelUpdater updater = UPDATERS.get(hash);
        if (updater != null) {
            IPLDObject<?> updated = updater.update(hash, context, removeHashes);
            if (updated != null) {
                updated.save(context, signer);
                return true;
            }
            return false;
        }
        return true;
    }

    private void replaceRoot(String rootHash) throws IOException, IllegalArgumentException, IllegalAccessException {
        removeHashes.add(rootHash);
        ModelUpdater rootUpdater = UPDATERS.get(rootHash);
        IPLDObject<?> root = rootUpdater == null ? null : rootUpdater.update(rootHash, context, removeHashes);
        if (root == null) {
            root = new IPLDObject<>(rootHash, ROOT_LOADER_FACTORY.createLoader(), context, null);
        }
        prepareForSave(root);
        for (String remove : removeHashes) {
            access.removeObject(remove);
        }
        context.saveObject(root, DEFAULT_SIGNER);
    }

    private String getJSONString(byte[] bytes) {
        String compact = new String(bytes, StandardCharsets.UTF_8);
        if (PRETTY_PRINTING) {
            return PRETTY_GSON.toJson(JsonParser.parseString(compact));
        }
        return compact;
    }

    private static Field MULTIHASH_FIELD;
    {
        try {
            MULTIHASH_FIELD = IPLDObject.class.getDeclaredField("multihash");
            MULTIHASH_FIELD.setAccessible(true);
        }
        catch (NoSuchFieldException | SecurityException e) {
            e.printStackTrace();
        }
    }

    private boolean prepareForSave(IPLDObject<?> object)
            throws IllegalArgumentException, IllegalAccessException, IOException {
        boolean res = false;
        IPLDSerializable mapped = object.getMapped();
        Class<?> clazz = mapped.getClass();
        do {
            Field[] declaredFields = clazz.getDeclaredFields();
            for (Field field : declaredFields) {
                Class<?> fieldType = field.getType();
                if (fieldType == IPLDObject.class) {
                    field.setAccessible(true);
                    IPLDObject<?> value = (IPLDObject<?>) field.get(mapped);
                    if (value != null) {
                        IPLDObject<?> updated = checkMultihash(value);
                        if (updated != null) {
                            res = true;
                            if (updated != value) {
                                field.set(mapped, updated);
                            }
                        }
                    }
                }
                else if (Map.class.isAssignableFrom(fieldType)) {
                    field.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    Map<Object, Object> map = (Map<Object, Object>) field.get(mapped);
                    if (map != null) {
                        Map<Object, Object> updatedValues = new HashMap<>();
                        for (Entry<?, ?> entry : map.entrySet()) {
                            Object value = entry.getValue();
                            if (value instanceof IPLDObject<?>) {
                                IPLDObject<?> updated = checkMultihash((IPLDObject<?>) value);
                                if (updated != null) { // we keep the key, as it is ignored during saving and duplicate
                                                       // keys are extremely unlikely
                                    if (updated == value) {
                                        res = true;
                                    }
                                    else {
                                        updatedValues.put(entry.getKey(), updated);
                                    }
                                }
                            }
                            else if (value instanceof IPLDObject<?>[]) {
                                List<IPLDObject<?>> updatedList = new ArrayList<>();
                                boolean changed = false;
                                for (IPLDObject<?> val : (IPLDObject<?>[]) value) {
                                    IPLDObject<?> updated = checkMultihash(val);
                                    if (updated == null || updated == val) {
                                        updatedList.add(val);
                                        if (!res && updated != null) {
                                            res = true;
                                        }
                                    }
                                    else {
                                        updatedList.add(updated);
                                        changed = true;
                                    }
                                }
                                if (changed) {
                                    IPLDObject<?>[] updatedArray = (IPLDObject<?>[]) Array
                                            .newInstance(value.getClass().getComponentType(), 0);
                                    updatedArray = updatedList.toArray(updatedArray);
                                    updatedValues.put(entry.getKey(), updatedArray);
                                }
                            }
                        }
                        if (updatedValues.size() > 0) {
                            for (Entry<?, ?> entry : updatedValues.entrySet()) {
                                map.put(entry.getKey(), entry.getValue());
                            }
                            res = true;
                        }
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        while (clazz != null);
        return res;
    }

    private IPLDObject<?> checkMultihash(IPLDObject<?> object)
            throws IllegalArgumentException, IllegalAccessException, IOException {
        String multihash = object.getMultihash();
        if (multihash != null) {
            ModelUpdater updater = UPDATERS.get(multihash);
            IPLDObject<?> updated = updater == null ? null : updater.update(multihash, context, removeHashes);
            if (updated == null) {
                if (updater == null) {
                    if (prepareForSave(object)) {
                        removeHashes.add(multihash);
                        MULTIHASH_FIELD.set(object, null);
                        return object;
                    }
                }
                else {
                    removeHashes.add(multihash);
                }
            }
            else {
                removeHashes.add(multihash);
            }
            return updated;
        }
        return null;
    }

}
