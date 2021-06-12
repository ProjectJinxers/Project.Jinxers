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

import java.io.IOException;
import java.util.Map;

import org.projectjinxers.account.Signer;
import org.projectjinxers.ipld.IPLDContext;
import org.projectjinxers.ipld.IPLDReader;
import org.projectjinxers.ipld.IPLDWriter;

/**
 * @author ProjectJinxers
 *
 */
public class UserState implements IPLDSerializable {

    private int version;
    private int rating;
    private IPLDObject<User> user;
    private IPLDObject<UserState> previousVersion;
    private Map<String, IPLDObject<? extends Document>> documents;
    private Map<String, IPLDObject<? extends Document>> removedDocuments;
    private Map<String, IPLDObject<Document>> falseClaims;
    private Map<String, IPLDObject<Review>> falseApprovals;
    private Map<String, IPLDObject<Review>> falseDeclinations;
    private Map<String, IPLDObject<?>> settlementRequests;
    private Map<String, IPLDObject<?>> ownershipRequests;
    private Map<String, IPLDObject<?>> unbanRequests;
    private Map<String, IPLDObject<?>> grantedOwnerships;
    private Map<String, IPLDObject<?>> grantedUnbans;

    @Override
    public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
            Metadata metadata) {

    }

    @Override
    public void writeProperties(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {

    }

}
