package org.sagebionetworks.dian.datamigration.tools.schedulev2;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WakeSleepSchedule {

    public String app_version;          // "app_version" : (string, version of the app),
    public String device_id;            // "device_id" : (string, the unique id for this device),
    public String device_info;          // "device_info": (a string with format "OS name|device model|OS version", ie "iOS|iPhone8,4|10.1.1")
    public String participant_id;       // "participant_id" : (string, the user's participant id),

    @JsonProperty("wakeSleepData")
    @JsonAlias({"wake_sleep_data"})
    public List<WakeSleepData> wakeSleepData;

    public String model_version = "0";

    public String timezone_name;       // name of timezone ie "Central Standard Time"
    public String timezone_offset;     // offset from utc ie "UTC-05:00"

    public WakeSleepSchedule() {
    }

    public static class WakeSleepData {
        public String bed;
        public String wake;
        public String weekday;
        public WakeSleepData() {
        }
    }
}

