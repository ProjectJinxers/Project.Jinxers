/*
	Copyright (C) 2021 ProjectJinxers

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.projectjinxers.model;

import org.projectjinxers.ipld.IPLDContext;
import org.projectjinxers.ipld.IPLDReader;

/**
 * @author ProjectJinxers
 *
 */
public class Review extends Document implements Loader<Review> {

    private Boolean approve;

    @Override
    public Review getOrCreateDataInstance(IPLDReader reader, Metadata metadata) {
        return this;
    }

    @Override
    public Review getLoaded() {
        return this;
    }

    @Override
    public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
            Metadata metadata) {
        super.read(reader, context, validationContext, eager, metadata);
        this.approve = reader.readBoolean("approve");
    }

}
