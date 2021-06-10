/*
	Copyright (C) 2021 ProjectJinxers

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.projectjinxers.config;

import java.util.Map;

/**
 * Corresponds to the config.yml resource file.
 * 
 * @author ProjectJinxers
 */
public class Config extends YamlConfig {
	
	private static final String KEY_IPFS_NODE = "ipfs.node";
	private static final String KEY_IPFS_HOST = "host";
	private static final String KEY_IPFS_PORT = "port";
	private static final String KEY_IPFS_VERSION = "version";
	private static final String KEY_IPFS_NODE_TIMEOUT = "timeout";
	private static final String KEY_IPFS_CONN_TIMEOUT = "connection";
	private static final String KEY_IPFS_READ_TIMEOUT = "read";
	private static final String KEY_IPFS_SECURE = "secure";
	
	private static Config sharedInstance;
	
	/**
	 * @return the singleton shared instance
	 */
	public static Config getSharedInstance() {
		if (sharedInstance == null) {
			sharedInstance = new Config();
		}
 		return sharedInstance;
	}
	
	private final String ipfsHost;
	private final int ipfsPort;
	private final String ipfsVersion;
	private final int ipfsConnectionTimeout;
	private final int ipfsReadTimeout;
	private final boolean ipfsSecure;
	
	private Config() {
		super("config.yml");
		Map<?, ?> node = getRootObject(KEY_IPFS_NODE);
		this.ipfsHost = (String) node.get(KEY_IPFS_HOST);
		this.ipfsPort = (int) node.get(KEY_IPFS_PORT);
		this.ipfsVersion = (String) node.get(KEY_IPFS_VERSION);
		if (node.containsKey(KEY_IPFS_NODE_TIMEOUT)) {
			Map<?, ?> timeouts = (Map<?, ?>) node.get(KEY_IPFS_NODE_TIMEOUT);
			this.ipfsConnectionTimeout = (int) timeouts.get(KEY_IPFS_CONN_TIMEOUT);
			this.ipfsReadTimeout = (int) timeouts.get(KEY_IPFS_READ_TIMEOUT);
		}
		else {
			this.ipfsConnectionTimeout = -1;
			this.ipfsReadTimeout = -1;
		}
		this.ipfsSecure = Boolean.TRUE.equals(node.get(KEY_IPFS_SECURE));
	}
	
	/**
	 * @return true if the IPFS timeouts have been configured
	 */
	public boolean hasIPFSTimeout() {
		return ipfsConnectionTimeout >= 0;
	}
	
	/**
	 * @return the host name of the IPFS node
	 */
	public String getIPFSHost() {
		return ipfsHost;
	}
	
	/**
	 * @return the port of the IPFS node
	 */
	public int getIPFSPort() {
		return ipfsPort;
	}
	
	/**
	 * @return the IPFS version fragment of the URL
	 */
	public String getIPFSVersion() {
		return ipfsVersion;
	}
	
	/**
	 * @return the connection timeout for connections to the configured IPFS node
	 */
	public int getIPFSConnectionTimeout() {
		return ipfsConnectionTimeout;
	}
	
	/**
	 * @return the read timeout for connections to the configured IPFS node
	 */
	public int getIPFSReadTimeout() {
		return ipfsReadTimeout;
	}
	
	/**
	 * @return true if the https scheme is to be used
	 */
	public boolean isIPFSSecure() {
		return ipfsSecure;
	}

}
