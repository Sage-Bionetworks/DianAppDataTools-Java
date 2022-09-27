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

import org.joda.time.DateTime
import org.joda.time.Days
import org.sagebionetworks.dian.datamigration.HmDataModel.*
import org.sagebionetworks.dian.datamigration.tools.adherence.earnings.EarningDetails
import org.sagebionetworks.dian.datamigration.tools.adherence.earnings.EarningOverview
import org.sagebionetworks.dian.datamigration.tools.adherence.earnings.EarningOverview.*
import java.lang.IllegalArgumentException
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

open class SageEarningsController {

    companion object {
        public const val fourFourEarning = "$1.00"
        public const val fourFourEarningVal = 1.0f
        public const val twoADayEarning = "$6.00"
        public const val twoADayEarningVal = 6.0f
        public const val twentyOneEarning = "$5.00"
        public const val twentyOneEarningVal = 5.0f
        public const val allSessionEarning = "$0.50"
        public const val allSessionEarningVal = 0.5f
    }

    private var lastCalculatedDate = DateTime.now()
    private var needsRecalculated = true
    public var completedTests: List<CompletedTest> = listOf()
        set(value) {
            field = filterAndConvertTests(value)
            needsRecalculated = true
        }

    private var earningsOverview: EarningOverview? = null
    private var earningsDetail: EarningDetails? = null
    private var studySummary: StudySummary? = null

    open var studyStartDate: DateTime? = null
    open var baselineTestComplete: CompletedTest? = null

    open fun now(): DateTime {
        return DateTime.now()
    }

    /**
     * The keys are the study burst index (0 = 1st study burst, 9 = 10th study burst)
     * The value is the days since start that the study burst is currently set to
     */
    open fun arcStartDays(): HashMap<Int, Int> {
        throw IllegalArgumentException("Must be implemented by sub-class")
        return hashMapOf()
    }

    /**
     * Alternative arc start days, allow you to provide a back-up set of arcStartDays,
     * that takes major re-scheduling into consideration.
     */
    var alternativeArcStartDays = hashMapOf<Int, Int>()

    open fun getCurrentEarningsOverview(): EarningOverview? {
        possibleRecalculateEarnings()
        return earningsOverview
    }

    open fun getCurrentEarningsDetails(): EarningDetails? {
        possibleRecalculateEarnings()
        return earningsDetail
    }

    open fun getCurrentStudySummary(): StudySummary? {
        possibleRecalculateEarnings()
        return studySummary
    }

    private fun possibleRecalculateEarnings() {
        val now = now()
        val isDayBehind = now.dayOfYear() != lastCalculatedDate.dayOfYear()
        if (needsRecalculated || isDayBehind ) {
            if (recalculateEarnings()) {
                lastCalculatedDate = now
                needsRecalculated = false
            }
        }
    }

    private fun recalculateEarnings(): Boolean {
        val current = currentPeriod() ?: run { return false }
        val studyStart = studyStartDate ?: run { return false }

        // This getter does some converting and filtering, so only compute once and pass
        // around to other functions that need it
        val completed = completedTests
        // Calculate all the goals for the current study period, and all previous
        val allGoals = calculateEarningsMap(completed, current)

        earningsOverview = calculateEarningsOverview(completed, current, allGoals)
        earningsDetail = calculateEarningsDetail(studyStart, completed, current, allGoals)
        studySummary = calculateStudySummary(completed, current, allGoals)

        return true
    }

