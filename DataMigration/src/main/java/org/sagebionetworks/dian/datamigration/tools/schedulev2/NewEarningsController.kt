package org.sagebionetworks.dian.datamigration.tools.schedulev2

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

import com.google.gson.Gson
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone.Companion.currentSystemDefault
import org.joda.time.DateTime
import org.joda.time.LocalTime
import org.sagebionetworks.bridge.rest.model.AdherenceRecord
import org.sagebionetworks.dian.datamigration.tools.adherence.SageScheduleController.Companion.ACTIVITY_EVENT_CREATE_SCHEDULE
import org.sagebionetworks.dian.datamigration.tools.adherence.earnings.EarningDetails
import org.sagebionetworks.dian.datamigration.tools.adherence.earnings.EarningOverview
import org.sagebionetworks.dian.datamigration.tools.adherence.earnings.EarningOverview.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


open class NewEarningsController {

    companion object {
        const val fourFourEarning = "$1.00"
        const val fourFourEarningVal = 1.0f
        const val twoADayEarning = "$6.00"
        const val twoADayEarningVal = 6.0f
        const val twentyOneEarning = "$5.00"
        const val twentyOneEarningVal = 5.0f
        const val allSessionEarning = "$0.50"
        const val allSessionEarningVal = 0.5f
        const val daysInStudyBurst = 7
        const val maxSessionsInWeek = 28
    }

    public var completedTests: List<CompletedTest> = listOf()
    val gson = Gson()

    open var studyBurstsSchedule: StudyBurstSession.StudyBurstSchedule? = null

    open fun initializeWithStudyBursts(
            schedule: StudyBurstSession.StudyBurstSchedule,
            adherence: List<AdherenceRecord>,
            practiceTestComplete: Boolean) {

        studyBurstsSchedule = schedule
        val allTests = adherence.mapNotNull {
            val clientDataJson = it.clientData ?: run { return@mapNotNull null }
            // Remove duplicates
            return@mapNotNull gson.fromJson(
                    ScheduleV2Migration.gson.toJsonTree(clientDataJson)
                            .asJsonObject, CompletedTest::class.java)
                    .copy(instanceGuid = it.instanceGuid)
        }
        val uniqueTests = mutableListOf<CompletedTest>()
        for (test in allTests) {
            if (!uniqueTests.any {
                it.eventId == test.eventId &&
                it.day == test.day &&
                it.session == test.session
            }) {
                uniqueTests.add(test)
            }
        }
        completedTests = uniqueTests
    }

    /**
     * @param studyBurst to filter completed tests on
     * @return Only completed test in the study burst
     */
    private fun completedTests(studyBurst: StudyBurstSession.StudyBurst): List<CompletedTest> {
        return completedTests.filter { it.eventId == studyBurst.originEventId }
    }

    /**
     * This should only be used to calculate earnings before the study burst starts
     */
    private fun createMockBeforeStudyBurstStarts(): StudyBurstSession.StudyBurst {
        val now = now().atTime(0, 0)
        val nowInstant = Clock.System.now().toString()
        return StudyBurstSession.StudyBurst(listOf(listOf(StudyBurstSession(
                "Practice", ACTIVITY_EVENT_CREATE_SCHEDULE, nowInstant, now, now)
        )))
    }

    open fun now(): LocalDate {
        return Clock.System.todayAt(timeZone())
    }

    open fun timeZone(): TimeZone {
        return currentSystemDefault()
    }

    fun recalculateEarnings(): List<String> {
        val studyBurstList = allStudyBurstsUntilNow()
        val current = studyBurstList.lastOrNull() ?: createMockBeforeStudyBurstStarts()

        val allGoals = when(studyBurstList.size) {
            // Study hasn't started yet, treat empty first study burst
            0 -> listOf(calculateAllGoals(current))
            // Calculate all the goals for the current study period, and all previous.
            else -> studyBurstList.map { calculateAllGoals(it) }
        }

        val details = calculateEarningsDetail(allGoals)
        val earningsList = details.cycles.map { it.total }

        return earningsList
    }

    // Remove duplicate tests based on study burst id and day/session in burst
    public fun filterAndConvertTests(tests: List<CompletedTest>): List<CompletedTest> {
        val converted = mutableListOf<CompletedTest>()
        tests.forEach { test ->
            // Only add this test if it is not a duplicate,
            // And not outside the expected days from study burst start idx
            if (test.day < daysInStudyBurst &&
                    !converted.any {
                        it.eventId == test.eventId &&
                                it.day == test.day &&
                                it.session == test.session }) {
                converted.add(CompletedTest(test.eventId, test.instanceGuid,
                        test.week, test.day, test.session, test.completedOn))
            }
        }
        return converted
    }

