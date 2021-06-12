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
import org.projectjinxers.ipld.IPLDContext;
import org.projectjinxers.ipld.IPLDReader;
import org.projectjinxers.ipld.IPLDReader.KeyProvider;
import org.projectjinxers.ipld.IPLDWriter;

/**
 * Instances of this class represent the state of a user (rating, documents, requests etc.) at a specific time.
 * 
 * @author ProjectJinxers
 */
public class UserState implements IPLDSerializable, Loader<UserState> {

    private static final String KEY_VERSION = "v";
    private static final String KEY_RATING = "r";
    private static final String KEY_USER = "u";
    private static final String KEY_PREVIOUS_VERSION = "p";
    private static final String KEY_DOCUMENTS = "d";
    private static final String KEY_REMOVED_DOCUMENTS = "m";
    private static final String KEY_FALSE_CLAIMS = "c";
    private static final String KEY_FALSE_APPROVALS = "a";
    private static final String KEY_FALSE_DECLINATIONS = "e";
    private static final String KEY_SETTLEMENT_REQUESTS = "s";
    private static final String KEY_OWNERSHIP_REQUESTS = "o";
    private static final String KEY_UNBAN_REQUESTS = "n";
    private static final String KEY_GRANTED_OWNERSHIPS = "g";
    private static final String KEY_GRANTED_UNBANS = "b";

    private static final KeyProvider<Document> DOCUMENT_KEY_PROVIDER = new KeyProvider<>() {
    };
    private static final KeyProvider<Review> REVIEW_KEY_PROVIDER = new KeyProvider<>() {
    };
    private static final KeyProvider<SettlementRequest> SETTLEMENT_KEY_PROVIDER = new KeyProvider<>() {
        @Override
        public String getKey(IPLDObject<SettlementRequest> object) {
            return object.getMapped().getDocument().getMultihash();
        }
    };
    private static final KeyProvider<OwnershipRequest> OWNERSHIP_REQUEST_KEY_PROVIDER = new KeyProvider<>() {
        @Override
        public String getKey(IPLDObject<OwnershipRequest> object) {
            return object.getMapped().getDocument().getMultihash();
        }
    };
    private static final KeyProvider<UnbanRequest> UNBAN_REQUEST_KEY_PROVIDER = new KeyProvider<>() {
        @Override
        public String getKey(IPLDObject<UnbanRequest> object) {
            return object.getMapped().getDocument().getMultihash();
        }
    };
    private static final KeyProvider<GrantedOwnership> GRANTED_OWNERSHIP_KEY_PROVIDER = new KeyProvider<>() {
        @Override
        public String getKey(IPLDObject<GrantedOwnership> object) {
            return object.getMapped().getDocument().getMultihash();
        }
    };
    private static final KeyProvider<GrantedUnban> GRANTED_UNBAN_KEY_PROVIDER = new KeyProvider<>() {
        @Override
        public String getKey(IPLDObject<GrantedUnban> object) {
            return object.getMapped().getUnbanRequest().getMapped().getDocument().getMultihash();
        }
    };

    private int version;
    private int rating;
    private IPLDObject<User> user;
    private IPLDObject<UserState> previousVersion;
    private Map<String, IPLDObject<Document>> documents;
    private Map<String, IPLDObject<Document>> removedDocuments;
    private Map<String, IPLDObject<Document>> falseClaims;
    private Map<String, IPLDObject<Review>> falseApprovals;
    private Map<String, IPLDObject<Review>> falseDeclinations;
    private Map<String, IPLDObject<SettlementRequest>> settlementRequests;
    private Map<String, IPLDObject<OwnershipRequest>> ownershipRequests;
    private Map<String, IPLDObject<UnbanRequest>> unbanRequests;
    private Map<String, IPLDObject<GrantedOwnership>> grantedOwnerships;
    private Map<String, IPLDObject<GrantedUnban>> grantedUnbans;