    // The completed test data coming down from the web is inconsistent and includes the baseline test
    // First, filter out the baseline tutorial test, since that does not earn anything
    // Also, unfortunately on iOS there is a "week" field bug where all study period weeks,
    // are labeled incorrectly, being one off the correct one, so fix that by
    // snapping the week number to the expected.
    // This same fix will correct an issue on Android where week 0, day 7 shows up
    // as week 1, day 7, the week will snap back to week 0
    public fun filterAndConvertTests(tests: List<CompletedTest>): List<CompletedTest> {
        // If a week number is less than or equal to two weeks away, we snap to it
        val minAcceptableDistance = 2
        val expectedWeeks = arcStartDays().values.map { it / 7 }
        val backupWeeks = alternativeArcStartDays.values.map { it / 7 }
        val converted = mutableListOf<CompletedTest>()
        tests.forEach { test ->
            var newWeek = test.week
            // Shift a test closer to the expected week
            if (!expectedWeeks.contains(test.week)) {
                expectedWeeks.forEachIndexed { index, weekNum ->
                    if (abs(test.week - weekNum) <= minAcceptableDistance) {
                        newWeek = weekNum
                    } else if (index < backupWeeks.size) { // Check backups?
                        val backUpWeekNum = backupWeeks[index]
                        if (abs(test.week - backUpWeekNum) <= minAcceptableDistance) {
                            newWeek = weekNum  // Use expected ARC week, but include this test
                        }
                    }
                }
            }
            // Only add this test if it is not a duplicate
            if (!converted.any {
                it.week == test.week &&
                it.day == test.day &&
                it.session == test.session }) {
                converted.add(CompletedTest(
                        newWeek, test.day, test.session, test.completedOn))
            }
        }
        // Store baseline test separately
        baselineTestComplete = converted.firstOrNull {
            it.week ==0 && it.day == 0 && it.session == 0 }
        // Filter out baseline test, it should not be included in earnings
        return converted.filter { it.week != 0 || it.day != 0 }
    }

    private fun cycleIdx(week: Int): Int {
        val cycleStartDays = arcStartDays()
        cycleStartDays.keys.forEach {
            val value = cycleStartDays[it]
            if (week == value) {
                return it
            }
        }
        return 0
    }

    private fun earningValueStr(goalName: String): String {
        return when (goalName) {
            FOUR_OUT_OF_FOUR -> fourFourEarning
            TWENTY_ONE_SESSIONS -> twentyOneEarning
            TWO_A_DAY -> twoADayEarning
            else -> allSessionEarning // any single test session earnings
        }
    }

    open fun currentPeriod(): TestState? {
        val start = studyStartDate?.withTimeAtStartOfDay() ?: run {
            return null
        }

        val nowVal = now()
        val nowSecondsVal = nowVal.millis.toDouble() / 1000.0
        val studyPeriodStartDays = arcStartDays().values.sortedBy { it }
        val daysSinceStart = Days.daysBetween(start, nowVal).days

        // Get the arc start day that is for the current period
        var currentArcIdx = studyPeriodStartDays.filter { daysSinceStart >= it }.size - 1
        if (currentArcIdx < 0) {
            currentArcIdx = 0
        } else if (currentArcIdx >= studyPeriodStartDays.size) {
            currentArcIdx = studyPeriodStartDays.size - 1
        }

        // Check for baseline week which has slightly diff scheduling logic
        if (daysSinceStart <= 7) {
            return TestState(nowSecondsVal, 0, daysSinceStart, 0, "")
        }

        val weeksSinceStart = studyPeriodStartDays[currentArcIdx] / 7
        val dayOfWeek = daysSinceStart - (weeksSinceStart * 7)

        return TestState(nowSecondsVal, weeksSinceStart, dayOfWeek, 0, "")
    }

    data class TestState(
        var session_date: Double,
        var week: Int,
        var day: Int,
        var session: Int,
        var session_id: String)

    data class SageGoal(
        var goal: EarningOverview.Goal,
        var earnings: Float,
        var testState: TestState)

    data class SageDetailsGoal(
        var goal: EarningDetails.Goal,
        var earnings: Float,
        var testState: TestState)

    private fun calculateAllSessionsGoal(completed: List<CompletedTest>,
                                        testState: TestState): SageGoal {

        val completedCount = completed.filter {
            it.week == testState.week
        }.size

        val targetCompleteCount = 28 // 7 days with 4 sessions a day
        val name = TEST_SESSION
        val value = allSessionEarning
        val progress = (100 * (completedCount.toFloat() / targetCompleteCount.toFloat())).roundToInt()
        val progressComponents: List<Int> = listOf(completedCount)
        val amountEarned = "$1?"  // not sure what this is?
        val isComplete = false // this is not really a completable goal, always make it false
        val completedOn: Double? = null
        val earningsVal = allSessionEarningVal * min(completedCount, 28).toFloat()

        val goal = EarningOverview.Goal()
        goal.name = name
        goal.value = value
        goal.progress = progress
        goal.amount_earned = amountEarned
        goal.completed = isComplete
        goal.completed_on = completedOn?.toLong()
        goal.progress_components = progressComponents

        return SageGoal(goal, earningsVal, testState)
    }

