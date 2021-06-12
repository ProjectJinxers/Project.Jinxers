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

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import org.ethereum.crypto.ECKey.ECDSASignature;
import org.projectjinxers.account.Signer;
import org.projectjinxers.model.IPLDObject;
import org.projectjinxers.model.IPLDSerializable;
import org.projectjinxers.model.Metadata;
import org.spongycastle.util.Arrays;

import com.google.gson.stream.JsonWriter;

/**
 * JSON implementation of the interface for writing (serializing) objects to IPFS. The default implementation will not
 * write JSON null values. If a value is to be written, and that value is null, it is skipped. You should not rely on
 * JSON null. There should be no difference between a missing field and a field with value null.
 * 
 * @author ProjectJinxers
 */
public class IPLDJsonWriter implements IPLDWriter {

    private JsonWriter jsonWriter;

    IPLDJsonWriter() {

    }

    @Override
    public byte[] write(IPLDContext context, IPLDObject<?> object, Signer signer) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        jsonWriter = new JsonWriter(new BufferedWriter(new OutputStreamWriter(baos)));
        jsonWriter.beginObject().name(IPLDJsonReader.KEY_DATA).beginObject();
        jsonWriter.flush();
        byte[] start = baos.toByteArray();
        baos.reset();
        // jsonWriter.beginObject();
        object.write(this, signer, context);
        jsonWriter.flush();
        byte[] data = baos.toByteArray();
        baos.reset();
        jsonWriter.endObject();
        Metadata metadata = object.signIfMandatory(signer, data);
        writeMetadata(metadata);
        jsonWriter.endObject();
        jsonWriter.flush();
        byte[] end = baos.toByteArray();
        return Arrays.concatenate(start, data, end);
    }

    @Override
    public byte[] hashBase(IPLDContext context, IPLDSerializable data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        jsonWriter = new JsonWriter(new BufferedWriter(new OutputStreamWriter(baos)));
        jsonWriter.beginObject();
        data.write(this, null, context);
        jsonWriter.flush();
        // We needed beginObject() in write() after KEY_DATA, otherwise the name would not be contained in start but
        // in data. OTOH we also need beginObject() here, to prevent an exception. To make sure the hash base is the
        // same in both cases, we remove the first byte here.
        byte[] bytes = baos.toByteArray();
        byte[] res = new byte[bytes.length - 1];
        System.arraycopy(bytes, 1, res, 0, res.length);
        return res;
    }

    @Override
    public void writeBoolean(String key, Boolean value) throws IOException {
        if (value != null) {
            jsonWriter.name(key).value(value);
        }
    }

    @Override
    public void writeChar(String key, Character value) throws IOException {
        if (value != null) {
            jsonWriter.name(key).value(value);
        }
    }

    @Override
    public void writeNumber(String key, Number value) throws IOException {
        if (value != null) {
            jsonWriter.name(key).value(value);
        }
    }

    @Override
    public void writeString(String key, String value) throws IOException {
        if (value != null) {
            jsonWriter.name(key).value(value);
        }
    }

    @Override
    public void writeLink(String key, String link) throws IOException {
        if (link != null) {
            jsonWriter.name(key);
            writeLink(link);
        }
    }

    @Override
    public void writeBooleanArray(String key, boolean[] value) throws IOException {
        if (value != null) {
            jsonWriter.name(key).beginArray();
            for (boolean val : value) {
                jsonWriter.value(val);
            }
            jsonWriter.endArray();
        }
    }

    @Override
    public void writeByteArray(String key, byte[] value, ByteCodec codec) throws IOException {
        if (value != null) {
            writeString(key, codec.encode(value));
        }
    }

    @Override
    public void writeCharArray(String key, char[] value) throws IOException {
        if (value != null) {
            jsonWriter.name(key).beginArray();
            for (char val : value) {
                jsonWriter.value(val);
            }
            jsonWriter.endArray();
        }
    }

    @Override
    public void writeIntArray(String key, int[] value) throws IOException {
        if (value != null) {
            jsonWriter.name(key).beginArray();
            for (int val : value) {
                jsonWriter.value(val);
            }
            jsonWriter.endArray();
        }
    }

    @Override
    public void writeLongArray(String key, long[] value) throws IOException {
        if (value != null) {
            jsonWriter.name(key).beginArray();
            for (long val : value) {
                jsonWriter.value(val);
            }
            jsonWriter.endArray();
        }
    }

    @Override
    public void writeNumberArray(String key, Number[] value) throws IOException {
        if (value != null) {
            jsonWriter.name(key).beginArray();
            for (Number val : value) {
                jsonWriter.value(val);
            }
            jsonWriter.endArray();
        }
    }

    @Override
    public void writeStringArray(String key, String[] value) throws IOException {
        if (value != null) {
            jsonWriter.name(key).beginArray();
            for (String val : value) {
                jsonWriter.value(val);
            }
            jsonWriter.endArray();
        }
    }

    @Override
    public void writeLinkArray(String key, String[] links) throws IOException {
        if (links != null) {
            jsonWriter.name(key).beginArray();
            for (String link : links) {
                writeLink(link);
            }
            jsonWriter.endArray();
        }
    }

    @Override
    public void writeLinkArray(String key, IPLDObject<?>[] links, Signer signer, IPLDContext context)
            throws IOException {
        if (links != null) {
            jsonWriter.name(key).beginArray();
            for (IPLDObject<?> link : links) {
                String multihash = link.getMultihash();
                if (multihash == null) {
                    multihash = context.saveObject(link, signer);
                }
                writeLink(multihash);
            }
            jsonWriter.endArray();
        }
    }

    private void writeLink(String link) throws IOException {
        jsonWriter.beginObject().name("/").value(link).endObject();
    }

    private void writeMetadata(Metadata metadata) throws IOException {
        jsonWriter.name(IPLDJsonReader.KEY_METADATA).beginObject().name(IPLDJsonReader.KEY_VERSION)
                .value(metadata.getVersion());
        ECDSASignature signature = metadata.getSignature();
        if (signature != null) {
            jsonWriter.name(IPLDJsonReader.KEY_SIGNATURE_R).value(signature.r).name(IPLDJsonReader.KEY_SIGNATURE_S)
                    .value(signature.s).name(IPLDJsonReader.KEY_SIGNATURE_V).value(signature.v);
        }
        jsonWriter.endObject();
    }

}
