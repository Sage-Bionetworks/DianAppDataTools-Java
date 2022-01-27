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
package org.sagebionetworks.research.sagearc

import junit.framework.Assert.*
import org.joda.time.DateTime
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.sagebionetworks.dian.datamigration.HmDataModel.*
import org.sagebionetworks.dian.datamigration.tools.adherence.earnings.EarningOverview.*

@RunWith(JUnit4::class)
public class SageEarningsControllerTests {

    val controller = MockSageEarningsController()

    fun createTest(week: Int, day: Int, session: Int = 0): CompletedTest {
        return CompletedTest(week, day, session, (System.currentTimeMillis() / 1000L).toDouble())
    }

    fun startDate(): DateTime {
        return DateTime.parse("2021-08-10T11:21:00.000-07:00")
    }

    fun studyPeriod1Start(): DateTime {
        return DateTime.parse("2021-08-10T00:00:00.000-07:00")
    }

    fun studyPeriod1End(): DateTime {
        return DateTime.parse("2021-08-17T00:00:00.000-07:00")
    }

    fun studyPeriod2Start(): DateTime {
        return studyPeriod1Start().plusDays(182)
    }

    fun studyPeriod2End(): DateTime {
        return studyPeriod1Start().plusDays(182 + 7)
    }

    @Test
    fun testDay0() {
        // User should not get earnings for day 0, which is the tutorial
        controller.completedTests = listOf(createTest(0, 0))
        controller.overridingNow = startDate().plusMinutes(1)
        
        val earnings = controller.getCurrentEarningsOverview()
        assertNotNull(earnings)

        val twentyOneGoal = earnings?.goals?.firstOrNull { it.name == TWENTY_ONE_SESSIONS }
        assertNotNull(twentyOneGoal)
        assertEquals(twentyOneGoal?.progress, 0)

        val twoADayGoal = earnings?.goals?.firstOrNull { it.name == TWO_A_DAY }
        assertNotNull(twoADayGoal)
        assertEquals(twoADayGoal?.progress, 0)

        val fourOfFourGoal = earnings?.goals?.firstOrNull { it.name == FOUR_OUT_OF_FOUR }
        assertNull(fourOfFourGoal) // no 4 of 4 on baseline tutorial day

        val allSessionsGoal = earnings?.goals?.firstOrNull { it.name == TEST_SESSION }
        assertNotNull(allSessionsGoal)
        assertEquals(allSessionsGoal?.progress, 0)

        assertEquals(earnings?.cycle_earnings, "$0.00")
        assertEquals(earnings?.total_earnings, "$0.00")

        val summary = controller.getCurrentStudySummary()
        assertNotNull(summary)
        assertEquals(summary?.days_tested, 0)
        assertEquals(summary?.goals_met, 0)
        assertEquals(summary?.tests_taken, 0)
        assertEquals(summary?.total_earnings, "$0.00")

        controller.getCurrentEarningsDetails()
        val details = controller.getCurrentEarningsDetails()
        assertNotNull(details)
        assertEquals(details?.total_earnings, "$0.00")
        assertEquals(details?.cycles?.size, 1)

        val studyPeriod1 = details?.cycles?.firstOrNull()
        assertNotNull(studyPeriod1)
        assertEquals(studyPeriod1?.cycle, 0)
        assertEquals(studyPeriod1?.total, "$0.00")
        assertEquals(studyPeriod1?.start_date, (studyPeriod1Start().millis / 1000L))
        assertEquals(studyPeriod1?.end_date, (studyPeriod1End().millis / 1000L))

        val goals = studyPeriod1?.details
        assertEquals(goals?.size, 4)
    }

    @Test
    fun testDay1Unfinished() {
        // User should not get earnings for day 0, but gets earnings from day 1, 3 sessions complete
        controller.completedTests = listOf(
            createTest(0, 0),
            createTest(0, 1, 0), createTest(0, 1, 1), createTest(0, 1, 2))

        controller.overridingNow = startDate().plusDays(1).withTimeAtStartOfDay().plusMinutes(1)
                
        val earnings = controller.getCurrentEarningsOverview()
        assertNotNull(earnings)

        val twentyOneGoal = earnings?.goals?.firstOrNull{ it.name == TWENTY_ONE_SESSIONS }
        assertNotNull(twentyOneGoal)
        assertEquals(twentyOneGoal?.progress_components, listOf(3))

        val twoADayGoal = earnings?.goals?.firstOrNull{ it.name == TWO_A_DAY }
        assertNotNull(twoADayGoal)
        assertEquals(twoADayGoal?.progress_components, listOf(100, 0, 0, 0, 0, 0, 0))

        val fourOfFourGoal = earnings?.goals?.firstOrNull{ it.name == FOUR_OUT_OF_FOUR }
        assertNotNull(fourOfFourGoal)
        assertEquals(fourOfFourGoal?.progress_components, listOf(100, 100, 100, 0))
        assertFalse(fourOfFourGoal?.completed ?: true)

        val allSessionsGoal = earnings?.goals?.firstOrNull{ it.name == TEST_SESSION }
        assertNotNull(allSessionsGoal)
        assertEquals(allSessionsGoal?.progress_components, listOf(3))

        // 3 .testSession goals at it.50 each
        assertEquals(earnings?.cycle_earnings, "$1.50")
        assertEquals(earnings?.total_earnings, "$1.50")

        val summary = controller.getCurrentStudySummary()
        assertNotNull(summary)
        assertEquals(summary?.days_tested, 0)
        assertEquals(summary?.goals_met, 0)
        assertEquals(summary?.tests_taken, 3)
        assertEquals(summary?.total_earnings, "$1.50")

        val details = controller.getCurrentEarningsDetails()
                assertNotNull(details)
        assertEquals(details?.total_earnings, "$1.50")
        assertEquals(details?.cycles?.size, 1)

        val studyPeriod1 = details?.cycles?.firstOrNull()
        assertNotNull(studyPeriod1)
        assertEquals(studyPeriod1?.cycle, 0)
        assertEquals(studyPeriod1?.total, "$1.50")
        assertEquals(studyPeriod1?.start_date, (studyPeriod1Start().millis / 1000L))
        assertEquals(studyPeriod1?.end_date, (studyPeriod1End().millis / 1000L))

        val goals = studyPeriod1?.details
        assertEquals(goals?.size, 4)
    }

    @Test
    fun testDay1Finished() {
        // User should not get earnings for day 0, but gets earnings from day 1, 3 sessions complete
        controller.completedTests = listOf(
            createTest(0, 0),
            createTest(0, 1, 0), createTest(0, 1, 1), createTest(0, 1, 2), createTest(0, 1, 3))

        controller.overridingNow = startDate().plusDays(1).withTimeAtStartOfDay().plusMinutes(1)

        val earnings = controller.getCurrentEarningsOverview()
        assertNotNull(earnings)

        val twentyOneGoal = earnings?.goals?.firstOrNull{ it.name == TWENTY_ONE_SESSIONS }
        assertNotNull(twentyOneGoal)
        assertEquals(twentyOneGoal?.progress_components, listOf(4))

        val twoADayGoal = earnings?.goals?.firstOrNull{ it.name == TWO_A_DAY }
        assertNotNull(twoADayGoal)
        assertEquals(twoADayGoal?.progress_components, listOf(100, 0, 0, 0, 0, 0, 0))

        val fourOfFourGoal = earnings?.goals?.firstOrNull{ it.name == FOUR_OUT_OF_FOUR }
        assertNotNull(fourOfFourGoal)
        assertEquals(fourOfFourGoal?.progress_components, listOf(100, 100, 100, 100))
        assertTrue(fourOfFourGoal?.completed ?: false)

        val allSessionsGoal = earnings?.goals?.firstOrNull{ it.name == TEST_SESSION }
        assertNotNull(allSessionsGoal)
        assertEquals(allSessionsGoal?.progress_components, listOf(4))

        // 4 .testSession goals at it.50 each, and the 4 of 4 goal of $1
        assertEquals(earnings?.cycle_earnings, "$3.00")
        assertEquals(earnings?.total_earnings, "$3.00")

        val summary = controller.getCurrentStudySummary()
        assertNotNull(summary)
        assertEquals(summary?.days_tested, 0)
        assertEquals(summary?.goals_met, 1)
        assertEquals(summary?.tests_taken, 4)
        assertEquals(summary?.total_earnings, "$3.00")

        val details = controller.getCurrentEarningsDetails()
                assertNotNull(details)
        assertEquals(details?.total_earnings, "$3.00")
        assertEquals(details?.cycles?.size, 1)

        val studyPeriod1 = details?.cycles?.firstOrNull()
        assertNotNull(studyPeriod1)
        assertEquals(studyPeriod1?.cycle, 0)
        assertEquals(studyPeriod1?.total, "$3.00")
        assertEquals(studyPeriod1?.start_date, (studyPeriod1Start().millis / 1000L))
        assertEquals(studyPeriod1?.end_date, (studyPeriod1End().millis / 1000L))

        val goals = studyPeriod1?.details
        assertEquals(goals?.size, 4)
    }

