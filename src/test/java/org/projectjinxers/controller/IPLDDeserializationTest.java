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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.projectjinxers.util.TestSerializationUtil.jsonBytes;

import java.io.IOException;

import org.ethereum.crypto.ECKey.ECDSASignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.projectjinxers.account.Signer;
import org.projectjinxers.model.IPLDSerializable;
import org.projectjinxers.model.Loader;
import org.projectjinxers.model.LoaderFactory;
import org.projectjinxers.model.Metadata;
import org.projectjinxers.model.ValidationContext;

/**
 * Test class for IPLD deserialization via {@link IPLDReader}.
 * 
 * @author ProjectJinxers
 */
class IPLDDeserializationTest {

    static interface IPLDReadable extends IPLDSerializable, Loader<IPLDReadable> {

        @Override
        default IPLDReadable getOrCreateDataInstance(IPLDReader reader, Metadata metadata) {
            return this;
        }

        @Override
        default IPLDReadable getLoaded() {
            return this;
        }

        @Override
        default void write(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {

        }

    }

    private IPLDContext context;
    private IPLDReader reader;

    @BeforeEach
    void setup() {
        context = new IPLDContext(null, IPLDEncoding.JSON, IPLDEncoding.JSON, false);
        reader = IPLDEncoding.JSON.createReader();
    }

    static class SimpleData implements IPLDReadable {

        private int value;

        @Override
        public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
                Metadata metadata) {
            value = reader.readNumber("value").intValue();
        }

    }

    @Test
    void testSimple() {
        SimpleData simple = new SimpleData();
        String json = "{\"value\":9}";
        reader.read(context, null, jsonBytes(json), simple, false);
        assertEquals(9, simple.value);
    }

    static class MultiValueData implements IPLDReadable {

        private char c;
        private String s;
        private boolean b;

