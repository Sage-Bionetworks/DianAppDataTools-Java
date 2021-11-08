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

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HmDataModelTests {

    @Test
    public void test_hmUserConstructor_ExistingUser() {
        ConstructorParams params = createParams();
        HmDataModel.HmUser user = new HmDataModel.HmUser(params.participant, params.rater,
                params.site, params.notes, params.phone, params.participantDeviceId);

        assertEquals("000001", user.arcId);
        assertEquals("d1a5cbaf-288c-48dd-9d4a-98c90213ac01", user.externalId);
        assertEquals("d1a5cbaf-288c-48dd-9d4a-98c90213ac01", user.deviceId);
        assertEquals("SiteA", user.studyId);
        assertEquals("+11111111111", user.phone);
        // Existing users will migrate with their password as also their Device ID
        assertEquals("d1a5cbaf-288c-48dd-9d4a-98c90213ac01", user.password);
        assertNull(user.name);
        assertEquals("Note", user.notes);
        assertEquals(user.rater.email, params.rater.email);
        assertEquals(user.siteLocation.name, params.site.name);
    }

    @Test
    public void test_hmUserConstructor_NewUser() {
        ConstructorParams params = createParams();

        // New users have no Device ID, so no migration account is made,
        // only a normal Arc ID external ID account is made.
        params.participantDeviceId = null;

        HmDataModel.HmUser user = new HmDataModel.HmUser(params.participant, params.rater,
                params.site, params.notes, params.phone, params.participantDeviceId);

        assertEquals("000001", user.arcId);
        assertEquals("000001", user.externalId);
        assertEquals("No-Device-Id", user.deviceId);
        assertEquals("SiteA", user.studyId);
        assertEquals("+11111111111", user.phone);
        assertNotNull(user.password);
        assertEquals(9, user.password.length());
        assertNull(user.name);
        assertEquals("Note", user.notes);
        assertEquals(user.rater.email, params.rater.email);
        assertEquals(user.siteLocation.name, params.site.name);
    }

    @Test
    public void test_hmUserConstructor_NoSite() {
        ConstructorParams params = createParams();

        // Some users will be considered bad data, and have no site
        // These will be filtered and uploaded to a special study for auditing purposes
        params.site = null;

        HmDataModel.HmUser user = new HmDataModel.HmUser(params.participant, params.rater,
                params.site, params.notes, params.phone, params.participantDeviceId);

        assertEquals("000001", user.arcId);
        assertEquals("000001", user.externalId);
        assertEquals("d1a5cbaf-288c-48dd-9d4a-98c90213ac01", user.deviceId);
        assertEquals("Happy-Medium-Errors", user.studyId);
        assertEquals("+11111111111", user.phone);
        assertNotNull(user.password);
        assertEquals(9, user.password.length());
        assertNull(user.name);
        assertEquals("Note Could not find site location ", user.notes);
        assertEquals(user.rater.email, params.rater.email);
        assertNull(user.siteLocation);
    }

    @Test
    public void test_findParticipant() {
        HmDataModel.TableRow.Participant[] participants = new HmDataModel.TableRow.Participant[] {
                new HmDataModel.TableRow.Participant("1", "100000"),
                new HmDataModel.TableRow.Participant("2", "1"),
                new HmDataModel.TableRow.Participant("3", "123456")
        };

        HmDataModel.TableRow.Participant participant =
                HmDataModel.TableRow.findParticipant("1", participants);
        assertNotNull(participant);
        assertEquals("100000", participant.participant_id);

        participant = HmDataModel.TableRow.findParticipant("2", participants);
        assertNotNull(participant);
        // Note that this is not transformed yet into a valid 6 digit Arc ID, and this is OK
        assertEquals("1", participant.participant_id);

        participant = HmDataModel.TableRow.findParticipant("3", participants);
        assertNotNull(participant);
        assertEquals("123456", participant.participant_id);

        participant = HmDataModel.TableRow.findParticipant("4", participants);
        assertNull(participant);
    }

    @Test
    public void test_findSiteLocation() {
        HmDataModel.TableRow.SiteLocation[] siteLocations = new HmDataModel.TableRow.SiteLocation[] {
                new HmDataModel.TableRow.SiteLocation("1", "SiteA"),
                new HmDataModel.TableRow.SiteLocation("2", "SiteB"),
                new HmDataModel.TableRow.SiteLocation("3", "SiteC")
        };

        HmDataModel.TableRow.ParticipantSiteLocation[] userSiteList =
                new HmDataModel.TableRow.ParticipantSiteLocation[] {
                new HmDataModel.TableRow.ParticipantSiteLocation("1", "1", "1"),
                new HmDataModel.TableRow.ParticipantSiteLocation("2", "2", "2"),
                new HmDataModel.TableRow.ParticipantSiteLocation("3", "3", "3"),
                new HmDataModel.TableRow.ParticipantSiteLocation("4", "4", "1")
        };

        HmDataModel.TableRow.SiteLocation site =
                HmDataModel.TableRow.findSiteLocation("1", userSiteList, siteLocations);
        assertNotNull(site);
        assertEquals("SiteA", site.name);

        site = HmDataModel.TableRow.findSiteLocation("2", userSiteList, siteLocations);
        assertNotNull(site);
        assertEquals("SiteB", site.name);

        site = HmDataModel.TableRow.findSiteLocation("3", userSiteList, siteLocations);
        assertNotNull(site);
        assertEquals("SiteC", site.name);

        site = HmDataModel.TableRow.findSiteLocation("4", userSiteList, siteLocations);
        assertNotNull(site);
        assertEquals("SiteA", site.name);

        site = HmDataModel.TableRow.findSiteLocation("5", userSiteList, siteLocations);
        assertNull(site);
    }

    @Test
    public void test_findRater() {
        HmDataModel.TableRow.Rater[] raters = new HmDataModel.TableRow.Rater[] {
                new HmDataModel.TableRow.Rater("1", "a@b.c"),
                new HmDataModel.TableRow.Rater("2", "d@e.f"),
                new HmDataModel.TableRow.Rater("3", "g@h.i")
        };

        HmDataModel.TableRow.ParticipantRater[] userRaterList =
                new HmDataModel.TableRow.ParticipantRater[] {
                        new HmDataModel.TableRow.ParticipantRater("1", "1"),
                        new HmDataModel.TableRow.ParticipantRater("2", "2"),
                        new HmDataModel.TableRow.ParticipantRater("3", "3"),
                        new HmDataModel.TableRow.ParticipantRater("1", "4")
                };

        HmDataModel.TableRow.Rater rater =
                HmDataModel.TableRow.findParticipantRater("1", userRaterList, raters);
        assertNotNull(rater);
        assertEquals("a@b.c", rater.email);

        rater = HmDataModel.TableRow.findParticipantRater("2", userRaterList, raters);
        assertNotNull(rater);
        assertEquals("d@e.f", rater.email);

        rater = HmDataModel.TableRow.findParticipantRater("3", userRaterList, raters);
        assertNotNull(rater);
        assertEquals("g@h.i", rater.email);

        rater = HmDataModel.TableRow.findParticipantRater("4", userRaterList, raters);
        assertNotNull(rater);
        assertEquals("a@b.c", rater.email);

        rater = HmDataModel.TableRow.findParticipantRater("5", userRaterList, raters);
        assertNull(rater);
    }

    @Test
    public void test_findDeviceId() {
        HmDataModel.TableRow.ParticipantDeviceId[] userDeviceIdList =
                new HmDataModel.TableRow.ParticipantDeviceId[] {
                        new HmDataModel.TableRow.ParticipantDeviceId(
                                "1", "1",
                                "d1a5cbaf-288c-48dd-9d4a-98c90213ac01", "1576165222"),
                        new HmDataModel.TableRow.ParticipantDeviceId(
                                "2", "2",
                                "abc5cbaf-288c-48dd-9d4a-98c90213ac01", "1576165222")
                };

        HmDataModel.TableRow.ParticipantDeviceId deviceId =
                HmDataModel.TableRow.findParticipantDeviceId("1", userDeviceIdList);
        assertNotNull(deviceId);
        assertEquals("d1a5cbaf-288c-48dd-9d4a-98c90213ac01", deviceId.device_id);

        deviceId = HmDataModel.TableRow.findParticipantDeviceId("2", userDeviceIdList);
        assertNotNull(deviceId);
        assertEquals("abc5cbaf-288c-48dd-9d4a-98c90213ac01", deviceId.device_id);

        deviceId = HmDataModel.TableRow.findParticipantDeviceId("3", userDeviceIdList);
        assertNull(deviceId);
    }

    @Test
    public void test_findNotes() {
        HmDataModel.TableRow.ParticipantNotes[] userNotesList =
                new HmDataModel.TableRow.ParticipantNotes[] {
                        new HmDataModel.TableRow.ParticipantNotes(
                                "1", "1", "NoteA"),
                        new HmDataModel.TableRow.ParticipantNotes(
                                "2", "2", "NoteB")
                };

        HmDataModel.TableRow.ParticipantNotes note =
                HmDataModel.TableRow.findParticipantNotes("1", userNotesList);
        assertNotNull(note);
        assertEquals("NoteA", note.note);

        note = HmDataModel.TableRow.findParticipantNotes("2", userNotesList);
        assertNotNull(note);
        assertEquals("NoteB", note.note);

        note = HmDataModel.TableRow.findParticipantNotes("3", userNotesList);
        assertNull(note);
    }

    @Test
    public void test_findPhone() {
        HmDataModel.TableRow.ParticipantPhone[] userPhoneList =
                new HmDataModel.TableRow.ParticipantPhone[] {
                        new HmDataModel.TableRow.ParticipantPhone(
                                "1", "1", "+11111111111"),
                        new HmDataModel.TableRow.ParticipantPhone(
                                "2", "2", "+22222222222")
                };

        HmDataModel.TableRow.ParticipantPhone phone =
                HmDataModel.TableRow.findParticipantPhone("1", userPhoneList);
        assertNotNull(phone);
        assertEquals("+11111111111", phone.phone);

        phone = HmDataModel.TableRow.findParticipantPhone("2", userPhoneList);
        assertNotNull(phone);
        assertEquals("+22222222222", phone.phone);

        phone = HmDataModel.TableRow.findParticipantPhone("3", userPhoneList);
        assertNull(phone);
    }

    @Test
    public void test_testSessionEquality() {
        HmDataModel.TestSession session1 = new HmDataModel.TestSession(
                "000001", 0, 0, 0, 1);
        HmDataModel.TestSession session2 = new HmDataModel.TestSession(
                "000001", 0, 0, 0, 1);

        assertTrue(session1.equals(session2));
        assertTrue(session2.equals(session1));

        session1 = new HmDataModel.TestSession(
                "000001", 0, 0, 1,1);
        session2 = new HmDataModel.TestSession(
                "000001", 0, 0, 1, 1);

        assertTrue(session1.equals(session2));
        assertTrue(session2.equals(session1));

        session1 = new HmDataModel.TestSession(
                "000001", 1, 0, 0, 1);
        session2 = new HmDataModel.TestSession(
                "000001", 1, 0, 0, 1);

        assertTrue(session1.equals(session2));
        assertTrue(session2.equals(session1));

        session1 = new HmDataModel.TestSession(
                "000001", 0, 1, 0, 1);
        session2 = new HmDataModel.TestSession(
                "000001", 0, 1, 0, 1);

        assertTrue(session1.equals(session2));
        assertTrue(session2.equals(session1));

        session1 = new HmDataModel.TestSession(
                "000001", 0, 0, 0, 1);
        session2 = new HmDataModel.TestSession(
                "000001", 0, 0, 1, 0);

        assertFalse(session1.equals(session2));
        assertFalse(session2.equals(session1));

        session1 = new HmDataModel.TestSession(
                "000001", 0, 0, 0,1);
        session2 = new HmDataModel.TestSession(
                "000001", 0, 1, 0, 1);

        assertFalse(session1.equals(session2));
        assertFalse(session2.equals(session1));

        session1 = new HmDataModel.TestSession(
                "000001", 0, 0, 0, 0);
        session2 = new HmDataModel.TestSession(
                "000001", 1, 0, 0, 0);

        assertFalse(session1.equals(session2));
        assertFalse(session2.equals(session1));
    }

    @Test
    public void test_testCompletedList() {
        List<HmDataModel.TestSession> sessionList = new ArrayList<>();
        sessionList.add(new HmDataModel.TestSession(
                "000001", 0, 0, 0, 1));
        // Duplicate should be skipped
        sessionList.add(new HmDataModel.TestSession(
                "000001", 0, 0, 0,1));
        sessionList.add(new HmDataModel.TestSession(
                "000001", 0, 0, 1, 1));
        // Unfinished session should be skipped
        sessionList.add(new HmDataModel.TestSession(
                "000001", 0, 0, 2, 0));

        HmDataModel.CompletedTestList testList =
                new HmDataModel.CompletedTestList(sessionList);

        assertEquals(2, testList.completed.size());

        HmDataModel.CompletedTest test = testList.completed.get(0);
        assertEquals(0, test.week);
        assertEquals(0, test.day);
        assertEquals(0, test.session);

        test = testList.completed.get(1);
        assertEquals(0, test.week);
        assertEquals(0, test.day);
        assertEquals(1, test.session);
    }

    private ConstructorParams createParams() {
        ConstructorParams params = new ConstructorParams();
        params.participant = new HmDataModel.TableRow.Participant("1", "000001");
        params.rater = new HmDataModel.TableRow.Rater("1", "a@b.c");
        params.site = new HmDataModel.TableRow.SiteLocation("1", "Site A");
        params.notes = new HmDataModel.TableRow.ParticipantNotes("1", "1", "Note");
        params.phone = new HmDataModel.TableRow.ParticipantPhone("1", "1", "+11111111111");
        params.participantDeviceId = new HmDataModel.TableRow.
                ParticipantDeviceId("1", "1",
                "d1a5cbaf-288c-48dd-9d4a-98c90213ac01", "1576165222");
        return params;
    }

    private static class ConstructorParams {
        private HmDataModel.TableRow.Participant participant;
        private HmDataModel.TableRow.Rater rater;
        private HmDataModel.TableRow.SiteLocation site;
        private HmDataModel.TableRow.ParticipantNotes notes;
        private HmDataModel.TableRow.ParticipantPhone phone;
        private HmDataModel.TableRow.ParticipantDeviceId participantDeviceId;
    }
}