    private fun earningValueStr(goalName: String): String {
        return when (goalName) {
            FOUR_OUT_OF_FOUR -> fourFourEarning
            TWENTY_ONE_SESSIONS -> twentyOneEarning
            TWO_A_DAY -> twoADayEarning
            else -> allSessionEarning // any single test session earnings
        }
    }

    /**
     * @param studyBurst the study burst of interest
     * @return which day, bounded from 0 to (daysInStudyBurst - 1)
     */
    private fun dayInStudyBurst(studyBurst: StudyBurstSession.StudyBurst): Int {
        return max(0, min(studyBurst.startDate.daysUntil(now()), daysInStudyBurst - 1))
    }

    /**
     * @param studyBurst the study burst of interest
     * @return the number of days the user has tested in the study burst 0-7
     */
    private fun daysTestedInStudyBurst(studyBurst: StudyBurstSession.StudyBurst): Int {
        val dayIdxNoBounds = studyBurst.startDate.daysUntil(now())
        if (dayIdxNoBounds >= daysInStudyBurst) {
            return daysInStudyBurst
        } else if (dayIdxNoBounds <= 0) {
            return 0
        }
        return dayIdxNoBounds
    }

    /**
     * @return a list of study burst event IDs from the start of the study until now
     */
    open fun allStudyBurstsUntilNow(): List<StudyBurstSession.StudyBurst> {
        val now = now()
        return studyBurstsSchedule?.studyBurstList?.filter { now >= it.startDate } ?: listOf()
    }

    /**
     * @param studyBurst to calculate the goals for
     * @return the list of all earnings goals for the study burst
     */
    private fun calculateAllSessionsGoal(studyBurst: StudyBurstSession.StudyBurst): SageGoal {
        val completedInStudyBurst = completedTests(studyBurst)
        val completedCount = min(completedInStudyBurst.size, maxSessionsInWeek)
        val name = TEST_SESSION
        val value = allSessionEarning
        val progress = min(100, (100 * (completedCount.toFloat() / maxSessionsInWeek.toFloat())).roundToInt())
        val progressComponents: List<Int> = listOf(completedCount)
        val amountEarned = "$1?"  // not sure what this is?
        val isComplete = false // this is not really a completable goal, always make it false
        val completedOn: Double? = null
        val earningsVal = allSessionEarningVal * min(completedCount, maxSessionsInWeek).toFloat()

        val goal = EarningOverview.Goal()
        goal.name = name
        goal.value = value
        goal.progress = progress
        goal.amount_earned = amountEarned
        goal.completed = isComplete
        goal.completed_on = completedOn?.toLong()
        goal.progress_components = progressComponents

        return SageGoal(goal, earningsVal, studyBurst)
    }

    /**
     * @param studyBurst to calculate the goals for
     * @return the list of all earnings goals for the study burst
     */
    private fun calculateTwentyOneGoal(studyBurst: StudyBurstSession.StudyBurst): SageGoal {
        val completedInStudyBurst = completedTests(studyBurst)
        val completedCount = min(completedInStudyBurst.size, maxSessionsInWeek)
        var earningsVal = 0.0f
        val targetCompleteCount = 21
        val name = TWENTY_ONE_SESSIONS
        val value = twentyOneEarning
        val progress = min(100, 100 * (completedCount.toFloat() / targetCompleteCount.toFloat()).toInt())
        val progressComponents: List<Int> = listOf(completedCount)
        val amountEarned = "$1?"  // not sure what this is?
        val isComplete = completedCount >= targetCompleteCount
        var completedOn: Double? = null
        if (isComplete) {
            earningsVal = twentyOneEarningVal
            completedOn = completedInStudyBurst.sortedBy { it.completedOn }
                    .lastOrNull()?.completedOn ?: (System.currentTimeMillis() / 1000).toDouble()
        }

        val goal = EarningOverview.Goal()
        goal.name = name
        goal.value = value
        goal.progress = progress
        goal.amount_earned = amountEarned
        goal.completed = isComplete
        goal.completed_on = completedOn?.toLong()
        goal.progress_components = progressComponents

        return SageGoal(goal, earningsVal, studyBurst)
    }

