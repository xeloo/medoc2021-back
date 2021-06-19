package org.xsite.server;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

public class Session {

    private static final Map<String, Session> sessionMap = new ConcurrentHashMap<>();

    public final String id;
    public long lastAccess;
    private final Map<Object, Object> attributes = new ConcurrentHashMap<>();

    private Session(String id) {
        this.id = id;
    }

    static Session getOrCreate(String id) {
        return sessionMap.computeIfAbsent(id, Session::new);
    }

    public static Session getSession(String id) {
        return sessionMap.get(id);
    }

    public static Session current() {
        return Context.current().session();
    }

    public static Collection<Session> sessionList() {
        return sessionMap.values();
    }

    public void invalidate() {
        sessionMap.remove(id);
    }

    public static void remove(Function<Session, Boolean> test) {
        for( Session session : sessionMap.values() ) {
            if( test.apply(session) ) {
                sessionMap.remove(session.id);
            }
        }
    }

    public <T> T get(Object key) {
        return (T)attributes.computeIfAbsent(key, o -> Context.current().webApp().createValue(o));
    }

    public <T> T get(Object key, Supplier<T> ifAbsent) {
        return (T)attributes.computeIfAbsent(key, o -> ifAbsent.get());
    }

    public <T> Session set(Object key, T value) {
        if( value != null )
            attributes.put(key, value);
        else
            attributes.remove(key);
        return this;
    }

    public <T> Session set(T value) {
        if( value != null ) {
            attributes.put(value.getClass(), value);
        }
        return this;
    }

}
