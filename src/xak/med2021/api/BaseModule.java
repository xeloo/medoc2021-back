package xak.med2021.api;

import java.util.ArrayList;
import java.util.List;

import org.xsite.server.Session;
import org.xsite.webapp.AppModule;
import org.xsite.webapp.WebApp;

import xak.med2021.model.User;

public abstract class BaseModule extends AppModule {

    private static final Object LOG_KEY = new Object();

    protected static final WebApp.AccessController AUTHORIZED = ctx -> {
        Session session = ctx.session();
        return session != null && session.get(User.class) != null;
    };
    protected static final WebApp.AccessController ADMIN = ctx -> {
        Session session = ctx.session();
        if( session == null )
            return false;
        User user = session.get(User.class);
        return user != null && user.is("admin");
    };


    public User currentUser() {
        Session session = Session.current();
        return session != null ? session.get(User.class) : null;
    }

    public void event(String type) {
        event(type, null);
    }

    public void event(String type, Object data) {
        webApp.sendEvent("/api", type, data);
    }

    public void log(String type, String message) {
        Session session = Session.current();
        if( session != null ) {
            List<LogItem> log = session.get(LOG_KEY, ArrayList::new);
            log.add(new LogItem(type, message));
        }
    }

    public List<LogItem> getSessionLog(String sessionId) {
        Session session = Session.getSession(sessionId);
        return session != null ? session.get(LOG_KEY) : null;
    }

    public void clearSessionLog(String sessionId) {
        Session session = Session.getSession(sessionId);
        if( session != null ) {
            Object log = session.get(LOG_KEY);
            if( log != null ) {
                ((List<?>)log).clear();
            }
        }
    }

    public static class LogItem {
        String type;
        String msg;
        long time;

        public LogItem() {
        }

        public LogItem(String type, String msg) {
            this.type = type;
            this.msg = msg;
            time = System.currentTimeMillis();
        }
    }

}
