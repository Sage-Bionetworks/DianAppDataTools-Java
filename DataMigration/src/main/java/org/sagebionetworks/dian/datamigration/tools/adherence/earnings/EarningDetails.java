package org.sagebionetworks.dian.datamigration.tools.adherence.earnings;

import java.util.ArrayList;
import java.util.List;

public class EarningDetails {

    public String total_earnings;
    public List<Cycle> cycles;

    public EarningDetails(){
        total_earnings = new String();
        cycles = new ArrayList<>();
    }

    public static class Cycle {
        public Integer cycle;
        public String total;
        public Long start_date;
        public Long end_date;
        public List<Goal> details;

        public Cycle() {
            cycle = new Integer(-1);
            total = new String();
            end_date = new Long(0);
            details = new ArrayList<>();
        }
    }

    public static class Goal {
        public String name;
        public String value;
        public Integer count_completed;
        public String amount_earned;
    }

}
