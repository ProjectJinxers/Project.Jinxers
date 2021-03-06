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

import org.ethereum.crypto.ECKey.ECDSASignature;
import org.projectjinxers.account.Signer;
import org.projectjinxers.model.IPLDSerializable;
import org.projectjinxers.model.Loader;
import org.projectjinxers.model.Metadata;

/**
 * Wrapper class for data model objects, that can be saved as IPLD in IPFS. By default, loading/reading an instance from
 * IPFS does not automatically load the entire (sub)tree. Primitive kinds are mapped. Links are resolved on access.
 * 
 * @author ProjectJinxers
 * @param <D> the type of the data instance
 */
public class IPLDObject<D extends IPLDSerializable> {

    public enum ProgressTask {

        INIT(0, 5, "Initializing…", null), SAVE(1, 5, "Saving…", "Deleting…"),
        SIGN(2, 5, "Signing…", "Signing removal…"), LINK_USER(3, 5, "Linking to user…", "Linking removal to user…"),
        LINK_MODEL(4, 5, "Linking to model…", "Linking removal to model…"), LOAD(0, 1, "Loading…", null);

        private int totalProgressTask;
        private int totalProgressTasks;
        private String progressMessageDefault;
        private String progressMessageDestroy;

        private ProgressTask(int totalProgressTask, int totalProgressTasks, String progressMessageDefault,
                String progressMessageDestroy) {
            this.totalProgressTask = totalProgressTask;
            this.totalProgressTasks = totalProgressTasks;
            this.progressMessageDefault = progressMessageDefault;
            this.progressMessageDestroy = progressMessageDestroy;
        }

        public int getTotalProgressTask() {
            return totalProgressTask;
        }

        public int getTotalProgressTasks() {
            return totalProgressTasks;
        }

        public String getProgressMessageCreate() {
            return progressMessageDefault;
        }

        public String getProgressMessageDestroy() {
            return progressMessageDestroy == null ? progressMessageDefault : progressMessageDestroy;
        }

        public String getProgressMessage(boolean destroying) {
            return destroying && progressMessageDestroy != null ? progressMessageDestroy : progressMessageDefault;
        }

    }

    public static interface ProgressListener {

        boolean isDeterminate();

        void startedTask(ProgressTask task, int steps);

        void nextStep();

        void finishedTask(ProgressTask task);

        void failedTask(ProgressTask task, String message, Throwable failure);

        void enqueued();

        boolean dequeued();

        void obsoleted();

        boolean isCanceled();

    }

    private String multihash;
    private D mapped;
    private IPLDContext context;
    private ValidationContext validationContext;
    private Loader<D> loader;
    private Metadata metadata;
    private ECDSASignature foreignSignature;

    private ProgressListener progressListener;

    /**
     * Constructor for locally created objects. Usually the instance will be written to IPFS.
     * 
     * @param data the data instance
     */
    public IPLDObject(D data) {
        this.mapped = data;
    }

    /**
     * Constructor for locally created objects with signatures of related objects (e.g. ownership transfer requests),
     * that don't even have to be stored in IPFS. That signature is used as this instance's signature.
     * 
     * @param data             the data instance
     * @param foreignSignature the foreign signature
     */
    public IPLDObject(D data, ECDSASignature foreignSignature) {
        this.mapped = data;
        this.foreignSignature = foreignSignature;
    }

    /**
     * Constructor for loaded objects or root objects that are to be loaded.
     * 
     * @param multihash         the multihash
     * @param loader            a wrapper for the data instance (might be the data instance itself, though)
     * @param context           the context
     * @param validationContext the validation context
     */
    public IPLDObject(String multihash, Loader<D> loader, IPLDContext context, ValidationContext validationContext) {
        this.multihash = multihash;
        this.loader = loader;
        this.context = context;
        this.validationContext = validationContext;
    }

    /**
     * Copy constructor for previousVersion links.
     * 
     * @param source the source object
     * @param data   the copy of the data instance
     */
    public IPLDObject(IPLDObject<D> source, D data) {
        this.multihash = source.multihash;
        this.mapped = data;
        this.metadata = source.metadata;
    }

    /**
     * @return the multihash
     */
    public String getMultihash() {
        return multihash;
    }

    public boolean isMapped() {
        return mapped != null;
    }

    /**
     * If the data instance has not been resolved, yet, it is loaded with the help of the context, that had been passed
     * to the constructor.
     * 
     * @return the resolved and mapped data instance
     */
    public D getMapped() {
        if (mapped == null && multihash != null) {
            ProgressListener progressListener = this.progressListener;
            if (progressListener != null) {
                progressListener.startedTask(ProgressTask.LOAD, -1);
            }
            try {
                LoadResult result = context.loadObject(this);
                if (result == null) {
                    if (progressListener != null) {
                        progressListener.failedTask(ProgressTask.LOAD, "Failed to load object.", null);
                    }
                }
                else {
                    IPLDObject<?> fromCache = result.getFromCache();
                    if (fromCache == null) {
                        this.metadata = result.getLoadedMetadata();
                        this.mapped = loader.getLoaded();
                    }
                    else {
                        this.metadata = fromCache.getMetadata();
                        @SuppressWarnings("unchecked") // obviously correct
                        D mapped = (D) fromCache.getMapped();
                        mapped.loadedFromCache();
                        this.mapped = mapped;
                    }
                    if (progressListener != null) {
                        progressListener.finishedTask(ProgressTask.LOAD);
                    }
                }
            }
            catch (IOException e) {
                if (progressListener != null) {
                    progressListener.failedTask(ProgressTask.LOAD, "Failed to load object.", e);
                }
                throw new RuntimeException(e);
            }
        }
        return mapped;
    }

