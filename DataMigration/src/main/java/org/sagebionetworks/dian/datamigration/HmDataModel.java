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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.sagebionetworks.dian.datamigration.MigrationUtil.ERROR_STUDY_ID;
import static org.sagebionetworks.dian.datamigration.MigrationUtil.NO_DEVICE_ID;

/**
 * This file contains all the data models used by HappyMedium,
 * as well as the data models we use to transfer HM users
 * over to Bridge server.
 */
public class HmDataModel {

    public static final long NO_DEVICE_ID_CREATED_ON = 0;
    // Device IDs cannot meet the bridge password requirements alone, so append this to the end
    public static final String ARC_PASSWORD_REQUIREMENTS = "Arc#";

    /**
     * This class is used to compile the need to know data
     * about a Happy Medium user, all in one data class
     */
    public static class HmUser {
        // Unique 6 digit code used to identify a user
        public String arcId;
        // External ID for the account
        public String externalId;
        // Password for account
        public String password;
        // This is the Bridge sub-study identifier
        public String studyId;
        // Non-null in the case of studyId EXR, null for External ID users
        public String phone;
        // Only used in QA, used to track a user's testing name
        public String name;
        // A unique UUID created when a user first signed up in Happy Medium's app.
        // This is only available to the user and HM's servers.
        // We use it as a one-time use activation code to transfer the user's data.
        public String deviceId;
        // When the deviceId was created on HM's server.
        public long deviceIdCreatedAt;
        // The site location managing the user.
        // For HASD, this is always Marisol at WashU.
        // For DIAN_OBS, this will be whichever university or organization running the sub-study
        public TableRow.SiteLocation siteLocation;
        // This is the individual who helped the user on-board on the app.
        public TableRow.Rater rater;
        // These are the notes associated with a user
        public String notes;

        public HmUser() {}

        public HmUser(TableRow.Participant participant,
                      TableRow.Rater rater,
                      TableRow.SiteLocation site,
                      TableRow.ParticipantNotes notes,
                      TableRow.ParticipantPhone phone,
                      TableRow.ParticipantDeviceId participantDeviceId) {

            this.arcId = MigrationUtil.fixParticipantId(participant.participant_id);

            // If the rater is null at this point,
            // that means HM has created the user,
            // but they have not yet signed in.
            // Therefore, there is nothing to migrate.
            this.rater = rater;

            // HASD users do not have phone numbers,
            // they only have Arc ID, and their pw is their RaterID
            if (phone != null) {
                this.phone = phone.phone;
            }

            // This is only used in Sage QA,
            // HM does not store a user's name
            if (participant.name != null) {
                this.name = participant.name;
            }

            // Assign notes or empty string
            if (notes != null) {
                this.notes = notes.note;
            }

            // In this case, there is nothing we can do for migrating the user.
            // They are an invalid user that does not belong to a site yet.
            // Send them to the Happy Medium Error sub-study for tracking purposes.
            if (site == null || site.name == null) {
                String errorNote = " Could not find site location ";
                System.out.println(errorNote + " for user " + this.arcId);
                if (this.notes != null) {
                    errorNote = this.notes + errorNote;
                }

                this.studyId = ERROR_STUDY_ID;
                this.externalId = this.arcId;
                this.password = PasswordGenerator.INSTANCE.nextPassword();
                if (participantDeviceId == null ) {
                    this.deviceId = NO_DEVICE_ID;
                    this.deviceIdCreatedAt = NO_DEVICE_ID_CREATED_ON;
                } else {
                    this.deviceId = participantDeviceId.device_id;
                    this.deviceIdCreatedAt = Long.parseLong(participantDeviceId.created_at);
                }
                this.notes = errorNote;
                return;
            }

            site.name = MigrationUtil.bridgifySiteName(site.name);
            // We can assign these for the remaining cases
            this.studyId = site.name;
            this.siteLocation = site;

            // In this case, a user has been created for a site location,
            // but that user has not signed in yet.
            // In this case, make a new account that the site can use later.
            if (participantDeviceId == null) {
                System.out.println("Unused user for site " + this.arcId);
                this.deviceId = NO_DEVICE_ID;
                this.deviceIdCreatedAt = NO_DEVICE_ID_CREATED_ON;
                this.externalId = this.arcId;
                this.password = PasswordGenerator.INSTANCE.nextPassword();
                return;
            }

            // In this case, the user has a valid device-id,
            // which means they have been using the HappyMedium app.
            // We need to create a temporary data holding account that only they can access to.
            // When they update to the Sage Bridge app, they can use their
            // device-id to download their data and create a new account on Bridge.
            System.out.println("Migrating user account as device-id " + this.arcId);
            this.deviceId = participantDeviceId.device_id;
            this.deviceIdCreatedAt = Long.parseLong(participantDeviceId.created_at);
            this.externalId = this.deviceId;
            this.password = this.deviceId + ARC_PASSWORD_REQUIREMENTS;
        }
    }

