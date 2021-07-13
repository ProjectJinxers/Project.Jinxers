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
 * @author ProjectJinxers
 *
 */
public class TestIPLDObject<D extends IPLDSerializable> extends IPLDObject<D> {

    private D fixture;
    private Signer signer;
    private String testMultihash;
    private Metadata testMetadata;
    private ECDSASignature testSignature;
    private boolean defaultBehavior;

    /**
     * Constructor for a new test fixture.
     * 
     * @param data the fixture
     */
    public TestIPLDObject(D data) {
        super(data);
        this.fixture = data;
    }

    public TestIPLDObject(D data, Signer signer) {
        super(data);
        this.signer = signer;
        this.defaultBehavior = true;
    }

    /**
     * Constructor for a new signed test fixture. The signature will be the foreign signature at the same time.
     * 
     * @param data             the fixture
     * @param foreignSignature the foreign signature
     */
    public TestIPLDObject(D data, ECDSASignature signature) {
        super(data, signature);
        this.fixture = data;
        this.testSignature = signature;
    }

    /**
     * Constructor for copying a test fixture (e.g. for previousVersion links). The new instance's metadata is set as
     * the the source's metadata by calling the getter {@link IPLDObject#getMetadata()}. If the source is also a
     * TestIPLDObject where the fixture is null, this will result in a Metadata instance with version 0. To avoid that,
     * you can simply pass a non-TestIPLDObject instance.
     * 
     * @param source the copy source
     * @param data   the fixture
     */
    public TestIPLDObject(IPLDObject<D> source, D data) {
        super(source, data);
        this.fixture = data;
        this.testMultihash = source.getMultihash();
        this.testMetadata = source.getMetadata();
    }

    /**
     * Convenience constructor for creating a test fixture with a given hash. The hash can be any string. The created
     * instance's metadata will for ever be null (barring mock shenanigans). The metadata will be resolved on first
     * access (which does not happen during construction).
     * 
     * @param multihash the hash
     * @param data      the fixture
     */
    public TestIPLDObject(String multihash, D data) {
        this(new IPLDObject<D>(multihash, null, null, null) {
            @Override
            public Metadata getMetadata() {
                return null;
            }
        }, data);
    }

    /**
     * Convenience constructor for creating a test fixture with a given hash and metadata. The hash can be any string.
     * 
     * @param multihash the hash
     * @param metadata  the metadata
     * @param data      the fixture
     */
    public TestIPLDObject(String multihash, Metadata metadata, D data) {
        this(new TestIPLDObject<D>(multihash, metadata), data);
    }

    /**
     * Constructor for default behavior. The data instance resolution will be the same as in the super class. As will be
     * saving the instance.
     * 
     * @param multihash         the multihash
     * @param loader            a wrapper for the data instance (might be the data instance itself, though)
     * @param context           the context
     * @param validationContext the validation context
     */
    public TestIPLDObject(String multihash, Loader<D> loader, IPLDContext context,
            ValidationContext validationContext) {
        super(multihash, loader, context, validationContext);
        this.defaultBehavior = true;
    }

    /**
     * Constructor for creating an empty fixture with a given hash. The instance will remain empty for ever, since there
     * will be no default behavior. For the same reason the metadata will be null for ever.
     * 
     * @param multihash the hash
     */
    public TestIPLDObject(String multihash) {
        super(multihash, null, null, null);
    }

    /**
     * Constructor for creating an empty fixture with a given hash and metadata. The instance will remain empty for
     * ever, since there will be no default behavior.
     * 
     * @param multihash the hash
     * @param metadata  the metadata
     */
    public TestIPLDObject(String multihash, Metadata metadata) {
        this(multihash);
        this.testMetadata = metadata;
    }

    @Override
    public D getMapped() {
        if (defaultBehavior) {
            return super.getMapped();
        }
        return fixture;
    }

    @Override
    public Metadata getMetadata() {
        if (defaultBehavior) {
            return super.getMetadata();
        }
        if (testMetadata == null) {
            testMetadata = new Metadata(fixture == null ? 0 : fixture.getMetaVersion(), testSignature);
        }
        return testMetadata;
    }

    @Override
    String save(IPLDContext context, Signer signer, ProgressListener progressListener) throws IOException {
        if (defaultBehavior) {
            return super.save(context, this.signer == null ? signer : this.signer, progressListener);
        }
        return testMultihash;
    }

}
