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

import org.sagebionetworks.client.exceptions.SynapseException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Main executable class for the data migration from HappyMedium to Sage Bionetworks
 */
public class DataMigration {

    private static final String USER_ARG = "user";
    private static final String DATA_ARG = "data";

    public static void main(String[] args) throws IOException, SynapseException {
        if (args.length == 0) {
            illegalArguments();
        }

        try {
            if ("user".equals(args[0])) {
                runUserMigration();
            } else if ("data".equals(args[0])) {
                runDataMigration();
            } else {
                illegalArguments();
            }
        } finally {
            // Delete all traces of the algorithm.
            // This is for enhanced data privacy,
            // to ensure user data does not remain in the environment.
            SynapseUtil.clearAllFiles();
        }
    }

    private static void illegalArguments() {
        throw new IllegalArgumentException(
                "\nError: valid argument must be provided.\n" +
                "Available options include:\n" +
                "\"" + USER_ARG + "\" to run the user migration\n" +
                "or \"" + DATA_ARG + "\" to run the data migration.");
    }

    /**
     * This function reads a ZIP of user participant files from Synapse
     * in HM's JSON export format, and creates all the users on Bridge.
     *
     * This must be run BEFORE the DataMigration executable can succeed,
     * as there will be no users to write the user reports to otherwise.
     */
    private static void runUserMigration() throws SynapseException, IOException {
        System.out.println("Beginning User Migration");

        SynapseUtil.initializeSynapse();
        SynapseUtil.downloadAndUnzipAllParticipantFiles();

        List<File> folders = new ArrayList<>();
        for (SynapseUtil.DownloadFolder downloadFolder : SynapseUtil.DownloadFolder.userFolders()) {
            folders.add(downloadFolder.unzippedFolder());
        }
        List<MigrationUtil.HmUser> hmUsers = MigrationUtil.createHmUserRaterData(folders);

        // TODO: mdephillips 9/22/21 create users on bridge
        System.out.println("Completed user migration successfully");
    }

    /**
     * This function reads daily ZIP data exports from Synapse
     * in HM's JSON export format, parses the JSON,
     * and then uploads the data to the corresponding Bridge users.
     *
     * This must be run AFTER the UserMigration functions succeeds,
     * as there will be no users to write the user reports to otherwise.
     */
    private static void runDataMigration() throws SynapseException, IOException {
        System.out.println("Beginning Data Migration");

        SynapseUtil.initializeSynapse();
        SynapseUtil.downloadAndUnzipAllUserDataFiles();
        List<MigrationUtil.HmUserData> uniqueUserData = MigrationUtil.createHmUserData(
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


