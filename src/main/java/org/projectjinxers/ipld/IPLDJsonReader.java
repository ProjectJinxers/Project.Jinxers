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

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.ethereum.crypto.ECKey.ECDSASignature;
import org.projectjinxers.model.IPLDSerializable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * JSON implementation of the interface for reading (deserializing) objects from
 * IPFS.
 * 
 * @author ProjectJinxers
 */
public class IPLDJsonReader implements IPLDReader {

    public static final String KEY_INNER_LINK = "/";
    public static final String KEY_SIGNATURE = "signature";
    public static final String KEY_SIGNATURE_R = "r";
    public static final String KEY_SIGNATURE_S = "s";
    public static final String KEY_SIGNATURE_V = "v";

    private Map<String, JsonPrimitive> primitives = new HashMap<>();
    private Map<String, JsonPrimitive[]> primitiveArrays = new HashMap<>();
    private Map<String, String> links = new HashMap<>();
    private Map<String, String[]> linkArrays = new HashMap<>();

    IPLDJsonReader() {

    }

    @Override
    public ECDSASignature read(IPLDContext context, byte[] bytes, IPLDSerializable emptyInstance, boolean eager) {
        ECDSASignature signature = null;
        JsonElement element = JsonParser.parseReader(new InputStreamReader(new ByteArrayInputStream(bytes)));
        if (element.isJsonObject()) {
            for (Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                String key = entry.getKey();
                JsonElement value = entry.getValue();
                if (value.isJsonObject()) {
                    if (KEY_SIGNATURE.equals(key)) {
                        JsonObject signatureObject = value.getAsJsonObject();
                        BigInteger r = signatureObject.get(KEY_SIGNATURE_R).getAsBigInteger();
                        BigInteger s = signatureObject.get(KEY_SIGNATURE_S).getAsBigInteger();
                        byte v = signatureObject.get(KEY_SIGNATURE_V).getAsByte();
                        signature = new ECDSASignature(r, s);
                        signature.v = v;
                    }
                    else {
                        // must be a link
                        links.put(key, getLink(value));
                    }
                }
                else if (value.isJsonArray()) {
                    int i = 0;
                    boolean isLink = false;
                    JsonPrimitive[] primitiveValues = null;
                    String[] linkValues = null;
                    JsonArray jsonArray = value.getAsJsonArray();
                    for (JsonElement item : jsonArray) {
                        if (i == 0) {
                            if (item.isJsonObject()) {
                                isLink = true;
                                linkValues = new String[jsonArray.size()];
                                linkValues[i] = getLink(item);
                                linkArrays.put(key, linkValues);
                            }
                            else {
                                primitiveValues = new JsonPrimitive[jsonArray.size()];
                                primitiveValues[i] = item.getAsJsonPrimitive();
                                primitiveArrays.put(key, primitiveValues);
                            }
                        }
                        else if (isLink) {
                            linkValues[i] = getLink(item);
                        }
                        else {
                            primitiveValues[i] = item.getAsJsonPrimitive();
                        }
                        i++;
                    }
                }
                else {
                    primitives.put(key, value.getAsJsonPrimitive());
                }
            }
        }
        emptyInstance.read(this, eager ? context : null);
        return signature;
    }

    private String getLink(JsonElement element) {
        return element.getAsJsonObject().get(KEY_INNER_LINK).getAsString();
    }

    @Override
    public Boolean readBoolean(String key) {
        JsonPrimitive primitive = primitives.get(key);
        return primitive == null ? null : primitive.getAsBoolean();
    }

    @Override
    public Character readCharacter(String key) {
        JsonPrimitive primitive = primitives.get(key);
        return primitive == null ? null : primitive.getAsCharacter();
    }

    @Override
    public Number readNumber(String key) {
        JsonPrimitive primitive = primitives.get(key);
        return primitive == null ? null : primitive.getAsNumber();
    }

    @Override
    public String readString(String key) {
        JsonPrimitive primitive = primitives.get(key);
        return primitive == null ? null : primitive.getAsString();
    }

    @Override
    public String readLink(String key) {
        return links.get(key);
    }

    @Override
    public boolean[] readBooleanArray(String key) {
        JsonPrimitive[] jsonPrimitives = primitiveArrays.get(key);
        if (jsonPrimitives == null) {
            return null;
        }
        boolean[] res = new boolean[jsonPrimitives.length];
        int i = 0;
        for (JsonPrimitive jsonPrimitive : jsonPrimitives) {
            res[i++] = jsonPrimitive.getAsBoolean();
        }
        return res;
    }

    @Override
    public char[] readCharArray(String key) {
        JsonPrimitive[] jsonPrimitives = primitiveArrays.get(key);
        if (jsonPrimitives == null) {
            return null;
        }
        char[] res = new char[jsonPrimitives.length];
        int i = 0;
        for (JsonPrimitive jsonPrimitive : jsonPrimitives) {
            res[i++] = jsonPrimitive.getAsCharacter();
        }
        return res;
    }

    @Override
    public Number[] readNumberArray(String key) {
        JsonPrimitive[] jsonPrimitives = primitiveArrays.get(key);
        if (jsonPrimitives == null) {
            return null;
        }
        Number[] res = new Number[jsonPrimitives.length];
        int i = 0;
        for (JsonPrimitive jsonPrimitive : jsonPrimitives) {
            res[i++] = jsonPrimitive.getAsNumber();
        }
        return res;
    }

    @Override
    public String[] readStringArray(String key) {
        JsonPrimitive[] jsonPrimitives = primitiveArrays.get(key);
        if (jsonPrimitives == null) {
            return null;
        }
        String[] res = new String[jsonPrimitives.length];
        int i = 0;
        for (JsonPrimitive jsonPrimitive : jsonPrimitives) {
            res[i++] = jsonPrimitive.getAsString();
        }
        return res;
    }

    @Override
    public String[] readLinksArray(String key) {
        return linkArrays.get(key);
    }

}
