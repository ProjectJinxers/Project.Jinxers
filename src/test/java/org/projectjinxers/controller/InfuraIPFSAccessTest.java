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

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Base64;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.projectjinxers.account.Signer;
import org.projectjinxers.config.Config;
import org.projectjinxers.config.TestSecretConfig;
import org.projectjinxers.model.IPLDSerializable;
import org.projectjinxers.model.Loader;
import org.projectjinxers.model.Metadata;
import org.projectjinxers.model.ValidationContext;

import io.ipfs.api.IPFS;
import io.ipfs.api.JSONParser;
import io.ipfs.api.MerkleNode;
import io.ipfs.api.Multipart;
import io.ipfs.api.NamedStreamable;

/**
 * Test class for connecting to ipfs.infura.io. The only two add operations that didn't return 500, 403 or 401 are root
 * add and block put. Neither of them required authentication. Currently dag put doesn't work. With valid credentials
 * for an Ethereum project, the server returns 403. With sign in credentials or credentials for a FileCoin project, it
 * returns 401. In the dashboard for the Ethereum project the dag put requests are listed as (blank). The root add
 * requests are listed as /api/v0/add. The tests only work, if the ipfs.infura.io node is configured in config.yml. You
 * can rename or copy the file config-infura.yml. Infura secrets must be configured in test-secret-config.yml, which is
 * obviously not pushed to version control. The structure is: infura at root level and 'user' as well as 'pass' as
 * children. The current save test doesn't actually save. The hash for reading the object is what the save operation, if
 * it worked, would return. Reading (dag get) didn't work at first (read timeout), but after a little while, the
 * document, which had been saved on the local node, apparently made it to the Infura node.
 * 
 * @author ProjectJinxers
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ IPFS.class })
@PowerMockIgnore("javax.management.*")
public class InfuraIPFSAccessTest {

    private static final class BasicAuthenticator extends Authenticator {

        private static String user;
        private static String pass;

        {
            TestSecretConfig config = TestSecretConfig.getSharedInstance();
            user = config.getInfuraUser();
            pass = config.getInfuraPass();
        }

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(user, pass.toCharArray());
        }
    }

    @BeforeClass
    public static void configureBasicAuth() {
        Authenticator.setDefault(new BasicAuthenticator());
    }

    @Test
    public void testConfig() {
        Assert.assertEquals("ipfs.infura.io", Config.getSharedInstance().getIPFSHost());
    }

    private byte[] bytes;

    @Test
    public void testSave() throws Exception {
        IPFSAccess access = new IPFSAccess();
        access.configure();
        Field field = IPFSAccess.class.getDeclaredField("ipfs");
        field.setAccessible(true);
        IPFS ipfs = (IPFS) field.get(access);

        IPFS.Dag spy = PowerMockito.spy(ipfs.dag);
        IPLDContext context = new IPLDContext(access, IPLDEncoding.JSON, IPLDEncoding.CBOR, false) {
            @Override
            public String saveObject(IPLDObject<?> object, Signer signer) throws IOException {
                IPLDWriter writer = IPLDEncoding.JSON.createWriter();
                final byte[] writtenBytes = writer.write(this, object, signer);
                String fileContents = new String(writtenBytes, StandardCharsets.UTF_8);
                System.out.println(fileContents);
                bytes = writtenBytes;
                MerkleNode node = spy.put(IPLDEncoding.JSON.getIn(), bytes, IPLDEncoding.CBOR.getIn());
                return node.hash.toString();
            }
        };
        Mockito.doAnswer(new Answer<MerkleNode>() {
            @Override
            public MerkleNode answer(InvocationOnMock invocation) throws Throwable {
                String version = Config.getSharedInstance().getIPFSVersion();
                String prefix = ipfs.protocol + "://" + ipfs.host + ":" + ipfs.port
                        + (version == null ? "/api/v0/" : version);

                Multipart m = new Multipart(prefix + "dag/put/?stream-channels=true&input-enc="
                        + invocation.getArgument(0) + "&f=" + invocation.getArgument(2), "UTF-8");
                Field connField = m.getClass().getDeclaredField("httpConn");
                connField.setAccessible(true);
                HttpURLConnection conn = (HttpURLConnection) connField.get(m);
                Field streamField = m.getClass().getDeclaredField("out");
                streamField.setAccessible(true);
                URL url = conn.getURL();
                conn.disconnect();
                try {
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setUseCaches(false);
                    conn.setDoOutput(true);
                    conn.setDoInput(true);
                    conn.setRequestProperty("Expect", "100-continue");
                    conn.setRequestProperty("Content-Type",
                            "multipart/form-data; boundary=" + Multipart.createBoundary());
                    conn.setRequestProperty("User-Agent", "Java IPFS CLient");
                    String encoded = Base64.getEncoder().encodeToString(
                            (BasicAuthenticator.user + ":" + BasicAuthenticator.pass).getBytes(StandardCharsets.UTF_8));
                    conn.setRequestProperty("Authorization", "Basic " + encoded);
                    streamField.set(m, conn.getOutputStream());
                    connField.set(m, conn);
                }
                catch (IOException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }

                m.addFilePart("file", Paths.get(""), new NamedStreamable.ByteArrayWrapper(bytes));
                String res = m.finish();
                return MerkleNode.fromJSON(JSONParser.parse(res));
            }
        }).when(spy).put(Mockito.any(), Mockito.any(), Mockito.any());

        TestData testData = new TestData();
        testData.text = "Yo!";
        // The next 2 lines would save the testData. Unfortunately Infura returns a 403 error. So this test only reads.
        // IPLDObject<TestData> wrapper = new IPLDObject<>(testData);
        String multihash = "zdpuAzDcDFcSmmSgCtV5hzQrL5JUtXu8BPyz7rvGUVsepLCcs";// context.saveObject(wrapper, null);
        Assert.assertNotNull(multihash);
        TestData read = new TestData();
        context.loadObject(multihash, read, null);
        Assert.assertEquals(testData.text, read.text);
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
