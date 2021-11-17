package org.sagebionetworks.dian.datamigration;

import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PasswordGeneratorTests {
    @Test
    public void test_createBridgePassword() throws IOException {
        // Test 10000 bridge passwords for validity
        Map<Integer, Integer> counts = new HashMap<>();
        for (int i = 0; i < 9; i++) {
            counts.put(i, 0);
        }

        for (int i = 0; i < 10000; i++) {
            String password = PasswordGenerator.INSTANCE.nextPassword();
            assertNotNull(password);
            assertEquals(9, password.length());
            assertTrue(isValidBridgePassword(password));

            // Add to the counts of where each symbol is
            for (int j = 0; j < PasswordGenerator.SYMBOLIC.length(); j++) {
                int symbolIdx =  password.indexOf(PasswordGenerator.SYMBOLIC.charAt(j));
                if (symbolIdx >= 0) {
                    counts.put(symbolIdx, counts.get(symbolIdx) + 1);
                }
            }
        }

        for (int i = 0; i < 9; i++) {
            // Make sure that the distribution has at least 1% of the distribution
            assertTrue(counts.get(i) > 10);
        }
    }

    public boolean isValidBridgePassword(String password) {
        boolean containsUppercase = false;
        boolean containsLowercase = false;
        boolean containsNumeric = false;
        boolean containsSpecial = false;

        for (int i = 0; i < password.length(); i++) {
            String character = Character.toString(password.charAt(i));
            containsUppercase = containsUppercase || PasswordGenerator.UPPERCASE.contains(character);
            containsLowercase = containsLowercase || PasswordGenerator.LOWERCASE.contains(character);
            containsNumeric = containsNumeric || PasswordGenerator.NUMERIC.contains(character);
            containsSpecial = containsSpecial || PasswordGenerator.SYMBOLIC.contains(character);
        }

        return containsUppercase && containsLowercase && containsNumeric && containsSpecial;
    }

}
