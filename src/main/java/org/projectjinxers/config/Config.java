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

/**
 * Corresponds to the config.yml resource file.
 * 
 * @author ProjectJinxers
 */
public class Config extends YamlConfig<Config.Root> {

    static class Root {

        public IPFS ipfs;
        public IOTA iota;

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

    }

    static class IOTAMain {

        public String address;

    }

    private static Config sharedInstance;

    /**
     * @return the singleton shared instance for default values
     */
    public static Config getSharedInstance() {
        if (sharedInstance == null) {
            sharedInstance = new Config();
        }
        return sharedInstance;
    }

    private Config() {
        super("config.yml", Root.class);
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
    public String getIOTAMainAddress() {
        return root.iota.main.address;
    }

}
