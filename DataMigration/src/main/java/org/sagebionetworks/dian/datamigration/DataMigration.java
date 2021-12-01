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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.sagebionetworks.client.exceptions.SynapseException;
import org.sagebionetworks.dian.datamigration.HmDataModel.HmUser;
import org.sagebionetworks.dian.datamigration.HmDataModel.HmUserData;

/**
 * Main executable class for the data migration from HappyMedium to Sage Bionetworks
 */
public class DataMigration {

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

        // Initialize Sage APIs
        SynapseUtil.initializeSynapse();
        BridgeJavaSdkUtil.initialize();

        // Download the participant and data files
        SynapseUtil.downloadAndUnzipAllUserDataFiles();
        SynapseUtil.downloadAndUnzipAllParticipantFiles();

        // Create the data model from the participant files
        List<HmUser> userList = MigrationUtil.createHmUserRaterData(Arrays.asList(
                SynapseUtil.DownloadFolder.hasd.unzippedFolder(),
                SynapseUtil.DownloadFolder.exr.unzippedFolder()));

        // Create the data model from the data files
        List<HmUserData> userDataList = MigrationUtil.createHmUserData(
                SynapseUtil.DownloadFolder.test_session.unzippedFolder(),
                SynapseUtil.DownloadFolder.test_session_schedule.unzippedFolder(),
                SynapseUtil.DownloadFolder.wake_sleep_schedule.unzippedFolder());

        // Migrate all users and their data
        List<Exception> exceptions = new ArrayList<Exception>();
        try {
	        for (HmUser user: userList) {
	            HmUserData data = MigrationUtil.findMatchingData(user, userDataList);
	            BridgeJavaSdkUtil.migrateUser(user, data);
	        }
        } catch (Exception e) {
        	exceptions.add(e);
        }
        if (!exceptions.isEmpty()) {
        	// throw one big exception
        	StringBuilder cumulativeMessages = new StringBuilder();
        	for (Exception e : exceptions) {
        		String m = e.getMessage();
        		if (m!=null && m.length()>0) {
        			cumulativeMessages.append(m+"\n");
        		}
        	}
        	throw new RuntimeException(cumulativeMessages.toString());
        }

        System.out.println("Completed data migration successfully");
    }
}


