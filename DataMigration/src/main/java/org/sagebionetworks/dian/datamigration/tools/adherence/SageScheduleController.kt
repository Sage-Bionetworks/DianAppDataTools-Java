package org.sagebionetworks.dian.datamigration.tools.adherence

import com.google.gson.Gson
import com.google.gson.internal.LinkedTreeMap
import org.joda.time.DateTime
import org.joda.time.LocalTime
import org.joda.time.Minutes
import org.joda.time.Weeks
import org.joda.time.format.DateTimeFormatterBuilder
import org.sagebionetworks.bridge.rest.model.*
import org.sagebionetworks.dian.datamigration.HmDataModel
import org.sagebionetworks.dian.datamigration.tools.rescheduler.TestSchedule
import org.sagebionetworks.dian.datamigration.tools.rescheduler.TestSchedule.TestScheduleSession
import org.sagebionetworks.dian.datamigration.tools.schedulev2.AdherenceToolV2
import org.sagebionetworks.dian.datamigration.tools.schedulev2.WakeSleepSchedule
import org.sagebionetworks.research.sagearc.SageEarningsController
import java.util.*

open class SageScheduleController {

    companion object {
        /**
         * The HM schedule model used timezone names that were not compatible with the
         * new Sage schedule system.
         * This map allows converting from the HM model over to the IANA timezones.
         * The keys were collected by crawling all the ARC user's schedules.
         */
        val IANA_TIMEZONE_MAP: Map<String, String> = mapOf(
                Pair("Mountain Standard Time", "US/Mountain"),
                Pair("Australian Western Standard Time", "Australia/Perth"),
                Pair("Australian Eastern Standard Time", "Australia/Brisbane"),
                Pair("Argentina Standard Time", "America/Argentina/Buenos_Aires"),
                Pair("Colombia Standard Time", "America/Bogota"),
                Pair("Pacific Standard Time", "US/Pacific"),
                Pair("Japan Standard Time", "Asia/Tokyo"),
                Pair("Eastern Standard Time", "US/Eastern"),
                Pair("Greenwich Mean Time", "UTC"),
                Pair("Central Standard Time", "US/Central"))

        /**
         * The number of days in a study burst, this value should only be referenced
         * when working with V1 of the schedule, as this is dynamic for the Sage V2 scheduling
         */
        val daysInFirstStudyBurst = 8
        val daysInAllStudyBursts = 7
        val sessionsInABurst = 28
        fun daysInStudyBurst(studyBurstIndex: Int): Int {
            return when(studyBurstIndex) {
                0 -> daysInFirstStudyBurst
                else -> daysInAllStudyBursts
            }
        }
        /**
         * The number of sessions in a day, this value should only be referenced
         * when working with V1 of the schedule, as this is dynamic for the Sage V2 scheduling
         */
        val sessionsInADay = 4

        // Activity Event names
        const val ACTIVITY_EVENT_CREATE_SCHEDULE = "timeline_retrieved"
        const val ACTIVITY_EVENT_STUDY_BURST_FORMAT = "study_burst:" + ACTIVITY_EVENT_CREATE_SCHEDULE + "_burst:%02d"
        fun studyBurstActivityEventId(burstIdx: Int): String {
            return String.format(ACTIVITY_EVENT_STUDY_BURST_FORMAT, burstIdx)
        }

        /**
         * Converts an HM timestamp that are stored on both iOS/Android to a DateTime
         * @param hmTimeStamp a unix timestamp since 1970 in seconds
         * @return a DateTime based on the parameter hmTimeStamp
         */
        public fun createDateTime(hmTimeStamp: Double): DateTime {
            return DateTime((hmTimeStamp * 1000).toLong())
        }
    }

    /**
     * Organizes the V1 schedule model data by study burst groups
     * @param schedule the user's test schedule
     * @return the test sessions grouped by study burst
     */
    open fun createV1Schedule(schedule: TestSchedule): SageV1Schedule {

        var v1Schedule = SageV1Schedule(schedule.timezone_name, mutableListOf())

        ArrayList(schedule.sessions).sortedBy { it.session_date }.forEachIndexed { idx, it ->
            // Some schedules have study burst session 1 as (0, 0) instead of (1, 0),
            // so skip idx = 1 it will never be the start of study burst 2
            if (it.day == 0 && it.session == 0 && idx != 1) {
                v1Schedule.studyBursts.add(SageV1StudyBurst(mutableListOf()))
            }
            v1Schedule.studyBursts.last().sessions.add(it)
        }

        return v1Schedule
    }

