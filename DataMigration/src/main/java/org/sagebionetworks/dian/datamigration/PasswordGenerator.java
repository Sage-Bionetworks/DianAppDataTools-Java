/*
 * BSD 3-Clause License
 *
 * Copyright 2021  Sage Bionetworks. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1.  Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2.  Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3.  Neither the name of the copyright holder(s) nor the names of any contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission. No license is granted to the trademarks of
 * the copyright holders even if such marks are included in this software.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.sagebionetworks.dian.datamigration;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;

import java.security.SecureRandom;
import java.util.Iterator;
import java.util.Set;

public class PasswordGenerator {
    public static final PasswordGenerator INSTANCE = new PasswordGenerator();

    // DIAN passwords are 9 digits long
    public static final int DEFAULT_PASSWORD_LENGTH = 9;

    private static final SecureRandom RANDOM = new SecureRandom();

    // Bridge password must be at least 8 characters;
    // Bridge password must contain at least one uppercase letter (a-z)
    // Letter "O" has been removed, as it shows up too similar to number "0" on bridge
    // Letter "I" has been removed, as it shows up too similar to letter "l" on bridge
    public static final String UPPERCASE = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    // Bridge password must contain at least one lowercase letter (a-z)
    // Letter "l" has been removed, as it shows up too similar to letter "I" on bridge
    public static final String LOWERCASE = "abcdefghijkmnopqrstuvwxyz";
    // "0" has been removed, as it shows up too similar to letter "O" on bridge
    public static final String NUMERIC = "123456789";
    // Bridge password must contain at least one symbol ( !"#$%&'()*+,-./:;<=>?@[\]^_`{|}~ );
    // This subset of symbols was chosen because they would be easier to communicate over the phone
    // As well as removing some that the older participants wouldn't understand
    public static final String SYMBOLIC = "&.!?";

    private static final char[] UPPERCASE_ARRAY = UPPERCASE.toCharArray();
    private static final char[] LOWERCASE_ARRAY = LOWERCASE.toCharArray();
    private static final char[] NUMERIC_ARRAY = NUMERIC.toCharArray();
    private static final char[] SYMBOLIC_ARRAY = SYMBOLIC.toCharArray();
    private static final char[] ALPHANUMERIC_ARRAY = (UPPERCASE+LOWERCASE+NUMERIC).toCharArray();

    private PasswordGenerator() {
    }

    public String nextPassword() {
        return nextPassword(DEFAULT_PASSWORD_LENGTH);
    }

    public String nextPassword(int length) {
        // Looping until we have 4 indices can take a long time or hang if length is <4, prevent either
        Preconditions.checkArgument(length >= 4);

        final char[] buffer = new char[length];
        for (int i = 0; i < buffer.length; ++i) {
            buffer[i] = ALPHANUMERIC_ARRAY[RANDOM.nextInt(ALPHANUMERIC_ARRAY.length)];
        }
        // ensure that all character types are always present
        Iterator<Integer> indices = getFourUniqueIntegers(length).iterator();
        replace(buffer, UPPERCASE_ARRAY, indices.next());
        replace(buffer, LOWERCASE_ARRAY, indices.next());
        replace(buffer, NUMERIC_ARRAY, indices.next());
        replace(buffer, SYMBOLIC_ARRAY, indices.next());
        return new String(buffer);
    }

    private Set<Integer> getFourUniqueIntegers(int max) {
        Set<Integer> set = Sets.newHashSetWithExpectedSize(4);
        while (set.size() < 4) {
            set.add(RANDOM.nextInt(max));
        }
        return set;
    }

    /** Insert a randomly selected character at position in the array */
    private void replace(char[] buffer, char[] charArray, int pos) {
        buffer[pos] = charArray[RANDOM.nextInt(charArray.length)];
    }
}