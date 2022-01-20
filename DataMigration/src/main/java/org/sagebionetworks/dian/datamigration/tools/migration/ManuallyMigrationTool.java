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

package org.sagebionetworks.dian.datamigration.tools.migration;

import org.sagebionetworks.client.exceptions.SynapseException;
import org.sagebionetworks.dian.datamigration.BridgeJavaSdkUtil;
import org.sagebionetworks.dian.datamigration.HmDataModel.HmUser;
import org.sagebionetworks.dian.datamigration.HmDataModel.HmUserData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Main executable class for the data migration from HappyMedium to Sage Bionetworks
 */
public class ManuallyMigrationTool {

    public static void main(String[] args) throws IOException {
        if (args.length < 4) {
            throw new IllegalStateException("Please provide valid parameters in the form of:\n" +
                    "java -jar NameOfJar.jar BRIDGE_EMAIL BRIDGE_PASSWORD BRIDGE_ID DEVICE_ID_TO_MIGRATE\n\n" +
                    "Please note that Synapse accounts are not compatible with this program.");
        }

        BridgeJavaSdkUtil.initialize(args[0], args[1], args[2]);
        BridgeJavaSdkUtil.manuallyMigrateUser(args[3]);
    }
}


