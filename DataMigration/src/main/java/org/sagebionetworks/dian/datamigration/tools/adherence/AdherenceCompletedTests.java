package org.sagebionetworks.dian.datamigration.tools.adherence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AdherenceCompletedTests {

    public List<AdherenceCompletedTest> completed;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AdherenceCompletedTest {
        public Double session_date;    // the date/time when this session was completed
        public Integer week;           // 0-indexed week that this session takes place in
        public Integer day;            // 0-indexed day within the current week
        public Integer session;        // 0-indexed session within the current day
    }
}
