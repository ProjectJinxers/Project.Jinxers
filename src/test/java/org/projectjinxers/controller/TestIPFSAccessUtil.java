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
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.projectjinxers.account.ECCSigner;
import org.projectjinxers.account.Signer;
import org.projectjinxers.account.Users;
import org.projectjinxers.config.Config;
import org.projectjinxers.model.Document;
import org.projectjinxers.model.IPLDSerializable;
import org.projectjinxers.model.LoaderFactory;
import org.projectjinxers.model.ModelState;
import org.projectjinxers.model.Review;
import org.projectjinxers.model.SealedDocument;
import org.projectjinxers.model.User;
import org.projectjinxers.model.UserState;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;

import static org.projectjinxers.util.ObjectUtility.isNullOrBlank;

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
            "model/modelController/settlement/sealedTruthInversion.json" };

    private static final String UPDATE_FILE_CONTENTS_SINGLE_PATH = null;
    private static final String UPDATE_FILE_CONTENTS_MULTIPLE_PATH = "model/modelController/baseStates/twentyfourUsers.json";
    private static final String REPLACE_FILE_CONTENTS_SINGLE_PATH = null;
    private static final String REPLACE_FILE_CONTENTS_MULTIPLE_PATH = "model/modelController/settlement/validTruthInversion.json";

    private static final int USER_SECURITY_LEVEL = 0;
    /**
     * If not null, and this hash is encountered during replacing, the in memory models will be cleared and re-populated
     * by saving the root again (after resetting all multihashes).
     */
    private static final String REPLACE_ROOT_HASH = "03cc091eab564683138a2134eb19107ba082c4865a36f8d218d0af4c0821e236";
    private static final LoaderFactory<?> ROOT_LOADER_FACTORY = LoaderFactory.MODEL_STATE;
    private static final boolean REPLACE_ALL = false; // set to true to replace all saved objects with new saved
                                                      // objects, regardless of whether or not they or their referenced
                                                      // objects had been changed

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

    private static final long REQUIRED_SETTLEMENT_REQUEST_AGE = 1000L * 60 * 60 * 24 * 4;
    private static final long REQUIRED_SETTLEMENT_EXECUTION_AGE = 1000L * 60 * 60 * 24 * 4;
    private static final long REQUIRED_OWNERSHIP_TRANSFER_INACTIVITY = 1000L * 60 * 60 * 24 * 30;
    private static final Date ELIGIBLE_FOR_SETTLEMENT_REQUEST = new Date(
            System.currentTimeMillis() - REQUIRED_SETTLEMENT_REQUEST_AGE);
    private static final Date ELIGIBLE_FOR_SETTLEMENT_EXECUTION = new Date(ELIGIBLE_FOR_SETTLEMENT_REQUEST.getTime()
            - REQUIRED_SETTLEMENT_EXECUTION_AGE + Config.DEFAULT_TIMESTAMP_TOLERANCE + 1000);
    private static final Date ELIGIBLE_FOR_OWNERSHIP_TRANSFER = new Date(
            System.currentTimeMillis() - REQUIRED_OWNERSHIP_TRANSFER_INACTIVITY);

    public static final Signer DEFAULT_SIGNER = new ECCSigner("user", "pass");
    public static final Signer DEFAULT_SIGNER_SECURITY_LEVEL_1 = new ECCSigner("user", "pass", 1);
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
    static interface ModelUpdater<T extends IPLDSerializable> {

        IPLDObject<T> load(String hash, IPLDContext context);

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
        IPLDObject<T> update(IPLDObject<T> loaded, IPLDContext context, Set<String> removeHashes)
                throws IOException, IllegalArgumentException, IllegalAccessException;

    }

    private static final Map<String, ModelUpdater<?>> UPDATERS = new HashMap<>();
    static {
        UPDATERS.put("03cc091eab564683138a2134eb19107ba082c4865a36f8d218d0af4c0821e236",
                new ModelUpdater<ModelState>() {

                    @Override
                    public IPLDObject<ModelState> load(String hash, IPLDContext context) {
                        return new IPLDObject<>(hash, LoaderFactory.MODEL_STATE.createLoader(), context, null);
                    }

                    @Override
                    public IPLDObject<ModelState> update(IPLDObject<ModelState> loaded, IPLDContext context,
                            Set<String> removeHashes)
                            throws IOException, IllegalArgumentException, IllegalAccessException {
                        IPLDObject<UserState> userState = loaded.getMapped()
                                .expectUserState("1140e8cd69b506268623fd27f869185e0ef188650227327357da93bfa988d6dd");
                        String documentHash = "d93fd2d86eb0aec5b1183300b49b61a15b42e461f62b235be49f10d3499af136";
                        IPLDObject<Document> document = userState.getMapped().getDocument(documentHash);
                        long timestamp = System.currentTimeMillis();
                        Set<Entry<String, IPLDObject<UserState>>> allUserStateEntries = new LinkedHashSet<>(
                                loaded.getMapped().getAllUserStateEntries());
                        ModelState updated = loaded.getMapped();
                        for (Entry<String, IPLDObject<UserState>> entry : allUserStateEntries) {
                            if (entry.getValue() == userState) {
                                UserState updatedOwner = entry.getValue().getMapped().updateLinks(null, null, null,
                                        null, null, null, null, null, null, null, entry.getValue(), null);
                                updatedOwner.removeTrueClaim(document);
                                IPLDObject<UserState> updatedOwnerState = new IPLDObject<>(updatedOwner);
                                updatedOwnerState.save(context, null, null);
                                updated = updated.updateUserState(updatedOwnerState, null, null, null, null, null,
                                        loaded, timestamp, null);
                            }
                            else {
                                String username = entry.getValue().getMapped().getUser().getMapped().getUsername();
                                if ("second".equals(username)) {
                                    Signer signer = new ECCSigner(username, "pass2");
                                    IPLDObject<Review> review = new IPLDObject<>(
                                            "bc4e784b0e742d1656dcd1cb8285cea5b4afe3086c0c4b92060051fe474c5169",
                                            LoaderFactory.REVIEW.createLoader(), context, null);

                                    IPLDObject<Document> toSeal = entry.getValue().getMapped()
                                            .expectDocumentObject(review.getMapped().getFirstVersionHash());
                                    UserState rewarded = entry.getValue().getMapped().updateLinks(null, null, null,
                                            null, null, null, Set.of(toSeal.getMultihash()), null, null, null,
                                            entry.getValue(), null);

                                    rewarded.removeFalseDeclination(review);

                                    IPLDObject<UserState> rewardedObject = new IPLDObject<>(rewarded);
                                    SealedDocument sealed = new SealedDocument(toSeal);
                                    SealedDocument inverted = loaded.getMapped().expectSealedDocument(documentHash)
                                            .invertTruth();
                                    updated.updateUserState(rewardedObject, null, null, null,
                                            Arrays.asList(new IPLDObject<>(sealed), new IPLDObject<>(inverted)), null,
                                            null, timestamp, null);
                                }
                                if (username.startsWith("user")) {
                                    String password = username.replace("user", "pass");
                                    Signer signer = new ECCSigner(username, password);

                                    Map<String, IPLDObject<Document>> allDocuments = entry.getValue().getMapped()
                                            .getAllDocuments();
                                    IPLDObject review = null;
                                    if (allDocuments != null && Boolean.TRUE.equals(
                                            ((Review) ((review = allDocuments.values().iterator().next()).getMapped()))
                                                    .getApprove())) {
                                        UserState updatedReviewer = entry.getValue().getMapped().updateLinks(null, null,
                                                null, null, null, null, null, null, null, null, entry.getValue(), null);

                                        updatedReviewer.removeTrueApproval(review);

                                        updated.updateUserState(new IPLDObject<>(updatedReviewer), null, null, null,
                                                null, null, null, timestamp, null);
                                    }
                                }
                            }
                        }
                        return new IPLDObject<>(updated);
                    }

                });
    }

    private static final boolean PRETTY_PRINTING = true;
    private static final Gson PRETTY_GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private static final String HEADER = "\n****************************************************************\n%s\n\n";
    private static final String FOOTER = "\n****************************************************************\n";

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

    private static boolean prepareForSave(IPLDObject<?> object, IPLDContext context, Set<String> removeHashes)
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
                        IPLDObject<?> updated = checkMultihash(value, context, removeHashes);
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
                                IPLDObject<?> updated = checkMultihash((IPLDObject<?>) value, context, removeHashes);
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
                                    IPLDObject<?> updated = checkMultihash(val, context, removeHashes);
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

    private static IPLDObject<?> checkMultihash(IPLDObject<?> object, IPLDContext context, Set<String> removeHashes)
            throws IllegalArgumentException, IllegalAccessException, IOException {
        String multihash = object.getMultihash();
        if (multihash != null) {
            ModelUpdater<?> updater = UPDATERS.remove(multihash);
            IPLDObject<?> updated = updater == null ? null : update(updater, multihash, context, removeHashes);
            if (updated == null) {
                if (updater == null) {
                    if (prepareForSave(object, context, removeHashes) || REPLACE_ALL) {
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
                if (updated.getMultihash() == null) {
                    updated.save(context, DEFAULT_SIGNER, null);
                }
                removeHashes.add(multihash);
            }
            return updated;
        }
        return null;
    }

    private static <T extends IPLDSerializable> IPLDObject<T> update(ModelUpdater<T> updater, String hash,
            IPLDContext context, Set<String> removeHashes)
            throws IllegalArgumentException, IllegalAccessException, IOException {
        IPLDObject<T> loaded = updater.load(hash, context);
        return updater.update(loaded, context, removeHashes);
    }

    private static String getJSONString(byte[] bytes) {
        String compact = new String(bytes, StandardCharsets.UTF_8);
        if (PRETTY_PRINTING) {
            return PRETTY_GSON.toJson(JsonParser.parseString(compact));
        }
        return compact;
    }

    public static void makeEligibleForSettlementRequest(Document document)
            throws IllegalArgumentException, IllegalAccessException {
        DOCUMENT_DATE_FIELD.set(document, ELIGIBLE_FOR_SETTLEMENT_REQUEST);
    }

    public static void makeEligibleForSettlementExcecution(Document document)
            throws IllegalArgumentException, IllegalAccessException {
        DOCUMENT_DATE_FIELD.set(document, ELIGIBLE_FOR_SETTLEMENT_EXECUTION);
    }

    public static void makeEligibleForOwnershipTransfer(Document document)
            throws IllegalArgumentException, IllegalAccessException {
        DOCUMENT_DATE_FIELD.set(document, ELIGIBLE_FOR_OWNERSHIP_TRANSFER);
    }

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
        User user = new User("user", Users.createAccount("user", "pass", USER_SECURITY_LEVEL).getPubKey());
        IPLDObject<User> userObject = new IPLDObject<>(user);
        UserState userState = new UserState(userObject);
        IPLDObject<UserState> userStateObject = new IPLDObject<>(userState);
        Signer signer = DEFAULT_SIGNER;
        userStateObject.save(context, signer, null);
        IPLDObject<ModelState> modelStateObject = new IPLDObject<>(modelState);
        modelState.updateUserState(userStateObject, null, null, null, null, null, null, System.currentTimeMillis(),
                null);
        modelStateObject.save(context, signer, null);
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
        boolean nonEmpty = !isNullOrBlank(filepath);
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
        boolean nonEmpty = !isNullOrBlank(filepath);
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
        boolean nonEmpty = !isNullOrBlank(filepath);
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
        boolean nonEmpty = !isNullOrBlank(filepath);
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
                access.removeOldObject(hash);
            }
            for (String removeHash : removeHashes) {
                access.removeOldObject(removeHash);
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
                    access.removeOldObject(hash);
                }
            }
            for (String removeHash : removeHashes) {
                access.removeOldObject(removeHash);
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
        ModelUpdater<?> updater = UPDATERS.get(hash);
        if (updater != null) {
            return updateObject(updater, hash, signer);
        }
        return true;
    }

    private <T extends IPLDSerializable> boolean updateObject(ModelUpdater<T> updater, String hash, Signer signer)
            throws IOException, IllegalArgumentException, IllegalAccessException {
        IPLDObject<T> loaded = updater.load(hash, context);
        IPLDObject<T> updated = updater.update(loaded, context, removeHashes);
        if (updated != null) {
            updated.save(context, signer, null);
            return updated != loaded;
        }
        return false;
    }

    private void replaceRoot(String rootHash) throws IOException, IllegalArgumentException, IllegalAccessException {
        ModelUpdater<?> rootUpdater = UPDATERS.remove(rootHash);
        IPLDObject<?> root;
        if (rootUpdater == null) {
            removeHashes.add(rootHash);
            root = new IPLDObject<>(rootHash, ROOT_LOADER_FACTORY.createLoader(), context, null);
        }
        else {
            root = updateRoot(rootUpdater, rootHash);
        }
        prepareForSave(root, context, removeHashes);
        for (String remove : removeHashes) {
            access.removeOldObject(remove);
        }
        String newRootHash = context.saveObject(root, DEFAULT_SIGNER, null);
        System.out.println("New root hash: " + newRootHash + "\n");
    }

    private <T extends IPLDSerializable> IPLDObject<T> updateRoot(ModelUpdater<T> rootUpdater, String rootHash)
            throws IllegalArgumentException, IllegalAccessException, IOException {
        IPLDObject<T> loaded = rootUpdater.load(rootHash, context);
        IPLDObject<T> updated = rootUpdater.update(loaded, context, removeHashes);
        if (updated == loaded) {
            removeHashes.add(rootHash);
        }
        return updated == null ? loaded : updated;
    }

}
