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
package org.projectjinxers.controller;

/**
 * Context class for integration test cases. There is no connection to IPFS, no mocking required.
 * 
 * @author ProjectJinxers
 */
public class TestIPLDContext extends IPLDContext {

    /**
     * Constructor.
     * 
     * @param access the test IPFS access
     * @param in     the encoding for submitting data to memory (currently ignored, always JSON)
     * @param out    the encoding for saving data in and reading data from memory (currently ignored, always JSON)
     * @param eager  indicates whether or not links are to be resolved instantly (as opposed to on-demand)
     */
    public TestIPLDContext(TestIPFSAccess access, IPLDEncoding in, IPLDEncoding out, boolean eager) {
        super(access, in, out, eager);
    }

}
