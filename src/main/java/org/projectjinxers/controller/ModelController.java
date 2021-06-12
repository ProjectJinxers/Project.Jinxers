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
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import org.projectjinxers.config.Config;
import org.projectjinxers.ipld.IPLDEncoding;
import org.projectjinxers.model.IPLDObject;
import org.projectjinxers.model.ModelState;
import org.projectjinxers.model.ValidationContext;

/**
 * Coordinates the model states. The constructor tries to find and initialize the most recent state. It also subscribes
 * to the topics for being able to continuously receive model states and ownership requests from peers.
 * 
 * @author ProjectJinxers
 */
public class ModelController {

    private IPFSAccess access = new IPFSAccess();
    private IPLDContext context = new IPLDContext(access, IPLDEncoding.JSON, IPLDEncoding.CBOR, false);

    private String mainIOTAAddress;
    private IPLDObject<ModelState> currentModelState;

    private Config config;

    /**
     * Constructor. If it returns without throwing an exception, the instance is completely initialized and continuously
     * listens for model states and ownership requests from peers.
     * 
     * @throws Exception if initialization failed and the application should not continue running.
     */
    public ModelController() throws Exception {
        config = Config.getSharedInstance();
        mainIOTAAddress = config.getIOTAMainAddress();
        String currentModelStateHash;
        try {
            currentModelStateHash = readModelStateHash(mainIOTAAddress);
            if (currentModelStateHash != null) {
                currentModelState = loadModelState(currentModelStateHash, false);
            }
        }
        catch (FileNotFoundException e) {
            do {
                currentModelStateHash = readNextModelStateHashFromTangle(mainIOTAAddress);
                if (currentModelStateHash != null) {
                    try {
                        currentModelState = loadModelState(currentModelStateHash, true);
                        saveModelStateHash(currentModelStateHash);
                    }
                    catch (Exception e2) {

                    }
                }
            }
            while (currentModelStateHash != null);
        }
        access.ipfs.pubsub.sub(mainIOTAAddress);
        access.ipfs.pubsub.sub("or" + mainIOTAAddress);
    }

    public IPLDObject<ModelState> getCurrentModelState() {
        return currentModelState;
    }

    private String readModelStateHash(String address) throws IOException {
        File storage = new File(address);
        BufferedReader br = new BufferedReader(new FileReader(storage));
        try {
            return br.readLine();
        }
        finally {
            br.close();
        }
    }

    private String readNextModelStateHashFromTangle(String address) {
        return null;
    }

    private IPLDObject<ModelState> loadModelState(String multihash, boolean validate) {
        ModelState modelState = new ModelState();
        IPLDObject<ModelState> object = new IPLDObject<>(multihash, modelState, context,
                validate ? new ValidationContext() : null);
        object.getMapped();
        return object;
    }

    private void saveModelStateHash(String address) throws IOException {
        File storage = new File(address);
        BufferedWriter writer = new BufferedWriter(new FileWriter(storage));
        writer.write(address);
        writer.close();
    }

}