    @Test
    fun testWeek1Unfinished() {
        controller.completedTests = listOf(
            createTest(0, 0),
            createTest(0, 1, 0), createTest(0, 1, 1),
            createTest(0, 2, 0), createTest(0, 2, 1),
            createTest(0, 3, 0), createTest(0, 3, 1),
            createTest(0, 4, 0), createTest(0, 4, 1),
            createTest(0, 5, 0), createTest(0, 5, 1),
            createTest(0, 6, 0), createTest(0, 6, 1),
            createTest(0, 7, 0))

        controller.overridingNow = startDate().plusDays(7).withTimeAtStartOfDay().plusMinutes(1)

        val earnings = controller.getCurrentEarningsOverview()
        assertNotNull(earnings)

        val twentyOneGoal = earnings?.goals?.firstOrNull{ it.name == TWENTY_ONE_SESSIONS }
        assertNotNull(twentyOneGoal)
        assertEquals(twentyOneGoal?.progress_components, listOf(13))
        assertFalse(twentyOneGoal?.completed ?: true)

        val twoADayGoal = earnings?.goals?.firstOrNull{ it.name == TWO_A_DAY }
        assertNotNull(twoADayGoal)
        assertEquals(twoADayGoal?.progress_components, listOf(100, 100, 100, 100, 100, 100, 0))
        assertFalse(twoADayGoal?.completed ?: true)

        val fourOfFourGoal = earnings?.goals?.firstOrNull{ it.name == FOUR_OUT_OF_FOUR }
        assertNotNull(fourOfFourGoal)
        assertEquals(fourOfFourGoal?.progress_components, listOf(100, 0, 0, 0))
        assertFalse(fourOfFourGoal?.completed ?: true)

        val allSessionsGoal = earnings?.goals?.firstOrNull{ it.name == TEST_SESSION }
        assertNotNull(allSessionsGoal)
        assertEquals(allSessionsGoal?.progress_components, listOf(13))

        // 13 .testSession goals at it.50 each
        assertEquals(earnings?.cycle_earnings, "$6.50")
        assertEquals(earnings?.total_earnings, "$6.50")

        val summary = controller.getCurrentStudySummary()
        assertNotNull(summary)
        assertEquals(summary?.days_tested, 6)
        assertEquals(summary?.goals_met, 0)
        assertEquals(summary?.tests_taken, 13)
        assertEquals(summary?.total_earnings, "$6.50")

        val details = controller.getCurrentEarningsDetails()
                assertNotNull(details)
        assertEquals(details?.total_earnings, "$6.50")
        assertEquals(details?.cycles?.size, 1)

        val studyPeriod1 = details?.cycles?.firstOrNull()
        assertNotNull(studyPeriod1)
        assertEquals(studyPeriod1?.cycle, 0)
        assertEquals(studyPeriod1?.total, "$6.50")
        assertEquals(studyPeriod1?.start_date, (studyPeriod1Start().millis / 1000L))
        assertEquals(studyPeriod1?.end_date, (studyPeriod1End().millis / 1000L))

        val goals = studyPeriod1?.details
        assertEquals(goals?.size, 4)
    }

    @Test
    fun testWeek1Finished() {
        controller.completedTests = listOf(
            createTest(0, 0),
            createTest(0, 1, 0), createTest(0, 1, 1), createTest(0, 1, 2),
            createTest(0, 2, 0), createTest(0, 2, 1), createTest(0, 2, 2),
            createTest(0, 3, 0), createTest(0, 3, 1), createTest(0, 3, 2),
            createTest(0, 4, 0), createTest(0, 4, 1), createTest(0, 4, 2),
            createTest(0, 5, 0), createTest(0, 5, 1), createTest(0, 5, 2),
            createTest(0, 6, 0), createTest(0, 6, 1), createTest(0, 6, 2),
            createTest(0, 7, 0), createTest(0, 7, 1), createTest(0, 7, 2))

        controller.overridingNow = startDate().plusDays(7).withTimeAtStartOfDay().plusMinutes(1)

        val earnings = controller.getCurrentEarningsOverview()
        assertNotNull(earnings)

        val twentyOneGoal = earnings?.goals?.firstOrNull{ it.name == TWENTY_ONE_SESSIONS }
        assertNotNull(twentyOneGoal)
        assertEquals(twentyOneGoal?.progress_components, listOf(21))
        assertTrue(twentyOneGoal?.completed ?: false)

        val twoADayGoal = earnings?.goals?.firstOrNull{ it.name == TWO_A_DAY }
        assertNotNull(twoADayGoal)
        assertEquals(twoADayGoal?.progress_components, listOf(100, 100, 100, 100, 100, 100, 100))
        assertTrue(twoADayGoal?.completed ?: false)

        val fourOfFourGoal = earnings?.goals?.firstOrNull{ it.name == FOUR_OUT_OF_FOUR }
        assertNotNull(fourOfFourGoal)
        assertEquals(fourOfFourGoal?.progress_components, listOf(100, 100, 100, 0))
        assertFalse(fourOfFourGoal?.completed ?: true)

        val allSessionsGoal = earnings?.goals?.firstOrNull{ it.name == TEST_SESSION }
        assertNotNull(allSessionsGoal)
        assertEquals(allSessionsGoal?.progress_components, listOf(21))

        // 21 .testSession goals at it.50 each,
        // two a day goal at $6
        // and 21 session goal at $5
        assertEquals(earnings?.cycle_earnings, "$21.50")
        assertEquals(earnings?.total_earnings, "$21.50")

        val summary = controller.getCurrentStudySummary()
        assertNotNull(summary)
        assertEquals(summary?.days_tested, 6)
        assertEquals(summary?.goals_met, 2)
        assertEquals(summary?.tests_taken, 21)
        assertEquals(summary?.total_earnings, "$21.50")

        val details = controller.getCurrentEarningsDetails()
                assertNotNull(details)
        assertEquals(details?.total_earnings, "$21.50")
        assertEquals(details?.cycles?.size, 1)

        val studyPeriod1 = details?.cycles?.firstOrNull()
        assertNotNull(studyPeriod1)
        assertEquals(studyPeriod1?.cycle, 0)
        assertEquals(studyPeriod1?.total, "$21.50")
        assertEquals(studyPeriod1?.start_date, (studyPeriod1Start().millis / 1000L))
        assertEquals(studyPeriod1?.end_date, (studyPeriod1End().millis / 1000L))

        val goals = studyPeriod1?.details
        assertEquals(goals?.size, 4)
    }

    @Test
    fun testWeek1PerfectiOS() {
        controller.completedTests = listOf(
            createTest(0, 0),
            createTest(0, 1, 0), createTest(0, 1, 1), createTest(0, 1, 2), createTest(0, 1, 3),
            createTest(0, 2, 0), createTest(0, 2, 1), createTest(0, 2, 2), createTest(0, 2, 3),
            createTest(0, 3, 0), createTest(0, 3, 1), createTest(0, 3, 2), createTest(0, 3, 3),
            createTest(0, 4, 0), createTest(0, 4, 1), createTest(0, 4, 2), createTest(0, 4, 3),
            createTest(0, 5, 0), createTest(0, 5, 1), createTest(0, 5, 2), createTest(0, 5, 3),
            createTest(0, 6, 0), createTest(0, 6, 1), createTest(0, 6, 2), createTest(0, 6, 3),
            createTest(0, 7, 0), createTest(0, 7, 1), createTest(0, 7, 2), createTest(0, 7, 3))

        controller.overridingNow = startDate().plusDays(7).withTimeAtStartOfDay().plusMinutes(1)

        val earnings = controller.getCurrentEarningsOverview()
        assertNotNull(earnings)

        val twentyOneGoal = earnings?.goals?.firstOrNull{ it.name == TWENTY_ONE_SESSIONS }
        assertNotNull(twentyOneGoal)
        assertEquals(twentyOneGoal?.progress_components, listOf(28))
        assertTrue(twentyOneGoal?.completed ?: false)

        val twoADayGoal = earnings?.goals?.firstOrNull{ it.name == TWO_A_DAY }
        assertNotNull(twoADayGoal)
        assertEquals(twoADayGoal?.progress_components, listOf(100, 100, 100, 100, 100, 100, 100))
        assertTrue(twoADayGoal?.completed ?: false)

        val fourOfFourGoal = earnings?.goals?.firstOrNull{ it.name == FOUR_OUT_OF_FOUR }
        assertNotNull(fourOfFourGoal)
        assertEquals(fourOfFourGoal?.progress_components, listOf(100, 100, 100, 100))
        assertTrue(fourOfFourGoal?.completed ?: false)

        val allSessionsGoal = earnings?.goals?.firstOrNull{ it.name == TEST_SESSION }
        assertNotNull(allSessionsGoal)
        assertEquals(allSessionsGoal?.progress_components, listOf(28))

        // 28 .testSession goals at it.50 each,
        // two a day goal at $6
        // and 21 session goal at $5
        // 7 four of four goals at $1 each
        assertEquals(earnings?.cycle_earnings, "$32.00")
        assertEquals(earnings?.total_earnings, "$32.00")

        val summary = controller.getCurrentStudySummary()
        assertNotNull(summary)
        assertEquals(summary?.days_tested, 6)
        assertEquals(summary?.goals_met, 9)
        assertEquals(summary?.tests_taken, 28)
        assertEquals(summary?.total_earnings, "$32.00")

        val details = controller.getCurrentEarningsDetails()
                assertNotNull(details)
        assertEquals(details?.total_earnings, "$32.00")
        assertEquals(details?.cycles?.size, 1)

        val studyPeriod1 = details?.cycles?.firstOrNull()
        assertNotNull(studyPeriod1)
        assertEquals(studyPeriod1?.cycle, 0)
        assertEquals(studyPeriod1?.total, "$32.00")
        assertEquals(studyPeriod1?.start_date, (studyPeriod1Start().millis / 1000L))
        assertEquals(studyPeriod1?.end_date, (studyPeriod1End().millis / 1000L))

        val goals = studyPeriod1?.details
        assertEquals(goals?.size, 4)
    }

