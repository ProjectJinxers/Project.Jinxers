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
package org.projectjinxers.util;

import java.util.Arrays;

/**
 * @author ProjectJinxers
 *
 */
public class ObjectUtility {

    public static boolean isEqual(Object o1, Object o2) {
        return o1 == o2 || o1 != null && o1.equals(o2);
    }

    public static boolean isEqual(Object o1, Object o2, Object defaultValue) {
        if (o1 == o2) {
            return true;
        }
        return isEqual(o1 == null ? defaultValue : o1, o2 == null ? defaultValue : o2);
    }

    public static boolean isEqual(long[] a1, long[] a2, long[] defaultValue) {
        if (a1 == a2) {
            return true;
        }
        return Arrays.equals(a1 == null ? defaultValue : a1, a2 == null ? defaultValue : a2);
    }

    public static boolean isNullOrEmpty(String s) {
        return s == null || s.equals("");
    }

    public static boolean isNullOrBlank(String s) {
        return s == null || s.trim().equals("");
    }

    public static long[] parseLongValues(String valuesString) throws NumberFormatException {
        String[] lines = valuesString.trim().split("\\R");
        long[] res = new long[lines.length];
        int i = 0;
        for (String line : lines) {
            res[i++] = Long.parseLong(line);
        }
        return res;
    }

    public static String createValuesString(long[] values) {
        StringBuilder sb = new StringBuilder(values.length * 20);
        boolean first = true;
        for (long value : values) {
            if (first) {
                first = false;
            }
            else {
                sb.append('\n');
            }
            sb.append(value);
        }
        return sb.toString();
    }

}
