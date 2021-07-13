package org.projectjinxers.controller;

import java.io.IOException;
import java.util.Map;

import org.ethereum.crypto.ECKey.ECDSASignature;
import org.projectjinxers.account.Signer;
import org.projectjinxers.controller.IPLDObject.ProgressListener;
import org.projectjinxers.model.IPLDSerializable;

public class SaveStepCounter implements IPLDWriter {

    private int steps;
    private boolean rootObject = true;

    public int getSteps() {
        return steps;
    }

    @Override
    public byte[] write(IPLDContext context, IPLDObject<?> object, Signer signer, ProgressListener progressListener)
            throws IOException {
        if (rootObject || object.getMultihash() == null) {
            steps++;
            object.write(this, signer, context, progressListener);
        }
        if (rootObject) {
            rootObject = false;
        }
        else {
            ECDSASignature signature = object.getForeignSignature();
            if (signature == null && object.getMapped().isSignatureMandatory()) {
                steps++;
            }
        }
        return null;
    }

    @Override
    public byte[] hashBase(IPLDContext context, IPLDSerializable data) throws IOException {
        return null;
    }

    @Override
    public void writeBoolean(String key, Boolean value) throws IOException {
    }

    @Override
    public void writeIfTrue(String key, boolean value) throws IOException {
    }

    @Override
    public void writeChar(String key, Character value) throws IOException {
    }

    @Override
    public void writeNumber(String key, Number value) throws IOException {
    }

    @Override
    public void writeString(String key, String value) throws IOException {
    }

    @Override
    public void writeLink(String key, String link) throws IOException {
    }

    @Override
    public void writeLink(String key, IPLDObject<?> link, Signer signer, IPLDContext context,
            ProgressListener progressListener) throws IOException {
        if (link != null) {
            write(context, link, signer, progressListener);
        }
    }

    @Override
    public void writeBooleanArray(String key, boolean[] value) throws IOException {

    }

    @Override
    public void writeByteArray(String key, byte[] value, ByteCodec codec) throws IOException {
    }

    @Override
    public void writeCharArray(String key, char[] value) throws IOException {

    }

    @Override
    public void writeIntArray(String key, int[] value) throws IOException {

    }

    @Override
    public void writeLongArray(String key, long[] value) throws IOException {

    }

    @Override
    public void writeNumberArray(String key, Number[] value) throws IOException {

    }

    @Override
    public void writeStringArray(String key, String[] value) throws IOException {

    }

    @Override
    public void writeLinkArray(String key, String[] links) throws IOException {

    }

    @Override
    public void writeLinkArray(String key, IPLDObject<?>[] links, Signer signer, IPLDContext context,
            ProgressListener progressListener) throws IOException {
        if (links != null) {
            for (IPLDObject<?> link : links) {
                write(context, link, signer, progressListener);
            }
        }
    }

    @Override
    public void writeLinkArrays(String key, Map<String, String[]> links) throws IOException {
        if (links != null) {
            for (String[] arr : links.values()) {
                steps += arr.length;
            }
        }
    }

    @Override
    public <D extends IPLDSerializable> void writeLinkObjects(String key, Map<String, IPLDObject<D>> links,
            Signer signer, IPLDContext context, ProgressListener progressListener) throws IOException {
        if (links != null) {
            for (IPLDObject<D> link : links.values()) {
                write(context, link, signer, progressListener);
            }
        }
    }

    @Override
    public <D extends IPLDSerializable> void writeLinkObjectArrays(String key, Map<String, IPLDObject<D>[]> linkArrays,
            Signer signer, IPLDContext context, ProgressListener progressListener) throws IOException {
        if (linkArrays != null) {
            for (IPLDObject<D>[] links : linkArrays.values()) {
                for (IPLDObject<D> link : links) {
                    write(context, link, signer, progressListener);
                }
            }
        }
    }

}
