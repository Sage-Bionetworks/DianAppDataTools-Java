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
import org.sagebionetworks.repo.model.EntityHeader;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class SynapseUtilTests {

    @Test
    public void testFindFilterWithName() {
        List<EntityHeader> entities = new ArrayList<>();
        entities.add(createEntity("test_session"));
        entities.add(createEntity("test_session_schedule"));
        entities.add(createEntity("wake_sleep_schedule"));
        entities.add(createEntity("2021-09-24"));
        entities.add(createEntity("2021-10-01"));
        entities.add(createEntity("2021-10-10"));
        entities.add(createEntity("abcdefg"));
        entities.add(createEntity("adsfefsefs2021-10-10"));

        // Test null finds
        assertNull(SynapseUtil.findFolderWithName("abcd", entities));
        assertNull(SynapseUtil.findFolderWithName("2021-09-2", entities));
        assertNull(SynapseUtil.findFolderWithName("test_session1", entities));

        // Test matches
        EntityHeader testSession = SynapseUtil
                .findFolderWithName("test_session", entities);
        assertNotNull(testSession);
        assertEquals("test_session", testSession.getName());

        EntityHeader testSessionSchedule = SynapseUtil
                .findFolderWithName("test_session_schedule", entities);
        assertNotNull(testSessionSchedule);
        assertEquals("test_session_schedule", testSessionSchedule.getName());

        EntityHeader wakeSleepSchedule = SynapseUtil
                .findFolderWithName("wake_sleep_schedule", entities);
        assertNotNull(wakeSleepSchedule);
        assertEquals("wake_sleep_schedule", wakeSleepSchedule.getName());
    }

    @Test
    public void testFindZip_FirstOne() {
        List<EntityHeader> entities = new ArrayList<>();
        entities.add(createEntity("test_session"));
        entities.add(createEntity("test_session_schedule"));
        entities.add(createEntity("wake_sleep_schedule"));
        entities.add(createEntity("2021-09-24"));
        entities.add(createEntity("2021-10-01"));
        entities.add(createEntity("2021-10-10"));
        entities.add(createEntity("abcdefg.zip"));
        entities.add(createEntity("adsfefsefs2021-10-10.zip"));
        entities.add(createEntity("adsfefsefs2021-10-12.zip"));

        EntityHeader zipHeader = SynapseUtil.zipFileFromEntityList(entities, null, null);
        assertNotNull(zipHeader);
        assertEquals("abcdefg.zip", zipHeader.getName());
    }

    @Test
    public void testFindZip_PrefixSuffix() {
        List<EntityHeader> entities = new ArrayList<>();
        entities.add(createEntity("test_session.zip"));
        entities.add(createEntity("test_session_schedule.zip"));
        entities.add(createEntity("wake_sleep_schedule.zip"));
        entities.add(createEntity("2021-09-24"));
        entities.add(createEntity("2021-10-01"));
        entities.add(createEntity("2021-10-10"));
        entities.add(createEntity("adsfefsefs2021-10-10"));
        entities.add(createEntity("abcdefg.zip"));
        entities.add(createEntity("adsfefsefs2021-10-10.zip"));
        entities.add(createEntity("adsfefsefs2021-10-12.zip"));
        entities.add(createEntity("unique_test_session.zip"));
        entities.add(createEntity("unique_test2_session.zip"));

        EntityHeader zipHeader = SynapseUtil.zipFileFromEntityList(
                entities, "unique", ".zip");
        assertNotNull(zipHeader);
        assertEquals("unique_test_session.zip", zipHeader.getName());
    }

    private EntityHeader createEntity(String name) {
        EntityHeader entity = new EntityHeader();
        entity.setName(name);
        return entity;
    }
}