package org.xsite.util;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.nio.file.StandardWatchEventKinds.*;

public class FileWatcher {

    private static WatchService watcher;
    private static Map<WatchKey, List<Handler>> handlersMap;
    // NOTE: handlers is not thread safe. It can be modified from watcher thread and from from other thread by calling watch() method.
    // NOTE: But watch() calling in app init. Isn't it? If no, it should be thread safe.


    private static void init() throws IOException {
        if( watcher == null ) {
            watcher = FileSystems.getDefault().newWatchService();
            handlersMap = new HashMap<>();

            Thread thread = new Thread(FileWatcher::mainLoop);
            thread.setDaemon(true);
            thread.start();
        }
    }

    public static void watch(Path path, Handler handler) throws IOException {
        init();
        addDir(path, List.of(handler));
    }

    private static void addDir(Path path, List<Handler> handlers) throws IOException {
        if( Files.isDirectory(path) ) {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                    if( !dir.equals(key.watchable()) ) {
                        // Bug: JDK-12.0.1: when directory was renamed key.watchable() returns old directory path. So re-register it.
                        key.cancel();
                        handlersMap.remove(key);
                        key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                    }
                    handlersMap.computeIfAbsent(key, watchKey -> new ArrayList<>(1)).addAll(handlers);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private static void mainLoop() {
        while( true ) {
            try {
                WatchKey key = watcher.take();
                Path dir = (Path)key.watchable();
                List<Handler> handlers = handlersMap.get(key);

                for( WatchEvent<?> event : key.pollEvents() ) {
                    Path file = dir.resolve((Path)event.context());
                    if( event.kind() == ENTRY_CREATE ) {
                        addDir(file, handlers);
                    }

                    handlers.forEach(h -> h.handle(event.kind(), file));
                }

                if( !key.reset() ) {
                    // When directory was deleted key automatically canceled.
                    handlersMap.remove(key);
                    handlers.forEach(h -> h.handle(ENTRY_DELETE, dir));
                }
            } catch( IOException e ) {
                e.printStackTrace();
            } catch( InterruptedException e ) {
                e.printStackTrace();
                return;
            }
        }
    }

    public interface Handler {
        void handle(WatchEvent.Kind<?> entryDelete, Path path);
    }


/*
    public static void main(String[] args) throws IOException {
        try {
            watch(Paths.get("d:\\wt"), () -> {
                System.out.println("CHANGED 1");
            });
            watch(Paths.get("d:\\wt"), () -> {
                System.out.println("CHANGED 2");
            });
            while( true ) {
                System.in.read();
                System.out.println(handlersMap);
            }
        } catch( IOException e ) {
            e.printStackTrace();
        }
    }
*/

}
