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

import org.projectjinxers.controller.IPLDReader;

/**
 * Factory for loaders. Those are needed when recursively loading links.
 * 
 * @author ProjectJinxers
 */
public interface LoaderFactory<D extends IPLDSerializable> {

    /**
     * The factory for Document instances.
     */
    public static final LoaderFactory<Document> DOCUMENT = new LoaderFactory<>() {
        @Override
        public Loader<Document> createLoader() {
            return new Loader<>() {

                private Document loaded;

                @Override
                public Document getOrCreateDataInstance(IPLDReader reader, Metadata metadata) {
                    if (loaded == null) {
                        if (reader.hasLinkKey(Review.KEY_DOCUMENT)) {
                            loaded = new Review();
                        }
                        else {
                            loaded = new Document();
                        }
                    }
                    return loaded;
                }

                @Override
                public Document getLoaded() {
                    return loaded;
                }
            };
        }
    };

    /**
     * The factory for Review instances.
     */
    public static final LoaderFactory<Review> REVIEW = new LoaderFactory<>() {
        @Override
        public Loader<Review> createLoader() {
            return new Review();
        }
    };

    /**
     * The factory for Voting instances.
     */
    public static final LoaderFactory<Voting> VOTING = new LoaderFactory<>() {
        @Override
        public Loader<Voting> createLoader() {
            return new Voting();
        }
    };

    /**
     * The factory for Votable instances.
     */
    public static final LoaderFactory<Votable> VOTABLE = new LoaderFactory<>() {
        @Override
        public Loader<Votable> createLoader() {
            return new Loader<Votable>() {

                private Votable loaded;

                @Override
                public Votable getOrCreateDataInstance(IPLDReader reader, Metadata metadata) {
                    if (loaded == null) {
                        if (reader.hasLinkArrayKey(OwnershipSelection.KEY_SELECTION)) {
                            loaded = new OwnershipSelection();
                        }
                        else {
                            loaded = new UnbanRequest();
                        }
                    }
                    return loaded;
                }

                @Override
                public Votable getLoaded() {
                    return loaded;
                }

            };
        }
    };

    /**
     * The factory for Vote instances.
     */
    public static final LoaderFactory<Vote> VOTE = new LoaderFactory<>() {
        @Override
        public Loader<Vote> createLoader() {
            return new Loader<Vote>() {

                private Vote loaded;

                @Override
                public Vote getOrCreateDataInstance(IPLDReader reader, Metadata metadata) {
                    if (loaded == null) {
                        if (reader.hasPrimitiveKey(YesNoMaybeVote.KEY_BOOLEAN_VALUE)) {
                            loaded = new YesNoMaybeVote();
                        }
                        else {
                            loaded = new ValueVote();
                        }
                    }
                    return loaded;
                }

                @Override
                public Vote getLoaded() {
                    return loaded;
                }
            };
        }
    };

    /**
     * The factory for ModelState instances.
     */
    public static final LoaderFactory<ModelState> MODEL_STATE = new LoaderFactory<>() {
        @Override
        public Loader<ModelState> createLoader() {
            return new ModelState();
        }
    };

    /**
     * The factory for UserState instances.
     */
    public static final LoaderFactory<UserState> USER_STATE = new LoaderFactory<>() {
        @Override
        public Loader<UserState> createLoader() {
            return new UserState();
        }
    };

    /**
     * The factory for User instances.
     */
    public static final LoaderFactory<User> USER = new LoaderFactory<>() {
        @Override
        public Loader<User> createLoader() {
            return new User();
        }
    };

    /**
     * The factory for SettlementRequest instances.
     */
    public static final LoaderFactory<SettlementRequest> SETTLEMENT_REQUEST = new LoaderFactory<>() {
        @Override
        public Loader<SettlementRequest> createLoader() {
            return new SettlementRequest();
        }
    };

    /**
     * The factory for OwnershipRequest instances.
     */
    public static final LoaderFactory<OwnershipRequest> OWNERSHIP_REQUEST = new LoaderFactory<>() {
        @Override
        public Loader<OwnershipRequest> createLoader() {
            return new OwnershipRequest();
        }
    };

    /**
     * The factory for UnbanRequest instances.
     */
    public static final LoaderFactory<UnbanRequest> UNBAN_REQUEST = new LoaderFactory<>() {
        @Override
        public Loader<UnbanRequest> createLoader() {
            return new UnbanRequest();
        }
    };

    /**
     * The factory for GrantedOwnership instances.
     */
    public static final LoaderFactory<GrantedOwnership> GRANTED_OWNERSHIP = new LoaderFactory<>() {
        @Override
        public Loader<GrantedOwnership> createLoader() {
            return new GrantedOwnership();
        }
    };

    /**
     * The factory for GrantedUnban instances.
     */
    public static final LoaderFactory<GrantedUnban> GRANTED_UNBAN = new LoaderFactory<>() {
        @Override
        public Loader<GrantedUnban> createLoader() {
            return new GrantedUnban();
        }
    };

    /**
     * Creates a new loader.
     * 
     * @return the created loader
     */
    Loader<D> createLoader();

}
