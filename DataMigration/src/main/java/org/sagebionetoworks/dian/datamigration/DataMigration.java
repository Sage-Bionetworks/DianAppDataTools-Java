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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This executable file reads daily ZIP data exports from Synapse
 * in HM's JSON export format, parses the JSON,
 * and then uploads the data to the corresponding Bridge users.
 *
 * This must be run AFTER the UserMigration executable to succeed.
 */
public class DataMigration {

    public static void main(String[] args) {
        System.out.println("Beginning Data Migration");

        try {
            SynapseUtil.initializeSynapse();

            SynapseUtil.DianFiles files = SynapseUtil.findRelevantFiles();
            SynapseUtil.DianFileFolders folders = SynapseUtil.downloadDianFiles(files);

            List<SynapseUtil.HmUserData> uniqueUserData = SynapseUtil.createHmUserData(folders);

            String sessionToken = BridgeUtil.authenticate();
            BridgeUtil.UserList bridgeUserList = BridgeUtil.getAllUsers(sessionToken);

            // Match HM users to existing Bridge users, if no users
            // are found to match, you probably need to run UserMigration first
            List<BridgeUtil.MigrationPair> usersToMigrate =
                    BridgeUtil.getUsersToMatch(uniqueUserData, bridgeUserList.items);

            BridgeUtil.writeAllUserReports(sessionToken, usersToMigrate);

            // Delete all users after creating them
            BridgeUtil.deleteUserList(sessionToken, usersToMigrate);

            System.out.println("Completed data migration\nMigrated " + uniqueUserData.size() + " accounts.");

        } catch (IOException | SynapseException e) {
            System.out.println("Failed to migrate data " + e.getLocalizedMessage());
        }

        // Delete all traces of the algorithm
        SynapseUtil.clearAllFiles();
    }
}


