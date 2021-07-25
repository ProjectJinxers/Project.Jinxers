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
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Stream;

import org.projectjinxers.config.Config;
import org.spongycastle.util.encoders.Base64;

import io.ipfs.api.IPFS;
import io.ipfs.api.MerkleNode;
import io.ipfs.cid.Cid;
import io.ipfs.multibase.Base58;

/**
 * Provides access to the IPFS API.
 * 
 * @author ProjectJinxers
 */
public class IPFSAccess {

    /**
     * The access to the IPFS API.
     */
    private IPFS ipfs;

    private String peerIDBase64;

    /**
     * Constructor.
     */
    public IPFSAccess() {

    }

    /**
     * Reads the config and configures the {@link IPFS} instance appropriately.
     */
    void configure() {
        Config config = Config.getSharedInstance();
        String multiaddr = config.getIPFSMultiaddr();
        if (multiaddr != null) {
            ipfs = new IPFS(multiaddr);
        }
        else {
            String version = config.getIPFSVersion();
            if (version == null) {
                ipfs = new IPFS(config.getIPFSHost(), config.getIPFSPort());
            }
            else if (config.hasIPFSTimeout()) {
                ipfs = new IPFS(config.getIPFSHost(), config.getIPFSPort(), config.getIPFSVersion(),
                        config.getIPFSConnectionTimeout(), config.getIPFSReadTimeout(), config.isIPFSSecure());
            }
            else {
                ipfs = new IPFS(config.getIPFSHost(), config.getIPFSPort(), version, config.isIPFSSecure());
            }
        }
        try {
            Map<?, ?> id = ipfs.id();
            String peerID = (String) id.get("ID");
            byte[] decoded = Base58.decode(peerID);
            this.peerIDBase64 = Base64.toBase64String(decoded);
        }
        catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public String getPeerIDBase64() {
        return peerIDBase64;
    }

    /**
     * Loads the object with the given multihash from IPFS as a DAG.
     * 
     * @param multihash the multihash
     * @return the loaded bytes (currently UTF-8 encoded JSON-String)
     * @throws IOException if loading the object fails
     */
    public byte[] loadObject(String multihash) throws IOException {
        return ipfs.dag.get(Cid.decode(multihash));
    }

    /**
     * Saves the object (its serialized binary form) in IPFS as a DAG.
     * 
     * @param inputFormat  the input format (the format in which the bytes are transferred to IPFS)
     * @param bytes        the bytes
     * @param outputFormat the output format (the format in which the bytes are saved in IPFS)
     * @return the multihash
     * @throws IOException if saving the object fails
     */
    public String saveObject(String inputFormat, byte[] bytes, String outputFormat) throws IOException {
        MerkleNode node = ipfs.dag.put(inputFormat, bytes, outputFormat);
        return node.hash.toString();
    }

    /**
     * Publishes the given message for the given topic.
     * 
     * @param topic   the topic
     * @param message the message
     * @throws Exception if publishing the message fails
     */
    public void publish(String topic, String message) throws Exception {
        ipfs.pubsub.pub(topic, URLEncoder.encode(message, StandardCharsets.UTF_8));
    }

    /**
     * Subscribes the current thread to the given topic. This method blocks the current thread.
     * 
     * @param topic the topic
     * @return an infinite stream of received messages
     * @throws Exception if subscribing fails
     */
    public Stream<Map<String, Object>> subscribe(String topic) throws Exception {
        return ipfs.pubsub.sub(topic);
    }

    /**
     * Reads the model state hash for the given address from local storage. The corresponding model can be trusted. It
     * doesn't have to be validated.
     * 
     * @param address the address (defines a subnet)
     * @return the model state hash
     * @throws IOException if accessing the local storage fails
     */
    public String readModelStateHash(String address) throws IOException {
        File storage = new File(address);
        BufferedReader br = new BufferedReader(new FileReader(storage));
        try {
            return br.readLine();
        }
        finally {
            br.close();
        }
    }

    /**
     * Saves the model state hash for the given address in local storage. Call this for validated model states only,
     * since readers trust that the model state be valid.
     * 
     * @param address the address (defines a subnet)
     * @param hash    the model state hash
     * @throws IOException if writing fails
     */
    public void saveModelStateHash(String address, String hash) throws IOException {
        File storage = new File(address);
        BufferedWriter writer = new BufferedWriter(new FileWriter(storage));
        writer.write(hash);
        writer.close();
    }

}