    /**
     * Takes a wake sleep (availability) schedule and converts it to the new cross-platform V2 version
     * @param availability the wake sleep schedule parsed from the report
     * @return the new V2 model of availability
     */
    open fun createV2Availability(availability: WakeSleepSchedule): SageV2Availability {

        val formatter = DateTimeFormatterBuilder()
                .appendPattern("h:mm a")
                .toFormatter()
                .withLocale(Locale.US)
        val twentyFourHourFormatter = DateTimeFormatterBuilder()
                .appendPattern("H:mm")
                .toFormatter()
                .withLocale(Locale.US)

        val wakeStr = availability.wakeSleepData.first().wake
                .replace("a.m.", "AM")
                .replace("p.m.", "PM")
                .replace("a. m.", "AM")
                .replace("p. m.", "PM")
                .replace("午前", "AM")
                .replace("午後", "PM")

        val bedStr = availability.wakeSleepData.first().bed
                .replace("a.m.", "AM")
                .replace("p.m.", "PM")
                .replace("a. m.", "AM")
                .replace("p. m.", "PM")
                .replace("午前", "AM")
                .replace("午後", "PM")

        val wakeLocalTime = if (wakeStr.uppercase().contains("AM") ||
                wakeStr.uppercase().contains("PM")) {
            LocalTime.parse(wakeStr, formatter)
        } else {
            LocalTime.parse(wakeStr, twentyFourHourFormatter)
        }

        val bedLocalTime = if (bedStr.uppercase().contains("AM") ||
                bedStr.uppercase().contains("PM")) {
            LocalTime.parse(bedStr, formatter)
        } else {
            LocalTime.parse(bedStr, twentyFourHourFormatter)
        }

        return SageV2Availability(
                SageV2Availability.formatter.print(wakeLocalTime),
                SageV2Availability.formatter.print(bedLocalTime))
    }

    /**
     * Maps the startTime of a session to the index of the session within the day
     * This only works with the specific DIAN study, and requires sessions to have these
     * specific start times.
     * @param session scheduled session to operate on
     * @param dailySessions all the sessions in this particular day of the week,
     *                      including the session of interest
     * @return session index of the day based on the specific DIAN study scheduling
     */
    public fun sessionOfDayIdx(session: ScheduledSession,
                               dailySessions: List<ScheduledSession>): Int {
        return dailySessions
                .sortedBy { LocalTime.parse(it.startTime) }
                .indexOfFirst { it.instanceGuid == session.instanceGuid }
    }

    /**
     * @param sessions all sessions in the participant's study schedule
     * @param activityEventId the activity event group that dayNumber is in reference to
     * @param startDay the start day of the sessions to find
     */
    public fun allSessionsOfDay(sessions: List<ScheduledSession>,
                                activityEventId: String, startDay: Int): List<ScheduledSession> {
        return sessions.filter {
            it.startEventId == activityEventId && it.startDay == startDay
        }
    }

    /**
     * Finds a completed test that matches the V2 bridge scheduled session info
     * @param session target to find a match in the completed tests
     * @param dailySessions all the sessions in this particular day of the week,
     *                      including the session of interest
     * @param earningsController that contains the completed test list, and is all setup
     * @return a matching completed test for the session, or null if none exists
     */
    public fun findCompletedTest(
            session: ScheduledSession,
            dailySessions: List<ScheduledSession>,
            earningsController: SageEarningsControllerV2)
        : CompletedTestV2? {

        val index = studyBurstIndex(session.startEventId)
        if (index < 0) {
            // Baseline test is not a part of the study bursts
            // It is also tracked differently in the earnings controller,
            // as it does not earn the user money
            return CompletedTestV2.createFrom(session.startEventId,
                    earningsController.baselineTestComplete)
        }

        val studyBurstWeekNum = (earningsController.arcStartDays()[index] ?: 0) / 7
        // Sage schedules start with day 1, not 0, so offset to match CompletedTest days,
        // Unless it is the baseline week, where HM had 1-based index
        val dayOfWeekIdx = if(studyBurstWeekNum == 0) {
            session.startDay
        } else {
            session.startDay - 1
        }
        val sessionIndex = sessionOfDayIdx(session, dailySessions)
        // Find the matching CompletedTest based of week/day/session_idx
        val matchingTest = earningsController.completedTests.firstOrNull {
            it.week == studyBurstWeekNum &&
            it.day == dayOfWeekIdx &&
            it.session == sessionIndex
        }
        // Set the EventId in V2 of the EarningsController
        return CompletedTestV2.createFrom(session.startEventId, matchingTest)
    }

