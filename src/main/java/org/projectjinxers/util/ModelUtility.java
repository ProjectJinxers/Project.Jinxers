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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Queue;
import java.util.Set;

import org.projectjinxers.config.SecretConfig;
import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.controller.SimpleProgressListener;
import org.projectjinxers.controller.IPLDObject.ProgressListener;
import org.projectjinxers.controller.IPLDObject.ProgressTask;
import org.projectjinxers.controller.IPLDReader.KeyProvider;
import org.projectjinxers.controller.ValidationException;
import org.projectjinxers.model.IPLDSerializable;

/**
 * @author ProjectJinxers
 *
 */
public class ModelUtility {

    public interface CompletionHandler {

        void completed(int successCount);

    }

    public static final int CURRENT_HASH_OBFUSCATION_VERSION = 0;

    public static <D extends IPLDSerializable> boolean isEqual(IPLDObject<D> o1, IPLDObject<D> o2) {
        if (o1 == o2) {
            return true;
        }
        if (o1 == null || o2 == null) {
            return false;
        }
        D mapped1 = o1.getMapped();
        D mapped2 = o2.getMapped();
        if (mapped1 == mapped2) {
            return true;
        }
        return o1.getMultihash().equals(o2.getMultihash());
    }

    public static <D extends IPLDSerializable> void expectEqual(IPLDObject<D> o1, IPLDObject<D> o2) {
        expectEqual(o1, o2, "expected equal objects");
    }

    public static <D extends IPLDSerializable> void expectEqual(IPLDObject<D> o1, IPLDObject<D> o2, String message) {
        if (!isEqual(o1, o2)) {
            throw new ValidationException(message);
        }
    }