    private fun calculateTwentyOneGoal(completed: List<CompletedTest>,
                                      testState: TestState): SageGoal {

        val completedCount = completed.filter {
            it.week == testState.week
        }.size

        var earningsVal = 0.0f
        val targetCompleteCount = 21
        val name = TWENTY_ONE_SESSIONS
        val value = twentyOneEarning
        val progress = 100 * (completedCount.toFloat() / targetCompleteCount.toFloat()).toInt()
        val progressComponents: List<Int> = listOf(completedCount)
        val amountEarned = "$1?"  // not sure what this is?
        val isComplete = completedCount >= targetCompleteCount
        var completedOn: Double? = null
        if (isComplete) {
            earningsVal = twentyOneEarningVal
            val lastCompleted = completed.filter {
                it.week == testState.week
            }.sortedBy { it.completedOn }.lastOrNull()
            completedOn = lastCompleted?.completedOn ?:
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

        return SageGoal(goal, earningsVal, testState)
    }

    private fun calculateTwoADayGoal(completed: List<CompletedTest>,
                                    testState: TestState): SageGoal {

        val targetDaySessionCompleteCout = 2
        val progressComponents: MutableList<Int> = mutableListOf(0, 0, 0, 0, 0, 0, 0)
        val startDayIdx = if (testState.week == 0) { 1 } else { 0 }
        val dayIdxAdjustment = if (testState.week == 0) { -1 } else { 0 }
        for (dayIdx in startDayIdx until (startDayIdx + 7)) {
            val count = completed.filter {
                it.week == testState.week && it.day == dayIdx
            }.size
            val progressIdx = dayIdx + dayIdxAdjustment
            if (progressIdx < progressComponents.size &&
                    count >= targetDaySessionCompleteCout) {
                progressComponents[progressIdx] = 100
            }
        }

        var earningsVal = 0.0f
        val targetCompleteCount = 7
        val completedCount = progressComponents.filter({ it >= 100 }).size
        val name = TWO_A_DAY
        val value = twoADayEarning
        val progress = 100 * (completedCount.toFloat() / targetCompleteCount.toFloat()).toInt()
        val amountEarned = "$1?"  // not sure what this is?
        val isComplete = completedCount >= targetCompleteCount
        var completedOn: Double? = null
        if (isComplete) {
            earningsVal = twoADayEarningVal
            val lastCompleted = completed.filter { it.week == testState.week }
                    .sortedBy { it.completedOn }.lastOrNull()
            completedOn = lastCompleted?.completedOn ?:
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

        return SageGoal(goal, earningsVal, testState)
    }

    private fun calculateFourFourGoal(completed: List<CompletedTest>,
                                     testState: TestState): SageGoal {

        var earningsVal = 0.0f
        var progressComponents: MutableList<Int> = mutableListOf(0, 0, 0, 0)
        val applicableTests = completed.filter {
            val isApplicable = it.week == testState.week && it.day == testState.day
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
            earningsVal = SageEarningsController.fourFourEarningVal
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

        return SageGoal(goal, earningsVal, testState)
    }

    private fun calculateAllGoals(completed: List<CompletedTest>,
                                 studyPeriod: TestState): List<SageGoal> {

        // These are the current week/day that show up on the earnings tab
        val twoADayGoal = calculateTwoADayGoal(completed, studyPeriod)
        val twentyOneGoal = calculateTwentyOneGoal(completed, studyPeriod)
        val allSessionsGoal = calculateAllSessionsGoal(completed, studyPeriod)

        // Calculate the other 4 of 4 goals for the period
        val startDay = if(studyPeriod.week == 0) { 1 } else { 0 }
        val all4of4Goals = (startDay until startDay + 7).map {
            val testState = TestState(studyPeriod.session_date, studyPeriod.week, it,
                    studyPeriod.session, studyPeriod.session_id)
            return@map calculateFourFourGoal(completed, testState)
        }

        val allGoals = mutableListOf(twoADayGoal, twentyOneGoal, allSessionsGoal)
        allGoals.addAll(all4of4Goals)

        return allGoals
    }

    // Returns an array where the index of each element corresponds to the study period id
    private fun calculateEarningsMap(completed: List<CompletedTest>,
                                    atAndBeforeCurrent: TestState): List<List<SageGoal>> {

        val nowVal = (now().millis / 1000L).toDouble()

        // Get all study periods before the current to get past earnings
        val studyPeriods = arcStartDays().values.sorted()
            .filter { (it / 7) <= atAndBeforeCurrent.week }
                // session_date isn't used, so just make it "now"
                // also, use day 1, as every study period has a day 1
                .map { TestState(nowVal, (it / 7), 1, 0, "") }

        return studyPeriods.map { calculateAllGoals(completed, it) }
    }

    private fun calculateEarningsOverview(completed: List<CompletedTest>,
                                         current: TestState,
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

        currentGoals.filter {  // Get first 4 of 4 goal
            it.goal.name == FOUR_OUT_OF_FOUR &&
            it.testState.week == current.week &&
            it.testState.day == current.day
        }.firstOrNull()?.let {
            overviewGoals.add(it)
        }

        val goals = overviewGoals.map { it.goal }
        var newAchievements = listOf<EarningOverview.Achievement>()
        earningsOverview?.let {
            newAchievements = calculateAchievements(it.goals, goals)
        }

        val cycle = max(0, allGoals.size - 1)

        val earnings = EarningOverview()
        earnings.total_earnings = totalEarningsStr
        earnings.cycle = cycle
        earnings.cycle_earnings = cycelEarningsStr
        earnings.goals = goals
        earnings.new_achievements = newAchievements

        return earnings
    }

    private fun calculateEarningsDetail(studyStart: DateTime,
                                       completedTests: List<CompletedTest>,
                                       current: TestState,
                                       allGoals: List<List<SageGoal>>): EarningDetails {

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
            val periodStartDayOffset = arcStartDays()[studyPeriodIdx] ?: 0
            val periodStart = studyStart.withTimeAtStartOfDay().plusDays(periodStartDayOffset)
            val periodEnd = studyStart.withTimeAtStartOfDay().plusDays(periodStartDayOffset + 7)

            val periodEarnings = periodGoals.map { it.earnings }.sum()
            val periodEarningsStr = String.format("$%.2f", periodEarnings)

            totalEarnings += periodEarnings

            val cycle = EarningDetails.Cycle()
            cycle.cycle = studyPeriodIdx
            cycle.total = periodEarningsStr
            cycle.start_date = periodStart.millis / 1000L
            cycle.end_date = periodEnd.millis / 1000L
            cycle.details = details
            cycles.add(cycle)
        }

        val totalEarningsStr = String.format("$%.2f", totalEarnings)

        val earnings = EarningDetails()
        earnings.total_earnings = totalEarningsStr
        earnings.cycles = cycles

        return earnings
    }

    private fun calculateStudySummary(completed: List<CompletedTest>,
                                     current: TestState,
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

        // Get the days through the current period, and then all other periods are 7 days of tests
        val dayIdx = if (current.week == 0) { current.day - 1 } else { current.day }
        var daysTested = min(7, dayIdx)
        if (allGoals.size > 1) {
            daysTested += (allGoals.size - 1) * 7
        }
        daysTested = max(0, daysTested) // fix baseline day being -1

        val testsTaken = completed.size

        val summary = StudySummary()
        summary.total_earnings = totalEarningsStr
        summary.tests_taken = testsTaken
        summary.days_tested = daysTested
        summary.goals_met = goalsMet
        return summary
    }

    public fun calculateTotalEarnings(): String {
        val current = currentPeriod() ?: run { return "" }
        // This getter does some converting and filtering, so only compute once and pass
        // around to other functions that need it
        val completed = completedTests
        val allGoals = calculateEarningsMap(completed, current)

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

    public data class StudySummary(
        var total_earnings: String = "$0.00",
        var tests_taken: Int = 0,
        var days_tested: Int = 0,
        var goals_met: Int = 0
    )
}

open class AdherenceSageEarningsController(): SageEarningsController() {

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
}