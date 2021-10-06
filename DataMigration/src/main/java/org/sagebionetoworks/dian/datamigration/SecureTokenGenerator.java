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
package org.sagebionetoworks.dian.datamigration;

import static com.google.common.base.Preconditions.checkArgument;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.Random;

/**
 * This code came from Stack Exchange, with some changes to make it thread-safe. Unfortunately I
 * then lost the reference to the page I took it from. Cleaned up to our formatting standards.
 *
 *
 */
public class SecureTokenGenerator {

    private static final String UPPERCASE_ALPHA = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWERCASE_ALPHA = "abcdefghijklmnopqrstuvwxyz";
    private static final String NUMERIC = "0123456789";
    private static final String SPECIAL = "~!@#$%^&*-_=+?";

    private static final String ALPHANUMERIC = UPPERCASE_ALPHA + LOWERCASE_ALPHA + NUMERIC;
    private static final String PASSWORD = ALPHANUMERIC + SPECIAL;

    public static final SecureTokenGenerator INSTANCE = new SecureTokenGenerator();

    public static final SecureTokenGenerator PHONE_CODE_INSTANCE =
            new SecureTokenGenerator(6, new SecureRandom(), NUMERIC);

    public static final SecureTokenGenerator NAME_SCOPE_INSTANCE =
            new SecureTokenGenerator(5, new SecureRandom(), ALPHANUMERIC);

    public static final SecureTokenGenerator BRIDGE_PASSWORD =
            new SecureTokenGenerator(20, new SecureRandom(), PASSWORD);

    private final Random random;
    private final char[] characters;
    private final int length;

    private SecureTokenGenerator(int length, Random random, String characters) {
        checkArgument(length > 1);
        checkArgument(characters != null && characters.length() >= 2);

        this.length = length;
        this.random = Objects.requireNonNull(random);
        this.characters = characters.toCharArray();
    }

    /**
     * Create session identifiers. This is 4.36e+37 unique values, which is enough
     * for a good session key.
     */
    private SecureTokenGenerator() {
        this(21, new SecureRandom(), ALPHANUMERIC);
    }

    public String nextToken() {
        final char[] buffer = new char[length];
        for (int i = 0; i < buffer.length; ++i) {
            buffer[i] = characters[random.nextInt(characters.length)];
        }
        return new String(buffer);
    }

    /**
     * Guaranteed to generate a random password string compatible with Bridge
     * @return a random password string compatible with Bridge
     */
    public String nextBridgePassword() {
        String token = nextToken();
        while(!isValidBridgePassword(token)) {
            token = nextToken();
        }
        return token;
    }

    private boolean isValidBridgePassword(String password) {
        boolean containsUppercase = false;
        boolean containsLowercase = false;
        boolean containsNumeric = false;
        boolean containsSpecial = false;

        for (int i = 0; i < password.length(); i++) {
            String character = Character.toString(password.charAt(i));
            containsUppercase |= UPPERCASE_ALPHA.contains(character);
            containsLowercase |= LOWERCASE_ALPHA.contains(character);
            containsNumeric |= NUMERIC.contains(character);
            containsSpecial |= SPECIAL.contains(character);
        }

        return containsUppercase && containsLowercase && containsNumeric && containsSpecial;
    }
}