    /**
     * A mapping of Arc ID to a user's data
     */
    public static class HmUserData {
        public String arcId;
        // The list of completed tests the user has done
        public HmDataModel.CompletedTestList completedTests;
        // The most recent wake sleep schedule the user has completed
        public Path wakeSleepSchedule;
        // The most recent test session schedule the user has completed
        public Path testSessionSchedule;
    }

    /**
     * Classes used to parse participant data exported as JSON
     * These classes were exported from a SQL database, so many
     * of them are table row ID references, instead of class object references.
     */
    public static class TableRow {

        public static <T> T parseTableRow(
                ObjectMapper mapper, Path path, Class<T> valueType) throws IOException {
            if (path == null) {
                return null;
            }
            try (InputStream is = Files.newInputStream(path)) {
                T retVal = mapper.readValue(is, valueType);
                System.out.println("Successfully parsed " + path.getFileName().toString());
                return retVal;
            }
        }

        public static SiteLocation findSiteLocation(
                String participantTableId, ParticipantSiteLocation[] siteLocMap, SiteLocation[] sites) {

            for (ParticipantSiteLocation siteLocMapping: siteLocMap) {
                if (siteLocMapping.participant != null &&
                        siteLocMapping.participant.equals(participantTableId)) {
                    String siteTableId = siteLocMapping.site_location;
                    for (SiteLocation siteMatch: sites) {
                        if (siteMatch.id != null && siteMatch.id.equals(siteTableId)) {
                            return siteMatch;
                        }
                    }
                }
            }
            return null;
        }

        public static Participant findParticipant(
                String participantTableId, Participant[] participants) {

            for (Participant participant: participants) {
                if (participant.id != null && participant.id.equals(participantTableId)) {
                    return participant;
                }
            }
            return null;
        }

        public static Rater findParticipantRater(
                String participantTableId, ParticipantRater[] participantRaters, Rater[] raters) {
            for (ParticipantRater rater: participantRaters) {
                if (rater.participant != null && rater.participant.equals(participantTableId)) {
                    String raterTableId = rater.registered_by;
                    for (Rater raterMatch: raters) {
                        if (raterMatch.id != null && raterMatch.id.equals(raterTableId)) {
                            return raterMatch;
                        }
                    }
                }
            }
            return null;
        }

        public static ParticipantPhone findParticipantPhone(
                String participantTableId, ParticipantPhone[] phoneList) {

            for (ParticipantPhone phone: phoneList) {
                if (phone != null && phone.participant_id.equals(participantTableId)) {
                    return phone;
                }
            }
            return null;
        }

        public static ParticipantNotes findParticipantNotes(
                String participantTableId, ParticipantNotes[] notesList) {

            for (ParticipantNotes note: notesList) {
                if (note != null && note.participant.equals(participantTableId)) {
                    return note;
                }
            }
            return null;
        }

