package xak.med2021.model;

import java.util.ArrayList;
import java.util.List;

public class Patient {

    public String id;
    public String lastName;
    public String firstName;
    public String middleName;
    public String sex;
    public String age;
    public String docId;
    public String diagId;
    public String startDate;
    public String endDate;

    public List<Procedure> treatment = new ArrayList<>();


}