        @Override
        public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
                Metadata metadata) {
            c = reader.readCharacter("c");
            s = reader.readString("s");
            b = reader.readBoolean("b");
        }

    }

    @Test
    void testMultiValue() {
        MultiValueData multiValueData = new MultiValueData();
        String json = "{\"c\":110,\"s\":\"multi\",\"b\":true}";
        reader.read(context, null, jsonBytes(json), multiValueData, false);
        assertEquals('n', multiValueData.c);
        assertEquals("multi", multiValueData.s);
        assertEquals(true, multiValueData.b);
    }

    static class StringLinkValueData implements IPLDReadable {

        private String link;

        @Override
        public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
                Metadata metadata) {
            link = reader.readLink("link");
        }

    }

    @Test
    void testStringLinkValue() {
        StringLinkValueData stringLinkValueData = new StringLinkValueData();
        String json = "{\"link\":{\"/\":\"a\"}}";
        reader.read(context, null, jsonBytes(json), stringLinkValueData, false);
        assertEquals("a", stringLinkValueData.link);
    }

    static class ObjectLinkValueData implements IPLDReadable {

        private IPLDObject<SimpleData> link;

        @Override
        public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
                Metadata metadata) {
            link = reader.readLinkObject("link", context, validationContext, new LoaderFactory<SimpleData>() {
                @SuppressWarnings({ "unchecked", "rawtypes" })
                @Override
                public Loader<SimpleData> createLoader() {
                    return (Loader) new SimpleData();
                }
            }, eager);
        }

    }

    private boolean didRecurse = false;

    @Test
    void testObjectLinkValue() throws IOException {
        ObjectLinkValueData objectLinkValueData = new ObjectLinkValueData();
        String json = "{\"link\":{\"/\":\"a\"}}";
        IPLDContext spy = Mockito.spy(context);
        didRecurse = false;
        Mockito.doAnswer(new Answer<ECDSASignature>() {

            @Override
            public ECDSASignature answer(InvocationOnMock invocation) throws Throwable {
                didRecurse = true;
                return null;
            }

        }).when(spy).loadObject(Mockito.anyString(), Mockito.any(), Mockito.any());
        reader.read(spy, null, jsonBytes(json), objectLinkValueData, true);
        assertTrue(didRecurse);
        assertEquals("a", objectLinkValueData.link.getMultihash());
    }

    static class PrimitiveArrayValueData implements IPLDReadable {

        private int[] ints;

        @Override
        public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
                Metadata metadata) {
            ints = reader.readIntArray("ints");
        }

    }

    @Test
    void testPrimitiveArrayValue() throws IOException {
        PrimitiveArrayValueData primitiveArrayValueData = new PrimitiveArrayValueData();
        String json = "{\"ints\":[8,12,4,0]}";
        reader.read(context, null, jsonBytes(json), primitiveArrayValueData, false);
        assertArrayEquals(new int[] { 8, 12, 4, 0 }, primitiveArrayValueData.ints);
    }

    static class StringArrayValueData implements IPLDReadable {

        private String[] strings;

        @Override
        public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
                Metadata metadata) {
            strings = reader.readStringArray("strings");
        }

    }

    @Test
    void testStringArrayValue() throws IOException {
        StringArrayValueData stringArrayValueData = new StringArrayValueData();
        String json = "{\"strings\":[\"a\",\"b\",\"c\"]}";
        reader.read(context, null, jsonBytes(json), stringArrayValueData, true);
        assertArrayEquals(new String[] { "a", "b", "c" }, stringArrayValueData.strings);
    }

    static class StringLinkArrayValueData implements IPLDReadable {

        private String[] links;

        @Override
        public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
                Metadata metadata) {
            links = reader.readLinksArray("links");
        }

    }

    @Test
    void testStringLinkArrayValue() {
        StringLinkArrayValueData stringLinkArrayValueData = new StringLinkArrayValueData();
        String json = "{\"links\":[{\"/\":\"a\"},{\"/\":\"b\"},{\"/\":\"c\"}]}";
        reader.read(context, null, jsonBytes(json), stringLinkArrayValueData, true);
        assertArrayEquals(new String[] { "a", "b", "c" }, stringLinkArrayValueData.links);
    }

    static class ObjectLinkArrayValueData implements IPLDReadable {

        private IPLDObject<SimpleData>[] links;

        @Override
        public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
                Metadata metadata) {
            links = reader.readLinkObjectsArray("links", context, validationContext, new LoaderFactory<SimpleData>() {
                @SuppressWarnings({ "unchecked", "rawtypes" })
                @Override
                public Loader<SimpleData> createLoader() {
                    return (Loader) new SimpleData();
                }
            }, eager);
        }

    }

    @Test
    void testObjectLinkArrayValue() {
        ObjectLinkArrayValueData objectLinkArrayValueData = new ObjectLinkArrayValueData();
        String json = "{\"links\":[{\"/\":\"a\"},{\"/\":\"b\"},{\"/\":\"c\"}]}";
        reader.read(context, null, jsonBytes(json), objectLinkArrayValueData, false);
        String[] linkMultihashes = new String[objectLinkArrayValueData.links.length];
        int i = 0;
        for (IPLDObject<?> link : objectLinkArrayValueData.links) {
            linkMultihashes[i++] = link.getMultihash();
        }
        assertArrayEquals(new String[] { "a", "b", "c" }, linkMultihashes);
    }

    private boolean[] didRecurseAll;
    private int recursions;

    @Test
    void testRecursiveObjectLinkArrayValue() throws IOException {
        ObjectLinkArrayValueData objectLinkArrayValueData = new ObjectLinkArrayValueData();
        String json = "{\"links\":[{\"/\":\"a\"},{\"/\":\"b\"},{\"/\":\"c\"}]}";
        didRecurseAll = new boolean[3];
        recursions = 0;
        IPLDContext spy = Mockito.spy(context);
        Mockito.doAnswer(new Answer<ECDSASignature>() {

            @Override
            public ECDSASignature answer(InvocationOnMock invocation) throws Throwable {
                didRecurseAll[recursions++] = true;
                return null;
            }

        }).when(spy).loadObject(Mockito.anyString(), Mockito.any(), Mockito.any());
        reader.read(spy, null, jsonBytes(json), objectLinkArrayValueData, true);
        String[] linkMultihashes = new String[objectLinkArrayValueData.links.length];
        int i = 0;
        for (IPLDObject<?> link : objectLinkArrayValueData.links) {
            linkMultihashes[i++] = link.getMultihash();
        }
        assertArrayEquals(new String[] { "a", "b", "c" }, linkMultihashes);
        assertEquals(3, recursions);
        assertArrayEquals(new boolean[] { true, true, true }, didRecurseAll);
    }

}
