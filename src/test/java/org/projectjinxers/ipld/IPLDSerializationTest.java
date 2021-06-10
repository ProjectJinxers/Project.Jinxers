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
package org.projectjinxers.ipld;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.ethereum.crypto.ECKey;
import org.junit.jupiter.api.Test;
import org.projectjinxers.model.IPLDObject;
import org.projectjinxers.model.IPLDSerializable;

/**
 * Test class for IPLD serialization via {@link IPLDWriter}.
 * 
 * @author ProjectJinxers
 */
class IPLDSerializationTest {

    @Test
    void testSimple() throws IOException {
        IPFSAccess access = new IPFSAccess();
        IPLDContext context = new IPLDContext(access, IPLDEncoding.JSON, IPLDEncoding.JSON, false);
        IPLDWriter writer = IPLDEncoding.JSON.createWriter();
        SimpleData simpleData = new SimpleData();
        simpleData.value = 9;
        IPLDObject<SimpleData> object = new IPLDObject<>(simpleData);
        byte[] bytes = writer.write(context, object, null);
        String json = new String(bytes);
        assertEquals("{\"value\":9}", json);
    }

    static class SimpleData implements IPLDSerializable {

        private int value;

        @Override
        public void read(IPLDReader reader, IPLDContext contextForRecursion) {
            value = reader.readNumber("value").intValue();
        }

        @Override
        public byte[] writeProperties(IPLDWriter writer, ECKey signingKey, IPLDContext context) throws IOException {
            writer.writeNumber("value", value);
            return null;
        }

        @Override
        public byte[] hash() {
            return null;
        }

    }

}