    /**
     * @param session the scheduled session to create the adherence record from
     * @param eventTimestamp corresponding to the activity event id that spawned the session
     * @param finishedOn the date the session was completed
     * @param clientData free-form data you want stored with the adherence record
     * @param iANATimezone the time zone in reference to the eventTimestamp and finishedOn dates
     * @return a populated adherence record
     */
    public fun createAdherenceRecord(session: ScheduledSession,
                                     eventTimestamp: DateTime,
                                     finishedOn: DateTime,
                                     clientData: Any,
                                     iANATimezone: String?): AdherenceRecord {
        val record = AdherenceRecord()
        record.clientTimeZone = iANATimezone
        record.clientData = clientData
        record.instanceGuid = session.instanceGuid
        record.startedOn = finishedOn
        record.finishedOn = finishedOn
        record.eventTimestamp = eventTimestamp
        return record
    }

    /**
     * @param eventId the activity event identifier
     * @param eventList the list of activity events and their event date
     * @return the event date corresponding to the eventId
     */
    public fun eventTimestamp(eventId: String, eventList: StudyActivityEventList): DateTime {
        return eventList.items.first { it.eventId == eventId }.timestamp
    }

    /**
     * Convert the activity event identifier into a study burst index
     * @param activityEventId that triggers the study burst
     * @return -1 for baseline session, 0-based index for study burst
     */
    public fun studyBurstIndex(activityEventId: String): Int {
        // Only the baseline session is scheduled with this event,
        // return -1 in this case, as this is not technically part of the study burst
        if (activityEventId == ACTIVITY_EVENT_CREATE_SCHEDULE) {
            return -1
        }
        val lastTwoChars = activityEventId.substring(activityEventId.length - 2)
        return lastTwoChars.toInt() - 1  // -1 for 0 based study burst index
    }

    /**
     * Converts HM's time zone name for a schedule to the IANA V2 Sage version
     * @param rawTimezone timezone name from HM schedule
     * @return the IANA time zone, or null if one could not be determined
     */
    open fun convertToIANATimezone(rawTimezone: String?, default: String? = null): String? {
        // The DIAN apps do have a time zone string saved for each user,
        // but it’s not IANA, it’s Java’s old java.util.TimeZone.getDefault().
        // Bridge 2.0 API requires an IANA time zone, so we do some conversion here.
        if (rawTimezone != null) {
            var timezone: String? = rawTimezone // Remove unnecessary text (from iOS app)
                    .replace(" (fixed (equal to current))", "")
                    .replace(" (current)", "")
            if (IANA_TIMEZONE_MAP.containsKey(timezone)) {
                timezone = IANA_TIMEZONE_MAP[timezone]
            }
            return timezone
        }
        return default
    }

    open fun diff(completedList: List<HmDataModel.CompletedTest>,
                  adherence: List<AdherenceRecord>):
            List<HmDataModel.CompletedTest> {

        val gson = Gson()
        val adherenceCompleteTestList = adherence.map {
            gson.fromJson(gson.toJson(it.clientData), HmDataModel.CompletedTest::class.java)
        }
        if (adherenceCompleteTestList.size < completedList.size) {
            return completedList.filter {
                !adherenceCompleteTestList.any { it2 ->
                    it.week == it2.week &&
                            it.day == it2.day &&
                            it.session == it2.session
                }
            }
        } else {
            return adherenceCompleteTestList.filter {
                !completedList.any { it2 ->
                    it.week == it2.week &&
                            it.day == it2.day &&
                            it.session == it2.session
                }
            }
        }
    }

    /**
     * Create the availability time list for the entire user's scheduled sessions
     * @param the full session list for the user's schedule
     * @param availability the availability of the user for when they can do tests
     * @return a list the new random start times for each session, except for the baseline session
     */
    open fun createAvailableTimeList(sessionList: List<ScheduledSession>,
                                     availability: SageV2Availability): List<ScheduledSessionStart> {
        // Ignore baseline group that only has one session in the list, this is not scheduled randomly
        return organizeByDaySorted(sessionList).filter { it.size > 1 }.map {
            val randomTimeList = availability.randomSessionTimeStrings()
            return@map it.mapIndexed { i, session ->
                return@mapIndexed ScheduledSessionStart(session.instanceGuid, randomTimeList[i])
            }
        }.flatten()
    }

