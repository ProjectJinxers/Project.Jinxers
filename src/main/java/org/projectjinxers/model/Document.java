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
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import org.ethereum.crypto.ECKey.ECDSASignature;
import org.projectjinxers.account.Signer;
import org.projectjinxers.controller.IPLDContext;
import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.controller.IPLDReader;
import org.projectjinxers.controller.IPLDReader.KeyProvider;
import org.projectjinxers.controller.IPLDWriter;
import org.projectjinxers.controller.OwnershipTransferController;
import org.projectjinxers.controller.ValidationContext;
import org.projectjinxers.controller.ValidationException;

/**
 * Documents are one of the central parts of the system. Users can create, review, update and delete documents. If an
 * owner of a document has been inactive for a specific time span, other users can request ownership. If there is more
 * than one user, who requested ownership when ownership can be transferred, a voting (poll) is initiated. Without any
 * external factors or actors, the system will tally the votes after the deadline. If at least one initiator wants the
 * poll to be anonymous, it will be anonymous. If a voter wants to redirect the vote to the IOTA Tangle, the vote will
 * be read from the IOTA Tangle.
 * 
 * Documents (discussions) can conditionally be settled. On settlement approving and declining reviews are counted. If
 * one side has a big enough margin, that side will be deemed the 'winner'. All users on the winner side will gain
 * rating points, while users on the loser side will lose rating points. Users whose rating drops below a specific
 * threshold will be banned from all editing activities. They can only redeem themselves on a false-post basis by
 * initiating an unban poll. If they win, they can regain rating points by countering their own false posts.
 * 
 * Documents are saved as Markdown texts. HTML pages can be imported (and converted to Markdown).
 * 
 * @author ProjectJinxers
 */
public class Document implements IPLDSerializable {

    private static final String KEY_TITLE = "t";
    private static final String KEY_SUBTITLE = "b";
    private static final String KEY_VERSION = "v";
    private static final String KEY_TAGS = "g";
    private static final String KEY_DATE = "d";
    private static final String KEY_SOURCE = "s";
    private static final String KEY_CONTENTS = "c";
    private static final String KEY_USER_STATE = "u";
    private static final String KEY_PREVIOUS_VERSION = "p";
    private static final String KEY_FIRST_VERSION = "f";
    private static final String KEY_LINKS = "l";

    static final KeyProvider<Document> LINK_KEY_PROVIDER = new KeyProvider<Document>() {
    };

    private static IPLDObject<Document> getFirstVersionForSuccessor(IPLDObject<Document> document) {
        IPLDObject<Document> firstVersion = document.getMapped().firstVersion;
        return firstVersion == null ? document : firstVersion;
    }

    private String title;
    private String subtitle;
    private String version;
    private String tags;
    private Date date;
    private String source;
    private IPLDObject<DocumentContents> contents;
    private IPLDObject<UserState> userState;
    private IPLDObject<Document> previousVersion;
    private IPLDObject<Document> firstVersion;
    private Map<String, IPLDObject<Document>> links;

    Document() {

    }

    /**
     * Constructor for a new document (for a new version call an appropriate method on the current version).
     * 
     * @param title     the title
     * @param subtitle  the subtitle
     * @param abstr     the abstract
     * @param contents  the contents
     * @param version   the version
     * @param tags      the tags
     * @param source    the source
     * @param userState the current user state (before adding the document)
     */
    public Document(String title, String subtitle, String version, String tags, String source,
            IPLDObject<DocumentContents> contents, IPLDObject<UserState> userState) {
        this.title = title;
        this.subtitle = subtitle;
        this.version = version;
        this.tags = tags;
        this.date = new Date();
        this.source = source;
        this.contents = contents;
        this.userState = userState;
    }