    public static <T> int indexOfNonNullEntry(T[] array, T entry) {
        if (array == null) {
            return -1;
        }
        int i = 0;
        for (T item : array) {
            if (entry.equals(item)) {
                return i;
            }
            i++;
        }
        return -1;
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

    public static Map<String, String[]> getNewLinksArraysMap(Map<String, String[]> now, Map<String, String[]> since) {
        if (now == null || now.size() == 0) {
            return null;
        }
        if (since == null || since.size() == 0) {
            return new LinkedHashMap<>(now);
        }
        Map<String, String[]> res = new LinkedHashMap<>();
        for (Entry<String, String[]> entry : now.entrySet()) {
            String key = entry.getKey();
            String[] values = entry.getValue();
            String[] knownValues = since.get(key);
            if (knownValues == null) {
                res.put(key, values);
            }
            else {
                Set<String> knownKeys = new HashSet<>();
                for (String knownValue : knownValues) {
                    knownKeys.add(knownValue);
                }
                Collection<String> coll = new ArrayList<>();
                for (String value : values) {
                    if (!knownKeys.contains(value)) {
                        coll.add(value);
                    }
                }
                if (coll.size() > 0) {
                    String[] arr = new String[0];
                    arr = coll.toArray(arr);
                    res.put(key, arr);
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
                    knownKeys.add(knownValue.getMultihash());
                }
                for (IPLDObject<D> value : values) {
                    if (!knownKeys.contains(value.getMultihash())) {
                        coll.add(value);
                    }
                }
                if (coll.size() > 0) {
                    IPLDObject<D>[] arr = Arrays.copyOf(knownValues, 0);
                    arr = coll.toArray(arr);
                    res.put(key, arr);
                }
            }
        }
        return res.size() == 0 ? null : res;
    }

    public static byte[] obfuscateHash(byte[] toHash, long seed, int obfuscationVersion, int valueHashObfuscation,
            SecretConfig secretConfig) {
        long[] secretObfuscationParameters = secretConfig.getObfuscationParams();
        // just some random stuff, definitely not ideal, maybe even easily crackable, but that's what the version
        // parameter is for: room for improvement without breaking validation
        int hashCount = (int) (((seed + valueHashObfuscation) * secretObfuscationParameters[0]) % 31);
        byte[] res = toHash;
        for (int i = 0; i < hashCount; i++) {
            res = sha3(res);
        }
        int i = 0;
        int params = secretObfuscationParameters.length;
        for (byte b : res) {
            long obfuscation = secretObfuscationParameters[b % params];
            res[i++] = (byte) (b * obfuscation);
        }
        return res;
    }

    public static <T extends IPLDSerializable> Collection<IPLDObject<T>> dequeue(
            Map<String, Queue<IPLDObject<T>>> queues, Map<String, Map<String, IPLDObject<T>>> into,
            KeyProvider<T> keyProvider, Map<String, Collection<ProgressListener>> userHashes, boolean collect) {
        Collection<IPLDObject<T>> res = collect ? new ArrayList<>() : null;
        synchronized (queues) {
            for (Entry<String, Queue<IPLDObject<T>>> entry : queues.entrySet()) {
                String key = entry.getKey();
                Map<String, IPLDObject<T>> map = into.get(key);
                boolean addToMap;
                if (map == null) {
                    map = new LinkedHashMap<>();
                    addToMap = true;
                }
                else {
                    addToMap = false;
                }
                Collection<ProgressListener> userProgressListeners = userHashes.get(key);
                boolean addToHashes;
                if (userProgressListeners == null) {
                    userProgressListeners = new ArrayList<>();
                    addToHashes = true;
                }
                else {
                    addToHashes = false;
                }
                Queue<IPLDObject<T>> value = entry.getValue();
                Iterator<IPLDObject<T>> it = value.iterator();
                while (it.hasNext()) {
                    IPLDObject<T> next = it.next();
                    if (addProgressListener(next, userProgressListeners, true)) {
                        map.put(keyProvider.getKey(next), next);
                        if (collect) {
                            res.add(next);
                        }
                    }
                    else {
                        it.remove();
                    }
                }
                if (addToMap) {
                    if (map.size() > 0) {
                        into.put(key, map);
                    }
                }
                else if (value.isEmpty()) {
                    into.remove(key);
                }
                if (addToHashes) {
                    userHashes.put(key, userProgressListeners.size() > 0 ? userProgressListeners : null);
                }
            }
        }
        return res;
    }

    public static boolean addProgressListener(IPLDObject<?> object, Collection<ProgressListener> progressListeners,
            boolean dequeued) {
        ProgressListener progressListener = object.getProgressListener();
        if (progressListener != null) {
            if (dequeued) {
                if (progressListener.dequeued()) {
                    progressListeners.add(progressListener);
                    return true;
                }
                return false;
            }
            if (progressListener.isCanceled()) {
                return false;
            }
            progressListeners.add(progressListener);
        }
        return true;
    }

    public static <T extends IPLDSerializable> boolean addProgressListener(IPLDObject<T> object, String userHash,
            Map<String, Collection<ProgressListener>> userHashes, Map<String, Map<String, IPLDObject<T>>> map,
            KeyProvider<T> keyProvider) {
        ProgressListener progressListener = object.getProgressListener();
        if (progressListener != null) {
            if (progressListener.isCanceled()) {
                return false;
            }
            Collection<ProgressListener> progressListeners = userHashes.get(userHash);
            if (progressListeners == null) {
                progressListeners = new ArrayList<>();
                userHashes.put(userHash, progressListeners);
            }
            progressListeners.add(progressListener);
        }
        else if (!userHashes.containsKey(userHash)) {
            userHashes.put(userHash, null);
        }
        Map<String, IPLDObject<T>> userMap = map.get(userHash);
        if (userMap == null) {
            userMap = new LinkedHashMap<>();
            map.put(userHash, userMap);
        }
        userMap.put(keyProvider.getKey(object), object);
        return true;
    }

    public static <T extends IPLDSerializable, C extends IPLDSerializable> Map<String, IPLDObject<T>> addProgressListeners(
            Map<String, IPLDObject<T>> source, Map<String, IPLDObject<T>> dest, Map<String, IPLDObject<C>> complement,
            Collection<ProgressListener> progressListeners) {
        Map<String, IPLDObject<T>> res = dest;
        if (res == null) {
            res = new LinkedHashMap<>();
        }
        Iterator<Entry<String, IPLDObject<T>>> it = source.entrySet().iterator();
        while (it.hasNext()) {
            Entry<String, IPLDObject<T>> entry = it.next();
            IPLDObject<T> value = entry.getValue();
            if (addProgressListener(value, progressListeners, false)) {
                String key = entry.getKey();
                res.put(key, value);
                if (complement != null) {
                    complement.remove(key);
                }
            }
            else {
                it.remove();
            }
        }
        return res;
    }

    public static <T extends IPLDSerializable> Map<String, Queue<IPLDObject<T>>> enqueue(IPLDObject<T> object,
            Map<String, Queue<IPLDObject<T>>> queues, String userHash) {
        Map<String, Queue<IPLDObject<T>>> res = queues;
        if (res == null) {
            res = new HashMap<>();
        }
        synchronized (res) {
            Queue<IPLDObject<T>> queue = res.get(userHash);
            boolean addQueue;
            if (queue == null) {
                queue = new ArrayDeque<>();
                addQueue = true;
            }
            else {
                addQueue = false;
            }
            ProgressListener progressListener = object.getProgressListener();
            if (progressListener == null) {
                queue.add(object);
            }
            else if (progressListener.isCanceled()) {
                addQueue = false;
            }
            else {
                queue.add(object);
                progressListener.enqueued();
            }
            if (addQueue) {
                res.put(userHash, queue);
            }
        }
        return res;
    }

    public static <T extends IPLDSerializable> Map<String, IPLDObject<T>> enqueue(Map<String, IPLDObject<T>> source,
            Map<String, IPLDObject<T>> dest, boolean abortIfCanceled) {
        Map<String, IPLDObject<T>> res = dest;
        if (res == null) {
            res = new LinkedHashMap<>();
        }
        synchronized (res) {
            Iterator<Entry<String, IPLDObject<T>>> it = source.entrySet().iterator();
            do {
                Entry<String, IPLDObject<T>> entry = it.next();
                IPLDObject<T> value = entry.getValue();
                ProgressListener progressListener = value.getProgressListener();
                if (progressListener == null) {
                    res.put(entry.getKey(), value);
                }
                else if (progressListener.isCanceled()) {
                    if (abortIfCanceled) {
                        return null;
                    }
                    it.remove();
                }
                else {
                    res.put(entry.getKey(), value);
                    progressListener.enqueued();
                }
            }
            while (it.hasNext());
        }
        return res;
    }

    public static <T extends IPLDSerializable> Map<String, Queue<IPLDObject<T>>> enqueue(Map<String, IPLDObject<T>> map,
            Map<String, Queue<IPLDObject<T>>> queues, String userHash) {
        Map<String, Queue<IPLDObject<T>>> res = queues;
        if (res == null) {
            res = new HashMap<>();
        }
        synchronized (res) {
            Queue<IPLDObject<T>> queue = res.get(userHash);
            boolean addQueue;
            if (queue == null) {
                queue = new ArrayDeque<>();
                addQueue = true;
            }
            else {
                addQueue = false;
            }
            for (IPLDObject<T> object : map.values()) {
                ProgressListener progressListener = object.getProgressListener();
                if (progressListener == null) {
                    queue.add(object);
                }
                else if (!progressListener.isCanceled()) {
                    queue.add(object);
                    progressListener.enqueued();
                }
            }
            if (addQueue && queue.size() > 0) {
                res.put(userHash, queue);
            }
        }
        return res;
    }

    public static <T extends IPLDSerializable> void loadObject(IPLDObject<T> object,
            CompletionHandler completionHandler) {
        new Thread(() -> {
            if (object.isMapped()) {
                completionHandler.completed(1);
            }
            else {
                object.setProgressListener(new SimpleProgressListener() {
                    @Override
                    protected void finishedTask(ProgressTask task, boolean success) {
                        object.removeProgressListener(this);
                        completionHandler.completed(success ? 1 : 0);
                    }
                });
                object.getMapped();
            }
        }).start();
    }

    public static /* <T extends IPLDSerializable> */ void loadObjects(
            Collection<IPLDObject<? extends IPLDSerializable>> objects, CompletionHandler completionHandler) {
        new Thread(() -> {
            int totalAttempts = objects.size();
            AtomicInteger finishCounter = new AtomicInteger();
            AtomicInteger successCounter = new AtomicInteger();
            for (IPLDObject<?> object : objects) {
                if (object.isMapped()) {
                    int successCount = successCounter.incrementAndGet();
                    if (finishCounter.incrementAndGet() == totalAttempts) {
                        completionHandler.completed(successCount);
                    }
                }
                else {
                    object.setProgressListener(new SimpleProgressListener() {
                        @Override
                        protected void finishedTask(ProgressTask task, boolean success) {
                            object.removeProgressListener(this);
                            if (success) {
                                successCounter.incrementAndGet();
                            }
                            if (finishCounter.incrementAndGet() == totalAttempts) {
                                completionHandler.completed(successCounter.get());
                            }
                        }
                    });
                    object.getMapped();
                }
            }
        }).start();
    }

}
