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
import java.util.Map;

import org.projectjinxers.account.Signer;
import org.projectjinxers.controller.IPLDContext;
import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.controller.IPLDReader;
import org.projectjinxers.controller.IPLDWriter;
import org.projectjinxers.controller.ValidationContext;

/**
 * Review instances represent users' reviews of a document. There are also special reviews, which can unseal a sealed
 * document. They have to be on the opposite side of the majority and must be settled successfully. If settled
 * successfully, the reviewed document will be resettled, which leads to an inversion of the penalties and rewards. This
 * operation cascades up if the reviewed document is itself an unsealing review and down to all reviews, that have been
 * sealed (including unsealing reviews). The links included in unsealing reviews can be marked as documents to include
 * in the unsealing.
 * 
 * @author ProjectJinxers
 */
public class Review extends Document implements DocumentAction, Loader<Review> {

    private static final String KEY_UNSEAL = "U";
    private static final String KEY_APPROVE = "r";
    static final String KEY_DOCUMENT = "o";
    private static final String KEY_UNSEAL_LINKS = "L";

    private boolean unseal;
    private Boolean approve;
    private IPLDObject<Document> document;
    private Map<String, IPLDObject<Document>> unsealLinks;

    Review() {

    }

    /**
     * Constructor for a new review (for a new version call an appropriate method on the current version).
     * 
     * @param document
     * @param approve
     */
    public Review(String title, String subtitle, String abstr, String contents, String version, String tags,
            String source, IPLDObject<Document> document, boolean unseal, Boolean approve,
            IPLDObject<UserState> userState) {
        super(title, subtitle, abstr, contents, version, tags, source, userState);
        this.document = document;
        this.unseal = unseal;
        this.approve = approve;
    }

    @Override
    public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
            Metadata metadata) {
        super.read(reader, context, validationContext, eager, metadata);
        this.unseal = Boolean.TRUE.equals(reader.readBoolean(KEY_UNSEAL));
        this.approve = reader.readBoolean(KEY_APPROVE);
        this.document = reader.readLinkObject(KEY_DOCUMENT, context, validationContext, LoaderFactory.DOCUMENT, eager);
        this.unsealLinks = reader.readLinkObjects(KEY_UNSEAL_LINKS, context, validationContext, LoaderFactory.DOCUMENT,
                eager, Document.LINK_KEY_PROVIDER);
    }

    @Override
    public void write(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {
        super.write(writer, signer, context);
        writer.writeIfTrue(KEY_UNSEAL, unseal);
        writer.writeBoolean(KEY_APPROVE, approve);
        writer.writeLink(KEY_DOCUMENT, document, signer, null);
        writer.writeLinkObjects(KEY_UNSEAL_LINKS, unsealLinks, signer, null);
    }

    /**
     * @return whether or not this is a review, which can unseal the reviewed document
     */
    public boolean isUnseal() {
        return unseal;
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

    /**
     * Updates the properties of this review. Whether or not that is done in a copy depends on the parameter 'current'.
     * If that parameter's value is null, this object will be updated. Otherwise a copy of this object, where the
     * previous version is set as this object, will be updated.
     * 
     * @param title    the updated title
     * @param subtitle the updated subtitle
     * @param abstr    the updated abstract
     * @param contents the updated contents
     * @param version  the updated version
     * @param tags     the updated tags
     * @param source   the updated source
     * @param approve  the updated approve value
     * @param current  the current wrapper (pass null for updating this object, non-null for creating a new version
     *                 copy)
     * @return the updated object
     */
    public Review update(String title, String subtitle, String abstr, String contents, String version, String tags,
            String source, Boolean approve, IPLDObject<Document> current) {
        Review res = (Review) super.update(title, subtitle, abstr, contents, version, tags, source, current);
        res.approve = approve;
        return res;
    }

    @Override
    protected Document createCopyInstance() {
        Review res = new Review();
        res.approve = approve;
        res.document = document;
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
