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

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BridgeTests {

    private String sessionToken = "";

    // There is no ARC ID in Happy Medium or Sage with this ID
    private String testArcId = "800000";

    @Before
    public void before() throws MalformedURLException, IOException {
        sessionToken = BridgeUtil.authenticate();
        assertNotNull(sessionToken);
    }

    @Test
    public void testShouldMigrateUser() throws MalformedURLException, IOException {
        // Create new user
        BridgeUtil.BridgeUserData newUser = BridgeUtil.createUser(sessionToken, testArcId);
        assertNotNull(newUser);
        assertNotNull(newUser.id);
        assertEquals(testArcId, newUser.arcId);
        assertEquals(testArcId, newUser.externalId);
        assertNotNull(newUser.attributes);
        assertEquals(testArcId, newUser.attributes.ARC_ID);

        // New user has not signed into bridge yet, they should migrate
        assertTrue(BridgeUtil.shouldMigrate(sessionToken, newUser));

        // Write report showing that the user should still migrate
        BridgeUtil.MigratedStatus migratedStatus = new BridgeUtil.MigratedStatus();
        migratedStatus.status = false;
        String json = new ObjectMapper().writer().writeValueAsString(migratedStatus);
        BridgeUtil.writeSingletonReport(sessionToken, newUser.id,
                BridgeUtil.MIGRATED_REPORT_ID,json);

        // New user has not signed into bridge yet, they should migrate
        assertTrue(BridgeUtil.shouldMigrate(sessionToken, newUser));

        // Write report showing that the user should stop migrating
        // This will be what the app writes when it logs in
        migratedStatus.status = true;
        json = new ObjectMapper().writer().writeValueAsString(migratedStatus);
        BridgeUtil.writeSingletonReport(sessionToken, newUser.id,
                BridgeUtil.MIGRATED_REPORT_ID,json);

        // New user should have migration status set to true, so shouldMigrate is false
        assertFalse(BridgeUtil.shouldMigrate(sessionToken, newUser));

        // Delete user
        BridgeUtil.MigrationPair migrationPair = new BridgeUtil.MigrationPair();
        migrationPair.bridgeUser = newUser;
        BridgeUtil.deleteUserList(sessionToken, Collections.singletonList(migrationPair));
    }

    @Test
    public void testCreateAndDeleteUser() throws MalformedURLException, IOException {
        // Check the user does not already exist
        BridgeUtil.UserList userList = BridgeUtil.getAllUsers(sessionToken);
        assertNotNull(userList);
        assertNotNull(userList.items);
        assertFalse(userList.items.isEmpty());
        assertFalse(containsArcId(testArcId, userList));

        // Create new user
        BridgeUtil.BridgeUserData newUser = BridgeUtil.createUser(sessionToken, testArcId);
        assertNotNull(newUser);
        assertNotNull(newUser.id);
        assertEquals(testArcId, newUser.arcId);
        assertEquals(testArcId, newUser.externalId);
        assertNotNull(newUser.attributes);
        assertEquals(testArcId, newUser.attributes.ARC_ID);

        // Check that the user is added
        userList = BridgeUtil.getAllUsers(sessionToken);
        assertNotNull(userList);
        assertNotNull(userList.items);
        assertFalse(userList.items.isEmpty());
        assertTrue(containsArcId(testArcId, userList));

        // Delete user
        BridgeUtil.MigrationPair migrationPair = new BridgeUtil.MigrationPair();
        migrationPair.bridgeUser = newUser;
        BridgeUtil.deleteUserList(sessionToken, Collections.singletonList(migrationPair));

        // Check that the user is deleted
        userList = BridgeUtil.getAllUsers(sessionToken);
        assertNotNull(userList);
        assertNotNull(userList.items);
        assertFalse(userList.items.isEmpty());
        assertFalse(containsArcId(testArcId, userList));
    }

    @Test
    public void testReadAndWriteSingletonReport() throws MalformedURLException, IOException {
        // Create new user
        BridgeUtil.BridgeUserData newUser = BridgeUtil.createUser(sessionToken, testArcId);
        assertNotNull(newUser);
        assertNotNull(newUser.id);
        assertEquals(testArcId, newUser.arcId);
        assertEquals(testArcId, newUser.externalId);
        assertNotNull(newUser.attributes);
        assertEquals(testArcId, newUser.attributes.ARC_ID);

        // Verify the new user has no completed test list report
        SynapseUtil.CompletedTestList completedTestList = BridgeUtil.getSingletonReport(
                sessionToken, BridgeUtil.COMPLETED_TESTS_REPORT_ID,
                newUser.id, SynapseUtil.CompletedTestList.class);
        assertNull(completedTestList);

        // Write CompletedTest report
        completedTestList = new SynapseUtil.CompletedTestList();
        List<SynapseUtil.CompletedTest> testList = new ArrayList<>();
        testList.add(createCompletedTest(0, 0, 0));
        testList.add(createCompletedTest(0, 0, 1));
        completedTestList.completed = testList;
        String json = new ObjectMapper().writer().writeValueAsString(completedTestList);
        BridgeUtil.writeSingletonReport(sessionToken, newUser.id,
                BridgeUtil.COMPLETED_TESTS_REPORT_ID,json);

        // Read CompletedTest report from bridge
        SynapseUtil.CompletedTestList actualCompletedTestList = BridgeUtil.getSingletonReport(
                sessionToken, BridgeUtil.COMPLETED_TESTS_REPORT_ID,
                newUser.id, SynapseUtil.CompletedTestList.class);
        assertNotNull(actualCompletedTestList);
        assertNotNull(actualCompletedTestList.completed);
        assertEquals(2, actualCompletedTestList.completed.size());
        assertTrue(isCompletedTestEqual(
                completedTestList.completed.get(0),
                actualCompletedTestList.completed.get(0)));
        assertTrue(isCompletedTestEqual(
                completedTestList.completed.get(1),
                actualCompletedTestList.completed.get(1)));

        // Delete user
        BridgeUtil.MigrationPair migrationPair = new BridgeUtil.MigrationPair();
        migrationPair.bridgeUser = newUser;
        BridgeUtil.deleteUserList(sessionToken, Collections.singletonList(migrationPair));
    }

    @Test
    public void testGetUsers() throws MalformedURLException, IOException {
        BridgeUtil.UserList userList = BridgeUtil.getAllUsers(sessionToken);
        assertNotNull(userList);
        assertNotNull(userList.items);
        assertFalse(userList.items.isEmpty());
    }

    @Test
    public void testCreateUsersToMatch() throws MalformedURLException, IOException {
        List<SynapseUtil.HmUserData> hmUserList = new ArrayList<>();
        SynapseUtil.HmUserData hmUser2 = new SynapseUtil.HmUserData();
        hmUser2.arcId = "800001";
        hmUserList.add(hmUser2);

        SynapseUtil.HmUserData hmUser1 = new SynapseUtil.HmUserData();
        hmUser1.arcId = testArcId;
        hmUserList.add(hmUser1);

        List<BridgeUtil.BridgeUserData> bridgeUserList = new ArrayList<>();
        BridgeUtil.BridgeUserData bridgeUser2 = new BridgeUtil.BridgeUserData();
        bridgeUser2.arcId =  "800001";
        bridgeUser2.id = "1234";
        bridgeUserList.add(bridgeUser2);

        // This one should be removed, since it does not have a matching HM user
        BridgeUtil.BridgeUserData bridgeUser3 = new BridgeUtil.BridgeUserData();
        bridgeUser3.arcId =  "800005";
        bridgeUser3.id = "123456";
        bridgeUserList.add(bridgeUser3);

        List<BridgeUtil.MigrationPair> migrationPairList =
                BridgeUtil.createUsersToMatch(sessionToken, hmUserList, bridgeUserList);

        // The first one should be in ARC ID order
        assertNotNull(migrationPairList);
        assertEquals(2, migrationPairList.size());

        // Check user 1
        assertNotNull(migrationPairList.get(0).bridgeUser);
        assertNotNull(migrationPairList.get(0).bridgeUser.id);
        assertNotNull(migrationPairList.get(0).hmUser);
        assertEquals(testArcId, migrationPairList.get(0).bridgeUser.arcId);
        assertEquals(testArcId, migrationPairList.get(0).hmUser.arcId);

        // Check user 2
        assertNotNull(migrationPairList.get(1).bridgeUser);
        assertNotNull(migrationPairList.get(1).bridgeUser.id);
        assertNotNull(migrationPairList.get(1).hmUser);
        assertEquals("800001", migrationPairList.get(1).bridgeUser.arcId);
        assertEquals("1234", migrationPairList.get(1).bridgeUser.id);
        assertEquals("800001", migrationPairList.get(1).hmUser.arcId);

        // Delete user
        BridgeUtil.deleteUserList(sessionToken, Collections.singletonList(migrationPairList.get(0)));
    }

    private boolean isCompletedTestEqual(
            SynapseUtil.CompletedTest test0, SynapseUtil.CompletedTest test1) {

        return test0.week == test1.week &&
                test0.day == test1.day &&
                test0.session == test1.session &&
                test0.completedOn == test1.completedOn;
    }

    private SynapseUtil.CompletedTest createCompletedTest(int week, int day, int session) {
        SynapseUtil.CompletedTest test = new SynapseUtil.CompletedTest();
        test.week = week;
        test.day = day;
        test.session = session;
        test.completedOn = (double)System.currentTimeMillis() / 1000.0;
        return test;
    }

    private boolean containsArcId(String arcId, BridgeUtil.UserList userList) {
        List<String> arcIdList = new ArrayList<>();
        for (BridgeUtil.BridgeUserData bridgeUser: userList.items) {
            if (bridgeUser.arcId != null) {
                arcIdList.add(bridgeUser.arcId);
            }
        }
        return arcIdList.contains(arcId);
    }
}