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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.projectjinxers.util.TestSerializationUtil.jsonString;

import java.io.IOException;
import java.math.BigInteger;

import org.ethereum.crypto.ECKey.ECDSASignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.projectjinxers.account.ECCSigner;
import org.projectjinxers.account.Signer;
import org.projectjinxers.account.Users;
import org.projectjinxers.model.IPLDObject;
import org.projectjinxers.model.IPLDSerializable;
import org.projectjinxers.model.Metadata;
import org.projectjinxers.model.ValidationContext;

/**
 * Test class for IPLD serialization via {@link IPLDWriter}.
 * 
 * @author ProjectJinxers
 */
class IPLDSerializationTest {

    static interface IPLDWritable extends IPLDSerializable {

        @Override
        default void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
                Metadata metadata) {

        }

    }

    private IPFSAccess access;
    private IPLDContext context;
    private IPLDWriter writer;

    private static boolean sign;

    @BeforeEach
    void setup() {
        access = new IPFSAccess();
        context = new IPLDContext(access, IPLDEncoding.JSON, IPLDEncoding.JSON, false);
        writer = IPLDEncoding.JSON.createWriter();
        sign = false;
    }

    static class SimpleData implements IPLDWritable {

        private int value;

        @Override
        public boolean isSignatureMandatory() {
            return sign;
        }

        @Override
        public void writeProperties(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {
            writer.writeNumber("value", value);
        }

    }

    @Test
    void testSimple() throws IOException {
        SimpleData simpleData = new SimpleData();
        simpleData.value = 9;
        IPLDObject<SimpleData> object = new IPLDObject<>(simpleData);
        byte[] bytes = writer.write(context, object, null);
        String json = new String(bytes);
        assertEquals(jsonString("{\"value\":9}"), json);
    }

    static class MultiValueData implements IPLDWritable {

        private char c;
        private String s;
        private boolean b;

        @Override
        public void writeProperties(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {
            writer.writeChar("c", c);
            writer.writeString("s", s);
            writer.writeBoolean("b", b);
        }

    }

    @Test
    void testMultiValue() throws IOException {
        MultiValueData multiValueData = new MultiValueData();
        multiValueData.c = 'n';
        multiValueData.s = "multi";
        multiValueData.b = true;
        IPLDObject<MultiValueData> object = new IPLDObject<>(multiValueData);
        byte[] bytes = writer.write(context, object, null);
        String json = new String(bytes);
        assertEquals(jsonString("{\"c\":110,\"s\":\"multi\",\"b\":true}"), json);
    }

    static class StringLinkValueData implements IPLDWritable {

        private String link;

        @Override
        public void writeProperties(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {
            writer.writeLink("link", link);
        }

    }

    @Test
    void testStringLink() throws IOException {
        StringLinkValueData stringLinkData = new StringLinkValueData();
        stringLinkData.link = "a";
        IPLDObject<StringLinkValueData> object = new IPLDObject<>(stringLinkData);
        byte[] bytes = writer.write(context, object, null);
        String json = new String(bytes);
        assertEquals(jsonString("{\"link\":{\"/\":\"a\"}}"), json);
    }

    static class ObjectLinkValueData implements IPLDWritable {

        private IPLDObject<?> link;

        @Override
        public void writeProperties(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {
            writer.writeLink("link", link, signer, context);
        }

    }

    @Test
    void testRecursiveObjectLinkValue() throws IOException {
        ObjectLinkValueData objectLinkValueData = new ObjectLinkValueData();
        SimpleData simpleA = new SimpleData();
        simpleA.value = 0;
        IPLDObject<?> a = new IPLDObject<>(simpleA);
        objectLinkValueData.link = a;
        IPLDObject<ObjectLinkValueData> object = new IPLDObject<>(objectLinkValueData);
        IPLDContext spy = Mockito.spy(context);
        Mockito.doReturn("a").when(spy).saveObject(Mockito.any(), Mockito.any());
        byte[] bytes = writer.write(spy, object, null);
        String json = new String(bytes);
        assertEquals(jsonString("{\"link\":{\"/\":\"a\"}}"), json);
    }

    static class PrimitiveArrayValueData implements IPLDWritable {

        private int[] ints;

        @Override
        public void writeProperties(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {
            writer.writeIntArray("ints", ints);
        }

    }

    @Test
    void testPrimitiveArrayValue() throws IOException {
        PrimitiveArrayValueData primitiveArrayValueData = new PrimitiveArrayValueData();
        primitiveArrayValueData.ints = new int[] { 8, 12, 4, 0 };
        IPLDObject<PrimitiveArrayValueData> object = new IPLDObject<>(primitiveArrayValueData);
        byte[] bytes = writer.write(context, object, null);
        String json = new String(bytes);
        assertEquals(jsonString("{\"ints\":[8,12,4,0]}"), json);
    }

    static class StringArrayValueData implements IPLDWritable {

        private String[] strings;

        @Override
        public void writeProperties(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {
            writer.writeStringArray("strings", strings);
        }

    }

    @Test
    void testStringArrayValue() throws IOException {
        StringArrayValueData stringArrayValueData = new StringArrayValueData();
        stringArrayValueData.strings = new String[] { "a", "b", "c" };
        IPLDObject<StringArrayValueData> object = new IPLDObject<>(stringArrayValueData);
        byte[] bytes = writer.write(context, object, null);
        String json = new String(bytes);
        assertEquals(jsonString("{\"strings\":[\"a\",\"b\",\"c\"]}"), json);
    }

    static class StringLinkArrayValueData implements IPLDWritable {

        private String[] links;

        @Override
        public void writeProperties(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {
            writer.writeLinkArray("links", links);
        }

    }

    @Test
    void testStringLinkArrayValue() throws IOException {
        StringLinkArrayValueData stringLinkArrayValueData = new StringLinkArrayValueData();
        stringLinkArrayValueData.links = new String[] { "a", "b", "c" };
        IPLDObject<StringLinkArrayValueData> object = new IPLDObject<>(stringLinkArrayValueData);
        byte[] bytes = writer.write(context, object, null);
        String json = new String(bytes);
        assertEquals(jsonString("{\"links\":[{\"/\":\"a\"},{\"/\":\"b\"},{\"/\":\"c\"}]}"), json);
    }

    static class ObjectLinkArrayValueData implements IPLDWritable {

        private IPLDObject<?>[] links;

        @Override
        public void writeProperties(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {
            writer.writeLinkArray("links", links, signer, context);
        }

    }

    @Test
    void testObjectLinkArrayValue() throws IOException {
        ObjectLinkArrayValueData objectArrayValueData = new ObjectLinkArrayValueData();
        IPLDObject<?> a = new IPLDObject<>("a", context, null, null);
        IPLDObject<?> b = new IPLDObject<>("b", context, null, null);
        IPLDObject<?> c = new IPLDObject<>("c", context, null, null);
        objectArrayValueData.links = new IPLDObject<?>[] { a, b, c };
        IPLDObject<ObjectLinkArrayValueData> object = new IPLDObject<>(objectArrayValueData);
        byte[] bytes = writer.write(context, object, null);
        String json = new String(bytes);
        assertEquals(jsonString("{\"links\":[{\"/\":\"a\"},{\"/\":\"b\"},{\"/\":\"c\"}]}"), json);
    }

    @Test
    void testRecursiveObjectLinkArrayValue() throws IOException {
        ObjectLinkArrayValueData objectArrayValueData = new ObjectLinkArrayValueData();
        SimpleData simpleA = new SimpleData();
        simpleA.value = 0;
        IPLDObject<?> a = new IPLDObject<>(simpleA);
        SimpleData simpleB = new SimpleData();
        simpleB.value = 1;
        IPLDObject<?> b = new IPLDObject<>(simpleB);
        SimpleData simpleC = new SimpleData();
        simpleC.value = 2;
        IPLDObject<?> c = new IPLDObject<>(simpleC);
        objectArrayValueData.links = new IPLDObject<?>[] { a, b, c };
        IPLDObject<ObjectLinkArrayValueData> object = new IPLDObject<>(objectArrayValueData);
        IPLDContext spy = Mockito.spy(context);
        Mockito.doAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                IPLDObject<?> object = invocation.getArgument(0, IPLDObject.class);
                return String.valueOf((char) ('a' + ((SimpleData) object.getMapped()).value));
            }
        }).when(spy).saveObject(Mockito.any(), Mockito.any());
        byte[] bytes = writer.write(spy, object, null);
        String json = new String(bytes);
        assertEquals(jsonString("{\"links\":[{\"/\":\"a\"},{\"/\":\"b\"},{\"/\":\"c\"}]}"), json);
    }

    @Test
    void testSign() throws IOException {
        SimpleData simpleData = new SimpleData();
        simpleData.value = 41;
        IPLDObject<SimpleData> object = new IPLDObject<>(simpleData);
        sign = true;
        ECCSigner signer = new ECCSigner("user", "pass");
        byte[] bytes = writer.write(context, object, signer);
        String json = new String(bytes);
        ECDSASignature expectedSignature = new ECDSASignature(
                new BigInteger("1357931233302495577296061641443476864107299032798182399163036348318695313558"),
                new BigInteger("23462658328756911968606588084224614208373614148725659541764927042184603482760"));
        expectedSignature.v = 28;
        assertEquals(jsonString(expectedSignature, "{\"value\":41}"), json);
        assertTrue(context.verifySignature(object, signer, Users.createAccount("user", "pass").getPubKey()));
    }

}
