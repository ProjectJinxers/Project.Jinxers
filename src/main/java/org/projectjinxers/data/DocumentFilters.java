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
package org.projectjinxers.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.projectjinxers.data.ProgressObserver.ProgressChangeListener;

/**
 * @author ProjectJinxers
 *
 */
public class DocumentFilters {

    public interface DocumentFilter {

        boolean accept(Document document, ProgressChangeListener listener);

    }

    public static class TextFilter implements DocumentFilter {

        private String filterQuery;
        private DocumentFilter mainFilter;

        public TextFilter(String filterQuery, DocumentFilter mainFilter) {
            this.filterQuery = filterQuery;
            this.mainFilter = mainFilter;
        }

        @Override
        public boolean accept(Document document, ProgressChangeListener listener) {
            if (mainFilter == null || mainFilter.accept(document, listener)) {
                return filterQuery == null || document.conformsToFilterQuery(filterQuery, listener);
            }
            return false;
        }

    }

    public static class SettlementRequestsOnly implements DocumentFilter {

        @Override
        public boolean accept(Document document, ProgressChangeListener listener) {
            return document.isSettlementRequested();
        }

        @Override
        public String toString() {
            return "Settlement requests";
        }

    }

    public static class SealedOnly implements DocumentFilter {

        @Override
        public boolean accept(Document document, ProgressChangeListener listener) {
            return document.isSealed();
        }

        @Override
        public String toString() {
            return "Sealed";
        }

    }

    private static Map<String, DocumentFilter> FILTERS = new LinkedHashMap<>();

    static {
        FILTERS.put(null, null);
        SettlementRequestsOnly sro = new SettlementRequestsOnly();
        FILTERS.put(sro.toString(), sro);
        SealedOnly so = new SealedOnly();
        FILTERS.put(so.toString(), so);
    }

    public static void addFilter(DocumentFilter filter) {
        FILTERS.put(filter.toString(), filter);
    }

    public static Map<String, DocumentFilter> getFilters() {
        return Collections.unmodifiableMap(FILTERS);
    }

}
