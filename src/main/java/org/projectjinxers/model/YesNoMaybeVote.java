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
 * A simple Vote for a non-anonymous Voting where users can select 'yes', 'no' and nothing (e.g. maybe).
 * 
 * @author ProjectJinxers
 */
public class YesNoMaybeVote extends AbstractVote {

    static final String KEY_BOOLEAN_VALUE = "B";

    private Boolean value;

    YesNoMaybeVote() {

    }

    public YesNoMaybeVote(byte[] invitationKey, Boolean value) {
        super(invitationKey, false, 0);
        this.value = value;
    }

    @Override
    public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
            Metadata metadata) {
        super.read(reader, context, validationContext, eager, metadata);
        this.value = reader.readBoolean(KEY_BOOLEAN_VALUE);
    }

    @Override
    public void write(IPLDWriter writer, Signer signer, IPLDContext context, ProgressListener progressListener)
            throws IOException {
        super.write(writer, signer, context, progressListener);
        writer.writeBoolean(KEY_BOOLEAN_VALUE, value);
    }

    @Override
    public Object getValue() {
        return value;
    }

}
