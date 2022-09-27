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

import com.google.gson.Gson;

import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.rest.model.AdherenceRecord;
import org.sagebionetworks.bridge.rest.model.StudyActivityEvent;
import org.sagebionetworks.bridge.rest.model.StudyActivityEventList;
import org.sagebionetworks.bridge.rest.model.Timeline;
import org.sagebionetworks.dian.datamigration.tools.adherence.CompletedTestV2;
import org.sagebionetworks.dian.datamigration.tools.adherence.SageEarningsControllerV2;
import org.sagebionetworks.dian.datamigration.tools.adherence.SageScheduleController;
import org.sagebionetworks.dian.datamigration.tools.adherence.SageUserClientData;
import org.sagebionetworks.dian.datamigration.tools.adherence.SageV1Schedule;
import org.sagebionetworks.dian.datamigration.tools.adherence.SageV2Availability;
import org.sagebionetworks.dian.datamigration.tools.adherence.ScheduledSessionStart;
import org.sagebionetworks.dian.datamigration.tools.schedulev2.ScheduleV2Migration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ScheduleV2MigrationTests {

    private final Path resourceDirectory = Paths.get("src", "test", "resources");
    private final Path rootFileTestFolder = resourceDirectory.resolve("bridge2MigrationTests");
    private final Path all_schedules = rootFileTestFolder.resolve("all_schedules");
    private final Path all_availability = rootFileTestFolder.resolve("all_availability");
    private final Path all_completed = rootFileTestFolder.resolve("all_completed");
    private final Path scheduleJson = rootFileTestFolder.resolve("000050_V1_Schedule.json");
    private final Path scheduleMalformedJson = rootFileTestFolder.resolve("Malformed_V1_Schedule.json");
    private final Path availabilityJson = rootFileTestFolder.resolve("000050_Availability.json");
    private final Path scheduleV2Json = rootFileTestFolder.resolve("000050_V2_Schedule.json");
    private final SageScheduleController controller = new SageScheduleController();

    private final Gson gson = new Gson();
    public static Map<String, String> activityEventMap = new HashMap<String, String>() {{
        put("created_on", "2022-06-28T00:28:23.139Z");
        put("timeline_retrieved", "2021-06-22T16:15:57.775Z");
        put("study_burst:timeline_retrieved_burst:01", "2021-06-22T16:15:57.775Z");
        put("study_burst:timeline_retrieved_burst:02", "2021-12-21T16:02:12.916Z");
        put("study_burst:timeline_retrieved_burst:03", "2022-06-21T15:26:07.208Z");
        put("study_burst:timeline_retrieved_burst:04", "2022-12-20T16:28:03.983Z");
        put("study_burst:timeline_retrieved_burst:05", "2023-06-20T15:12:28.688Z");
        put("study_burst:timeline_retrieved_burst:06", "2023-12-19T15:50:03.678Z");
        put("study_burst:timeline_retrieved_burst:07", "2024-06-18T15:26:20.409Z");
        put("study_burst:timeline_retrieved_burst:08", "2024-12-17T15:53:05.956Z");
        put("study_burst:timeline_retrieved_burst:09", "2025-06-17T14:48:51.642Z");
        put("study_burst:timeline_retrieved_burst:10", "2025-12-16T15:35:13.667Z");
    }};
    public final StudyActivityEventList createActivityEventList() {
        StudyActivityEventList activityEventList =
                gson.fromJson("{\"items\":[]}", StudyActivityEventList.class);
        for (String eventId : activityEventMap.keySet()) {
            StudyActivityEvent event = new StudyActivityEvent();
            event.setEventId(eventId);
            event.setClientTimeZone("America/Chicago");
            event.setTimestamp(DateTime.parse(activityEventMap.get(eventId)));
            activityEventList.getItems().add(event);
        }
        return activityEventList;
    }

    public String getScheduleJson(String fileId) throws IOException {
        Path path = all_schedules.resolve(fileId + ".json");
        if (!Files.exists(path)) {
            return null;
        }
        return PathsHelper.readFile(path);
    }

    public String getAvailabilityJson(String fileId) throws IOException {
        Path path = all_availability.resolve(fileId + ".json");
        if (!Files.exists(path)) {
            return null;
        }
        return PathsHelper.readFile(path);
    }

    public String getCompletedTestJson(String fileId) throws IOException {
        Path path = all_completed.resolve(fileId + ".json");
        if (!Files.exists(path)) {
            return null;
        }
        return PathsHelper.readFile(path);
    }

    @Before
    public void before() throws IOException {

    }

    @After
    public void after() throws IOException {

    }

    @Test
    public void test_allUserAdherenceRecordsAndClientData() throws IOException {

        // Skip this test, as they have bad CompletedTestJson that fails the validation,
        // This user is already fully done with all their study burst and has been paid their earnings
        List<Integer> skipForNow = Arrays.asList(724);

        int highestFileInAllSession = 1012;
        for(int fileId = 0; fileId <= highestFileInAllSession; fileId++) {

            if (skipForNow.contains(fileId)) {
                continue;
            }

            String fileIdStr = fileId + "";

            if (SageScheduleControllerTests.withdrawnUsers.contains(fileId) ||
                    SageScheduleControllerTests.skipNeedsManualIntervention.contains(fileId)) {
                continue;
            }

            String scheduleJson = getScheduleJson(fileIdStr);
            if (scheduleJson == null) {
                continue;
            }
            String availabilityJson = getAvailabilityJson(fileIdStr);
            String completedJson = getCompletedTestJson(fileIdStr);

            SageV1Schedule v1Schedule =
                    ScheduleV2Migration.createV1Schedule(fileIdStr, scheduleJson);
            assertNotNull(v1Schedule);

            SageV2Availability v2Availability =
                    ScheduleV2Migration.createV2Availability(fileIdStr, availabilityJson);
            assertNotNull(v2Availability);

            SageEarningsControllerV2 earningsController =
                    ScheduleV2Migration.createEarningsController(fileIdStr, v1Schedule, completedJson);
            assertNotNull(earningsController);

            Gson gson = new Gson();
            Timeline timeline = gson.fromJson(
                    PathsHelper.readFile(scheduleV2Json), Timeline.class);
            StudyActivityEventList eventList = createActivityEventList();

            SageUserClientData clientData = ScheduleV2Migration.createUserClientData(
                    timeline, v2Availability, earningsController);
            assertNotNull(clientData);
            assertNotNull(clientData.getAvailability());
            assertNotNull(clientData.getAvailability().getBed());
            assertNotNull(clientData.getAvailability().getWake());
            // Format HH:MM
            assertEquals(5, clientData.getAvailability().getWake().length());
            assertEquals(5, clientData.getAvailability().getBed().length());
            // Check for migration status, should always be false in this scenario
            assertNotNull(clientData.getHasMigratedToV2());
            assertFalse(clientData.getHasMigratedToV2());
            // Check for new session start times
            assertNotNull(clientData.getSessionStartLocalTimes());
            // Make sure all instance GUIDs are unique
            HashSet<String> allGuids = new HashSet<>();
            for (ScheduledSessionStart sessionStart : clientData.getSessionStartLocalTimes()) {
                allGuids.add(sessionStart.getGuid());
            }
            assertEquals(280, clientData.getSessionStartLocalTimes().size());
            assertEquals(280, allGuids.size());

            assertNotNull(clientData.getEarnings());
            assertTrue(clientData.getEarnings().size() > 0);

            List<AdherenceRecord> adherenceList = ScheduleV2Migration.createAdherenceRecords(
                    timeline, eventList, v1Schedule, earningsController);

            assertNotNull(adherenceList);
            for(AdherenceRecord record : adherenceList) {
                CompletedTestV2 v2Test = gson.fromJson(
                        gson.toJson(record.getClientData()),
                        CompletedTestV2.class);
                assertNotNull(v2Test);
                // make sure event id is set, for the new earnings controller
                assertNotNull(v2Test.getEventId());
                assertTrue(v2Test.getEventId().contains(
                        SageScheduleController.ACTIVITY_EVENT_CREATE_SCHEDULE));
            }

            List<HmDataModel.CompletedTest> diff = controller.diff(
                    earningsController.getCompletedTests(), adherenceList);

            // Make sure there is an adherence record for every completed test
            assertEquals(0, diff.size());
        }
    }
}