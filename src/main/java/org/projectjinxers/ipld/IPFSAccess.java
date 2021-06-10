/*
 * Copyright (C) 2021 ProjectJinxers
 * 
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.projectjinxers.ipld;

import org.projectjinxers.config.Config;

import io.ipfs.api.IPFS;

/**
 * Provides access to the IPFS API.
 * 
 * @author ProjectJinxers
 */
public class IPFSAccess {

    /**
     * The access to the IPFS API.
     */
    final IPFS ipfs;

    /**
     * Reads the config and configures the {@link IPFS} instance appropriately.
     */
    public IPFSAccess() {
        Config config = Config.getSharedInstance();
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
