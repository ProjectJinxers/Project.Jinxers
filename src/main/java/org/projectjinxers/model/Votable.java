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

import org.projectjinxers.config.SecretConfig;
import org.projectjinxers.controller.ValidationContext;
import org.projectjinxers.controller.ValidationException;

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
    long getHashSeed();

    /**
     * @return the deadline for the voting
     */
    Date getDeadline();

    /**
     * @return a new empty Vote instance (provisional method, will be changed)
     */
    Vote createVote(byte[] invitationKey, int valueIndex, long seed, int obfuscationVersion, SecretConfig secretConfig);

    /**
     * @param forDisplay indicates whether or not the values will be displayed to select from (true) or will be used
     *                   internally (false, in which case it might just be the indices)
     * @return all values that can be voted for (clear text, no hashes)
     */
    Object[] getAllValues(boolean forDisplay);

    byte[][] getAllValueHashBases();

    int getPlainTextValueIndex(Object value);

    /**
     * Checks if the parameter has won the voting. If this is not the case, a {@link ValidationException} will be
     * thrown.
     * 
     * @param value  the expected winner
     * @param counts the actual counts (after tally)
     */
    void expectWinner(Object value, int[] counts, long seed);

    void validate(Voting voting, ValidationContext validationContext);

}
