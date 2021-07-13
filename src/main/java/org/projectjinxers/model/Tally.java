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
public class Tally implements IPLDSerializable, Loader<Tally> {

    private static final String KEY_COUNTS = "c";

    private int[] counts;

    Tally() {

    }

    public Tally(int[] counts) {
        this.counts = counts;
    }

    @Override
    public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
            Metadata metadata) {
        this.counts = reader.readIntArray(KEY_COUNTS);
    }

    @Override
    public void write(IPLDWriter writer, Signer signer, IPLDContext context, ProgressListener progressListener)
            throws IOException {
        writer.writeIntArray(KEY_COUNTS, counts);
    }

    public int[] getCounts() {
        int[] res = new int[counts.length];
        System.arraycopy(counts, 0, res, 0, res.length);
        return res;
    }

    @Override
    public Tally getOrCreateDataInstance(IPLDReader reader, Metadata metadata) {
        return this;
    }

    @Override
    public Tally getLoaded() {
        return this;
    }

}