    /**
     * Organize the sessions into groups by day, there should be 4 sessions in each day,
     * except for the baseline day, which should only have 1 session.
     * @param full session schedule list
     * @return a list of groups of schedules where the group is all in a day, and they are
     *         sorted within that group by local start time
     */
    open fun organizeByDaySorted(sessionList: List<ScheduledSession>): List<List<ScheduledSession>> {
        val sessionMap = mutableMapOf<Int, MutableList<ScheduledSession>>()
        sessionList.forEach {
            val mapIdx = ((studyBurstIndex(it.startEventId) + 1) * 7) + it.startDay
            sessionMap[mapIdx]?.add(it) ?: run {
                sessionMap[mapIdx] = mutableListOf(it)
            }
        }
        return sessionMap.toSortedMap().map { it.value.sortedBy { it.startTime } }
    }
}

data class SageV1StudyBurst(
        /** Each list is a day that should have a list of 4 sessions **/
        var sessions: MutableList<TestScheduleSession>) {
    /** The start date of this burst (first session date) **/
    val startDate: Double get() {
        return sessions.first().session_date
    }
    /** The end date of this burst (last session date) **/
    val endDate: Double get() {
        return sessions.last().session_date
    }
    /** The week number since study start, of the first session of the burst
     *  This is taken from the data model, and the calculation isn't always correct **/
    val startingWeekNum: Int get() {
        return sessions.first().week
    }
    /** The week number since study start, as calculated by session dates **/
    fun calculatedStartingWeekNum(studyStartSessionDate: Double): Int {
        val studyStartSessionDateTime = SageScheduleController.createDateTime(studyStartSessionDate)
        val thisBurstStartDateTime = SageScheduleController.createDateTime(startDate)
        return Weeks.weeksBetween(studyStartSessionDateTime, thisBurstStartDateTime).weeks
    }
}

data class SageV1Schedule(
        /** The start date of this burst **/
        var v1Timezone: String?,
        /** The list of study bursts **/
        var studyBursts: MutableList<SageV1StudyBurst>) {
    val studyStartDate: DateTime get() {
        return SageScheduleController.createDateTime(studyBursts.first().startDate)
    }
}

data class SageV2Availability(
        /** LocalTime H:mm format of the time the user wakes up and is available **/
        var wake: String,
        /** LocalTime HH:mm format of the time the user goes to bed and is not available **/
        var bed: String) {
    companion object {
        val formatter = DateTimeFormatterBuilder().appendPattern("HH:mm").toFormatter()
        val endOfDay = LocalTime(23, 59)
        val startOfDay = LocalTime(0, 0)
        val minimumAvailabilityInMinutes = 8 * 60 // 8 hours

        fun convertToTimeStringList(localTimeList: List<LocalTime>): List<String> {
            return localTimeList.map { formatter.print(it) }
        }

        /**
         * Calculates the minutes between two local times in a way that supports
         * an overnight availability calculation.
         * @param availabilityStart the start of the user's availability, this can be
         *                          later in the day than the availability end.
         * @param availabilityEnd the end of the user's availability, this can be earlier
         *                        in the day than the availability start.
         * @return the minutes between two local times
         */
        fun minutesBetween(availabilityStart: LocalTime, availabilityEnd: LocalTime): Int {
            // Check for overnight availability, which is calculated differently
            if (availabilityStart.isAfter(availabilityEnd)) {
                // + 1 because end of day is 11:59PM, not 12:00AM
                val wakeTimeUntilEndOfDay = Minutes.minutesBetween(availabilityStart, endOfDay).minutes + 1
                val startOfDayUntilBedTime = Minutes.minutesBetween(startOfDay, availabilityEnd).minutes
                return wakeTimeUntilEndOfDay + startOfDayUntilBedTime
                // Check for ending at start of day instead of end of day
            } else if (availabilityEnd == startOfDay) {
                return Minutes.minutesBetween(availabilityStart, endOfDay).minutes + 1
                // Expected duration for wake time until bed time
            } else {
                return Minutes.minutesBetween(availabilityStart, availabilityEnd).minutes
            }
        }
    }

    /**
     * Calculates the availability, or the minutes between wake and bed times
     */
    public fun availabilityInMinutes(): Int {
        return minutesBetween(wakeLocalTime(), bedLocalTime())
    }

    fun randomSessionTimeStrings(): List<String> {
        return convertToTimeStringList(randomSessionTimes())
    }

    // In at least a span of 8 hours of availability,
    // there should be 4 testing periods,
    // each one >= to 2 hours apart.
    //
    // It's not that critical that they have a pure model of randomization in the test notification.
    // It's more about spreading the assessments out throughout their day and trying to make
    // the test times (meaning when they take the tests, not necessarily when they get the notifications)
    // somewhat spontaneous/irregular.
    fun randomSessionTimes(): List<LocalTime> {
        var wakeTime = wakeLocalTime()
        val totalAvailabilityInMinutes = availabilityInMinutes()
        val minimumSessionWindow = minimumAvailabilityInMinutes / SageScheduleController.sessionsInADay
        val actualSessionWindows = totalAvailabilityInMinutes / SageScheduleController.sessionsInADay
        val randomWindowInMinutes = actualSessionWindows - minimumSessionWindow
        val randomTimes = mutableListOf<LocalTime>()
        val random = Random()
        for(i in 0 until SageScheduleController.sessionsInADay) {
            val randomOffset = when (randomWindowInMinutes) {
                0 -> 0 // fixes issues where random.nextInt(0) throws exception
                else -> random.nextInt(randomWindowInMinutes)
            }
            val minutesFromWake = (actualSessionWindows * i) + randomOffset
            randomTimes.add(wakeTime.plusMinutes(minutesFromWake))
        }
        // Sorting the random times will allow for the days sessions to always
        // be ascending in time.  This will fix any overnight availability.
        // As outlined in https://sagebionetworks.jira.com/browse/DIAN-425
        return randomTimes.sorted()
    }

    public fun wakeLocalTime(): LocalTime {
        return formatter.parseLocalTime(wake)
    }
    public fun bedLocalTime(): LocalTime {
        return formatter.parseLocalTime(bed)
    }
}

