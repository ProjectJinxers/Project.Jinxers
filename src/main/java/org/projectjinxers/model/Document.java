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
import java.util.Date;
import java.util.Map;

import org.projectjinxers.account.Signer;
import org.projectjinxers.controller.IPLDContext;
import org.projectjinxers.ipld.IPLDReader;
import org.projectjinxers.ipld.IPLDWriter;

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

    private static final String KEY_CONTENTS = "c";
    private static final String KEY_VERSION = "v";
    private static final String KEY_TAGS = "t";
    private static final String KEY_DATE = "d";
    private static final String KEY_SOURCE = "s";
    private static final String KEY_PREVIOUS_VERSION = "p";
    private static final String KEY_LINKS = "l";

    private String contents;
    private String version;
    private String tags;
    private Date date;
    private String source;
    private IPLDObject<Document> previousVersion;
    private Map<String, IPLDObject<Document>> links;

    @Override
    public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
            Metadata metadata) {
        this.contents = reader.readString(KEY_CONTENTS);
        this.version = reader.readString(KEY_VERSION);
        this.tags = reader.readString(KEY_TAGS);
        this.date = new Date(reader.readNumber(KEY_DATE).longValue());
        this.source = reader.readString(KEY_SOURCE);
        this.previousVersion = reader.readLinkObject(KEY_PREVIOUS_VERSION, context, validationContext,
                LoaderFactory.DOCUMENT, eager);
        this.links = reader.readLinkObjects(KEY_LINKS, context, validationContext, LoaderFactory.DOCUMENT, false, null);
    }

    @Override
    public void write(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {
        writer.writeString(KEY_CONTENTS, contents);
        writer.writeString(KEY_VERSION, version);
        writer.writeString(KEY_TAGS, tags);
        writer.writeNumber(KEY_DATE, date.getTime());
        writer.writeString(KEY_SOURCE, source);
        writer.writeLink(KEY_PREVIOUS_VERSION, previousVersion, signer, null);
        writer.writeLinkObjects(KEY_LINKS, links, signer, context);
    }

    @Override
    public boolean isSignatureMandatory() {
        return true;
    }

}
