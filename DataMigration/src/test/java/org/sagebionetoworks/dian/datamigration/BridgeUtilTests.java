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

import org.sagebionetoworks.dian.datamigration.HmDataModel.*;

import org.junit.Test;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class BridgeUtilTests {

    private final String testArcId = "800000";

    @Test
    public void testGetUsersToMatch() throws MalformedURLException, IOException {
        List<HmUserData> hmUserList = new ArrayList<>();
        HmUserData hmUser2 = new HmUserData();
        hmUser2.arcId = "800001";
        hmUserList.add(hmUser2);

        HmUserData hmUser1 = new HmUserData();
        hmUser1.arcId = testArcId;
        hmUserList.add(hmUser1);

        List<BridgeUtil.BridgeUser> bridgeUserList = new ArrayList<>();
        BridgeUtil.BridgeUser bridgeUser2 = new BridgeUtil.BridgeUser();
        bridgeUser2.arcId =  "800001";
        bridgeUser2.id = "1234";
        bridgeUserList.add(bridgeUser2);

        // This one should be removed, since it does not have a matching HM user
        BridgeUtil.BridgeUser bridgeUser3 = new BridgeUtil.BridgeUser();
        bridgeUser3.arcId =  "800005";
        bridgeUser3.id = "123456";
        bridgeUserList.add(bridgeUser3);

        List<BridgeUtil.MigrationPair> migrationPairList =
                BridgeUtil.getUsersToMatch(hmUserList, bridgeUserList);

        // The first one should be in ARC ID order
        assertNotNull(migrationPairList);
        assertEquals(1, migrationPairList.size());

        // Check user 2
        assertNotNull(migrationPairList.get(0).bridgeUser);
        assertNotNull(migrationPairList.get(0).bridgeUser.id);
        assertNotNull(migrationPairList.get(0).hmUser);
        assertEquals("800001", migrationPairList.get(0).bridgeUser.arcId);
        assertEquals("1234", migrationPairList.get(0).bridgeUser.id);
        assertEquals("800001", migrationPairList.get(0).hmUser.arcId);
    }
}