    @Test
    fun testWeek1PerfectAndroid() {
        controller.completedTests = listOf(
            createTest(0, 0),
            createTest(0, 1, 0), createTest(0, 1, 1), createTest(0, 1, 2), createTest(0, 1, 3),
            createTest(0, 2, 0), createTest(0, 2, 1), createTest(0, 2, 2), createTest(0, 2, 3),
            createTest(0, 3, 0), createTest(0, 3, 1), createTest(0, 3, 2), createTest(0, 3, 3),
            createTest(0, 4, 0), createTest(0, 4, 1), createTest(0, 4, 2), createTest(0, 4, 3),
            createTest(0, 5, 0), createTest(0, 5, 1), createTest(0, 5, 2), createTest(0, 5, 3),
            createTest(0, 6, 0), createTest(0, 6, 1), createTest(0, 6, 2), createTest(0, 6, 3),
            // Android labels week 0, day 7 as week 1, day 7, make sure algo accounts for it
            createTest(1, 7, 0), createTest(1, 7, 1), createTest(1, 7, 2), createTest(1, 7, 3))

        controller.overridingNow = startDate().plusDays(7).withTimeAtStartOfDay().plusMinutes(1)

        val earnings = controller.getCurrentEarningsOverview()
        assertNotNull(earnings)

        val twentyOneGoal = earnings?.goals?.firstOrNull{ it.name == TWENTY_ONE_SESSIONS }
        assertNotNull(twentyOneGoal)
        assertEquals(twentyOneGoal?.progress_components, listOf(28))
        assertTrue(twentyOneGoal?.completed ?: false)

        val twoADayGoal = earnings?.goals?.firstOrNull{ it.name == TWO_A_DAY }
        assertNotNull(twoADayGoal)
        assertEquals(twoADayGoal?.progress_components, listOf(100, 100, 100, 100, 100, 100, 100))
        assertTrue(twoADayGoal?.completed ?: false)

        val fourOfFourGoal = earnings?.goals?.firstOrNull{ it.name == FOUR_OUT_OF_FOUR }
        assertNotNull(fourOfFourGoal)
        assertEquals(fourOfFourGoal?.progress_components, listOf(100, 100, 100, 100))
        assertTrue(fourOfFourGoal?.completed ?: false)

        val allSessionsGoal = earnings?.goals?.firstOrNull{ it.name == TEST_SESSION }
        assertNotNull(allSessionsGoal)
        assertEquals(allSessionsGoal?.progress_components, listOf(28))

        // 28 .testSession goals at it.50 each,
        // two a day goal at $6
        // and 21 session goal at $5
        // 7 four of four goals at $1 each
        assertEquals(earnings?.cycle_earnings, "$32.00")
        assertEquals(earnings?.total_earnings, "$32.00")

        val summary = controller.getCurrentStudySummary()
        assertNotNull(summary)
        assertEquals(summary?.days_tested, 6)
        assertEquals(summary?.goals_met, 9)
        assertEquals(summary?.tests_taken, 28)
        assertEquals(summary?.total_earnings, "$32.00")

        val details = controller.getCurrentEarningsDetails()
                assertNotNull(details)
        assertEquals(details?.total_earnings, "$32.00")
        assertEquals(details?.cycles?.size, 1)

        val studyPeriod1 = details?.cycles?.firstOrNull()
        assertNotNull(studyPeriod1)
        assertEquals(studyPeriod1?.cycle, 0)
        assertEquals(studyPeriod1?.total, "$32.00")
        assertEquals(studyPeriod1?.start_date, (studyPeriod1Start().millis / 1000L))
        assertEquals(studyPeriod1?.end_date, (studyPeriod1End().millis / 1000L))

        val goals = studyPeriod1?.details
        assertEquals(goals?.size, 4)
    }

    @Test
    fun testWeek1PerfectExtraBadData() {
        // Test if user's data is duplicated or outside the study period window that we don't give them more money than the max
        controller.completedTests = listOf(
            createTest(0, 0), createTest(20, 0), createTest(8, 0), createTest(179, 0),
            createTest(0, 1, 0), createTest(0, 1, 0), createTest(0, 1, 1), createTest(0, 1, 2),  createTest(0, 1, 3),
            createTest(0, 2, 0), createTest(0, 2, 0), createTest(0, 2, 1), createTest(0, 2, 2), createTest(0, 2, 3),
            createTest(0, 3, 0), createTest(0, 3, 0), createTest(0, 3, 1), createTest(0, 3, 2), createTest(0, 3, 3),
            createTest(0, 4, 0), createTest(0, 4, 0), createTest(0, 4, 1), createTest(0, 4, 2), createTest(0, 4, 3),
            createTest(0, 5, 0), createTest(0, 5, 0), createTest(0, 5, 1), createTest(0, 5, 2), createTest(0, 5, 3),
            createTest(0, 6, 0), createTest(0, 6, 0), createTest(0, 6, 1), createTest(0, 6, 2), createTest(0, 6, 3),
            createTest(0, 7, 0), createTest(0, 7, 0), createTest(0, 7, 1), createTest(0, 7, 2), createTest(0, 7, 3))

        controller.overridingNow = startDate().plusDays(7).withTimeAtStartOfDay().plusMinutes(1)

        val earnings = controller.getCurrentEarningsOverview()
        assertNotNull(earnings)

        val twentyOneGoal = earnings?.goals?.firstOrNull{ it.name == TWENTY_ONE_SESSIONS }
        assertNotNull(twentyOneGoal)
        assertEquals(twentyOneGoal?.progress_components, listOf(35))
        assertTrue(twentyOneGoal?.completed ?: false)

        val twoADayGoal = earnings?.goals?.firstOrNull{ it.name == TWO_A_DAY }
        assertNotNull(twoADayGoal)
        assertEquals(twoADayGoal?.progress_components, listOf(100, 100, 100, 100, 100, 100, 100))
        assertTrue(twoADayGoal?.completed ?: false)

        val fourOfFourGoal = earnings?.goals?.firstOrNull{ it.name == FOUR_OUT_OF_FOUR }
        assertNotNull(fourOfFourGoal)
        assertEquals(fourOfFourGoal?.progress_components, listOf(100, 100, 100, 100))
        assertTrue(fourOfFourGoal?.completed ?: false)

        val allSessionsGoal = earnings?.goals?.firstOrNull{ it.name == TEST_SESSION }
        assertNotNull(allSessionsGoal)
        assertEquals(allSessionsGoal?.progress_components, listOf(35))

        // 28 .testSession goals at it.50 each,
        // two a day goal at $6
        // and 21 session goal at $5
        // 7 four of four goals at $1 each
        assertEquals(earnings?.cycle_earnings, "$32.00")
        assertEquals(earnings?.total_earnings, "$32.00")

        val summary = controller.getCurrentStudySummary()
        assertNotNull(summary)
        assertEquals(summary?.days_tested, 6)
        assertEquals(summary?.goals_met, 9)
        // This is the raw completed tests, we don't filter them based on if they were "within"
        // a study period or not, but it should never really happen as participants can only
        // complete a test during a specific time window within the study period
        assertEquals(summary?.tests_taken, 38)
        assertEquals(summary?.total_earnings, "$32.00")

        val details = controller.getCurrentEarningsDetails()
                assertNotNull(details)
        assertEquals(details?.total_earnings, "$32.00")
        assertEquals(details?.cycles?.size, 1)

        val studyPeriod1 = details?.cycles?.firstOrNull()
        assertNotNull(studyPeriod1)
        assertEquals(studyPeriod1?.cycle, 0)
        assertEquals(studyPeriod1?.total, "$32.00")
        assertEquals(studyPeriod1?.start_date, (studyPeriod1Start().millis / 1000L))
        assertEquals(studyPeriod1?.end_date, (studyPeriod1End().millis / 1000L))

        val goals = studyPeriod1?.details
        assertEquals(goals?.size, 4)
    }

    @Test
    fun testWeekDay181() {
        // Test if user's data is duplicated or outside the study period window that we don't give them more money than the max
        controller.completedTests = listOf(
            createTest(0, 0), createTest(20, 0), createTest(8, 0), createTest(180, 0),
            createTest(0, 1, 0), createTest(0, 1, 0), createTest(0, 1, 1), createTest(0, 1, 2),  createTest(0, 1, 3),
            createTest(0, 2, 0), createTest(0, 2, 0), createTest(0, 2, 1), createTest(0, 2, 2), createTest(0, 2, 3),
            createTest(0, 3, 0), createTest(0, 3, 0), createTest(0, 3, 1), createTest(0, 3, 2), createTest(0, 3, 3),
            createTest(0, 4, 0), createTest(0, 4, 0), createTest(0, 4, 1), createTest(0, 4, 2), createTest(0, 4, 3),
            createTest(0, 5, 0), createTest(0, 5, 0), createTest(0, 5, 1), createTest(0, 5, 2), createTest(0, 5, 3),
            createTest(0, 6, 0), createTest(0, 6, 0), createTest(0, 6, 1), createTest(0, 6, 2), createTest(0, 6, 3),
            createTest(0, 7, 0), createTest(0, 7, 0), createTest(0, 7, 1), createTest(0, 7, 2), createTest(0, 7, 3))

        controller.overridingNow = startDate().plusDays(181).withTimeAtStartOfDay().plusMinutes(1)

        val earnings = controller.getCurrentEarningsOverview()
        assertNotNull(earnings)

        val twentyOneGoal = earnings?.goals?.firstOrNull{ it.name == TWENTY_ONE_SESSIONS }
        assertNotNull(twentyOneGoal)
        assertEquals(twentyOneGoal?.progress_components, listOf(35))
        assertTrue(twentyOneGoal?.completed ?: false)

        val twoADayGoal = earnings?.goals?.firstOrNull{ it.name == TWO_A_DAY }
        assertNotNull(twoADayGoal)
        assertEquals(twoADayGoal?.progress_components, listOf(100, 100, 100, 100, 100, 100, 100))
        assertTrue(twoADayGoal?.completed ?: false)

        val fourOfFourGoal = earnings?.goals?.firstOrNull{ it.name == FOUR_OUT_OF_FOUR }
        assertNull(fourOfFourGoal)

        val allSessionsGoal = earnings?.goals?.firstOrNull{ it.name == TEST_SESSION }
        assertNotNull(allSessionsGoal)
        assertEquals(allSessionsGoal?.progress_components, listOf(35))

        // 28 .testSession goals at it.50 each,
        // two a day goal at $6
        // and 21 session goal at $5
        // 7 four of four goals at $1 each
        assertEquals(earnings?.cycle_earnings, "$32.00")
        assertEquals(earnings?.total_earnings, "$32.00")

        val summary = controller.getCurrentStudySummary()
        assertNotNull(summary)
        assertEquals(summary?.days_tested, 7)
        assertEquals(summary?.goals_met, 9)
        // This is the raw completed tests, we don't filter them based on if they were "within"
        // a study period or not, but it should never really happen as participants can only
        // complete a test during a specific time window within the study period
        assertEquals(summary?.tests_taken, 38)
        assertEquals(summary?.total_earnings, "$32.00")

        val details = controller.getCurrentEarningsDetails()
                assertNotNull(details)
        assertEquals(details?.total_earnings, "$32.00")
        assertEquals(details?.cycles?.size, 1)

        val studyPeriod1 = details?.cycles?.firstOrNull()
        assertNotNull(studyPeriod1)
        assertEquals(studyPeriod1?.cycle, 0)
        assertEquals(studyPeriod1?.total, "$32.00")
        assertEquals(studyPeriod1?.start_date, (studyPeriod1Start().millis / 1000L))
        assertEquals(studyPeriod1?.end_date, (studyPeriod1End().millis / 1000L))

        val goals = studyPeriod1?.details
        assertEquals(goals?.size, 4)
    }

