package org.xsite.webapp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xsite.server.Context;
import org.xsite.server.HttpMethod;

public class RequestManager {

    private final Map<HttpMethod, Handlers> methodHandlers = new HashMap<>();


    public void add(HttpMethod method, String path, RequestHandler handler) {
        methodHandlers.computeIfAbsent(method, m -> new Handlers()).add(path, handler);
    }

    public RequestHandler findHandler(Context ctx) {
        RequestHandler handler = methodHandlers.computeIfAbsent(ctx.method, m -> new Handlers()).find(ctx, ctx.path);
        if( handler == null )
            handler = methodHandlers.computeIfAbsent(HttpMethod.ANY, m -> new Handlers()).find(ctx, ctx.path);
        return handler;
    }


    private static class Handlers {

        private final Map<String, RequestHandler> handlers = new HashMap<>();

        private final Map<String, List<RequestItem>> prefixedList = new HashMap<>();


        private void addPrefixed(String prefix, RequestItem requestItem) {
            prefixedList.computeIfAbsent(prefix, s -> new ArrayList<>()).add(requestItem);
        }

        public void add(String path, RequestHandler handler) {
            // TODO: check for duplicates

            if( path.endsWith("/*") ) {
                String prefix = path.substring(0, path.length() - 2);
                addPrefixed(prefix, new RequestItem(handler, null));
                return;
            }

            int a = path.indexOf("/:");
            int b = path.indexOf("/{");
            int i = a == -1 ? b : b == -1 ? a : Math.min(a, b);
            if( i != -1 ) {
                String prefix = path.substring(0, i);
                RequestItem requestItem = new RequestItem(handler, path.substring(i + 1));
                addPrefixed(prefix + "#" + requestItem.parts.length, requestItem);
            } else {
                handlers.put(path, handler);
            }
        }

        public RequestHandler find(Context ctx, String path) {
            RequestHandler h = handlers.get(path);
            if( h != null )
                return h;

            String probe = path;
            int paramNumber = 0;
            while( true ) {
                int i = probe.lastIndexOf('/');
                if( i < 0 )
                    break;
                probe = probe.substring(0, i);
                paramNumber++;
                List<RequestItem> list = prefixedList.get(probe + "#" + paramNumber);
                if( list != null ) {
                    String[] params = path.substring(i + 1).split("/");
                    if( params.length < paramNumber ) { // if path ends with '/'
                        params = Arrays.copyOf(params, paramNumber);
                        params[paramNumber - 1] = "";
                    }

                    for( RequestItem item : list ) {
                        if( item.match(params) ) {
                            item.setParams(ctx, params);
                            return item.handler;
                        }
                    }
                }

                list = prefixedList.get(probe);
                if( list != null )
                    return list.get(0).handler;

            }

            return null;
        }
    }

    private static class RequestItem {
        final String[] parts;
        final boolean[] params;
        final RequestHandler handler;

        public RequestItem(RequestHandler handler, String tail) {
            this.handler = handler;

            if( tail != null ) {
                parts = tail.split("/");
                params = new boolean[parts.length];
                for( int i = 0; i < parts.length; i++ ) {
                    String p = parts[i];
                    if( p.startsWith("{") && p.endsWith("}") ) {
                        parts[i] = p.substring(1, p.length() - 1);
                        params[i] = true;
                    } else if( p.startsWith(":") ) {
                        parts[i] = p.substring(1);
                        params[i] = true;
                    }
                }
            } else {
                parts = null;
                params = null;
            }
        }

        public boolean match(String[] query) {
            if( query.length == parts.length ) {
                for( int i = 0; i < parts.length; i++ ) {
                    if( !params[i] && !parts[i].equals(query[i]) ) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }

        public void setParams(Context ctx, String[] query) {
            for( int i = 0; i < parts.length; i++ ) {
                if( params[i] ) {
                    ctx.param(parts[i], query[i]);
                }
            }
        }
    }

    public static class MultiRequestHandler implements RequestHandler {
        private final ArrayList<RequestHandler> list = new ArrayList<>();

        public MultiRequestHandler(RequestHandler h, RequestHandler... handlers) {
            if( h != null )
                list.add(h);
            list.addAll(Arrays.asList(handlers));
        }

        @Override
        public void handle(Context ctx) {
            for( RequestHandler h : list ) {
                h.handle(ctx);
                if( ctx.completed() )
                    break;
            }
        }
    }


}
