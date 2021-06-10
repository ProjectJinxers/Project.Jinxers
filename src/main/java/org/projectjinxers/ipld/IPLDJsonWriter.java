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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.ECKey.ECDSASignature;
import org.projectjinxers.model.IPLDObject;
import org.projectjinxers.model.IPLDSerializable;

import com.google.gson.stream.JsonWriter;

/**
 * JSON implementation of the interface for writing (serializing) objects to IPFS.
 * 
 * @author ProjectJinxers
 */
public class IPLDJsonWriter implements IPLDWriter {

    private JsonWriter jsonWriter;

    IPLDJsonWriter() {

    }

    @Override
    public <D extends IPLDSerializable> byte[] write(IPLDContext context, IPLDObject<D> object, ECKey signingKey)
            throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        jsonWriter = new JsonWriter(new OutputStreamWriter(baos));
        jsonWriter.beginObject();
        ECDSASignature signature = object.write(this, signingKey, context);
        if (signature != null) {
            writeSignature(signature);
        }
        jsonWriter.endObject();
        jsonWriter.flush();
        return baos.toByteArray();
    }

    @Override
    public void writeBoolean(String key, boolean value) throws IOException {
        jsonWriter.name(key).value(value);
    }

    @Override
    public void writeChar(String key, char value) throws IOException {
        jsonWriter.name(key).value(value);
    }

    @Override
    public void writeNumber(String key, Number value) throws IOException {
        jsonWriter.name(key).value(value);
    }

    @Override
    public void writeString(String key, String value) throws IOException {
        jsonWriter.name(key).value(value);
    }

    @Override
    public void writeLink(String key, String link) throws IOException {
        jsonWriter.name(key);
        writeLink(link);
    }

    @Override
    public void writeBooleanArray(String key, boolean[] value) throws IOException {
        jsonWriter.name(key).beginArray();
        for (boolean val : value) {
            jsonWriter.value(val);
        }
        jsonWriter.endArray();
    }

    @Override
    public void writeCharArray(String key, char[] value) throws IOException {
        jsonWriter.name(key).beginArray();
        for (char val : value) {
            jsonWriter.value(val);
        }
        jsonWriter.endArray();
    }

    @Override
    public void writeNumberArray(String key, Number[] value) throws IOException {
        jsonWriter.name(key).beginArray();
        for (Number val : value) {
            jsonWriter.value(val);
        }
        jsonWriter.endArray();
    }

    @Override
    public void writeStringArray(String key, String[] value) throws IOException {
        jsonWriter.name(key).beginArray();
        for (String val : value) {
            jsonWriter.value(val);
        }
        jsonWriter.endArray();
    }

    @Override
    public void writeLinkArray(String key, String[] links) throws IOException {
        jsonWriter.name(key).beginArray();
        for (String link : links) {
            writeLink(link);
        }
        jsonWriter.endArray();
    }

    private void writeLink(String link) throws IOException {
        jsonWriter.beginObject().name("/").value(link).endObject();
    }

    private void writeSignature(ECDSASignature signature) throws IOException {
        jsonWriter.name(IPLDJsonReader.KEY_SIGNATURE).beginObject().name(IPLDJsonReader.KEY_SIGNATURE_R)
                .value(signature.r).name(IPLDJsonReader.KEY_SIGNATURE_S).value(signature.s)
                .name(IPLDJsonReader.KEY_SIGNATURE_V).value(signature.v).endObject();
    }

}
