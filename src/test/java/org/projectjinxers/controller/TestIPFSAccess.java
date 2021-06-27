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

import static org.ethereum.crypto.HashUtil.sha3;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Stream;

import org.ethereum.crypto.ECKey.ECDSASignature;
import org.projectjinxers.account.Signer;
import org.spongycastle.util.encoders.Base64;
import org.spongycastle.util.encoders.Hex;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * IPFS access for test cases. Objects are stored locally in memory and can be loaded from files. PubSub-Messages can be
 * simulated, so the subscriber actually receives them. Publishing a message simply stores it in a map and does not
 * forward it to the subscriber. The most recent published message for a given topic can be queried for assertions.
 * Hashes used in this class are not actually multihashes. They are simply hex-strings of the SHA3 hash of the bytes.
 * Save failures can be simulated via whitelists and blacklists, where blacklists take precedence over whitelists.
 * 
 * @author ProjectJinxers
 */
public class TestIPFSAccess extends IPFSAccess {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private Map<String, byte[]> objects = new HashMap<>();
    private Map<String, BlockingQueue<Map<String, Object>>> messageQueues = new HashMap<>();

    private Set<String> availableStreams = new HashSet<>();
    private Map<String, String> publishedMessages = new HashMap<>();
    private String publishedMessageTopic;

    private Set<String> saveFailures = new HashSet<>();
    private Set<String> noSaveFailures = new HashSet<>();
    private int saveFailureCountdown;

    private Map<String, String> modelStateHashes = new HashMap<>();

    private boolean calculatingHashOnly;

    @Override
    public byte[] loadObject(String hash) throws IOException {
        return objects.get(hash);
    }

    @Override
    public String saveObject(String inputFormat, byte[] bytes, String outputFormat) {
        if (!calculatingHashOnly && --saveFailureCountdown == 0) {
            throw new RuntimeException("Simulated save failure");
        }
        String hash = getHash(bytes);
        if (calculatingHashOnly) {
            return hash;
        }
        if (saveFailures.isEmpty()) {
            if (noSaveFailures.size() > 0 && !noSaveFailures.contains(hash)) {
                throw new RuntimeException("Simulated save failure");
            }
        }
        else if (saveFailures.contains(hash)) {
            throw new RuntimeException("Simulated save failure");
        }
        objects.put(hash, bytes);
        return hash;
    }

    @Override
    public void publish(String topic, String message) throws Exception {
        synchronized (publishedMessages) {
            publishedMessages.put(topic, message);
            publishedMessageTopic = topic;
            publishedMessages.notifyAll();
        }
    }

