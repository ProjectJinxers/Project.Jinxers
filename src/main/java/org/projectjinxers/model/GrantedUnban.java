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
import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.controller.IPLDReader;
import org.projectjinxers.controller.IPLDWriter;
import org.projectjinxers.controller.ValidationContext;

/**
 * Granted unbans can be validated by checking the existence of specific instances of this class.
 * 
 * @author ProjectJinxers
 */
public class GrantedUnban implements IPLDSerializable, Loader<GrantedUnban> {

    private static final String KEY_UNBAN_REQUEST = "u";

    private IPLDObject<UnbanRequest> unbanRequest;

    @Override
    public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
            Metadata metadata) {
        this.unbanRequest = reader.readLinkObject(KEY_UNBAN_REQUEST, context, validationContext,
                LoaderFactory.UNBAN_REQUEST, eager);
    }

    @Override
    public void write(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {
        writer.writeLink(KEY_UNBAN_REQUEST, unbanRequest, null, null);
    }

    public IPLDObject<UnbanRequest> getUnbanRequest() {
        return unbanRequest;
    }

    @Override
    public GrantedUnban getOrCreateDataInstance(IPLDReader reader, Metadata metadata) {
        return this;
    }

    @Override
    public GrantedUnban getLoaded() {
        return this;
    }

}
