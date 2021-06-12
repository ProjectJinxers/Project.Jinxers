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

import org.projectjinxers.ipld.IPLDReader;

/**
 * Factory for loaders. Those are needed when recursively loading links.
 * 
 * @author ProjectJinxers
 */
public interface LoaderFactory<D extends IPLDSerializable> {

    /**
     * The factory for Document instances.
     */
    public static final LoaderFactory<Document> DOCUMENT = new LoaderFactory<Document>() {
        @Override
        public Loader<Document> createLoader() {
            return new Loader<Document>() {

                private Document loaded;

                @Override
                public Document getOrCreateDataInstance(IPLDReader reader, Metadata metadata) {
                    if (loaded == null) {
                        if (reader.hasLinkKey("document")) {
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
    public static final LoaderFactory<Review> REVIEW = new LoaderFactory<Review>() {
        @Override
        public Loader<Review> createLoader() {
            return new Review();
        }
    };

    /**
     * Creates a new loader.
     * 
     * @return the created loader
     */
    Loader<D> createLoader();

}
