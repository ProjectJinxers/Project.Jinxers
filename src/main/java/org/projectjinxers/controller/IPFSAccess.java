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

import io.ipfs.api.IPFS;
import io.ipfs.api.MerkleNode;
import io.ipfs.cid.Cid;

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
    }

    public byte[] loadObject(String multihash) throws IOException {
        return ipfs.dag.get(Cid.decode(multihash));
    }

    public String saveObject(String inputFormat, byte[] bytes, String outputFormat) throws IOException {
        MerkleNode node = ipfs.dag.put(inputFormat, bytes, outputFormat);
        return node.hash.toString();
    }

    public void publish(String topic, String message) throws Exception {
        ipfs.pubsub.pub(topic, URLEncoder.encode(message, StandardCharsets.UTF_8));
    }

    public Stream<Map<String, Object>> subscribe(String topic) throws Exception {
        return ipfs.pubsub.sub(topic);
    }

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

    public void saveModelStateHash(String address, String hash) throws IOException {
        File storage = new File(address);
        BufferedWriter writer = new BufferedWriter(new FileWriter(storage));
        writer.write(hash);
        writer.close();
    }

}
