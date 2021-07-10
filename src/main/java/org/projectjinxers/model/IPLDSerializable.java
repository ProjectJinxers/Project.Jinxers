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
package org.projectjinxers.model;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.ethereum.crypto.ECKey;
import org.projectjinxers.account.Signer;
import org.projectjinxers.controller.IPLDContext;
import org.projectjinxers.controller.IPLDReader;
import org.projectjinxers.controller.IPLDWriter;
import org.projectjinxers.controller.ValidationContext;
import org.projectjinxers.controller.ValidationException;

/**
 * The base interface for all data model classes, that can be saved as IPLD in IPFS.
 * 
 * @author ProjectJinxers
 */
public interface IPLDSerializable {

    public static interface KeyCollector<D extends IPLDSerializable> {

        Set<String> getHashes(D instance);

        default Set<String> getSplitHashes(D instance) {
            Set<String> firstHashes = getFirstHashes(instance);
            Set<String> secondHashes = getSecondHashes(instance);
            if (firstHashes == null) {
                return secondHashes;
            }
            if (secondHashes == null) {
                return firstHashes;
            }
            Set<String> union = new HashSet<>(firstHashes);
            union.addAll(secondHashes);
            if (union.size() < firstHashes.size() + secondHashes.size()) {
                throw new ValidationException("intersection of first and second hashes is not empty");
            }
            return union;
        }

        default Set<String> getFirstHashes(D instance) {
            return null;
        }

        default Set<String> getSecondHashes(D instance) {
            return null;
        }

        default void validateUndeletableEntries(Collection<D> previousStates) {
            Set<String> previousHashes = null;
            for (D previousState : previousStates) {
                Set<String> hashes = getHashes(previousState);
                if (previousHashes != null && !hashes.containsAll(previousHashes)) {
                    throw new ValidationException("found unexpectedly deleted entries");
                }
                previousHashes = hashes;
            }
        }

        default void validateMoveOnceUndeletableEntries(Collection<D> previousStates) {
            Set<String> previousMovedHashes = null;
            Set<String> previousHashes = null;
            for (D previousState : previousStates) {
                Set<String> movedHashes = getSecondHashes(previousState);
                if (previousMovedHashes != null && !movedHashes.containsAll(previousMovedHashes)) {
                    throw new ValidationException("found unexpectedly deleted moved entries");
                }
                Set<String> hashes = getHashes(previousState);
                if (previousHashes != null && !hashes.containsAll(previousHashes)) {
                    throw new ValidationException("found unexpectedly deleted entries");
                }
                previousMovedHashes = movedHashes;
                previousHashes = hashes;
            }
        }

    }

    public static class SplitSourceKeyCollector<D1 extends IPLDSerializable, D2 extends IPLDSerializable> {

        private KeyCollector<D1> firstKeyCollector;
        private KeyCollector<D2> secondKeyCollector;

        public SplitSourceKeyCollector(KeyCollector<D1> firstKeyCollector, KeyCollector<D2> secondKeyCollector) {
            this.firstKeyCollector = firstKeyCollector;
            this.secondKeyCollector = secondKeyCollector;
        }

        public void validateUndeletableEntries(Collection<D1> firstPreviousStates,
                Collection<D2> secondPreviousStates) {
            if (firstPreviousStates.isEmpty()) {
                return;
            }
            Set<String> previousHashes = null;
            Iterator<D2> secondPreviousStatesIterator = secondPreviousStates.iterator();
            D2 secondPreviousState = secondPreviousStatesIterator.hasNext() ? secondPreviousStatesIterator.next()
                    : null;
            for (D1 previousState : firstPreviousStates) {
                Set<String> hashes = new HashSet<>();
                Set<String> sourceHashes = firstKeyCollector.getHashes(previousState);
                if (sourceHashes != null) {
                    hashes.addAll(sourceHashes);
                }
                Set<String> movedHashes = secondPreviousState == null ? null
                        : secondKeyCollector.getHashes(secondPreviousState);
                if (movedHashes != null) {
                    hashes.addAll(movedHashes);
                }
                if (previousHashes != null) {
                    while (!hashes.containsAll(previousHashes)) {
                        if (secondPreviousStatesIterator.hasNext()) {
                            secondPreviousState = secondPreviousStatesIterator.next();
                            movedHashes = secondKeyCollector.getHashes(secondPreviousState);
                            if (movedHashes != null) {
                                hashes.addAll(movedHashes);
                            }
                        }
                        else {
                            throw new ValidationException("found unexpectedly deleted entries");
                        }
                    }
                }
                previousHashes = hashes;
            }
        }

    }

    /**
     * The meta version of a stored instance is stored with it in the metadata object. This enables code changes in data
     * model classes, that also change hashes (the ones for signatures and multihashes, as well). Method implementations
     * of {@link #read(IPLDReader, IPLDContext, boolean)} and {@link #write(IPLDWriter, ECKey, IPLDContext)} must
     * respect the meta version value. The default implementation returns 0.
     * 
     * @return the meta version (default 0)
     */
    default int getMetaVersion() {
        return 0;
    }

    /**
     * This method is invoked before the metadata block is added to the data, which is to be sent to IPFS. If this
     * method returns true, the data block will be signed and the signature will be added to the metadata block. The
     * default implementation returns false. Override, if the signature is mandatory.
     * 
     * @return true iff the data block must be signed (default: false)
     */
    default boolean isSignatureMandatory() {
        return false;
    }

    default void loadedFromCache() {

    }

    /**
     * Reads (deserializes) the single properties.
     * 
     * @param reader            provides the single values by key
     * @param context           the context
     * @param validationContext the validationContext
     * @param eager             if true, links should be resolved with the help of the context (not with the given
     *                          reader!)
     * @param metadata          the metadata (contains the stored meta version)
     */
    void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
            Metadata metadata);

    /**
     * Writes (serializes) the single properties.
     * 
     * @param writer  takes the single values by key
     * @param signer  the signer (for recursion)
     * @param context the context (for recursion)
     * @throws IOException if writing a single property fails
     */
    void write(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException;

    /**
     * Calculates the bytes to hash for creating or verifying a signature. The default implementation forwards the call
     * to the given writer.
     * 
     * @param writer  the writer that usually writes the bytes to hash
     * @param context the context
     * @return the bytes to hash
     * @throws IOException if writing the bytes fails
     */
    default byte[] hashBase(IPLDWriter writer, IPLDContext context) throws IOException {
        return writer.hashBase(context, this);
    }

}
