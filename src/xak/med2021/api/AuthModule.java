package xak.med2021.api;

import java.util.Date;

import org.xsite.server.Context;
import org.xsite.server.Session;

import xak.med2021.model.User;
import xak.med2021.services.AuthService;

import static org.xsite.webapp.params.Param.NOT_EMPTY;

public class AuthModule extends BaseModule {

    private final AuthService authService;

    public AuthModule() {
        authService = AuthService.instance();

        path("/api", () -> {
            method("isAuthorized", this::isAuthorized);
            method("login", this::login);
            method("logout", this::logout);
            method("getTime", this::getTime);
        });
    }

    private boolean isAuthorized() {
        return login() != null;
    }

    private User login() {
        Session session = Session.current();

        String login = param("username").getString();
        if( login == null ) {
            if( session == null )
                return null;

            User user = session.get(User.class);
            if( user != null ) {
                user = authService.getUser(user.id);
                if( user == null ) {
                    session.invalidate();
                }
            }
            return user;
        }

        String password = param("password", NOT_EMPTY).getString();

        if( sendFormError() ) {
            return null;
        }

        User user = authService.auth(login, password);

        if( user != null ) {
            if( session == null ) {
                session = Context.current().newSession();
            }
            session.set(user);
        } else {
            if( session != null ) {
                session.invalidate();
            }
        }

        return user;
    }

    private boolean logout() {
        Session.current().invalidate();
        return true;
    }

    private String getTime() {
        return new Date().toString();
    }

}
