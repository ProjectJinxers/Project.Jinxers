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

import java.util.Date;

/**
 * Interface for all objects that can be voted for.
 * 
 * @author ProjectJinxers
 */
public interface Votable extends IPLDSerializable {

    /**
     * @return whether or not the voting must be anonymous
     */
    boolean isAnonymous();

    /**
     * @return the seed for generating hashes (input for a public algorithm with secret inputs)
     */
    int getHashSeed();

    /**
     * @return the deadline for the voting
     */
    Date getDeadline();

    /**
     * @return a new empty Vote instance (provisional method, will be changed)
     */
    Vote createVote();

    /**
     * @return all values that can be voted for (clear text, no hashes)
     */
    Object[] getAllValues();

    /**
     * @return true iff the parameter has won the voting (provisional method, will be changed)
     */
    boolean checkWinner();

}
