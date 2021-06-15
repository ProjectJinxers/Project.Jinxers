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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.projectjinxers.account.Signer;
import org.projectjinxers.config.Config;
import org.projectjinxers.model.IPLDSerializable;
import org.projectjinxers.model.Loader;
import org.projectjinxers.model.Metadata;
import org.projectjinxers.model.ValidationContext;

/**
 * Test class for saving and retrieving a simple JSON string on a local IPFS node. The out encoding has to be cbor.
 * Didn't work with json. The transferred data is always JSON, however.
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
        access.configure();
        IPLDContext context = new IPLDContext(access, IPLDEncoding.JSON, IPLDEncoding.CBOR, false);
        TestData testData = new TestData();
        testData.text = "Yo!";
        IPLDObject<TestData> wrapper = new IPLDObject<>(testData);
        String multihash;
        multihash = wrapper.save(context, null);
        Assert.assertNotNull(multihash);
        Assert.assertEquals(multihash, wrapper.getMultihash());
        TestData read = new TestData();
        LoadResult result = context.loadObject(multihash, read, null);
        Assert.assertSame(result.getFromCache(), wrapper);
    }

    @Test
    void testLoad() throws IOException {
        IPFSAccess access = new IPFSAccess();
        access.configure();
        IPLDContext context = new IPLDContext(access, IPLDEncoding.JSON, IPLDEncoding.CBOR, false);
        TestData read = new TestData();
        LoadResult result = context.loadObject("zdpuAzDcDFcSmmSgCtV5hzQrL5JUtXu8BPyz7rvGUVsepLCcs", read, null);
        Assert.assertNull(result.getFromCache());
        Assert.assertEquals("Yo!", read.text);
    }

    static class TestData implements IPLDSerializable, Loader<TestData> {

        private String text;

        @Override
        public TestData getOrCreateDataInstance(IPLDReader reader, Metadata metadata) {
            return this;
        }

        @Override
        public TestData getLoaded() {
            return this;
        }

        @Override
        public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
                Metadata metadata) {
            this.text = reader.readString("text");
        }

        @Override
        public void write(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {
            writer.writeString("text", text);
        }

    }

}
