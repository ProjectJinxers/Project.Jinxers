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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.projectjinxers.account.Signer;
import org.projectjinxers.controller.IPLDContext;
import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.controller.IPLDReader;
import org.projectjinxers.controller.IPLDWriter;
import org.projectjinxers.controller.ValidationContext;
import org.projectjinxers.controller.ValidationException;

/**
 * Review instances represent users' reviews of a document. There are also special reviews, which can invert the truth
 * for a sealed document. They have to be on the opposite side of the majority and must be settled successfully. If
 * settled successfully, the reviewed document will be resettled, which leads to an inversion of the penalties and
 * rewards. This operation cascades up if the reviewed document is itself a truth inverting review and down to all
 * reviews, that have been sealed (including truth inverting reviews). The links included in truth inverting reviews can
 * be marked as documents to include in the inversion.
 * 
 * @author ProjectJinxers
 */
public class Review extends Document implements DocumentAction, Loader<Review> {

    private static final String KEY_INVERT_TRUTH = "I";
    private static final String KEY_APPROVE = "r";
    static final String KEY_DOCUMENT = "o";
    private static final String KEY_INVERT_TRUTH_LINKS = "L";

    private boolean invertTruth;
    private Boolean approve;
    private IPLDObject<Document> document;
    private Map<String, IPLDObject<Document>> invertTruthLinks;

    Review() {

    }

    /**
     * Constructor for a new review (for a new version call an appropriate method on the current version).
     * 
     * @param document
     * @param approve
     */
    public Review(String title, String subtitle, String version, String tags, String source,
            IPLDObject<DocumentContents> contents, IPLDObject<Document> document, boolean invertTruth, Boolean approve,
            IPLDObject<UserState> userState) {
        super(title, subtitle, version, tags, source, contents, userState);
        this.document = document;
        this.invertTruth = invertTruth;
        this.approve = approve;
    }

    @Override
    public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
            Metadata metadata) {
        this.approve = reader.readBoolean(KEY_APPROVE); // need this value before calling super (if the reader needs to
                                                        // be accessed in a specific order, set a flag and handle
                                                        // missing contents later)
        super.read(reader, context, validationContext, eager, metadata);
        this.invertTruth = Boolean.TRUE.equals(reader.readBoolean(KEY_INVERT_TRUTH));
        this.document = reader.readLinkObject(KEY_DOCUMENT, context, validationContext, LoaderFactory.DOCUMENT, eager);
        this.invertTruthLinks = reader.readLinkObjects(KEY_INVERT_TRUTH_LINKS, context, validationContext,
                LoaderFactory.DOCUMENT, eager, Document.LINK_KEY_PROVIDER);
    }

    @Override
    public void write(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {
        super.write(writer, signer, context);
        writer.writeIfTrue(KEY_INVERT_TRUTH, invertTruth);
        writer.writeBoolean(KEY_APPROVE, approve);
        writer.writeLink(KEY_DOCUMENT, document, signer, null);
        writer.writeLinkObjects(KEY_INVERT_TRUTH_LINKS, invertTruthLinks, signer, null);
    }

    @Override
    protected void handleMissingContents() {
        if (Boolean.FALSE.equals(approve)) {
            throw new ValidationException("declining review without contents");
        }
    }

    /**
     * @return whether or not this is a review, which can invert the truth for the reviewed document
     */
    public boolean isInvertTruth() {
        return invertTruth;
    }

    /**
     * @return the approve value
     */
    public Boolean getApprove() {
        return approve;
    }

    @Override
    public IPLDObject<Document> getDocument() {
        return document;
    }

    public Map<String, IPLDObject<Document>> getAllInvertTruthLinks() {
        return invertTruthLinks == null ? null : Collections.unmodifiableMap(invertTruthLinks);
    }

    /**
     * Updates the properties of this review. Whether or not that is done in a copy depends on the parameter 'current'.
     * If that parameter's value is null, this object will be updated. Otherwise a copy of this object, where the
     * previous version is set as this object, will be updated.
     * 
     * @param title    the updated title
     * @param subtitle the updated subtitle
     * @param version  the updated version
     * @param tags     the updated tags
     * @param source   the updated source
     * @param contents the updated contents
     * @param approve  the updated approve value
     * @param current  the current wrapper (pass null for updating this object, non-null for creating a new version
     *                 copy)
     * @return the updated object
     */
    public Review update(String title, String subtitle, String version, String tags, String source,
            IPLDObject<DocumentContents> contents, Boolean approve, IPLDObject<Document> current) {
        Review res = (Review) super.update(title, subtitle, version, tags, source, contents, current);
        res.approve = approve;
        return res;
    }

    @Override
    protected Document createCopyInstance() {
        Review res = new Review();
        res.invertTruth = invertTruth;
        res.approve = approve;
        res.document = document;
        res.invertTruthLinks = invertTruthLinks == null ? null : new LinkedHashMap<>(invertTruthLinks);
        return res;
    }

    @Override
    public Review getOrCreateDataInstance(IPLDReader reader, Metadata metadata) {
        return this;
    }

    @Override
    public Review getLoaded() {
        return this;
    }

}
