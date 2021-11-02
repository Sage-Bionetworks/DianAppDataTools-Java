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
        for (int i = 0; i < 10000; i++) {
            String password = PasswordGenerator.INSTANCE.nextPassword();
            assertNotNull(password);
            assertEquals(9, password.length());
            assertTrue(isValidBridgePassword(password));
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
