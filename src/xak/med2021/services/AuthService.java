package xak.med2021.services;

import xak.med2021.model.User;

public class AuthService {

    private static final AuthService inst = new AuthService();

    public static AuthService instance() {
        return inst;
    }

    public User getUser(String id) {
        User user = new User();
        user.id = id;
        return user;
    }

    public User auth(String login, String password) {
        User user = new User();
        user.id = login;
        return user;
    }
}