    @Test
    fun testDay182NotStarted() {
        // User should not get earnings for day 0, which is the tutorial
        controller.completedTests = listOf(
            createTest(0, 0), createTest(20, 0), createTest(8, 0), createTest(180, 0),
            createTest(0, 1, 0), createTest(0, 1, 0), createTest(0, 1, 1), createTest(0, 1, 2),  createTest(0, 1, 3),
            createTest(0, 2, 0), createTest(0, 2, 0), createTest(0, 2, 1), createTest(0, 2, 2), createTest(0, 2, 3),
            createTest(0, 3, 0), createTest(0, 3, 0), createTest(0, 3, 1), createTest(0, 3, 2), createTest(0, 3, 3),
            createTest(0, 4, 0), createTest(0, 4, 0), createTest(0, 4, 1), createTest(0, 4, 2), createTest(0, 4, 3),
            createTest(0, 5, 0), createTest(0, 5, 0), createTest(0, 5, 1), createTest(0, 5, 2), createTest(0, 5, 3),
            createTest(0, 6, 0), createTest(0, 6, 0), createTest(0, 6, 1), createTest(0, 6, 2), createTest(0, 6, 3),
            createTest(0, 7, 0), createTest(0, 7, 0), createTest(0, 7, 1), createTest(0, 7, 2), createTest(0, 7, 3))
        controller.overridingNow = startDate().plusDays(182).plusMinutes(1)

        val earnings = controller.getCurrentEarningsOverview()
        assertNotNull(earnings)

        val twentyOneGoal = earnings?.goals?.firstOrNull{ it.name == TWENTY_ONE_SESSIONS }
        assertNotNull(twentyOneGoal)
        assertEquals(twentyOneGoal?.progress, 0)

        val twoADayGoal = earnings?.goals?.firstOrNull{ it.name == TWO_A_DAY }
        assertNotNull(twoADayGoal)
        assertEquals(twoADayGoal?.progress, 0)

        val fourOfFourGoal = earnings?.goals?.firstOrNull{ it.name == FOUR_OUT_OF_FOUR }
        assertNotNull(fourOfFourGoal)
        assertEquals(fourOfFourGoal?.progress_components, listOf(0, 0, 0, 0))
        assertFalse(fourOfFourGoal?.completed ?: true)

        val allSessionsGoal = earnings?.goals?.firstOrNull{ it.name == TEST_SESSION }
        assertNotNull(allSessionsGoal)
        assertEquals(allSessionsGoal?.progress, 0)

        assertEquals(earnings?.cycle_earnings, "$0.00")
        assertEquals(earnings?.total_earnings, "$32.00")

        val summary = controller.getCurrentStudySummary()
        assertNotNull(summary)
        assertEquals(summary?.days_tested, 7)
        assertEquals(summary?.goals_met, 9)
        // This is the raw completed tests, we don't filter them based on if they were "within"
        // a study period or not, but it should never really happen as participants can only
        // complete a test during a specific time window within the study period
        assertEquals(summary?.tests_taken, 38)
        assertEquals(summary?.total_earnings, "$32.00")

        val details = controller.getCurrentEarningsDetails()
                assertNotNull(details)
        assertEquals(details?.total_earnings, "$32.00")
        assertEquals(details?.cycles?.size, 2)

        val studyPeriod1 = details?.cycles?.firstOrNull()
        assertNotNull(studyPeriod1)
        assertEquals(studyPeriod1?.cycle, 0)
        assertEquals(studyPeriod1?.total, "$32.00")
        assertEquals(studyPeriod1?.start_date, (studyPeriod1Start().millis / 1000L))
        assertEquals(studyPeriod1?.end_date, (studyPeriod1End().millis / 1000L))
        val goals1 = studyPeriod1?.details
                assertEquals(goals1?.size, 4)

        val studyPeriod2 = details?.cycles?.get(1)
        assertNotNull(studyPeriod2)
        assertEquals(studyPeriod2?.cycle, 1)
        assertEquals(studyPeriod2?.total, "$0.00")
        assertEquals(studyPeriod2?.start_date, (studyPeriod2Start().millis / 1000L))
        assertEquals(studyPeriod2?.end_date, (studyPeriod2End().millis / 1000L))
        val goals2 = studyPeriod2?.details
                assertEquals(goals2?.size, 4)
    }

    @Test
    fun testDay182CompletedDay1iOS() {
        controller.completedTests = listOf(
            createTest(0, 0),
            createTest(0, 1, 0), createTest(0, 1, 1), createTest(0, 1, 2), createTest(0, 1, 3),
            createTest(0, 2, 0), createTest(0, 2, 1), createTest(0, 2, 2), createTest(0, 2, 3),
            createTest(0, 3, 0), createTest(0, 3, 1), createTest(0, 3, 2), createTest(0, 3, 3),
            createTest(0, 4, 0), createTest(0, 4, 1), createTest(0, 4, 2), createTest(0, 4, 3),
            createTest(0, 5, 0), createTest(0, 5, 1), createTest(0, 5, 2), createTest(0, 5, 3),
            createTest(0, 6, 0), createTest(0, 6, 1), createTest(0, 6, 2), createTest(0, 6, 3),
            createTest(0, 7, 0), createTest(0, 7, 1), createTest(0, 7, 2), createTest(0, 7, 3),
            // iOS mis-labels study period 1 as week 25, make sure algo accounts for it
            createTest(25, 0, 0), createTest(25, 0, 1), createTest(25, 0, 2), createTest(25, 0, 3))

        controller.overridingNow = startDate().plusDays(182).plusMinutes(1)

        val earnings = controller.getCurrentEarningsOverview()
        assertNotNull(earnings)

        val twentyOneGoal = earnings?.goals?.firstOrNull{ it.name == TWENTY_ONE_SESSIONS }
        assertNotNull(twentyOneGoal)
        assertEquals(twentyOneGoal?.progress_components, listOf(4))

        val twoADayGoal = earnings?.goals?.firstOrNull{ it.name == TWO_A_DAY }
        assertNotNull(twoADayGoal)
        assertEquals(twoADayGoal?.progress_components, listOf(100, 0, 0, 0, 0, 0, 0))

        val fourOfFourGoal = earnings?.goals?.firstOrNull{ it.name == FOUR_OUT_OF_FOUR }
        assertNotNull(fourOfFourGoal)
        assertEquals(fourOfFourGoal?.progress_components, listOf(100, 100, 100, 100))
        assertTrue(fourOfFourGoal?.completed ?: false)

        val allSessionsGoal = earnings?.goals?.firstOrNull{ it.name == TEST_SESSION }
        assertNotNull(allSessionsGoal)
        assertEquals(allSessionsGoal?.progress_components, listOf(4))

        // 4 at it.50 for all sessions, and four of four complete at $1
        assertEquals(earnings?.cycle_earnings, "$3.00")
        assertEquals(earnings?.total_earnings, "$35.00")

        val summary = controller.getCurrentStudySummary()
        assertNotNull(summary)
        assertEquals(summary?.days_tested, 7)
        assertEquals(summary?.goals_met, 10)
        // This is the raw completed tests, we don't filter them based on if they were "within"
        // a study period or not, but it should never really happen as participants can only
        // complete a test during a specific time window within the study period
        assertEquals(summary?.tests_taken, 32)
        assertEquals(summary?.total_earnings, "$35.00")

        val details = controller.getCurrentEarningsDetails()
                assertNotNull(details)
        assertEquals(details?.total_earnings, "$35.00")
        assertEquals(details?.cycles?.size, 2)

        val studyPeriod1 = details?.cycles?.firstOrNull()
        assertNotNull(studyPeriod1)
        assertEquals(studyPeriod1?.cycle, 0)
        assertEquals(studyPeriod1?.total, "$32.00")
        assertEquals(studyPeriod1?.start_date, (studyPeriod1Start().millis / 1000L))
        assertEquals(studyPeriod1?.end_date, (studyPeriod1End().millis / 1000L))
        val goals1 = studyPeriod1?.details
                assertEquals(goals1?.size, 4)

        val studyPeriod2 = details?.cycles?.get(1)
        assertNotNull(studyPeriod2)
        assertEquals(studyPeriod2?.cycle, 1)
        assertEquals(studyPeriod2?.total, "$3.00")
        assertEquals(studyPeriod2?.start_date, (studyPeriod2Start().millis / 1000L))
        assertEquals(studyPeriod2?.end_date, (studyPeriod2End().millis / 1000L))
        val goals2 = studyPeriod2?.details
                assertEquals(goals2?.size, 4)
    }