    /**
     * @param studyBurst to calculate the goals for
     * @return the list of all earnings goals for the study burst
     */
    private fun calculateTwoADayGoal(studyBurst: StudyBurstSession.StudyBurst): SageGoal {
        val completedInStudyBurst = completedTests(studyBurst)
        val progressComponents: MutableList<Int> = mutableListOf(0, 0, 0, 0, 0, 0, 0)
        for (dayIdx in 0 until daysInStudyBurst) {
            val count = completedInStudyBurst.filter { it.day == dayIdx }.size
            // At least 2 sessions a day for the whole week
            if (dayIdx < progressComponents.size && count >= 2) {
                progressComponents[dayIdx] = 100
            }
        }

        var earningsVal = 0.0f
        val targetCompleteCount = daysInStudyBurst
        val completedCount = progressComponents.filter { it >= 100 }.size
        val name = TWO_A_DAY
        val value = twoADayEarning
        val progress = 100 * (completedCount.toFloat() / targetCompleteCount.toFloat()).toInt()
        val amountEarned = "$1?"  // not sure what this is?
        val isComplete = completedCount >= targetCompleteCount
        var completedOn: Double? = null
        if (isComplete) {
            earningsVal = twoADayEarningVal
            completedOn = completedInStudyBurst.sortedBy { it.completedOn }
                    .lastOrNull()?.completedOn ?: (System.currentTimeMillis() / 1000).toDouble()
        }

        val goal = EarningOverview.Goal()
        goal.name = name
        goal.value = value
        goal.progress = progress
        goal.amount_earned = amountEarned
        goal.completed = isComplete
        goal.completed_on = completedOn?.toLong()
        goal.progress_components = progressComponents

        return SageGoal(goal, earningsVal, studyBurst)
    }

    /**
     * @param studyBurst to calculate the goals for
     * @return the list of all earnings goals for the study burst
     */
    private fun calculateFourFourGoal(studyBurst: StudyBurstSession.StudyBurst, dayIdx: Int): SageGoal {
        val completedInStudyBurst = completedTests(studyBurst)
        var earningsVal = 0.0f
        val progressComponents: MutableList<Int> = mutableListOf(0, 0, 0, 0)
        val applicableTests = completedInStudyBurst.filter {
            val isApplicable = it.day == dayIdx
            if (isApplicable && it.session < progressComponents.size) {
                progressComponents[it.session] = 100
            }
            return@filter isApplicable
        }.sortedBy { it.completedOn }

        val targetCompleteCount = 4
        val name = FOUR_OUT_OF_FOUR
        val value = fourFourEarning
        val progress = 100 * (applicableTests.size.toFloat() / targetCompleteCount.toFloat()).toInt()
        val amountEarned = "$1?"  // not sure what this is?
        val isComplete = applicableTests.size >= targetCompleteCount
        var completedOn: Double? = null
        if (isComplete) {
            earningsVal = fourFourEarningVal
            completedOn = applicableTests.lastOrNull()?.completedOn ?:
                    (System.currentTimeMillis() / 1000).toDouble()
        }

        val goal = EarningOverview.Goal()
        goal.name = name
        goal.value = value
        goal.progress = progress
        goal.amount_earned = amountEarned
        goal.completed = isComplete
        goal.completed_on = completedOn?.toLong()
        goal.progress_components = progressComponents

        return FourOfFourSageGoal(goal, earningsVal, studyBurst, dayIdx)
    }

    /**
     * @param studyBurst to calculate the goals for
     * @return the list of all earnings goals for the study burst
     */
    private fun calculateAllGoals(studyBurst: StudyBurstSession.StudyBurst): List<SageGoal> {

        val twoADayGoal = calculateTwoADayGoal(studyBurst)
        val twentyOneGoal = calculateTwentyOneGoal(studyBurst)
        val allSessionsGoal = calculateAllSessionsGoal(studyBurst)

        // Calculate the other 4 of 4 goals for the study burst
        val all4of4Goals = (0 until daysInStudyBurst).map { dayIdx ->
            return@map calculateFourFourGoal(studyBurst, dayIdx)
        }

        val allGoals = mutableListOf(twoADayGoal, twentyOneGoal, allSessionsGoal)
        allGoals.addAll(all4of4Goals)

        return allGoals
    }

