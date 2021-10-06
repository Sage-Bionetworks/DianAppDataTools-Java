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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * This file contains all the data models used by HappyMedium
 */
public class HmDataModel {

    /**
     * Classes used to parse participant data exported as JSON
     * These classes were exported from a SQL database, so many
     * of them are table row ID references, instead of class object references.
     */
    public static class TableRow {

        public static SiteLocation findSiteLocation(
                String id, ParticipantSiteLocation[] siteLocMap, SiteLocation[] sites) {

            for (ParticipantSiteLocation siteLocMapping: siteLocMap) {
                if (siteLocMapping.participant != null && siteLocMapping.participant.equals(id)) {
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

        public static Participant findParticipant(String id, Participant[] participants) {
            for (Participant participant: participants) {
                if (participant.id != null && participant.id.equals(id)) {
                    return participant;
                }
            }
            return null;
        }

        public static Rater findParticipantRater(
                String id, ParticipantRater[] participantRaters, Rater[] raters) {
            for (ParticipantRater rater: participantRaters) {
                if (rater.participant != null && rater.participant.equals(id)) {
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

        public static ParticipantPhone findParticipantPhone(String id, ParticipantPhone[] phoneList) {
            for (ParticipantPhone phone: phoneList) {
                if (phone != null && phone.participant_id.equals(id)) {
                    return phone;
                }
            }
            return null;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ParticipantFiles {
            public File siteLocations;
            public File participantSiteLocations;
            public File phone;
            public File participants;
            public File participantRaters;
            public File raters;
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
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ParticipantPhone {
            // Table row ID
            public String id;
            // This participant is the ParticipantTableRow id field
            public String participant_id;
            // Internationally formatted phone number, always starting with "+"
            public String phone;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ParticipantRater {
            // Rater table row ID who registered this user
            public String registered_by;
            // This participant is the ParticipantTableRow id field
            public String participant;
            // Internationally formatted phone number, always starting with "+"
            public Double created_at;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Rater {
            // Rater table row ID
            public String id;
            // The study_id this rater is in charge of
            public String study_id;
            // The rater's email
            public String email;
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

        public TestSession() {}

        public String participant_id;
        public int finished_session; // 1 == finished, 0 == unfinished
        public int day;
        public int session;
        public int week;
        public double session_date;

        @Override
        public boolean equals(Object v) {
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
        public int hashCode() {
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

    /**
     * This class is used to compile the need to know data
     * about a Happy Medium user, all in one data class
     */
    public static class HmUser {
        public String arcId;
        public String studyId;
        public String phone;
        public String name;
        public String password;
        public TableRow.SiteLocation siteLocation;
        public TableRow.Rater rater;
    }

    public static class HmUserData {
        public String arcId;
        public HmDataModel.CompletedTestList completedTests;
        public File wakeSleepSchedule;
        public File testSessionSchedule;
    }
}