    @Test
    fun testDay182CompletedDay1Android() {
        controller.completedTests = listOf(
            createTest(0, 0),
            createTest(0, 1, 0), createTest(0, 1, 1), createTest(0, 1, 2), createTest(0, 1, 3),
            createTest(0, 2, 0), createTest(0, 2, 1), createTest(0, 2, 2), createTest(0, 2, 3),
            createTest(0, 3, 0), createTest(0, 3, 1), createTest(0, 3, 2), createTest(0, 3, 3),
            createTest(0, 4, 0), createTest(0, 4, 1), createTest(0, 4, 2), createTest(0, 4, 3),
            createTest(0, 5, 0), createTest(0, 5, 1), createTest(0, 5, 2), createTest(0, 5, 3),
            createTest(0, 6, 0), createTest(0, 6, 1), createTest(0, 6, 2), createTest(0, 6, 3),
            createTest(0, 7, 0), createTest(0, 7, 1), createTest(0, 7, 2), createTest(0, 7, 3),
            // iOS mis-labels study period 1 as week 25, make sure algo accounts for it
            createTest(26, 0, 0), createTest(26, 0, 1), createTest(26, 0, 2), createTest(26, 0, 3))

        controller.overridingNow = startDate().plusDays(182).plusMinutes(1)

        val earnings = controller.getCurrentEarningsOverview()
        assertNotNull(earnings)

        val twentyOneGoal = earnings?.goals?.firstOrNull{ it.name == TWENTY_ONE_SESSIONS }
        assertNotNull(twentyOneGoal)
        assertEquals(twentyOneGoal?.progress_components, listOf(4))

        val twoADayGoal = earnings?.goals?.firstOrNull{ it.name == TWO_A_DAY }
        assertNotNull(twoADayGoal)
        assertEquals(twoADayGoal?.progress_components, listOf(100, 0, 0, 0, 0, 0, 0))

        val fourOfFourGoal = earnings?.goals?.firstOrNull{ it.name == FOUR_OUT_OF_FOUR }
        assertNotNull(fourOfFourGoal)
        assertEquals(fourOfFourGoal?.progress_components, listOf(100, 100, 100, 100))
        assertTrue(fourOfFourGoal?.completed ?: false)

        val allSessionsGoal = earnings?.goals?.firstOrNull{ it.name == TEST_SESSION }
        assertNotNull(allSessionsGoal)
        assertEquals(allSessionsGoal?.progress_components, listOf(4))

        // 4 at it.50 for all sessions, and four of four complete at $1
        assertEquals(earnings?.cycle_earnings, "$3.00")
        assertEquals(earnings?.total_earnings, "$35.00")

        val summary = controller.getCurrentStudySummary()
        assertNotNull(summary)
        assertEquals(summary?.days_tested, 7)
        assertEquals(summary?.goals_met, 10)
        // This is the raw completed tests, we don't filter them based on if they were "within"
        // a study period or not, but it should never really happen as participants can only
        // complete a test during a specific time window within the study period
        assertEquals(summary?.tests_taken, 32)
        assertEquals(summary?.total_earnings, "$35.00")

        val details = controller.getCurrentEarningsDetails()
                assertNotNull(details)
        assertEquals(details?.total_earnings, "$35.00")
        assertEquals(details?.cycles?.size, 2)

        val studyPeriod1 = details?.cycles?.firstOrNull()
        assertNotNull(studyPeriod1)
        assertEquals(studyPeriod1?.cycle, 0)
        assertEquals(studyPeriod1?.total, "$32.00")
        assertEquals(studyPeriod1?.start_date, (studyPeriod1Start().millis / 1000L))
        assertEquals(studyPeriod1?.end_date, (studyPeriod1End().millis / 1000L))
        val goals1 = studyPeriod1?.details
                assertEquals(goals1?.size, 4)

        val studyPeriod2 = details?.cycles?.get(1)
        assertNotNull(studyPeriod2)
        assertEquals(studyPeriod2?.cycle, 1)
        assertEquals(studyPeriod2?.total, "$3.00")
        assertEquals(studyPeriod2?.start_date, (studyPeriod2Start().millis / 1000L))
        assertEquals(studyPeriod2?.end_date, (studyPeriod2End().millis / 1000L))
        val goals2 = studyPeriod2?.details
                assertEquals(goals2?.size, 4)
    }

    @Test
    fun testWeek26UnfinishediOS() {
        controller.completedTests = listOf(
            createTest(0, 0),
            // iOS mis-labels study period 1 as week 25, make sure algo accounts for it
            createTest(25, 0, 0), createTest(25, 0, 1),
            createTest(25, 1, 0), createTest(25, 1, 1),
            createTest(25, 2, 0), createTest(25, 2, 1),
            createTest(25, 3, 0), createTest(25, 3, 1),
            createTest(25, 4, 0), createTest(25, 4, 1),
            createTest(25, 5, 0), createTest(25, 5, 1),
            createTest(25, 6, 0))

        controller.overridingNow = startDate().plusDays(188).withTimeAtStartOfDay().plusMinutes(1)

        val earnings = controller.getCurrentEarningsOverview()
        assertNotNull(earnings)

        val twentyOneGoal = earnings?.goals?.firstOrNull{ it.name == TWENTY_ONE_SESSIONS }
        assertNotNull(twentyOneGoal)
        assertEquals(twentyOneGoal?.progress_components, listOf(13))
        assertFalse(twentyOneGoal?.completed ?: true)

        val twoADayGoal = earnings?.goals?.firstOrNull{ it.name == TWO_A_DAY }
        assertNotNull(twoADayGoal)
        assertEquals(twoADayGoal?.progress_components, listOf(100, 100, 100, 100, 100, 100, 0))
        assertFalse(twoADayGoal?.completed ?: true)

        val fourOfFourGoal = earnings?.goals?.firstOrNull{ it.name == FOUR_OUT_OF_FOUR }
        assertNotNull(fourOfFourGoal)
        assertEquals(fourOfFourGoal?.progress_components, listOf(100, 0, 0, 0))
        assertFalse(fourOfFourGoal?.completed ?: true)

        val allSessionsGoal = earnings?.goals?.firstOrNull{ it.name == TEST_SESSION }
        assertNotNull(allSessionsGoal)
        assertEquals(allSessionsGoal?.progress_components, listOf(13))

        // 14 .testSession goals at it.50 each
        assertEquals(earnings?.cycle_earnings, "$6.50")
        assertEquals(earnings?.total_earnings, "$6.50")

        val summary = controller.getCurrentStudySummary()
        assertNotNull(summary)
        assertEquals(summary?.days_tested, 13)
        assertEquals(summary?.goals_met, 0)
        // This is the raw completed tests, we don't filter them based on if they were "within"
        // a study period or not, but it should never really happen as participants can only
        // complete a test during a specific time window within the study period
        assertEquals(summary?.tests_taken, 13)
        assertEquals(summary?.total_earnings, "$6.50")

        val details = controller.getCurrentEarningsDetails()
                assertNotNull(details)
        assertEquals(details?.total_earnings, "$6.50")
        assertEquals(details?.cycles?.size, 2)

        val studyPeriod1 = details?.cycles?.firstOrNull()
        assertNotNull(studyPeriod1)
        assertEquals(studyPeriod1?.cycle, 0)
        assertEquals(studyPeriod1?.total, "$0.00")
        assertEquals(studyPeriod1?.start_date, (studyPeriod1Start().millis / 1000L))
        assertEquals(studyPeriod1?.end_date, (studyPeriod1End().millis / 1000L))
        val goals1 = studyPeriod1?.details
                assertEquals(goals1?.size, 4)

        val studyPeriod2 = details?.cycles?.get(1)
        assertNotNull(studyPeriod2)
        assertEquals(studyPeriod2?.cycle, 1)
        assertEquals(studyPeriod2?.total, "$6.50")
        assertEquals(studyPeriod2?.start_date, (studyPeriod2Start().millis / 1000L))
        assertEquals(studyPeriod2?.end_date, (studyPeriod2End().millis / 1000L))
        val goals2 = studyPeriod2?.details
                assertEquals(goals2?.size, 4)
    }

    @Test
    fun testWeek26UnfinishedAndroid() {
        controller.completedTests = listOf(
            createTest(0, 0),
            createTest(26, 0, 0), createTest(26, 0, 1),
            createTest(26, 1, 0), createTest(26, 1, 1),
            createTest(26, 2, 0), createTest(26, 2, 1),
            createTest(26, 3, 0), createTest(26, 3, 1),
            createTest(26, 4, 0), createTest(26, 4, 1),
            createTest(26, 5, 0), createTest(26, 5, 1),
            createTest(26, 6, 0))

        controller.overridingNow = startDate().plusDays(188).withTimeAtStartOfDay().plusMinutes(1)

        val earnings = controller.getCurrentEarningsOverview()
        assertNotNull(earnings)

        val twentyOneGoal = earnings?.goals?.firstOrNull{ it.name == TWENTY_ONE_SESSIONS }
        assertNotNull(twentyOneGoal)
        assertEquals(twentyOneGoal?.progress_components, listOf(13))
        assertFalse(twentyOneGoal?.completed ?: true)

        val twoADayGoal = earnings?.goals?.firstOrNull{ it.name == TWO_A_DAY }
        assertNotNull(twoADayGoal)
        assertEquals(twoADayGoal?.progress_components, listOf(100, 100, 100, 100, 100, 100, 0))
        assertFalse(twoADayGoal?.completed ?: true)

        val fourOfFourGoal = earnings?.goals?.firstOrNull{ it.name == FOUR_OUT_OF_FOUR }
        assertNotNull(fourOfFourGoal)
        assertEquals(fourOfFourGoal?.progress_components, listOf(100, 0, 0, 0))
        assertFalse(fourOfFourGoal?.completed ?: true)

        val allSessionsGoal = earnings?.goals?.firstOrNull{ it.name == TEST_SESSION }
        assertNotNull(allSessionsGoal)
        assertEquals(allSessionsGoal?.progress_components, listOf(13))

        // 14 .testSession goals at it.50 each
        assertEquals(earnings?.cycle_earnings, "$6.50")
        assertEquals(earnings?.total_earnings, "$6.50")

        val summary = controller.getCurrentStudySummary()
        assertNotNull(summary)
        assertEquals(summary?.days_tested, 13)
        assertEquals(summary?.goals_met, 0)
        // This is the raw completed tests, we don't filter them based on if they were "within"
        // a study period or not, but it should never really happen as participants can only
        // complete a test during a specific time window within the study period
        assertEquals(summary?.tests_taken, 13)
        assertEquals(summary?.total_earnings, "$6.50")

        val details = controller.getCurrentEarningsDetails()
                assertNotNull(details)
        assertEquals(details?.total_earnings, "$6.50")
        assertEquals(details?.cycles?.size, 2)

        val studyPeriod1 = details?.cycles?.firstOrNull()
        assertNotNull(studyPeriod1)
        assertEquals(studyPeriod1?.cycle, 0)
        assertEquals(studyPeriod1?.total, "$0.00")
        assertEquals(studyPeriod1?.start_date, (studyPeriod1Start().millis / 1000L))
        assertEquals(studyPeriod1?.end_date, (studyPeriod1End().millis / 1000L))
        val goals1 = studyPeriod1?.details
                assertEquals(goals1?.size, 4)

        val studyPeriod2 = details?.cycles?.get(1)
        assertNotNull(studyPeriod2)
        assertEquals(studyPeriod2?.cycle, 1)
        assertEquals(studyPeriod2?.total, "$6.50")
        assertEquals(studyPeriod2?.start_date, (studyPeriod2Start().millis / 1000L))
        assertEquals(studyPeriod2?.end_date, (studyPeriod2End().millis / 1000L))
        val goals2 = studyPeriod2?.details
                assertEquals(goals2?.size, 4)
    }