    private fun calculateEarningsOverview(
            currentStudyBurst: StudyBurstSession.StudyBurst,
            allGoals: List<List<SageGoal>>): EarningOverview {

        // These are the current week/day that show up on the earnings tab
        val currentGoals = allGoals.lastOrNull() ?: listOf()

        // Calculate the cycle earnings
        val cycleEarnings = currentGoals.map { it.earnings }.sum()
        val cycelEarningsStr = String.format("$%.2f", cycleEarnings)

        // Calculate the total earnings of all study periods
        var totalEarnings = 0.0f
        allGoals.forEach { goals ->
            totalEarnings += goals.map({ it.earnings }).sum()
        }
        val totalEarningsStr = String.format("$%.2f", totalEarnings)

        // For the earnings overview goals, we should only include today's 4 of 4 goal
        val overviewGoals = currentGoals.filter {
            it.goal.name != FOUR_OUT_OF_FOUR
        }.toMutableList()

        val currentDayIdx = dayInStudyBurst(currentStudyBurst)
        currentGoals.filter {  // Get first 4 of 4 goal
            val fourOfFourGoal = it as? FourOfFourSageGoal ?: run {
                return@filter false
            }
            return@filter it.goal.name == FOUR_OUT_OF_FOUR &&
                    it.studyBurst.startDate == currentStudyBurst.startDate &&
                    fourOfFourGoal.dayIdx == currentDayIdx
        }.firstOrNull()?.let {
            overviewGoals.add(it)
        }

        val goals = overviewGoals.map { it.goal }
//        var newAchievements = listOf<EarningOverview.Achievement>()
//        earningsOverview?.let {
//            newAchievements = calculateAchievements(it.goals, goals)
//        }

        val cycle = max(0, allGoals.size - 1)

        val earnings = EarningOverview()
        earnings.total_earnings = totalEarningsStr
        earnings.cycle = cycle
        earnings.cycle_earnings = cycelEarningsStr
        earnings.goals = goals
        //earnings.new_achievements = newAchievements

        return earnings
    }

    private fun calculateEarningsDetail(allGoals: List<List<SageGoal>>): EarningDetails {

        val detailNames = listOf(FOUR_OUT_OF_FOUR, TWO_A_DAY, TWENTY_ONE_SESSIONS, TEST_SESSION)
        var totalEarnings = 0.0f
        val cycles = mutableListOf<EarningDetails.Cycle>()
        for (studyPeriodIdx in 0 until allGoals.size) {
            val periodGoals = allGoals[studyPeriodIdx]
            val details = detailNames.map { name ->
                val value = earningValueStr(name)
                val goals = periodGoals.filter { it.goal.name == name }
                val completed = goals.filter { it.goal.completed == true }.size
                val amountEarned = goals.map { it.earnings }.sum()
                val progress = goals.map { it.goal.progress }.average().toInt()
                val amountEarnedStr = String.format("$%.2f", amountEarned)
                val cycleGoal = EarningDetails.Goal()
                cycleGoal.name = name
                cycleGoal.value = value
                cycleGoal.count_completed = completed
                cycleGoal.amount_earned = amountEarnedStr
                cycleGoal.progress = progress
                return@map cycleGoal
            }

            // These are only used in showing earnings details, doesn't need to perfectly match
            // what is stored in the app for StudyPeriod start/stop
            val periodStart = studyBurstsSchedule?.startDate(studyPeriodIdx) ?: now()
            val periodStartInstant = periodStart.atStartOfDayIn(timeZone())
            val periodEnd = studyBurstsSchedule?.endDate(studyPeriodIdx) ?: now()
            val periodEndInstant = periodEnd.atStartOfDayIn(timeZone())

            val periodEarnings = periodGoals.map { it.earnings }.sum()
            val periodEarningsStr = String.format("$%.2f", periodEarnings)

            totalEarnings += periodEarnings

            val cycle = EarningDetails.Cycle()
            cycle.cycle = studyPeriodIdx
            cycle.total = periodEarningsStr
            cycle.start_date = periodStartInstant.epochSeconds
            cycle.end_date = periodEndInstant.epochSeconds
            cycle.details = details
            cycles.add(cycle)
        }

        val totalEarningsStr = String.format("$%.2f", totalEarnings)

        val earnings = EarningDetails()
        earnings.total_earnings = totalEarningsStr
        earnings.cycles = cycles

        return earnings
    }

    private fun calculateStudySummary(
            currentStudyBurst: StudyBurstSession.StudyBurst,
            allGoals: List<List<SageGoal>>): StudySummary {

        var totalEarnings = 0.0f
        allGoals.forEach { goals ->
            totalEarnings += goals.map({ it.earnings }).sum()
        }
        val totalEarningsStr = String.format("$%.2f", totalEarnings)

        var goalsMet = 0
        allGoals.forEach { goals ->
            goalsMet += goals.filter { it.goal.completed == true }.size
        }


        // Get the days through the current period, and then all other periods are daysInStudyBurst days of tests
        var daysTested = daysTestedInStudyBurst(currentStudyBurst)
        if (allGoals.size > 1) {
            daysTested += (allGoals.size - 1) * daysInStudyBurst
        }
        daysTested = max(0, daysTested) // fix baseline day being -1

        val testsTaken = completedTests.size

        val summary = StudySummary()
        summary.total_earnings = totalEarningsStr
        summary.tests_taken = testsTaken
        summary.days_tested = daysTested
        summary.goals_met = goalsMet
        return summary
    }