    public ValidationContext getValidationContext() {
        return validationContext;
    }

    public Loader<D> getLoader() {
        return loader;
    }

    /**
     * @return the metadata (if this instance has no metadata, yet, {@link #getMapped()} is called)
     */
    public Metadata getMetadata() {
        if (metadata == null) {
            getMapped();
        }
        return metadata;
    }

    public ECDSASignature getForeignSignature() {
        return foreignSignature;
    }

    public void setProgressListener(ProgressListener listener) {
        this.progressListener = listener;
    }

    public ProgressListener getProgressListener() {
        return progressListener;
    }

    public void removeProgressListener(ProgressListener listener) {
        if (this.progressListener == listener) {
            this.progressListener = null;
        }
    }

    /**
     * Stores this instance in IPFS. This is a recursive operation. Referenced objects, that have not been saved, yet,
     * will also be saved automatically by calling this method. If one child save operation fails, the root save
     * operation fails, as well. Of course, if you retry with this exact instance, the successfully saved children won't
     * be saved again.
     * 
     * @param context the context
     * @param signer  the signer
     * @return the multihash with which the object can be retrieved
     * @throws IOException if saving fails
     */
    String save(IPLDContext context, Signer signer, ProgressListener progressListener) throws IOException {
        if (this.progressListener != null) {
            if (this.progressListener.isDeterminate()) {
                this.progressListener.startedTask(ProgressTask.INIT, 0);
                SaveStepCounter counter = new SaveStepCounter();
                counter.write(context, this, signer, progressListener);
                this.progressListener.finishedTask(ProgressTask.INIT);
                int totalSteps = counter.getSteps();
                this.progressListener.startedTask(ProgressTask.SAVE, totalSteps);
            }
            else {
                this.progressListener.startedTask(ProgressTask.SAVE, -1);
            }
        }
        try {
            this.multihash = context.saveObject(this, signer, progressListener);
        }
        catch (IOException e) {
            if (this.progressListener != null) {
                this.progressListener.failedTask(ProgressTask.SAVE, "Failed to save the object in IPFS.", e);
            }
            throw e;
        }
        catch (RuntimeException e) {
            if (this.progressListener != null) {
                this.progressListener.failedTask(ProgressTask.SAVE, "Failed to save the object in IPFS.", e);
            }
            throw e;
        }
        if (this.progressListener != null) {
            this.progressListener.finishedTask(ProgressTask.SAVE);
        }
        return multihash;
    }

    /**
     * Writes (serializes) the data instance to IPFS. Referenced objects, that have not been saved, yet, will also be
     * saved automatically by calling this method. If one child save operation fails, the root save operation fails, as
     * well. Of course, if you retry with this exact instance, the successfully saved children won't be saved again.
     * 
     * @param writer           takes the single properties by key
     * @param signer           the signer for recursion
     * @param context          the context
     * @param progressListener TODO
     * @throws IOException if writing a single property fails
     */
    void write(IPLDWriter writer, Signer signer, IPLDContext context, ProgressListener progressListener)
            throws IOException {
        mapped.write(writer, signer, context, progressListener);
    }

    /**
     * This method is called after serializing the data. If there is no foreign signature and the data indicates that
     * signing be mandatory, the data is hashed and signed and the signature (or foreign signature) is stored in the
     * returned metadata instance, which is stored in this object, as well.
     * 
     * @param signer   the signer (in case signing is mandatory)
     * @param hashBase the data to hash and sign
     * @return the metadata containing the signature and version
     */
    Metadata signIfMandatory(Signer signer, byte[] hashBase) {
        ECDSASignature signature = foreignSignature;
        if (signature == null && getMapped().isSignatureMandatory()) {
            if (progressListener != null) {
                progressListener.startedTask(ProgressTask.SIGN, 0);
            }
            signature = signer.sign(hashBase);
            if (progressListener != null) {
                progressListener.finishedTask(ProgressTask.SIGN);
            }
        }
        this.metadata = new Metadata(mapped.getMetaVersion(), signature);
        return metadata;
    }

    IPLDObject<D> withoutContext(LoadResult result) {
        IPLDObject<D> res = new IPLDObject<>(mapped == null ? loader.getLoaded() : mapped);
        res.multihash = multihash;
        res.loader = loader;
        res.metadata = result == null ? metadata : result.getLoadedMetadata();
        res.foreignSignature = foreignSignature;
        return res;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof IPLDObject<?>) {
            if (multihash != null) {
                return multihash.equals(((IPLDObject<?>) obj).multihash);
            }
            return super.equals(obj);
        }
        return false;
    }

    @Override
    public int hashCode() {
        if (multihash == null) {
            return super.hashCode();
        }
        return multihash.hashCode();
    }

}
