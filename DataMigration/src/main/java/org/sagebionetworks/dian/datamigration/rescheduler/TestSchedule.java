package org.sagebionetworks.dian.datamigration.rescheduler;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TestSchedule {

    public String app_version;                  // version of the app
    public String device_info;                  // a string with format
    public String participant_id;               // the user's participant id
    public String device_id;                    // the unique id for this device
    public String model_version = "0";          // the model version of this data object
    public List<TestScheduleSession> sessions;  // an array of objects that define each session

    public String timezone_name;       // name of timezone ie "Central Standard Time"
    public String timezone_offset;     // offset from utc ie "UTC-05:00"

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TestScheduleSession {

        public String session_id;      // an identifier for this specific session w/r/t the entire test. On iOS, we're just using the sessions "index", so to speak
        public Double session_date;    // the  date/time when this session is scheduled to start
        public Integer week;           // 0-indexed week that this session takes place in
        public Integer day;            // 0-indexed day within the current week
        public Integer session;        // 0-indexed session within the current day
        public List<String> types;     // test data objects
    }
}