    @Test
    fun testWeek26FinishediOS() {
        controller.completedTests = listOf(
            createTest(0, 0),
            createTest(25, 0, 0), createTest(25, 0, 1), createTest(25, 0, 2),
            createTest(25, 1, 0), createTest(25, 1, 1), createTest(25, 1, 2),
            createTest(25, 2, 0), createTest(25, 2, 1), createTest(25, 2, 2),
            createTest(25, 3, 0), createTest(25, 3, 1), createTest(25, 3, 2),
            createTest(25, 4, 0), createTest(25, 4, 1), createTest(25, 4, 2),
            createTest(25, 5, 0), createTest(25, 5, 1), createTest(25, 5, 2),
            createTest(25, 6, 0), createTest(25, 6, 1), createTest(25, 6, 2))

        controller.overridingNow = startDate().plusDays(188).withTimeAtStartOfDay().plusMinutes(1)

        val earnings = controller.getCurrentEarningsOverview()
        assertNotNull(earnings)

        val twentyOneGoal = earnings?.goals?.firstOrNull{ it.name == TWENTY_ONE_SESSIONS }
        assertNotNull(twentyOneGoal)
        assertEquals(twentyOneGoal?.progress_components, listOf(21))
        assertTrue(twentyOneGoal?.completed ?: false)

        val twoADayGoal = earnings?.goals?.firstOrNull{ it.name == TWO_A_DAY }
        assertNotNull(twoADayGoal)
        assertEquals(twoADayGoal?.progress_components, listOf(100, 100, 100, 100, 100, 100, 100))
        assertTrue(twoADayGoal?.completed ?: false)

        val fourOfFourGoal = earnings?.goals?.firstOrNull{ it.name == FOUR_OUT_OF_FOUR }
        assertNotNull(fourOfFourGoal)
        assertEquals(fourOfFourGoal?.progress_components, listOf(100, 100, 100, 0))
        assertFalse(fourOfFourGoal?.completed ?: true)

        val allSessionsGoal = earnings?.goals?.firstOrNull{ it.name == TEST_SESSION }
        assertNotNull(allSessionsGoal)
        assertEquals(allSessionsGoal?.progress_components, listOf(21))

        // 21 .testSession goals at it.50 each,
        // two a day goal at $6
        // and 21 session goal at $5
        assertEquals(earnings?.cycle_earnings, "$21.50")
        assertEquals(earnings?.total_earnings, "$21.50")

        val summary = controller.getCurrentStudySummary()
        assertNotNull(summary)
        assertEquals(summary?.days_tested, 13)
        assertEquals(summary?.goals_met, 2)
        // This is the raw completed tests, we don't filter them based on if they were "within"
        // a study period or not, but it should never really happen as participants can only
        // complete a test during a specific time window within the study period
        assertEquals(summary?.tests_taken, 21)
        assertEquals(summary?.total_earnings, "$21.50")

        val details = controller.getCurrentEarningsDetails()
                assertNotNull(details)
        assertEquals(details?.total_earnings, "$21.50")
        assertEquals(details?.cycles?.size, 2)

        val studyPeriod1 = details?.cycles?.firstOrNull()
        assertNotNull(studyPeriod1)
        assertEquals(studyPeriod1?.cycle, 0)
        assertEquals(studyPeriod1?.total, "$0.00")
        assertEquals(studyPeriod1?.start_date, (studyPeriod1Start().millis / 1000L))
        assertEquals(studyPeriod1?.end_date, (studyPeriod1End().millis / 1000L))
        val goals1 = studyPeriod1?.details
                assertEquals(goals1?.size, 4)

        val studyPeriod2 = details?.cycles?.get(1)
        assertNotNull(studyPeriod2)
        assertEquals(studyPeriod2?.cycle, 1)
        assertEquals(studyPeriod2?.total, "$21.50")
        assertEquals(studyPeriod2?.start_date, (studyPeriod2Start().millis / 1000L))
        assertEquals(studyPeriod2?.end_date, (studyPeriod2End().millis / 1000L))
        val goals2 = studyPeriod2?.details
                assertEquals(goals2?.size, 4)
    }

    @Test
    fun testWeek26FinishedAndroid() {
        controller.completedTests = listOf(
            createTest(0, 0),
            createTest(26, 0, 0), createTest(26, 0, 1), createTest(26, 0, 2),
            createTest(26, 1, 0), createTest(26, 1, 1), createTest(26, 1, 2),
            createTest(26, 2, 0), createTest(26, 2, 1), createTest(26, 2, 2),
            createTest(26, 3, 0), createTest(26, 3, 1), createTest(26, 3, 2),
            createTest(26, 4, 0), createTest(26, 4, 1), createTest(26, 4, 2),
            createTest(26, 5, 0), createTest(26, 5, 1), createTest(26, 5, 2),
            createTest(26, 6, 0), createTest(26, 6, 1), createTest(26, 6, 2))

        controller.overridingNow = startDate().plusDays(188).withTimeAtStartOfDay().plusMinutes(1)

        val earnings = controller.getCurrentEarningsOverview()
        assertNotNull(earnings)

        val twentyOneGoal = earnings?.goals?.firstOrNull{ it.name == TWENTY_ONE_SESSIONS }
        assertNotNull(twentyOneGoal)
        assertEquals(twentyOneGoal?.progress_components, listOf(21))
        assertTrue(twentyOneGoal?.completed ?: false)

        val twoADayGoal = earnings?.goals?.firstOrNull{ it.name == TWO_A_DAY }
        assertNotNull(twoADayGoal)
        assertEquals(twoADayGoal?.progress_components, listOf(100, 100, 100, 100, 100, 100, 100))
        assertTrue(twoADayGoal?.completed ?: false)

        val fourOfFourGoal = earnings?.goals?.firstOrNull{ it.name == FOUR_OUT_OF_FOUR }
        assertNotNull(fourOfFourGoal)
        assertEquals(fourOfFourGoal?.progress_components, listOf(100, 100, 100, 0))
        assertFalse(fourOfFourGoal?.completed ?: true)

        val allSessionsGoal = earnings?.goals?.firstOrNull{ it.name == TEST_SESSION }
        assertNotNull(allSessionsGoal)
        assertEquals(allSessionsGoal?.progress_components, listOf(21))

        // 21 .testSession goals at it.50 each,
        // two a day goal at $6
        // and 21 session goal at $5
        assertEquals(earnings?.cycle_earnings, "$21.50")
        assertEquals(earnings?.total_earnings, "$21.50")

        val summary = controller.getCurrentStudySummary()
        assertNotNull(summary)
        assertEquals(summary?.days_tested, 13)
        assertEquals(summary?.goals_met, 2)
        // This is the raw completed tests, we don't filter them based on if they were "within"
        // a study period or not, but it should never really happen as participants can only
        // complete a test during a specific time window within the study period
        assertEquals(summary?.tests_taken, 21)
        assertEquals(summary?.total_earnings, "$21.50")

        val details = controller.getCurrentEarningsDetails()
                assertNotNull(details)
        assertEquals(details?.total_earnings, "$21.50")
        assertEquals(details?.cycles?.size, 2)

        val studyPeriod1 = details?.cycles?.firstOrNull()
        assertNotNull(studyPeriod1)
        assertEquals(studyPeriod1?.cycle, 0)
        assertEquals(studyPeriod1?.total, "$0.00")
        assertEquals(studyPeriod1?.start_date, (studyPeriod1Start().millis / 1000L))
        assertEquals(studyPeriod1?.end_date, (studyPeriod1End().millis / 1000L))
        val goals1 = studyPeriod1?.details
                assertEquals(goals1?.size, 4)

        val studyPeriod2 = details?.cycles?.get(1)
        assertNotNull(studyPeriod2)
        assertEquals(studyPeriod2?.cycle, 1)
        assertEquals(studyPeriod2?.total, "$21.50")
        assertEquals(studyPeriod2?.start_date, (studyPeriod2Start().millis / 1000L))
        assertEquals(studyPeriod2?.end_date, (studyPeriod2End().millis / 1000L))
        val goals2 = studyPeriod2?.details
                assertEquals(goals2?.size, 4)
    }

