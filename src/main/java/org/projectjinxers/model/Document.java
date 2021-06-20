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
import org.projectjinxers.controller.IPLDWriter;
import org.projectjinxers.controller.ValidationContext;

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
    private static final String KEY_ABSTRACT = "a";
    private static final String KEY_CONTENTS = "c";
    private static final String KEY_VERSION = "v";
    private static final String KEY_TAGS = "g";
    private static final String KEY_DATE = "d";
    private static final String KEY_SOURCE = "s";
    private static final String KEY_USER_STATE = "u";
    private static final String KEY_PREVIOUS_VERSION = "p";
    private static final String KEY_LINKS = "l";

    private String title;
    private String subtitle;
    private String abstr;
    private String contents;
    private String version;
    private String tags;
    private Date date;
    private String source;
    private IPLDObject<UserState> userState;
    private IPLDObject<Document> previousVersion;
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
    public Document(String title, String subtitle, String abstr, String contents, String version, String tags,
            String source, IPLDObject<UserState> userState) {
        this.title = title;
        this.subtitle = subtitle;
        this.abstr = abstr;
        this.contents = contents;
        this.version = version;
        this.tags = tags;
        this.date = new Date();
        this.source = source;
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
        this.abstr = doc.abstr;
        this.contents = doc.contents;
        this.version = doc.version;
        this.tags = doc.tags;
        this.date = doc.date;
        this.source = doc.source;
        this.userState = userState;
        this.previousVersion = previousVersion;
    }

    @Override
    public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
            Metadata metadata) {
        this.title = reader.readString(KEY_TITLE);
        this.subtitle = reader.readString(KEY_SUBTITLE);
        this.abstr = reader.readString(KEY_ABSTRACT);
        this.contents = reader.readString(KEY_CONTENTS);
        this.version = reader.readString(KEY_VERSION);
        this.tags = reader.readString(KEY_TAGS);
        this.date = new Date(reader.readNumber(KEY_DATE).longValue());
        this.source = reader.readString(KEY_SOURCE);
        this.userState = reader.readLinkObject(KEY_USER_STATE, context, validationContext, LoaderFactory.USER_STATE,
                eager);
        this.previousVersion = reader.readLinkObject(KEY_PREVIOUS_VERSION, context, validationContext,
                LoaderFactory.DOCUMENT, eager);
        this.links = reader.readLinkObjects(KEY_LINKS, context, validationContext, LoaderFactory.DOCUMENT, false, null);
    }

    @Override
    public void write(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {
        writer.writeString(KEY_TITLE, title);
        writer.writeString(KEY_SUBTITLE, subtitle);
        writer.writeString(KEY_ABSTRACT, abstr);
        writer.writeString(KEY_CONTENTS, contents);
        writer.writeString(KEY_VERSION, version);
        writer.writeString(KEY_TAGS, tags);
        writer.writeNumber(KEY_DATE, date.getTime());
        writer.writeString(KEY_SOURCE, source);
        writer.writeLink(KEY_USER_STATE, userState, signer, null);
        writer.writeLink(KEY_PREVIOUS_VERSION, previousVersion, signer, null);
        writer.writeLinkObjects(KEY_LINKS, links, signer, context);
    }

    /**
     * @return the title
     */
    public String getTitle() {
        return title;
    }

    /**
     * @return the date when the document has been added
     */
    public Date getDate() {
        return date;
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

    /**
     * @return the first version (null-safe)
     */
    public Document getFirstVersion() {
        Document doc = this;
        do {
            Document previous = doc.getPreviousVersion();
            if (previous == null) {
                return doc;
            }
            doc = previous;
        }
        while (true);
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
    public Document update(String title, String subtitle, String abstr, String contents, String version, String tags,
            String source, IPLDObject<Document> current) {
        Document updated = current == null ? this : createCopyInstance();
        updated.title = title;
        updated.subtitle = subtitle;
        updated.abstr = abstr;
        updated.contents = contents;
        updated.version = version;
        updated.tags = tags;
        updated.source = source;
        updated.date = new Date();
        if (current != null) {
            updated.previousVersion = current;
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
                return (currentOwnerHash + "." + previousVersion.getMultihash()).getBytes(StandardCharsets.UTF_8);
            }
        }
        return IPLDSerializable.super.hashBase(writer, context);
    }

    private Document copy() {
        Document res = createCopyInstance();
        res.title = title;
        res.subtitle = subtitle;
        res.abstr = abstr;
        res.contents = contents;
        res.version = version;
        res.tags = tags;
        res.date = date;
        res.source = source;
        res.links = links;
        res.userState = userState;
        res.previousVersion = previousVersion;
        return res;
    }

    /**
     * @return a new instance for copying the values
     */
    protected Document createCopyInstance() {
        return new Document();
    }

}