        public static ParticipantDeviceId findParticipantDeviceId(
                String participantTableId, ParticipantDeviceId[] deviceIdList) {

            for (ParticipantDeviceId deviceId: deviceIdList) {
                if (deviceId != null && deviceId.participant.equals(participantTableId)) {
                    return deviceId;
                }
            }
            return null;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class SiteLocation {
            // Table row ID
            public String id;
            // Site location name
            public String name;
            public String contact_phone;
            public String contact_email;

            public SiteLocation() {}
            public SiteLocation(String id, String name) {
                this.id = id;
                this.name = name;
            }

            @Override
            public boolean equals(Object o) {
                if (o instanceof SiteLocation) {
                    return id != null && id.equals(((SiteLocation)o).id);
                }
                return false;
            }

            @Override
            public int hashCode() {
                return id.hashCode();
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Participant {
            // Table row ID
            public String id;
            // This participant_id is actually the user's ARC ID
            public String participant_id;
            // Only used in QA data sets
            public String name;
            // The table row id of the study
            // There are three studies we are concerned about
            // Map Arc (HASD), Dian Arc (EXR), and Dian Obs (EXR)
            public String study_id;

            public Participant() {}
            public Participant(String tableId, String arcId) {
                this.id = tableId;
                this.participant_id = arcId;
            }
        }

        public static String STUDY_ID_DIAN_ARC_EXR = "1";
        public static String STUDY_ID_MAP_ARC_HASD = "2";
        public static String STUDY_ID_DIAN_OBS_EXR = "3";

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ParticipantSiteLocation {
            // Table row ID
            public String id;
            // This participant is the ParticipantTableRow id field
            public String participant;
            // This site_location is the SiteLocationTableRow id field
            public String site_location;

            public ParticipantSiteLocation() {}
            public ParticipantSiteLocation(String tableId,
                                           String participantTableId,
                                           String siteTableId) {
                this.id = tableId;
                this.participant = participantTableId;
                this.site_location = siteTableId;
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ParticipantPhone {
            // Table row ID
            public String id;
            // This participant is the ParticipantTableRow id field
            public String participant_id;
            // Internationally formatted phone number, always starting with "+"
            public String phone;

            public ParticipantPhone() {}
            public ParticipantPhone(String tableId, String participantTableId, String phoneNumber) {
                this.id = tableId;
                this.participant_id = participantTableId;
                this.phone = phoneNumber;
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ParticipantRater {
            // Rater table row ID who registered this user
            public String registered_by;
            // This participant is the ParticipantTableRow id field
            public String participant;
            // Internationally formatted phone number, always starting with "+"
            public Double created_at;

            public ParticipantRater() {}
            public ParticipantRater(String registeredBy, String participantTableId) {
                this.registered_by = registeredBy;
                this.participant = participantTableId;
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Rater {
            // Rater table row ID
            public String id;
            // The study_id this rater is in charge of
            public String study_id;
            // The rater's email
            public String email;

            public Rater() {}
            public Rater(String tableId, String raterEmail) {
                this.id = tableId;
                this.email = raterEmail;
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ParticipantDeviceId {
            // ParticipantDeviceId table row ID
            public String id;
            // This participant is the ParticipantTableRow id field
            public String participant;
            // The participant's most recent device id
            public String device_id;
            // A string representation of long value timestamp seconds since 1970
            public String created_at;

            public ParticipantDeviceId() {}
            public ParticipantDeviceId(String tableId, String participantTableId,
                                       String deviceId, String createdAt) {
                this.id = tableId;
                this.participant = participantTableId;
                this.device_id = deviceId;
                this.created_at = createdAt;
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ParticipantNotes {
            // ParticipantDeviceId table row ID
            public String id;
            // This participant is the ParticipantTableRow id field
            public String participant;
            // The participant's notes
            public String note;

            public ParticipantNotes() {}
            public ParticipantNotes(String tableId, String participantTableId, String notes) {
                this.id = tableId;
                this.participant = participantTableId;
                this.note = notes;
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ParticipantScheduleData {
        public String participant_id;
    }

    /**
     * Directly copied from HappyMedium's Java code, this is
     * what gets uploaded when a user completes a test session
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TestSession {

        public TestSession() {

        }
        public TestSession(String participant, int week, int day, int session, int isFinished) {
            this.participant_id = participant;
            this.week = week;
            this.day = day;
            this.session = session;
            this.finished_session = isFinished;
        }

        public String participant_id;
        public int finished_session; // 1 == finished, 0 == unfinished
        public int day;
        public int session;
        public int week;
        public double session_date;

        @Override
        public final boolean equals(Object v) {
            boolean retVal = false;

            if (v instanceof TestSession) {
                TestSession ptr = (TestSession) v;
                retVal = ptr.week == this.week &&
                        ptr.day == this.day &&
                        ptr.session == this.session;
            }

            return retVal;
        }

        @Override
        public final int hashCode() {
            return (String.valueOf(week) +
                    String.valueOf(day) +
                    String.valueOf(session)).hashCode();
        }
    }

    /**
     * Stores the list of CompletedTests by a user
     */
    public static class CompletedTestList {
        public CompletedTestList() {}

        /**
         * @param testSessionList from the Synapse data.  These will have duplicates,
         *                        as well as incomplete test sessions. Constructing the
         *                        CompletedTestList through this constructor will create
         *                        a list of only completed, unique test sessions (day, week, session)
         */
        public CompletedTestList(List<TestSession> testSessionList) {
            completed = new ArrayList<>();

            // Loop through and add all unique, completed sessions
            for (TestSession testSession: testSessionList) {
                boolean unique = true;
                if (testSession.finished_session != 1) {
                    unique = false; // skip unfinished schedules
                }
                for (CompletedTest completedTest: completed) {
                    if (completedTest.week == testSession.week &&
                            completedTest.day == testSession.day &&
                            completedTest.session == testSession.session) {
                        unique = false;
                    }
                }
                if (unique) {
                    CompletedTest test = new CompletedTest();
                    test.week = testSession.week;
                    test.day = testSession.day;
                    test.session = testSession.session;
                    test.completedOn = testSession.session_date;
                    completed.add(test);
                }
            }

            // Sort in order of week, day, session
            completed.sort((test1, test2) -> {
                if (test1.week != test2.week) {
                    return Integer.compare(test1.week, test2.week);
                }
                if (test1.day != test2.day) {
                    return Integer.compare(test1.day, test2.day);
                }
                return Integer.compare(test1.session, test2.session);
            });
        }

        public List<CompletedTest> completed;
    }

    /**
     * Simplified version of a completed test, used to make the completed test list
     */
    public static class CompletedTest {
        public int week;
        public int day;
        public int session;
        public double completedOn;

        public CompletedTest() {}
        public CompletedTest(int week, int day, int session, double completedOn) {
            this.week = week;
            this.day = day;
            this.session = session;
            this.completedOn = completedOn;
        }
    }
}