    @Test
    fun testWeek26PerfectiOS() {
        controller.completedTests = listOf(
            createTest(0, 0),
            createTest(0, 1, 0), createTest(0, 1, 1), createTest(0, 1, 2), createTest(0, 1, 3),
            createTest(0, 2, 0), createTest(0, 2, 1), createTest(0, 2, 2), createTest(0, 2, 3),
            createTest(0, 3, 0), createTest(0, 3, 1), createTest(0, 3, 2), createTest(0, 3, 3),
            createTest(0, 4, 0), createTest(0, 4, 1), createTest(0, 4, 2), createTest(0, 4, 3),
            createTest(0, 5, 0), createTest(0, 5, 1), createTest(0, 5, 2), createTest(0, 5, 3),
            createTest(0, 6, 0), createTest(0, 6, 1), createTest(0, 6, 2), createTest(0, 6, 3),
            createTest(0, 7, 0), createTest(0, 7, 1), createTest(0, 7, 2), createTest(0, 7, 3),
            createTest(25, 0, 0), createTest(25, 0, 1), createTest(25, 0, 2), createTest(25, 0, 3),
            createTest(25, 1, 0), createTest(25, 1, 1), createTest(25, 1, 2), createTest(25, 1, 3),
            createTest(25, 2, 0), createTest(25, 2, 1), createTest(25, 2, 2), createTest(25, 2, 3),
            createTest(25, 3, 0), createTest(25, 3, 1), createTest(25, 3, 2), createTest(25, 3, 3),
            createTest(25, 4, 0), createTest(25, 4, 1), createTest(25, 4, 2), createTest(25, 4, 3),
            createTest(25, 5, 0), createTest(25, 5, 1), createTest(25, 5, 2), createTest(25, 5, 3),
            createTest(25, 6, 0), createTest(25, 6, 1), createTest(25, 6, 2), createTest(25, 6, 3))

        controller.overridingNow = startDate().plusDays(188).withTimeAtStartOfDay().plusMinutes(1)

        val earnings = controller.getCurrentEarningsOverview()
        assertNotNull(earnings)

        val twentyOneGoal = earnings?.goals?.firstOrNull{ it.name == TWENTY_ONE_SESSIONS }
        assertNotNull(twentyOneGoal)
        assertEquals(twentyOneGoal?.progress_components, listOf(28))
        assertTrue(twentyOneGoal?.completed ?: false)

        val twoADayGoal = earnings?.goals?.firstOrNull{ it.name == TWO_A_DAY }
        assertNotNull(twoADayGoal)
        assertEquals(twoADayGoal?.progress_components, listOf(100, 100, 100, 100, 100, 100, 100))
        assertTrue(twoADayGoal?.completed ?: false)

        val fourOfFourGoal = earnings?.goals?.firstOrNull{ it.name == FOUR_OUT_OF_FOUR }
        assertNotNull(fourOfFourGoal)
        assertEquals(fourOfFourGoal?.progress_components, listOf(100, 100, 100, 100))
        assertTrue(fourOfFourGoal?.completed ?: false)

        val allSessionsGoal = earnings?.goals?.firstOrNull{ it.name == TEST_SESSION }
        assertNotNull(allSessionsGoal)
        assertEquals(allSessionsGoal?.progress_components, listOf(28))

        // 28 .testSession goals at it.50 each,
        // two a day goal at $6
        // and 21 session goal at $5
        // 7 four of four goals at $1 each
        assertEquals(earnings?.cycle_earnings, "$32.00")
        assertEquals(earnings?.total_earnings, "$64.00")

        val summary = controller.getCurrentStudySummary()
        assertNotNull(summary)
        assertEquals(summary?.days_tested, 13)
        assertEquals(summary?.goals_met, 18)
        // This is the raw completed tests, we don't filter them based on if they were "within"
        // a study period or not, but it should never really happen as participants can only
        // complete a test during a specific time window within the study period
        assertEquals(summary?.tests_taken, 56)
        assertEquals(summary?.total_earnings, "$64.00")

        val details = controller.getCurrentEarningsDetails()
                assertNotNull(details)
        assertEquals(details?.total_earnings, "$64.00")
        assertEquals(details?.cycles?.size, 2)

        val studyPeriod1 = details?.cycles?.firstOrNull()
        assertNotNull(studyPeriod1)
        assertEquals(studyPeriod1?.cycle, 0)
        assertEquals(studyPeriod1?.total, "$32.00")
        assertEquals(studyPeriod1?.start_date, (studyPeriod1Start().millis / 1000L))
        assertEquals(studyPeriod1?.end_date, (studyPeriod1End().millis / 1000L))
        val goals1 = studyPeriod1?.details
                assertEquals(goals1?.size, 4)

        val studyPeriod2 = details?.cycles?.get(1)
        assertNotNull(studyPeriod2)
        assertEquals(studyPeriod2?.cycle, 1)
        assertEquals(studyPeriod2?.total, "$32.00")
        assertEquals(studyPeriod2?.start_date, (studyPeriod2Start().millis / 1000L))
        assertEquals(studyPeriod2?.end_date, (studyPeriod2End().millis / 1000L))
        val goals2 = studyPeriod2?.details
                assertEquals(goals2?.size, 4)
    }

    @Test
    fun testWeek26PerfectAndroid() {
        controller.completedTests = listOf(
            createTest(0, 0),
            createTest(0, 1, 0), createTest(0, 1, 1), createTest(0, 1, 2), createTest(0, 1, 3),
            createTest(0, 2, 0), createTest(0, 2, 1), createTest(0, 2, 2), createTest(0, 2, 3),
            createTest(0, 3, 0), createTest(0, 3, 1), createTest(0, 3, 2), createTest(0, 3, 3),
            createTest(0, 4, 0), createTest(0, 4, 1), createTest(0, 4, 2), createTest(0, 4, 3),
            createTest(0, 5, 0), createTest(0, 5, 1), createTest(0, 5, 2), createTest(0, 5, 3),
            createTest(0, 6, 0), createTest(0, 6, 1), createTest(0, 6, 2), createTest(0, 6, 3),
            // Android labels week 0, day 7 as week 1, day 7, make sure algo accounts for it
            createTest(1, 7, 0), createTest(1, 7, 1), createTest(1, 7, 2), createTest(1, 7, 3),
            // Android labels the rest of the study periods as expected
            createTest(26, 0, 0), createTest(26, 0, 1), createTest(26, 0, 2), createTest(26, 0, 3),
            createTest(26, 1, 0), createTest(26, 1, 1), createTest(26, 1, 2), createTest(26, 1, 3),
            createTest(26, 2, 0), createTest(26, 2, 1), createTest(26, 2, 2), createTest(26, 2, 3),
            createTest(26, 3, 0), createTest(26, 3, 1), createTest(26, 3, 2), createTest(26, 3, 3),
            createTest(26, 4, 0), createTest(26, 4, 1), createTest(26, 4, 2), createTest(26, 4, 3),
            createTest(26, 5, 0), createTest(26, 5, 1), createTest(26, 5, 2), createTest(26, 5, 3),
            createTest(26, 6, 0), createTest(26, 6, 1), createTest(26, 6, 2), createTest(26, 6, 3))

        controller.overridingNow = startDate().plusDays(188).withTimeAtStartOfDay().plusMinutes(1)

        val earnings = controller.getCurrentEarningsOverview()
        assertNotNull(earnings)

        val twentyOneGoal = earnings?.goals?.firstOrNull{ it.name == TWENTY_ONE_SESSIONS }
        assertNotNull(twentyOneGoal)
        assertEquals(twentyOneGoal?.progress_components, listOf(28))
        assertTrue(twentyOneGoal?.completed ?: false)

        val twoADayGoal = earnings?.goals?.firstOrNull{ it.name == TWO_A_DAY }
        assertNotNull(twoADayGoal)
        assertEquals(twoADayGoal?.progress_components, listOf(100, 100, 100, 100, 100, 100, 100))
        assertTrue(twoADayGoal?.completed ?: false)

        val fourOfFourGoal = earnings?.goals?.firstOrNull{ it.name == FOUR_OUT_OF_FOUR }
        assertNotNull(fourOfFourGoal)
        assertEquals(fourOfFourGoal?.progress_components, listOf(100, 100, 100, 100))
        assertTrue(fourOfFourGoal?.completed ?: false)

        val allSessionsGoal = earnings?.goals?.firstOrNull{ it.name == TEST_SESSION }
        assertNotNull(allSessionsGoal)
        assertEquals(allSessionsGoal?.progress_components, listOf(28))

        // 28 .testSession goals at it.50 each,
        // two a day goal at $6
        // and 21 session goal at $5
        // 7 four of four goals at $1 each
        assertEquals(earnings?.cycle_earnings, "$32.00")
        assertEquals(earnings?.total_earnings, "$64.00")

        val summary = controller.getCurrentStudySummary()
        assertNotNull(summary)
        assertEquals(summary?.days_tested, 13)
        assertEquals(summary?.goals_met, 18)
        // This is the raw completed tests, we don't filter them based on if they were "within"
        // a study period or not, but it should never really happen as participants can only
        // complete a test during a specific time window within the study period
        assertEquals(summary?.tests_taken, 56)
        assertEquals(summary?.total_earnings, "$64.00")

        val details = controller.getCurrentEarningsDetails()
                assertNotNull(details)
        assertEquals(details?.total_earnings, "$64.00")
        assertEquals(details?.cycles?.size, 2)

        val studyPeriod1 = details?.cycles?.firstOrNull()
        assertNotNull(studyPeriod1)
        assertEquals(studyPeriod1?.cycle, 0)
        assertEquals(studyPeriod1?.total, "$32.00")
        assertEquals(studyPeriod1?.start_date, (studyPeriod1Start().millis / 1000L))
        assertEquals(studyPeriod1?.end_date, (studyPeriod1End().millis / 1000L))
        val goals1 = studyPeriod1?.details
                assertEquals(goals1?.size, 4)

        val studyPeriod2 = details?.cycles?.get(1)
        assertNotNull(studyPeriod2)
        assertEquals(studyPeriod2?.cycle, 1)
        assertEquals(studyPeriod2?.total, "$32.00")
        assertEquals(studyPeriod2?.start_date, (studyPeriod2Start().millis / 1000L))
        assertEquals(studyPeriod2?.end_date, (studyPeriod2End().millis / 1000L))
        val goals2 = studyPeriod2?.details
                assertEquals(goals2?.size, 4)
    }

