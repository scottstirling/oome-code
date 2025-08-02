package org.oome.threads;

import java.io.IOException;

// Save as LoomThreadPinner.java and compile/run with JDK 21+

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/// A server that hangs requests on a worker thread for LoomThreadPinner.SLEEP_MINUTES
public class LoomThreadPinner {

    private static final Object lock = new Object();

    private static int LISTEN_PORT = 8080;

    private static int SLEEP_MINUTES = 3;
    
    private static String VIRTUAL_THREAD_NAME_PREFIX = "loom-thread-pinner-worker-";

    public static void main(String[] args) throws Exception {
        // Use a virtual thread factory that names threads
        var virtualThreadFactory = Thread.ofVirtual().name(VIRTUAL_THREAD_NAME_PREFIX, 0).factory();

        HttpServer server = HttpServer.create(new InetSocketAddress(LISTEN_PORT), 0);

        // This endpoint demonstrates an un-pinned virtual thread
        server.createContext("/good", exchange -> {
            handleRequest(exchange, "Standard virtual thread response.");
        });

        // This endpoint demonstrates a pinned virtual thread
        server.createContext("/pinned", exchange -> {
            synchronized (lock) {
                System.out.println("Thread entered synchronized block (will be pinned for " + SLEEP_MINUTES
                        + " minutes): " + Thread.currentThread());
                handleRequest(exchange, "Pinned virtual thread response.");
            }
        });

        server.setExecutor(Executors.newThreadPerTaskExecutor(virtualThreadFactory));
        server.start();
        long pid = ProcessHandle.current().pid();
        System.out.println("Server started on PID: " + pid);
    }

    private static void handleRequest(HttpExchange exchange, String requestString) throws IOException {
        try {
            Thread.sleep(Duration.ofMinutes(LoomThreadPinner.SLEEP_MINUTES));
        } catch (InterruptedException ex) {
        }
        exchange.sendResponseHeaders(200, requestString.length());
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(requestString.getBytes());
        }
    }
}
