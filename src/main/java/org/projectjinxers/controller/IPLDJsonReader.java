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

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.ethereum.crypto.ECKey.ECDSASignature;
import org.projectjinxers.model.IPLDSerializable;
import org.projectjinxers.model.Loader;
import org.projectjinxers.model.LoaderFactory;
import org.projectjinxers.model.Metadata;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * JSON implementation of the interface for reading (deserializing) objects from IPFS. JSON null values will be ignored
 * by default. You should not rely on JSON null. There should be no difference between a missing field and a field with
 * value null.
 * 
 * @author ProjectJinxers
 */
public class IPLDJsonReader implements IPLDReader {

    public static final String KEY_INNER_LINK = "/";
    public static final String KEY_METADATA = "meta";
    public static final String KEY_VERSION = "version";
    public static final String KEY_SIGNATURE_R = "r";
    public static final String KEY_SIGNATURE_S = "s";
    public static final String KEY_SIGNATURE_V = "v";
    public static final String KEY_DATA = "data";

    private Map<String, JsonPrimitive> primitives = new HashMap<>();
    private Map<String, JsonPrimitive[]> primitiveArrays = new HashMap<>();
    private Map<String, String> links = new HashMap<>();
    private Map<String, String[]> linkArrays = new HashMap<>();
    private Map<String, String[][]> linkArrayArrays = new HashMap<>();

    IPLDJsonReader() {

    }

    @Override
    public Metadata read(IPLDContext context, ValidationContext validationContext, byte[] bytes, Loader<?> loader,
            boolean eager) {
        ECDSASignature signature = null;
        int version = 0;
        JsonElement element = JsonParser.parseReader(new InputStreamReader(new ByteArrayInputStream(bytes)));
        if (element.isJsonObject()) {
            for (Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                String key = entry.getKey();
                JsonElement value = entry.getValue();
                if (value.isJsonObject()) {
                    if (KEY_METADATA.equals(key)) {
                        JsonObject metadataObject = value.getAsJsonObject();
                        if (metadataObject.has(KEY_SIGNATURE_R)) {
                            BigInteger r = metadataObject.get(KEY_SIGNATURE_R).getAsBigInteger();
                            BigInteger s = metadataObject.get(KEY_SIGNATURE_S).getAsBigInteger();
                            byte v = metadataObject.get(KEY_SIGNATURE_V).getAsByte();
                            signature = new ECDSASignature(r, s);
                            signature.v = v;
                        }
                        version = metadataObject.get(KEY_VERSION).getAsInt();
                    }
                    else if (KEY_DATA.equals(key)) {
                        readData(value.getAsJsonObject());
                    }
                }
            }
        }
        Metadata metadata = new Metadata(version, signature);
        IPLDSerializable dataInstance = loader.getOrCreateDataInstance(this, metadata);
        dataInstance.read(this, context, validationContext, eager, metadata);
        return metadata;
    }

