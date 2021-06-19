package xak.med2021.model;

import java.util.List;

public class User {

    public String uid;
    public String id;
    public String lastName;
    public String firstName;
    public String middleName;
    public String email;

    public List<String> roles;

    public boolean admin;

    public String fullName() {
        StringBuilder sb = new StringBuilder();
        if( lastName != null )
            sb.append(lastName);
        if( firstName != null && !firstName.isEmpty() )
            sb.append(" ").append(firstName);
        if( middleName != null && !middleName.isEmpty() )
            sb.append(" ").append(middleName);
        return sb.toString().trim();
    }

    public String shortName() {
        StringBuilder sb = new StringBuilder();
        if( lastName != null )
            sb.append(lastName);
        if( firstName != null && !firstName.isEmpty() )
            sb.append(" ").append(firstName.substring(0, 1).toUpperCase()).append(".");
        if( middleName != null && !middleName.isEmpty() )
            sb.append(" ").append(middleName.substring(0, 1).toUpperCase()).append(".");
        return sb.toString().trim();
    }

    public boolean is(String role) {
        return roles != null && roles.contains(role);
    }

}