    @Test
    fun testAchievement4Of4() {
        // Set to nil
        val oldTests = listOf(
                createTest(0, 0),
                createTest(0, 1, 0),
                createTest(0, 1, 1),
                createTest(0, 1, 2))

        controller.completedTests = oldTests
        controller.overridingNow = startDate().plusDays(1).plusMinutes(1)

        controller.getCurrentEarningsOverview()
        // This will force a comparison against the same earnings state
        controller.completedTests = oldTests
        val oldEarnings = controller.getCurrentEarningsOverview()

        val oldAchievements = oldEarnings?.new_achievements
        assertEquals(oldAchievements?.size, 0)

        // Just setting the tests will force a refresh on getCurrentEarningsOverview()
        val newTests = listOf(
                createTest(0, 0),
                createTest(0, 1, 0),
                createTest(0, 1, 1),
                createTest(0, 1, 2),
                createTest(0, 1, 3))

        controller.completedTests = newTests

        val newEarnings = controller.getCurrentEarningsOverview()
        val newAchievements = newEarnings?.new_achievements
        assertEquals(newAchievements?.size, 1)
        val achievement = newAchievements?.firstOrNull()
        assertEquals(achievement?.name, "4-out-of-4")
        assertEquals(achievement?.amount_earned, "$1.00")

        controller.completedTests = newTests
        val repeatEarnings = controller.getCurrentEarningsOverview()
        val repeatAchievements = repeatEarnings?.new_achievements
        assertEquals(repeatAchievements?.size, 0)
    }

    @Test
    fun testAchievement21Sessions() {
        // Set to nil
        val oldTests = listOf(
            createTest(0, 0),
            createTest(0, 1, 0), createTest(0, 1, 1), createTest(0, 1, 2),
            createTest(0, 2, 0), createTest(0, 2, 1), createTest(0, 2, 2),
            createTest(0, 3, 0), createTest(0, 3, 1), createTest(0, 3, 2),
            createTest(0, 4, 0), createTest(0, 4, 1), createTest(0, 4, 2),
            createTest(0, 5, 0), createTest(0, 5, 1), createTest(0, 5, 2),
            createTest(0, 6, 0), createTest(0, 6, 1), createTest(0, 6, 2),
            createTest(0, 7, 0), createTest(0, 7, 1))

        controller.completedTests = oldTests
        controller.overridingNow = startDate().plusDays(7).withTimeAtStartOfDay().plusMinutes(1)

        controller.getCurrentEarningsOverview()
        // This will force a comparison against the same earnings state
        controller.completedTests = oldTests
        val oldEarnings = controller.getCurrentEarningsOverview()
        val oldAchievements = oldEarnings?.new_achievements
        assertEquals(oldAchievements?.size, 0)

        val newTests = listOf(
            createTest(0, 0),
            createTest(0, 1, 0), createTest(0, 1, 1), createTest(0, 1, 2),
            createTest(0, 2, 0), createTest(0, 2, 1), createTest(0, 2, 2),
            createTest(0, 3, 0), createTest(0, 3, 1), createTest(0, 3, 2),
            createTest(0, 4, 0), createTest(0, 4, 1), createTest(0, 4, 2),
            createTest(0, 5, 0), createTest(0, 5, 1), createTest(0, 5, 2),
            createTest(0, 6, 0), createTest(0, 6, 1), createTest(0, 6, 2),
            createTest(0, 7, 0), createTest(0, 7, 1), createTest(0, 7, 2))

        controller.completedTests = newTests

        val newEarnings = controller.getCurrentEarningsOverview()
        val newAchievements = newEarnings?.new_achievements
        assertEquals(newAchievements?.size, 1)
        val achievement = newAchievements?.firstOrNull()
        assertEquals(achievement?.name, "21-sessions")
        assertEquals(achievement?.amount_earned, "$5.00")

        controller.completedTests = newTests
        val repeatEarnings = controller.getCurrentEarningsOverview()
        val repeatAchievements = repeatEarnings?.new_achievements
        assertEquals(repeatAchievements?.size, 0)
    }

    @Test
    fun testAchievement2ADay() {
        // Set to nil
        val oldTests = listOf(
            createTest(0, 0),
            createTest(0, 1, 0), createTest(0, 1, 1),
            createTest(0, 2, 0), createTest(0, 2, 1), createTest(0, 2, 2),
            createTest(0, 3, 0), createTest(0, 3, 1), createTest(0, 3, 2),
            createTest(0, 4, 0), createTest(0, 4, 1), createTest(0, 4, 2),
            createTest(0, 5, 0), createTest(0, 5, 1), createTest(0, 5, 2),
            createTest(0, 6, 0), createTest(0, 6, 1), createTest(0, 6, 2),
            createTest(0, 7, 0))

        controller.overridingNow = startDate().plusDays(7).withTimeAtStartOfDay().plusMinutes(1)

        controller.getCurrentEarningsOverview()
        // This will force a comparison against the same earnings state
        controller.completedTests = oldTests
        val oldEarnings = controller.getCurrentEarningsOverview()
        val oldAchievements = oldEarnings?.new_achievements
        assertEquals(oldAchievements?.size, 0)

        val newTests = listOf(
            createTest(0, 0),
            createTest(0, 1, 0), createTest(0, 1, 1),
            createTest(0, 2, 0), createTest(0, 2, 1), createTest(0, 2, 2),
            createTest(0, 3, 0), createTest(0, 3, 1), createTest(0, 3, 2),
            createTest(0, 4, 0), createTest(0, 4, 1), createTest(0, 4, 2),
            createTest(0, 5, 0), createTest(0, 5, 1), createTest(0, 5, 2),
            createTest(0, 6, 0), createTest(0, 6, 1), createTest(0, 6, 2),
            createTest(0, 7, 0), createTest(0, 7, 1), createTest(0, 7, 2))

        controller.completedTests = newTests
        val newEarnings = controller.getCurrentEarningsOverview()
        val newAchievements = newEarnings?.new_achievements
        assertEquals(newAchievements?.size, 1)
        val achievement = newAchievements?.firstOrNull()
        assertEquals(achievement?.name, "2-a-day")
        assertEquals(achievement?.amount_earned, "$6.00")

        controller.completedTests = newTests
        val repeatEarnings = controller.getCurrentEarningsOverview()
        val repeatAchievements = repeatEarnings?.new_achievements
        assertEquals(repeatAchievements?.size, 0)
    }

    @Test
    fun testAchievement21And4of4() {
        // Set to nil
        val oldTests = listOf(
            createTest(0, 0),
            createTest(0, 1, 0), createTest(0, 1, 1),
            createTest(0, 2, 0), createTest(0, 2, 1), createTest(0, 2, 2),
            createTest(0, 3, 0), createTest(0, 3, 1), createTest(0, 3, 2),
            createTest(0, 4, 0), createTest(0, 4, 1), createTest(0, 4, 2),
            createTest(0, 5, 0), createTest(0, 5, 1), createTest(0, 5, 2),
            createTest(0, 6, 0), createTest(0, 6, 1), createTest(0, 6, 2),
            createTest(0, 7, 0), createTest(0, 7, 1), createTest(0, 7, 2))

        controller.completedTests = oldTests
        controller.overridingNow = startDate().plusDays(7).withTimeAtStartOfDay().plusMinutes(1)

        controller.getCurrentEarningsOverview()
        // This will force a comparison against the same earnings state
        controller.completedTests = oldTests
        val oldEarnings = controller.getCurrentEarningsOverview()
        val oldAchievements = oldEarnings?.new_achievements
                assertEquals(oldAchievements?.size, 0)

        val newTests = listOf(
            createTest(0, 0),
            createTest(0, 1, 0), createTest(0, 1, 1),
            createTest(0, 2, 0), createTest(0, 2, 1), createTest(0, 2, 2),
            createTest(0, 3, 0), createTest(0, 3, 1), createTest(0, 3, 2),
            createTest(0, 4, 0), createTest(0, 4, 1), createTest(0, 4, 2),
            createTest(0, 5, 0), createTest(0, 5, 1), createTest(0, 5, 2),
            createTest(0, 6, 0), createTest(0, 6, 1), createTest(0, 6, 2),
            createTest(0, 7, 0), createTest(0, 7, 1), createTest(0, 7, 2), createTest(0, 7, 3))

        controller.completedTests = newTests
        val newEarnings = controller.getCurrentEarningsOverview()
        val newAchievements = newEarnings?.new_achievements
        assertEquals(newAchievements?.size, 2)
        val achievement = newAchievements?.firstOrNull { it.name == "21-sessions" }
        assertEquals(achievement?.name, "21-sessions")
        assertEquals(achievement?.amount_earned, "$5.00")

        val achievement2 = newAchievements?.firstOrNull { it.name == "4-out-of-4" }
        assertEquals(achievement2?.name, "4-out-of-4")
        assertEquals(achievement2?.amount_earned, "$1.00")

        controller.completedTests = newTests
        val repeatEarnings = controller.getCurrentEarningsOverview()
        val repeatAchievements = repeatEarnings?.new_achievements
        assertEquals(repeatAchievements?.size, 0)
    }
}

open class MockSageEarningsController(): SageEarningsController() {

    init {
        this.studyStartDate = DateTime.parse("2021-08-10T11:21:00.000-07:00")
    }

    override fun arcStartDays(): HashMap<Int, Int> {
        val days: java.util.HashMap<Int, Int> = object : java.util.HashMap<Int, Int>() {
            init {
                put(0, 0) // Test Cycle A
                put(1, 182) // Test Cycle B
                put(2, 182 * 2) // Test Cycle C
                put(3, 182 * 3) // Test Cycle D
                put(4, 182 * 4) // Test Cycle E
                put(5, 182 * 5) // Test Cycle F
                put(6, 182 * 6) // Test Cycle G
                put(7, 182 * 7) // Test Cycle H
                put(8, 182 * 8) // Test Cycle I
                put(9, 182 * 9) // Test Cycle I
            }
        }
        return days
    }

    public var overridingNow: DateTime = DateTime.now()
    override fun now(): DateTime {
        return overridingNow
    }
}