    private void readData(JsonObject data) {
        for (Entry<String, JsonElement> entry : data.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if (value.isJsonObject()) {
                // must be a link
                links.put(key, getLink(value));
            }
            else if (value.isJsonArray()) {
                int i = 0;
                boolean isLink = false;
                boolean isLinkArray = false;
                JsonPrimitive[] primitiveValues = null;
                String[] linkValues = null;
                String[][] linkArrayValues = null;
                JsonArray jsonArray = value.getAsJsonArray();
                for (JsonElement item : jsonArray) {
                    if (i == 0) {
                        if (item.isJsonObject()) {
                            isLink = true;
                            linkValues = new String[jsonArray.size()];
                            linkValues[i] = getLink(item);
                            linkArrays.put(key, linkValues);
                        }
                        else if (item.isJsonArray()) {
                            isLinkArray = true;
                            linkArrayValues = new String[jsonArray.size()][];
                            linkArrayValues[i] = getLinkArray(item.getAsJsonArray());
                            linkArrayArrays.put(key, linkArrayValues);
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
                    else if (isLinkArray) {
                        linkArrayValues[i] = getLinkArray(item.getAsJsonArray());
                    }
                    else {
                        primitiveValues[i] = item.getAsJsonPrimitive();
                    }
                    i++;
                }
            }
            else if (!value.isJsonNull()) {
                primitives.put(key, value.getAsJsonPrimitive());
            }
        }
    }

    private String getLink(JsonElement element) {
        return element.isJsonObject() ? element.getAsJsonObject().get(KEY_INNER_LINK).getAsString()
                : element.getAsString();
    }

    private String[] getLinkArray(JsonArray array) {
        String[] res = new String[array.size()];
        int i = 0;
        for (JsonElement element : array) {
            res[i++] = getLink(element);
        }
        return res;
    }

    @Override
    public boolean hasPrimitiveKey(String key) {
        return primitives.containsKey(key);
    }

    @Override
    public boolean hasLinkKey(String key) {
        return links.containsKey(key) || primitives.containsKey(key);
    }

    @Override
    public boolean hasPrimitiveArrayKey(String key) {
        return primitiveArrays.containsKey(key);
    }

    @Override
    public boolean hasLinkArrayKey(String key) {
        return linkArrays.containsKey(key) || primitiveArrays.containsKey(key);
    }

    @Override
    public Object readPrimitive(String key) {
        JsonPrimitive primitive = primitives.get(key);
        if (primitive != null) {
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            }
            if (primitive.isNumber()) {
                return primitive.getAsNumber();
            }
            if (primitive.isString()) {
                return primitive.getAsString();
            }
        }
        return null;
    }

    @Override
    public Boolean readBoolean(String key) {
        JsonPrimitive primitive = primitives.get(key);
        return primitive == null ? null : primitive.getAsBoolean();
    }

    @Override
    public Character readCharacter(String key) {
        JsonPrimitive primitive = primitives.get(key);
        return primitive == null ? null : (char) primitive.getAsInt();
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
        String res = links.get(key);
        return res == null ? readString(key) : res;
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
    public byte[] readByteArray(String key, ByteCodec codec) {
        String encoded = readString(key);
        return encoded == null ? null : codec.decode(encoded);
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
    public int[] readIntArray(String key) {
        JsonPrimitive[] jsonPrimitives = primitiveArrays.get(key);
        if (jsonPrimitives == null) {
            return null;
        }
        int[] res = new int[jsonPrimitives.length];
        int i = 0;
        for (JsonPrimitive jsonPrimitive : jsonPrimitives) {
            res[i++] = jsonPrimitive.getAsInt();
        }
        return res;
    }

    @Override
    public long[] readLongArray(String key) {
        JsonPrimitive[] jsonPrimitives = primitiveArrays.get(key);
        if (jsonPrimitives == null) {
            return null;
        }
        long[] res = new long[jsonPrimitives.length];
        int i = 0;
        for (JsonPrimitive jsonPrimitive : jsonPrimitives) {
            res[i++] = jsonPrimitive.getAsLong();
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
        String[] res = linkArrays.get(key);
        return res == null ? readStringArray(key) : res;
    }

    @Override
    public Map<String, String[]> readLinkCollections(String key) {
        String[][] linkArrays = linkArrayArrays.get(key);
        if (linkArrays == null) {
            return null;
        }
        Map<String, String[]> res = new LinkedHashMap<>();
        int i = 0;
        boolean first = true;
        String[] keys = null;
        for (String[] links : linkArrays) {
            if (first) {
                keys = links;
                first = false;
            }
            else {
                res.put(keys[i++], links);
            }
        }
        return res;
    }

    @Override
    public <D extends IPLDSerializable> Map<String, IPLDObject<D>[]> readLinkObjectCollections(String key,
            IPLDContext context, ValidationContext validationContext, LoaderFactory<D> loaderFactory, boolean eager,
            KeyProvider<D> keyProvider) {
        String[][] linkArrays = linkArrayArrays.get(key);
        if (linkArrays == null) {
            return null;
        }
        Map<String, IPLDObject<D>[]> res = new LinkedHashMap<>();
        for (String[] linkArray : linkArrays) {
            boolean first = true;
            @SuppressWarnings("unchecked")
            IPLDObject<D>[] array = (IPLDObject<D>[]) Array.newInstance(IPLDObject.class, linkArray.length);
            int i = 0;
            for (String link : linkArray) {
                IPLDObject<D> linkObject = new IPLDObject<>(link, loaderFactory.createLoader(), context,
                        validationContext);
                if (eager) {
                    linkObject.getMapped();
                }
                array[i++] = linkObject;
                if (first) {
                    String mapKey = keyProvider.getKey(linkObject);
                    res.put(mapKey, array);
                    first = false;
                }
            }
        }
        return res;
    }

}