data class SageUserClientData(
        /** True if the user has download and run the V2 Sage app
         * This will be set when the user logs in on the V2 app or
         * when they upgrade the app and migrate from V1 to V2 scheduling **/
        var hasMigratedToV2: Boolean?,
        /** Due to the limitation with Bridge V2 scheduling, and implementing
         * a Ecological momentary assessment (EMA) where the sessions are randomly
         * spread out throughout an availability period during the day,
         * we must store our own local times of day we should do each scheduled session **/
        var sessionStartLocalTimes: List<ScheduledSessionStart>?,
        /** User availability **/
        var availability: SageV2Availability?,
        /** Earnings in $, element at index 0 is first study burst, etc **/
        var earnings: List<String>?) {
    companion object {
        /** Helper function for java to have cleaner way to access this field */
        fun hasMigrated(clientData: SageUserClientData?): Boolean {
            return clientData?.hasMigratedToV2 ?: false
        }
        fun fromStudyParticipant(gson: Gson, participant: StudyParticipant): SageUserClientData? {
            val clientDataObj = participant.clientData ?: run { return null }
            return gson.fromJson(gson.toJson(clientDataObj), SageUserClientData::class.java)
        }
    }
}

open class SageEarningsControllerV2 : SageEarningsController() {

    var studyBurstStartDays = HashMap<Int, Int>()
    fun initializeWithStudyBursts(v1Schedule: SageV1Schedule,
                         completedTestList: List<HmDataModel.CompletedTest>) {
        studyStartDate = v1Schedule.studyStartDate
        studyBurstStartDays.clear()
        alternativeArcStartDays.clear()
        val studyStart = v1Schedule.studyStartDate
        for (i in v1Schedule.studyBursts.indices) {
            val studyBurst = v1Schedule.studyBursts[i]
            studyBurstStartDays[i] = studyBurst.startingWeekNum * 7
            val thisDateTime = SageScheduleController.createDateTime(studyBurst.startDate)
            val actualWeeks = Weeks.weeksBetween(studyStart, thisDateTime).weeks
            alternativeArcStartDays[i] = actualWeeks * 7  // * 7 to convert to days
        }
        super.completedTests = completedTestList
    }

    override fun arcStartDays(): HashMap<Int, Int> {
        return studyBurstStartDays
    }

    override fun now(): DateTime {
        // Move one study burst into the future to make sure we get all earnings
        return super.now().plusWeeks(26)
    }
}

public data class ScheduledSessionStart(
        /** The instanceGuid of a ScheduledSession object */
        val guid: String,
        /** The LocalTime start time of the session, in format "HH:mm" */
        val start: String)

public data class CompletedTestV2(
        public val eventId: String? = null,
        public val week: Int,
        public val day: Int,
        public val session: Int,
        public val completedOn: Double) {
    companion object {
        fun createFrom(eventId: String, completed: HmDataModel.CompletedTest?) : CompletedTestV2? {
            completed?.let {
                return CompletedTestV2(eventId, it.week, it.day, it.session, it.completedOn)
            } ?: run { return null }
        }
    }
}