    @Override
    public Stream<Map<String, Object>> subscribe(String topic) throws Exception {
        BlockingQueue<Map<String, Object>> blockingQueue;
        synchronized (availableStreams) {
            blockingQueue = messageQueues.get(topic);
            if (blockingQueue == null) {
                blockingQueue = new LinkedBlockingQueue<>();
                messageQueues.put(topic, blockingQueue);
            }
        }
        final BlockingQueue<Map<String, Object>> queue = blockingQueue;
        Stream<Map<String, Object>> res = Stream.generate(() -> {
            synchronized (availableStreams) {
                if (availableStreams.add(topic)) {
                    availableStreams.notifyAll();
                }
            }
            try {
                return queue.take();
            }
            catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        return res;
    }

    /**
     * Reads the given file and adds all JSON objects contained in it. There may be one single object or an array of
     * objects. If the file contains a map of objects, the call will not fail. However, the data stored locally as a
     * result of it, will not be the expected data. The keys are calculated from the JSON objects, so there is no need
     * to have this method read a map of objects. For simplicity, because there is the single object option, multiple
     * objects can only be read from a JSON array.
     * 
     * @param filepath the path to the file to read (relative to the working directory, must be on the classpath)
     * @return the hashes of the read objects (preserves the order in the file)
     * @throws FileNotFoundException if the file could not be found
     * @throws IOException           if reading the file failed
     */
    public String[] readObjects(String filepath) throws FileNotFoundException, IOException {
        JsonElement root = JsonParser
                .parseReader(new InputStreamReader(getClass().getClassLoader().getResourceAsStream(filepath)));
        if (root.isJsonObject()) {
            return new String[] { addJsonObject(root.getAsJsonObject()) };
        }
        else if (root.isJsonArray()) {
            List<String> hashes = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray()) {
                if (element.isJsonObject()) {
                    hashes.add(addJsonObject(element.getAsJsonObject()));
                }
            }
            return hashes.toArray(new String[0]);
        }
        return null;
    }

    private String addJsonObject(JsonObject o) {
        String json = GSON.toJson(o);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        return saveObject(IPLDEncoding.JSON.getIn(), bytes, IPLDEncoding.JSON.getOut());
    }

    /**
     * @return all locally stored objects
     */
    public byte[][] getAllObjects() {
        return objects.values().toArray(new byte[0][]);
    }

    /**
     * @param object the object
     * @return the hash for the given object
     */
    public String getHash(byte[] object) {
        return Hex.toHexString(sha3(object));
    }

    /**
     * Calculates the hash for the given object without saving it or any links.
     * 
     * @param object  the object
     * @param context the context
     * @param signer  the signer
     * @return the calculated hash
     * @throws IOException if writing the properties fails
     */
    public String getHash(IPLDObject<?> object, IPLDContext context, Signer signer) throws IOException {
        calculatingHashOnly = true;
        String hash = object.save(context, signer);
        calculatingHashOnly = false;
        return hash;
    }

    /**
     * Removes an object with the given hash (no IPFS functionality, just for test purposes)
     * 
     * @param hash the hash of the object to remove
     */
    public void removeObject(String hash) {
        objects.remove(hash);
    }

    public void retainObjects(Collection<String> hash) {

    }

    public void removeAllObjects() {
        objects.clear();
    }

    /**
     * Waits until the stream for receiving messages with the given topic is available.
     * 
     * @param topic         the topic
     * @param timeoutMillis the timeout in milliseconds
     * @return true iff the stream is available
     */
    public boolean waitForMessageStream(String topic, int timeoutMillis) {
        do {
            synchronized (availableStreams) {
                if (availableStreams.contains(topic)) {
                    return true;
                }
                try {
                    availableStreams.wait(timeoutMillis);
                }
                catch (InterruptedException e) {
                    continue;
                }
                return availableStreams.contains(topic);
            }
        }
        while (true);
    }

    /**
     * Convenience method for waiting until the stream for receiving model state messages is available.
     * 
     * @param mainIOTAAddress the main IOTA address
     * @param timeoutMillis   the timeout in milliseconds
     * @return true iff the stream is available
     */
    public boolean waitForModelStateMessageStream(String mainIOTAAddress, int timeoutMillis) {
        return waitForMessageStream(mainIOTAAddress, timeoutMillis);
    }

    /**
     * Convenience method for waiting until the stream for receiving ownership request messages is available.
     * 
     * @param mainIOTAAddress the main IOTA address
     * @param timeoutMillis   the timeout in milliseconds
     * @return true iff the stream is available
     */
    public boolean waitForOwnershipRequestMessageStream(String mainIOTAAddress, int timeoutMillis) {
        return waitForMessageStream("or" + mainIOTAAddress, timeoutMillis);
    }

    /**
     * Simulates a received message. The subscriber for the topic will receive that message.
     * 
     * @param topic   the topic
     * @param message the message
     */
    public void simulateMessage(String topic, Map<String, Object> message) {
        BlockingQueue<Map<String, Object>> blockingQueue = messageQueues.get(topic);
        if (blockingQueue != null) {
            blockingQueue.add(message);
        }
    }

    /**
     * Simulates a received model state message. This method blocks until the stream is available. The timeout is pretty
     * short, though. The subscriber for the topic will receive that message.
     * 
     * @param mainIOTAAddress the main IOTA address (the topic)
     * @param hash            the received hash
     */
    public void simulateModelStateMessage(String mainIOTAAddress, String hash) {
        waitForModelStateMessageStream(mainIOTAAddress, 200);
        String base64 = Base64.toBase64String(hash.getBytes(StandardCharsets.UTF_8));
        simulateMessage(mainIOTAAddress, Map.of("data", base64));
    }

    /**
     * Simulates a received ownership request message. This method blocks until the stream is available. The timeout is
     * pretty short, though. The subscriber for the topic will receive that message.
     * 
     * @param mainIOTAAddress the main IOTA address (part of the topic)
     * @param userHash        the user hash
     * @param documentHash    the doument hash
     * @param signer          the signer
     */
    public Map<String, Object> simulateOwnershipRequestMessage(String mainIOTAAddress, String userHash,
            String documentHash, boolean anonymousVoting, Signer signer) {

        waitForOwnershipRequestMessageStream(mainIOTAAddress, 200);
        String request = (anonymousVoting ? "-" : "+") + "." + userHash + "." + documentHash;
        byte[] requestBytes = request.getBytes(StandardCharsets.UTF_8);
        ECDSASignature signature = signer.sign(requestBytes);
        Map<String, Object> res = Map.of("data",
                Base64.toBase64String((request + "|" + signature.r + "|" + signature.s + "|" + signature.v)
                        .getBytes(StandardCharsets.UTF_8)));
        simulateOwnershipRequestMessage(mainIOTAAddress, res);
        return res;
    }

    /**
     * Simulates a received ownership request message. The subscriber for the topic will receive that message.
     * 
     * @param mainIOTAAddress the main IOTA address (part of the topic)
     * @param message         the message
     */
    public void simulateOwnershipRequestMessage(String mainIOTAAddress, Map<String, Object> message) {
        String topic = "or" + mainIOTAAddress;
        simulateMessage(topic, message);
    }

    /**
     * @param topic the topic
     * @return the most recent published message for the given topic
     */
    public String getPublishedMessage(String topic) {
        return publishedMessages.get(topic);
    }

    /**
     * Blocks until a message for the given topic is published or the timeout hits. If a message for another topic is
     * published, the timeout is reset and waiting begins again. The returned message is consumed, i.e.
     * {@link #getPublishedMessage(String)} with the same topic will return null, until another message is published for
     * the topic.
     * 
     * @param topic         the topic
     * @param timeoutMillis the timeout in milliseconds
     * @return the published message
     */
    public String waitForPublishedMessage(String topic, long timeoutMillis) {
        do {
            synchronized (publishedMessages) {
                if (topic.equals(publishedMessageTopic)) {
                    String msg = publishedMessages.remove(topic);
                    if (msg != null) {
                        return msg;
                    }
                }
                publishedMessageTopic = null;
                try {
                    publishedMessages.wait(timeoutMillis);
                }
                catch (InterruptedException e) {

                }
                if (publishedMessageTopic == null) {
                    return null;
                }
                if (topic.equals(publishedMessageTopic)) {
                    return publishedMessages.remove(topic);
                }
            }
        }
        while (true);
    }

    /**
     * Causes a save failure in the future. 0 or negative (not too close to min value) countdown effectively clears the
     * save failure. If the save failure already hit, a reset to restore normal save behavior is not necessary.
     * 
     * @param countdown 1-based count down (skips countdown - 1 save operations)
     */
    public void failSaveIn(int countdown) {
        this.saveFailureCountdown = countdown;
    }

    /**
     * Makes the next calls of {@link #saveObject(String, byte[], String)}, where the hash of the bytes to save is the
     * given hash, fail.
     * 
     * @param hash the hash
     */
    public void addSaveFailure(String hash) {
        saveFailures.add(hash);
    }

    /**
     * Makes the next calls of {@link #saveObject(String, byte[], String)}, where the hash of the bytes to save is the
     * calculated hash of the given object, fail. The parameters must be the same as for the save operation. The object
     * will not be saved. Nor will any of its links.
     * 
     * @param object  the object
     * @param context the context
     * @param signer  the signer
     * @return the calculated hash
     * @throws IOException if writing the properties fails
     */
    public String addSaveFailure(IPLDObject<?> object, IPLDContext context, Signer signer) throws IOException {
        String res = getHash(object, context, signer);
        saveFailures.add(res);
        return res;
    }

    /**
     * Makes the next calls of {@link #saveObject(String, byte[], String)}, where the hash of the bytes to save is the
     * given hash, succeed unless the hash has been added to the save failures (the save blacklist). If the save
     * blacklist is empty, all save operations, where the hashes are not on the whitelist, will fail.
     * 
     * @param hash the hash
     */
    public void addNoSaveFailure(String hash) {
        noSaveFailures.add(hash);
    }

    /**
     * Makes the next calls of {@link #saveObject(String, byte[], String)}, where the hash of the bytes to save is the
     * calculated hash of the given object, succeed unless the hash has been added to the save failures (the save
     * blacklist). If the save blacklist is empty, all save operations where the hashes are not on the whitelist, will
     * fail. The parameters must be the same as for the save operation. The object will not be saved. Nor will any of
     * its links.
     * 
     * @param object  the object
     * @param context the context
     * @param signer  the signer
     * @return the calculated hash
     * @throws IOException if writing the properties fails
     */
    public String addNoSaveFailure(IPLDObject<?> object, IPLDContext context, Signer signer) throws IOException {
        String res = getHash(object, context, signer);
        noSaveFailures.add(res);
        return res;
    }

    /**
     * Removes the given hash from the save blacklist. If the black list is empty afterwards, and there is a non-empty
     * save whitelist, and the hash is not on that list, there will still be save failures for that hash.
     * 
     * @param hash the hash
     */
    public void removeSaveFailure(String hash) {
        saveFailures.remove(hash);
    }

    /**
     * Clears the save black list. If there is a non-empty save whitelist, that list now becomes effective (the
     * blacklist took precedence over the white list).
     */
    public void clearSaveFailures() {
        saveFailures.clear();
    }

    /**
     * Removes the given hash from the save whitelist (i.e. the list of all hashes where the corresponding objects will
     * be saved successfully, unless they have been added to the blacklist).
     * 
     * @param hash the hash
     */
    public void removeNoSaveFailure(String hash) {
        noSaveFailures.remove(hash);
    }

    /**
     * Clears the save whitelist (i.e. the list of all hashes where the corresponding objects will be saved
     * successfully, unless they have been added to the blacklist). The save blacklist already took precedence, so if
     * the black list is not empty, nothing changes.
     */
    public void clearNoSaveFailures() {
        noSaveFailures.clear();
    }

    @Override
    public String readModelStateHash(String address) throws IOException {
        String res = modelStateHashes.get(address);
        if (res != null) {
            return res;
        }
        BufferedReader br = new BufferedReader(
                new InputStreamReader(getClass().getClassLoader().getResourceAsStream(address)));
        try {
            res = br.readLine();
            if (res != null) {
                saveModelStateHash(address, res);
            }
            return res;
        }
        finally {
            br.close();
        }
    }

    @Override
    public void saveModelStateHash(String address, String hash) throws IOException {
        modelStateHashes.put(address, hash);
    }

}