    public fun calculateTotalEarnings(): String {
        val now = now()
        val studyBurstList = allStudyBurstsUntilNow()
        val current = studyBurstList.lastOrNull() ?: createMockBeforeStudyBurstStarts()
        // Calculate all the goals for the current study period, and all previous
        val allGoals = studyBurstList.map { calculateAllGoals(it) }

        var totalEarnings = 0.0f
        allGoals.forEach { goals ->
            totalEarnings += goals.map { it.earnings }.sum()
        }

        return String.format("$%.2f", totalEarnings)
    }

    private fun calculateAchievements(old: List<EarningOverview.Goal>,
                                      new: List<EarningOverview.Goal>): List<EarningOverview.Achievement> {

        val achievements = mutableListOf<EarningOverview.Achievement>()
        // There are three goals that earn you achievements
        listOf(TWO_A_DAY, TWENTY_ONE_SESSIONS, FOUR_OUT_OF_FOUR).forEach { name ->
            val newGoal = new.firstOrNull { it.name == name }
            val oldGoal = old.firstOrNull { it.name == name }
            if (oldGoal != null && newGoal != null &&
                    oldGoal.completed == false && newGoal.completed == true) {
                val achievement = EarningOverview.Achievement()
                achievement.name = name
                achievement.amount_earned = newGoal.value
                achievements.add(achievement)
            }
        }
        return achievements
    }

    open class SageGoal(
            var goal: EarningOverview.Goal,
            var earnings: Float,
            val studyBurst: StudyBurstSession.StudyBurst)

    open class FourOfFourSageGoal(
            goal: EarningOverview.Goal,
            earnings: Float,
            studyBurst: StudyBurstSession.StudyBurst,
            val dayIdx: Int): SageGoal(goal, earnings, studyBurst)
}

public data class StudySummary(
        var total_earnings: String = "$0.00",
        var tests_taken: Int = 0,
        var days_tested: Int = 0,
        var goals_met: Int = 0)

data class CompletedTest(
        /** The study burst event ID this completed test was in */
        public val eventId: String? = null,
        /** The instanceGuid of the session */
        public val instanceGuid: String? = null,

        @Deprecated("Using week to represent a completed test was unreliable, " +
                "as this week will change based on study coordinators " +
                "re-scheduling study burst start dates. Use eventId which is naturally," +
                "groups the completed tests by study burst. ", ReplaceWith("eventId"))
        public val week: Int,

        public val day: Int,
        public val session: Int,
        public val completedOn: Double)

data class StudyBurstSession(
        /** Same as ScheduledSession instanceGuid */
        val instanceGuid: String,
        /** The ScheduledSession.startEventId */
        val burstId: String?,
        /** The timestamp associated completing this session */
        val burstTimestamp: String,
        /** Calculated from ScheduledSession.startDate and ScheduledSession.startTime */
        val startDateTime: LocalDateTime,
        /** Calculated from startDateTime plus ScheduledSession.expiration */
        val endDateTime: LocalDateTime) {

data class StudyBurst(
        /** Sessions grouped by day, ordered by session startDate */
        val sessions: List<List<StudyBurstSession>> = listOf()) {

    /** Event ID that created this study burst */
    val originEventId: String? get() {
        return sessions.firstOrNull()?.firstOrNull()?.burstId
    }

    /** Start date of the first session in chronological order */
    val startDate: LocalDate get() {
        return sessions.first().first().startDateTime.date
    }
    /** Start date of the last session in chronological order */
    val endDate: LocalDate get() {
        return sessions.last().last().startDateTime.date
    }
}

data class StudyBurstSchedule(
        val studyBurstList: List<StudyBurst> = listOf()
) {
    fun startDate(studyBurstIdx: Int): LocalDate? {
        if (studyBurstIdx < 0 || studyBurstIdx >= studyBurstList.size) {
            return null
        }
        return studyBurstList[studyBurstIdx].startDate
    }

    fun endDate(studyBurstIdx: Int): LocalDate? {
        if (studyBurstIdx < 0 || studyBurstIdx >= studyBurstList.size) {
            return null
        }
        return studyBurstList[studyBurstIdx].endDate
    }}
}