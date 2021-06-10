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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.ethereum.crypto.ECKey;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.projectjinxers.config.Config;
import org.projectjinxers.model.IPLDObject;
import org.projectjinxers.model.IPLDSerializable;

/**
 * Test class for saving and retrieving a simple JSON string on a local IPFS
 * node. The out encoding has to be cbor. Didn't work with json. The transferred
 * data is always JSON, however.
 * 
 * @author ProjectJinxers
 */
class LocalIPFSAccessTest {

    @Test
    void testConfig() {
        assertEquals("localhost", Config.getSharedInstance().getIPFSHost());
    }

    @Test
    void testSave() throws IOException {
        IPFSAccess access = new IPFSAccess();
        IPLDContext context = new IPLDContext(access, IPLDEncoding.JSON, IPLDEncoding.CBOR, false);
        TestData testData = new TestData();
        testData.text = "Yo!";
        IPLDObject<TestData> wrapper = new IPLDObject<>(testData);
        String multihash;
        multihash = context.saveObject(wrapper, null);
        Assert.assertNotNull(multihash);
        TestData read = new TestData();
        context.loadObject(multihash, read);
        Assert.assertEquals(testData.text, read.text);
    }

    static class TestData implements IPLDSerializable {

        private String text;

        @Override
        public void read(IPLDReader reader, IPLDContext contextForRecursion) {
            this.text = reader.readString("text");
        }

        @Override
        public byte[] writeProperties(IPLDWriter writer, ECKey signingKey, IPLDContext context) throws IOException {
            writer.writeString("text", text);
            return null;
        }

        @Override
        public byte[] hash() {
            return null;
        }

    }

}