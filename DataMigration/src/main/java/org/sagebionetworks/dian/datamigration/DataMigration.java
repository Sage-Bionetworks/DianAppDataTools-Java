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

import org.sagebionetworks.client.exceptions.SynapseException;
import org.sagebionetworks.dian.datamigration.HmDataModel.*;

import java.io.IOException;
import java.util.List;

/**
 * Main executable class for the data migration from HappyMedium to Sage Bionetworks
 */
public class DataMigration {

    /**
     * This function reads daily ZIP data exports from Synapse
     * in HM's JSON export format, parses the JSON,
     * and then uploads the data to the corresponding Bridge users.
     *
     * This must be run AFTER the UserMigration functions succeeds,
     * as there will be no users to write the user reports to otherwise.
     */
    public static void main(String[] args) throws IOException, SynapseException {
        try {
             runDataMigration();
        } finally {
            // Delete all traces of the algorithm.
            // This is for enhanced data privacy,
            // to ensure user data does not remain in the environment.
            SynapseUtil.clearAllFiles();
        }
    }

    private static void runDataMigration() throws SynapseException, IOException {
        System.out.println("Beginning Data Migration");

        // TODO: mdephillips 10/2/21 testing docker build, uncomment after
        SynapseUtil.initializeSynapse();
        SynapseUtil.downloadAndUnzipAllUserDataFiles();
        List<HmUserData> uniqueUserData = MigrationUtil.createHmUserData(
                SynapseUtil.DownloadFolder.test_session.unzippedFolder(),
                SynapseUtil.DownloadFolder.test_session_schedule.unzippedFolder(),
                SynapseUtil.DownloadFolder.wake_sleep_schedule.unzippedFolder());

        String sessionToken = BridgeUtil.authenticate();
        BridgeUtil.UserList bridgeUserList = BridgeUtil.getAllUsers(sessionToken);

        // Match HM users to existing Bridge users, if no users
        // are found to match, you probably need to run UserMigration first
        List<BridgeUtil.MigrationPair> usersToMigrate =
                BridgeUtil.getUsersToMatch(uniqueUserData, bridgeUserList.items);

        // TODO: mdephillips 9/22/21 write all user reports to bridge,
        // TODO: mdephillips 9/22/21 holding off for now until user migration algo is complete
        // BridgeUtil.writeAllUserReports(sessionToken, usersToMigrate);

        System.out.println("Completed data migration successfully");
    }
}


