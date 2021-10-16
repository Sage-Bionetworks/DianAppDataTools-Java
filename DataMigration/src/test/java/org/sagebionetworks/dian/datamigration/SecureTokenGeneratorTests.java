package org.sagebionetworks.dian.datamigration;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SecureTokenGeneratorTests {
    @Test
    public void test_createBridgePassword() throws IOException {
        // Test 10000 bridge passwords for validity
        for (int i = 0; i < 10000; i++) {
            String password = SecureTokenGenerator.BRIDGE_PASSWORD.nextBridgePassword();
            assertNotNull(password);
            assertEquals(20, password.length());
            assertTrue(SecureTokenGenerator.BRIDGE_PASSWORD.isValidBridgePassword(password));
        }
    }
}
