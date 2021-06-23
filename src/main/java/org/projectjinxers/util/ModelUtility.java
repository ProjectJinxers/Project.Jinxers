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
package org.projectjinxers.util;

import static org.ethereum.crypto.HashUtil.sha3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.projectjinxers.config.SecretConfig;
import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.model.IPLDSerializable;

/**
 * @author ProjectJinxers
 *
 */
public class ModelUtility {

    public static final int CURRENT_HASH_OBFUSCATION_VERSION = 0;

    private static int secretObfuscationParameter;

    static {
        secretObfuscationParameter = SecretConfig.getSharedInstance().getObfuscationParam();
    }

    public static <D extends IPLDSerializable> Collection<IPLDObject<D>> getNewLinks(Map<String, IPLDObject<D>> now,
            Map<String, IPLDObject<D>> since) {
        if (now == null || now.size() == 0) {
            return null;
        }
        if (since == null || since.size() == 0) {
            return new ArrayList<>(now.values());
        }
        Collection<IPLDObject<D>> res = new ArrayList<>();
        for (Entry<String, IPLDObject<D>> entry : now.entrySet()) {
            if (!since.containsKey(entry.getKey())) {
                res.add(entry.getValue());
            }
        }
        return res.size() == 0 ? null : res;
    }

    public static <D extends IPLDSerializable> Map<String, IPLDObject<D>> getNewLinksMap(Map<String, IPLDObject<D>> now,
            Map<String, IPLDObject<D>> since) {
        if (now == null || now.size() == 0) {
            return null;
        }
        if (since == null || since.size() == 0) {
            return new LinkedHashMap<>(now);
        }
        Map<String, IPLDObject<D>> res = new LinkedHashMap<>();
        for (Entry<String, IPLDObject<D>> entry : now.entrySet()) {
            if (!since.containsKey(entry.getKey())) {
                res.put(entry.getKey(), entry.getValue());
            }
        }
        return res.size() == 0 ? null : res;
    }

    public static <D extends IPLDSerializable> Collection<IPLDObject<D>> getNewForeignKeyLinks(
            Map<String, IPLDObject<D>> now, Map<String, IPLDObject<D>> since) {
        if (now == null || now.size() == 0) {
            return null;
        }
        if (since == null || since.size() == 0) {
            return new ArrayList<>(now.values());
        }
        Collection<IPLDObject<D>> res = new ArrayList<>();
        for (Entry<String, IPLDObject<D>> entry : now.entrySet()) {
            String key = entry.getKey();
            IPLDObject<D> value = entry.getValue();
            IPLDObject<D> knownValue = since.get(key);
            if (knownValue == null || !knownValue.getMultihash().equals(value.getMultihash())) {
                res.add(value);
            }
        }
        return res.size() == 0 ? null : res;
    }

    public static <D extends IPLDSerializable> Map<String, IPLDObject<D>> getNewForeignKeyLinksMap(
            Map<String, IPLDObject<D>> now, Map<String, IPLDObject<D>> since) {
        if (now == null || now.size() == 0) {
            return null;
        }
        if (since == null || since.size() == 0) {
            return new LinkedHashMap<>(now);
        }
        Map<String, IPLDObject<D>> res = new LinkedHashMap<>();
        for (Entry<String, IPLDObject<D>> entry : now.entrySet()) {
            String key = entry.getKey();
            IPLDObject<D> value = entry.getValue();
            IPLDObject<D> knownValue = since.get(key);
            if (knownValue == null || !knownValue.getMultihash().equals(value.getMultihash())) {
                res.put(key, value);
            }
        }
        return res.size() == 0 ? null : res;
    }

    public static <D extends IPLDSerializable> Collection<IPLDObject<D>> getNewLinkArrays(
            Map<String, IPLDObject<D>[]> now, Map<String, IPLDObject<D>[]> since) {
        if (now == null || now.size() == 0) {
            return null;
        }
        if (since == null || since.size() == 0) {
            Collection<IPLDObject<D>> res = new ArrayList<>();
            for (IPLDObject<D>[] vals : now.values()) {
                for (IPLDObject<D> val : vals) {
                    res.add(val);
                }
            }
            return res;
        }
        Collection<IPLDObject<D>> res = new ArrayList<>();
        for (Entry<String, IPLDObject<D>[]> entry : now.entrySet()) {
            String key = entry.getKey();
            IPLDObject<D>[] values = entry.getValue();
            IPLDObject<D>[] knownValues = since.get(key);
            if (knownValues == null) {
                for (IPLDObject<D> value : values) {
                    res.add(value);
                }
            }
            else {
                Set<String> knownKeys = new HashSet<>();
                for (IPLDObject<D> knownValue : knownValues) {
                    knownKeys.add(knownValue.getMultihash());
                }
                for (IPLDObject<D> value : values) {
                    if (!knownKeys.contains(value.getMultihash())) {
                        res.add(value);
                    }
                }
            }
        }
        return res.size() == 0 ? null : res;
    }

    public static <D extends IPLDSerializable> Map<String, IPLDObject<D>[]> getNewLinkArraysMap(
            Map<String, IPLDObject<D>[]> now, Map<String, IPLDObject<D>[]> since) {
        if (now == null || now.size() == 0) {
            return null;
        }
        if (since == null || since.size() == 0) {
            return new LinkedHashMap<>(now);
        }
        Map<String, IPLDObject<D>[]> res = new LinkedHashMap<>();
        for (Entry<String, IPLDObject<D>[]> entry : now.entrySet()) {
            String key = entry.getKey();
            IPLDObject<D>[] values = entry.getValue();
            IPLDObject<D>[] knownValues = since.get(key);
            if (knownValues == null) {
                res.put(key, values);
            }
            else {
                Set<String> knownKeys = new HashSet<>();
                Collection<IPLDObject<D>> coll = new ArrayList<>();
                for (IPLDObject<D> knownValue : knownValues) {
                    coll.add(knownValue);
                    knownKeys.add(knownValue.getMultihash());
                }
                for (IPLDObject<D> value : values) {
                    if (!knownKeys.contains(value.getMultihash())) {
                        coll.add(value);
                    }
                }
                IPLDObject<D>[] union = Arrays.copyOf(knownValues, 0);
                union = coll.toArray(union);
                res.put(key, union);
            }
        }
        return res.size() == 0 ? null : res;
    }

    public static byte[] obfuscateHash(byte[] toHash, int seed, int obfuscationVersion, int valueHashObfuscation) {
        // just some random stuff, definitely not ideal, maybe even easily crackable, but that's what the version
        // parameter is for: room for improvement without breaking validation
        int hashCount = (seed + valueHashObfuscation) * secretObfuscationParameter % 31;
        byte[] res = toHash;
        for (int i = 0; i < hashCount; i++) {
            res = sha3(res);
        }
        int i = 0;
        int obfuscation = secretObfuscationParameter;
        for (byte b : res) {
            res[i++] = (byte) (b * obfuscation);
            if (++obfuscation == 0) {
                obfuscation = seed;
            }
        }
        return res;
    }

}
