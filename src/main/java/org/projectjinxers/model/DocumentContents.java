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

import org.projectjinxers.account.Signer;
import org.projectjinxers.controller.IPLDContext;
import org.projectjinxers.controller.IPLDObject.ProgressListener;
import org.projectjinxers.controller.IPLDReader;
import org.projectjinxers.controller.IPLDWriter;
import org.projectjinxers.controller.ValidationContext;

/**
 * @author ProjectJinxers
 *
 */
public class DocumentContents implements IPLDSerializable, Loader<DocumentContents> {

    private static final String KEY_ABSTRACT = "a";
    private static final String KEY_CONTENTS = "c";

    private String abstr;
    private String contents;

    DocumentContents() {

    }

    public DocumentContents(String abstr, String contents) {
        this.abstr = abstr;
        this.contents = contents;
    }

    @Override
    public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
            Metadata metadata) {
        this.abstr = reader.readString(KEY_ABSTRACT);
        this.contents = reader.readString(KEY_CONTENTS);
    }

    @Override
    public void write(IPLDWriter writer, Signer signer, IPLDContext context, ProgressListener progressListener)
            throws IOException {
        writer.writeString(KEY_ABSTRACT, abstr);
        writer.writeString(KEY_CONTENTS, contents);
    }

    public String getAbstract() {
        return abstr;
    }

    public String getContents() {
        return contents;
    }

    @Override
    public DocumentContents getOrCreateDataInstance(IPLDReader reader, Metadata metadata) {
        return this;
    }

    @Override
    public DocumentContents getLoaded() {
        return this;
    }

}
