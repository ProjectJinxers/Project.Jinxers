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
package org.projectjinxers.config;

import java.util.Map;

/**
 * Corresponds to the config.yml resource file.
 * 
 * @author ProjectJinxers
 */
public class Config extends YamlConfig<Config.Root> {

    static class Root {

        public IPFS ipfs;
        public IOTA iota;
        public ValidationParams validationParams;

    }

    static class IPFS {

        public Node node;

    }

    static class Node {

        public String multiaddr;
        public String host;
        public int port;
        public String version;
        public boolean secure;
        public Timeout timeout;

    }

    static class Timeout {

        public int connection;
        public int read;

    }

    static class IOTA {

        public IOTAMain main;
        public Map<String, String> validHashes;

    }

    static class IOTAMain {

        public String address;

    }

    static class ValidationParams {

        public Long timestampTolerance;

    }

    // if changed in a running system, all affected model meta versions must be changed as well and validation must be
    // adjusted
    public static final long DEFAULT_TIMESTAMP_TOLERANCE = 1000L * 60 * 2;

    private static Config sharedInstance;

    /**
     * @return the singleton shared instance for default values
     */
    public static Config getSharedInstance() {
        if (sharedInstance == null) {
            sharedInstance = new Config("config.yml");
        }
        return sharedInstance;
    }

    private String iotaAddress;
    private Long timestampTolerance;

    private Config(Root root) {
        super(root);
    }

    /**
     * Constructor for custom configuration values.
     * 
     * @param filePath the path to the YAML file containing the custom configuration values (must be on the classpath)
     */
    public Config(String filePath) {
        super(filePath, Root.class);
    }

    /**
     * @return the multiaddr for configuring the IPFS node
     */
    public String getIPFSMultiaddr() {
        return root.ipfs.node.multiaddr;
    }

    /**
     * @return true if the IPFS timeouts have been configured
     */
    public boolean hasIPFSTimeout() {
        return root.ipfs.node.timeout != null;
    }

    /**
     * @return the host name of the IPFS node
     */
    public String getIPFSHost() {
        return root.ipfs.node.host;
    }

    /**
     * @return the port of the IPFS node
     */
    public int getIPFSPort() {
        return root.ipfs.node.port;
    }

    /**
     * @return the IPFS version fragment of the URL
     */
    public String getIPFSVersion() {
        return root.ipfs.node.version;
    }

    /**
     * @return the connection timeout for connections to the configured IPFS node
     */
    public int getIPFSConnectionTimeout() {
        return root.ipfs.node.timeout.connection;
    }

    /**
     * @return the read timeout for connections to the configured IPFS node
     */
    public int getIPFSReadTimeout() {
        return root.ipfs.node.timeout.read;
    }

    /**
     * @return true if the https scheme is to be used
     */
    public boolean isIPFSSecure() {
        return root.ipfs.node.secure;
    }

    /**
     * @return the main IOTA address (defines a subnet)
     */
    public String getIOTAAddress() {
        if (iotaAddress == null) {
            iotaAddress = root.iota.main.address;
        }
        return iotaAddress;
    }

    public String getValidHash(String iotaAddress) {
        Map<String, String> validHashes = root.iota.validHashes;
        return validHashes == null ? null : validHashes.get(iotaAddress);
    }

    public long getTimestampTolerance() {
        if (timestampTolerance == null) {
            ValidationParams params = root.validationParams;
            Long res = params == null ? null : params.timestampTolerance;
            timestampTolerance = res == null ? DEFAULT_TIMESTAMP_TOLERANCE : res;
        }
        return timestampTolerance;
    }

    public Config subConfig(String iotaAddress, long timestampTolerance) {
        Config res = new Config(root);
        res.iotaAddress = iotaAddress;
        res.timestampTolerance = timestampTolerance;
        return res;
    }

}