    /**
     * Constructor for a transferring a document.
     * 
     * @param userState       the current user state for the new owner
     * @param previousVersion the transferred document
     */
    public Document(IPLDObject<UserState> userState, IPLDObject<Document> previousVersion) {
        Document doc = previousVersion.getMapped();
        this.title = doc.title;
        this.subtitle = doc.subtitle;
        this.contents = doc.contents;
        this.version = doc.version;
        this.tags = doc.tags;
        this.date = doc.date;
        this.source = doc.source;
        this.userState = userState;
        this.previousVersion = previousVersion;
        this.firstVersion = getFirstVersionForSuccessor(previousVersion);
    }

    @Override
    public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
            Metadata metadata) {
        this.title = reader.readString(KEY_TITLE);
        this.subtitle = reader.readString(KEY_SUBTITLE);
        this.version = reader.readString(KEY_VERSION);
        this.tags = reader.readString(KEY_TAGS);
        long time = reader.readNumber(KEY_DATE).longValue();
        if (validationContext != null) {
            validationContext.validateTimestamp(time);
        }
        this.date = new Date(time);
        this.source = reader.readString(KEY_SOURCE);
        this.contents = reader.readLinkObject(KEY_CONTENTS, context, validationContext, LoaderFactory.DOCUMENT_CONTENTS,
                false); // saves a lot of memory when validating and sealing
        if (validationContext != null && contents == null) {
            handleMissingContents();
        }
        this.userState = reader.readLinkObject(KEY_USER_STATE, context, validationContext, LoaderFactory.USER_STATE,
                eager);
        this.previousVersion = reader.readLinkObject(KEY_PREVIOUS_VERSION, context, validationContext,
                LoaderFactory.DOCUMENT, eager);
        this.firstVersion = reader.readLinkObject(KEY_FIRST_VERSION, context, validationContext, LoaderFactory.DOCUMENT,
                eager);
        this.links = reader.readLinkObjects(KEY_LINKS, context, validationContext, LoaderFactory.DOCUMENT, false,
                LINK_KEY_PROVIDER);
        if (validationContext != null) {
            validationContext.addMustKeepUserState(userState);
        }
    }

    @Override
    public void write(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {
        writer.writeString(KEY_TITLE, title);
        writer.writeString(KEY_SUBTITLE, subtitle);
        writer.writeString(KEY_VERSION, version);
        writer.writeString(KEY_TAGS, tags);
        writer.writeNumber(KEY_DATE, date.getTime());
        writer.writeString(KEY_SOURCE, source);
        writer.writeLink(KEY_CONTENTS, contents, signer, context);
        writer.writeLink(KEY_USER_STATE, userState, signer, context);
        writer.writeLink(KEY_PREVIOUS_VERSION, previousVersion, signer, null);
        writer.writeLink(KEY_FIRST_VERSION, firstVersion, signer, null);
        writer.writeLinkObjects(KEY_LINKS, links, signer, context);
    }

    protected void handleMissingContents() {
        throw new ValidationException("document without contents");
    }

    /**
     * @return the title
     */
    public String getTitle() {
        return title;
    }

    public String getVersion() {
        return version;
    }

    public String getTags() {
        return tags;
    }

    /**
     * @return the date when the document has been added
     */
    public Date getDate() {
        return date;
    }

    public IPLDObject<DocumentContents> getContents() {
        return contents;
    }

    /**
     * @return the user state
     */
    public IPLDObject<UserState> getUserState() {
        return userState;
    }

    /**
     * @return the unwrapped user state (no null check)
     */
    public UserState expectUserState() {
        return userState.getMapped();
    }

    /**
     * Prepares transfer of this document to another owner. This instance's state is not changed.
     * 
     * @param newOwner         the new owner
     * @param currentWrapper   the current wrapper object (will be set as the new previousVersion)
     * @param foreignSignature the signature of the pub data
     * @return the new object (must be saved)
     */
    public IPLDObject<Document> transferTo(IPLDObject<UserState> newOwner, IPLDObject<Document> currentWrapper,
            ECDSASignature foreignSignature) {
        Document copy = copy();
        copy.userState = newOwner;
        copy.previousVersion = currentWrapper;
        copy.firstVersion = getFirstVersionForSuccessor(currentWrapper);
        IPLDObject<Document> res = new IPLDObject<Document>(copy, foreignSignature);
        return res;
    }

    /**
     * @return the hash of the previous version (null-safe)
     */
    public String getPreviousVersionHash() {
        return previousVersion == null ? null : previousVersion.getMultihash();
    }

    /**
     * @return the previous version (null-safe)
     */
    public Document getPreviousVersion() {
        return previousVersion == null ? null : previousVersion.getMapped();
    }

    public IPLDObject<Document> getPreviousVersionObject() {
        return previousVersion;
    }

    /**
     * @return the hash of the first version (null-safe)
     */
    public String getFirstVersionHash() {
        return firstVersion == null ? null : firstVersion.getMultihash();
    }

    /**
     * @return the first version (null-safe)
     */
    public Document getFirstVersion() {
        return firstVersion == null ? this : firstVersion.getMapped();
    }

    public Document update(String version, String tags, IPLDObject<DocumentContents> contents,
            IPLDObject<Document> current) {
        return update(title, subtitle, version, tags, source, contents, current);
    }

    /**
     * Updates the properties of this document. Whether or not that is done in a copy depends on the parameter
     * 'current'. If that parameter's value is null, this object will be updated. Otherwise a copy of this object, where
     * the previous version is set as this object, will be updated.
     * 
     * @param title    the updated title
     * @param subtitle the updated subtitle
     * @param abstr    the updated abstract
     * @param contents the updated contents
     * @param version  the updated version
     * @param tags     the updated tags
     * @param source   the updated source
     * @param current  the current wrapper (pass null for updating this object, non-null for creating a new version
     *                 copy)
     * @return the updated object
     */
    public Document update(String title, String subtitle, String version, String tags, String source,
            IPLDObject<DocumentContents> contents, IPLDObject<Document> current) {
        Document updated = current == null ? this : createCopyInstance();
        updated.title = title;
        updated.subtitle = subtitle;
        updated.version = version;
        updated.tags = tags;
        updated.source = source;
        updated.date = new Date();
        updated.contents = contents;
        if (current != null) {
            updated.previousVersion = current;
            updated.firstVersion = getFirstVersionForSuccessor(current);
            updated.links = links == null ? null : new LinkedHashMap<>(links);
        }
        return updated;
    }

    @Override
    public boolean isSignatureMandatory() {
        return true;
    }

    @Override
    public byte[] hashBase(IPLDWriter writer, IPLDContext context) throws IOException {
        if (previousVersion != null) {
            String currentOwnerHash = userState.getMapped().getUser().getMultihash();
            if (!currentOwnerHash.equals(previousVersion.getMapped().expectUserState().getUser().getMultihash())) {
                String previousHash = previousVersion.getMultihash();
                IPLDObject<OwnershipRequest> ownershipRequest = userState.getMapped().getOwnershipRequest(previousHash);
                boolean anonymousVoting = ownershipRequest == null ? false
                        : ownershipRequest.getMapped().isAnonymousVoting();
                String hashBase = OwnershipTransferController.composePubMessageRequest(anonymousVoting,
                        currentOwnerHash, previousHash);
                return hashBase.getBytes(StandardCharsets.UTF_8);
            }
        }
        return IPLDSerializable.super.hashBase(writer, context);
    }

    private Document copy() {
        Document res = createCopyInstance();
        res.title = title;
        res.subtitle = subtitle;
        res.version = version;
        res.tags = tags;
        res.date = date;
        res.source = source;
        res.contents = contents;
        res.links = links;
        res.userState = userState;
        res.previousVersion = previousVersion;
        res.firstVersion = firstVersion;
        return res;
    }

    /**
     * @return a new instance for copying the values
     */
    protected Document createCopyInstance() {
        return new Document();
    }

}
