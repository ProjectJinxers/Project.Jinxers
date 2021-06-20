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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.model.IPLDSerializable;

/**
 * @author ProjectJinxers
 *
 */
public class ModelUtility {

    public static <D extends IPLDSerializable> Collection<IPLDObject<D>> getNewLinks(Map<String, IPLDObject<D>> now,
            Map<String, IPLDObject<D>> since) {
        if (now == null) {
            return null;
        }
        if (since == null) {
            return Collections.unmodifiableCollection(now.values());
        }
        Collection<IPLDObject<D>> res = new ArrayList<>();
        for (Entry<String, IPLDObject<D>> entry : now.entrySet()) {
            if (!since.containsKey(entry.getKey())) {
                res.add(entry.getValue());
            }
        }
        return res;
    }

    public static <D extends IPLDSerializable> Collection<IPLDObject<D>> getNewForeignKeyLinks(
            Map<String, IPLDObject<D>> now, Map<String, IPLDObject<D>> since) {
        if (now == null) {
            return null;
        }
        if (since == null) {
            return Collections.unmodifiableCollection(now.values());
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
        return res;
    }

    public static <D extends IPLDSerializable> Collection<IPLDObject<D>> getNewLinkArrays(
            Map<String, IPLDObject<D>[]> now, Map<String, IPLDObject<D>[]> since) {
        if (now == null) {
            return null;
        }
        if (since == null) {
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
        return res;
    }

}