    @Override
    public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
            Metadata metadata) {
        this.version = reader.readNumber(KEY_VERSION).intValue();
        this.rating = reader.readNumber(KEY_RATING).intValue();
        this.user = reader.readLinkObject(KEY_USER, context, validationContext, LoaderFactory.USER, eager);
        this.previousVersion = reader.readLinkObject(KEY_PREVIOUS_VERSION, context, validationContext,
                LoaderFactory.USER_STATE, false);
        this.documents = reader.readLinkObjects(KEY_DOCUMENTS, context, validationContext, LoaderFactory.DOCUMENT,
                eager, DOCUMENT_KEY_PROVIDER);
        this.removedDocuments = reader.readLinkObjects(KEY_REMOVED_DOCUMENTS, context, validationContext,
                LoaderFactory.DOCUMENT, eager, DOCUMENT_KEY_PROVIDER);
        this.falseClaims = reader.readLinkObjects(KEY_FALSE_CLAIMS, context, validationContext, LoaderFactory.DOCUMENT,
                eager, DOCUMENT_KEY_PROVIDER);
        this.falseApprovals = reader.readLinkObjects(KEY_FALSE_APPROVALS, context, validationContext,
                LoaderFactory.REVIEW, eager, REVIEW_KEY_PROVIDER);
        this.falseDeclinations = reader.readLinkObjects(KEY_FALSE_DECLINATIONS, context, validationContext,
                LoaderFactory.REVIEW, eager, REVIEW_KEY_PROVIDER);
        this.settlementRequests = reader.readLinkObjects(KEY_SETTLEMENT_REQUESTS, context, validationContext,
                LoaderFactory.SETTLEMENT_REQUEST, eager, SETTLEMENT_KEY_PROVIDER);
        this.ownershipRequests = reader.readLinkObjects(KEY_OWNERSHIP_REQUESTS, context, validationContext,
                LoaderFactory.OWNERSHIP_REQUEST, eager, OWNERSHIP_REQUEST_KEY_PROVIDER);
        this.unbanRequests = reader.readLinkObjects(KEY_UNBAN_REQUESTS, context, validationContext,
                LoaderFactory.UNBAN_REQUEST, eager, UNBAN_REQUEST_KEY_PROVIDER);
        this.grantedOwnerships = reader.readLinkObjects(KEY_OWNERSHIP_REQUESTS, context, validationContext,
                LoaderFactory.GRANTED_OWNERSHIP, eager, GRANTED_OWNERSHIP_KEY_PROVIDER);
        this.grantedUnbans = reader.readLinkObjects(KEY_UNBAN_REQUESTS, context, validationContext,
                LoaderFactory.GRANTED_UNBAN, eager, GRANTED_UNBAN_KEY_PROVIDER);
    }

    @Override
    public void write(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {
        writer.writeNumber(KEY_VERSION, version);
        writer.writeNumber(KEY_RATING, rating);
        writer.writeLink(KEY_USER, user, signer, context);
        writer.writeLink(KEY_PREVIOUS_VERSION, previousVersion, signer, null);
        writer.writeLinkObjects(KEY_DOCUMENTS, documents, signer, context);
        writer.writeLinkObjects(KEY_REMOVED_DOCUMENTS, removedDocuments, signer, null);
        writer.writeLinkObjects(KEY_FALSE_CLAIMS, falseClaims, signer, null);
        writer.writeLinkObjects(KEY_FALSE_APPROVALS, falseApprovals, signer, null);
        writer.writeLinkObjects(KEY_FALSE_DECLINATIONS, falseDeclinations, signer, null);
        writer.writeLinkObjects(KEY_SETTLEMENT_REQUESTS, settlementRequests, signer, context);
        writer.writeLinkObjects(KEY_OWNERSHIP_REQUESTS, ownershipRequests, signer, context);
        writer.writeLinkObjects(KEY_UNBAN_REQUESTS, unbanRequests, signer, context);
        writer.writeLinkObjects(KEY_GRANTED_OWNERSHIPS, grantedOwnerships, signer, context);
        writer.writeLinkObjects(KEY_GRANTED_UNBANS, grantedUnbans, signer, context);
    }

    /**
     * @return the user
     */
    public IPLDObject<User> getUser() {
        return user;
    }

    @Override
    public UserState getOrCreateDataInstance(IPLDReader reader, Metadata metadata) {
        return this;
    }

    @Override
    public UserState getLoaded() {
        return this;
    }

}
