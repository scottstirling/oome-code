package org.oome.threads;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;

/// A simple web server demonstrating virtual and platform threads. When run, it
/// creates a few platform threads for background tasks and handles each incoming
/// web request with a new, named virtual thread.
///
/// This application is designed to be a long-running specimen for analysis with
/// diagnostic tools like {@code jstack}, {@code jcmd}, and Java Flight Recorder (JFR).
/// Standard Javadoc supports HTML tags for formatting, which we use here to
/// structure the documentation for better readability.
/// 
/// ### How to Run
/// The application can be run directly as a single-file source program.
///
/// ```bash
/// $ java LoomThreadServer.java
/// ```
///
/// ### How to Test
/// Once running, you can send requests to the different endpoints from another
/// terminal to create virtual threads.
///
/// ```bash
/// $ curl http://localhost:8080/good
/// $ curl http://localhost:8080/pinned
/// ```
/// 
///  @since oome.org 2025-08-02
///  @see <a href="https://oome.org/featured/loom-diagnostics-part-1-great-divide/">Loom Diagnostics, Part 1</a>
public class LoomThreadServer {

    private static int LISTEN_PORT = 8080;
    private static int PLATFORM_THREAD_POOL_SIZE = 3;

    public static void main(String[] args) throws Exception {
        // --- Create some long-running Platform Threads for comparison ---
        for (int i = 0; i < PLATFORM_THREAD_POOL_SIZE; i++) {
            final int workerId = i + 1;
            Thread.ofPlatform().name("Platform-Worker-" + workerId).start(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    System.out.println("Platform-Worker-" + workerId + " is doing background work.");
                    try {
                        Thread.sleep(Duration.ofSeconds(10));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }

        // --- Create an executor that uses one Virtual Thread per task ---
        var virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

        // --- Create a simple HTTP server ---
        HttpServer server = HttpServer.create(new InetSocketAddress(LISTEN_PORT ), 0);

        // --- Define contexts, each handled by a virtual thread ---
        server.createContext("/virtual", exchange -> {
            System.out.println("Handling /virtual request in thread: " + Thread.currentThread());
            String response = "Hello from a virtual thread! Processing for 5 seconds...\n";
            handleRequest(exchange, response);
        });

        server.createContext("/platform", exchange -> {
            System.out.println("Handling /platform request (but still on a VT carrier)...");
            String response = "This is also on a virtual thread, just demonstrating.\n";
            handleRequest(exchange, response);
        });

        server.setExecutor(virtualThreadExecutor); // Use virtual threads for requests
        server.start();

        long serverPID = ProcessHandle.current().pid();
        
        String startMessage = "Server started on port " + LISTEN_PORT + ". Press Ctrl+C to stop."; 
        String pidInfoMessage = "Your PID is: " + serverPID;
        String userInfoMessage = "Run 'jstack " + serverPID + "' in another terminal to see the threads.";
        System.out.println(startMessage);
        System.out.println(pidInfoMessage);
        System.out.println(userInfoMessage);
    }

    private static void handleRequest(com.sun.net.httpserver.HttpExchange exchange, String response)
            throws IOException {
        // Simulate a blocking I/O operation (like a database call)
        int requestBlockSeconds = 20;
        
        try {
            Thread.sleep(Duration.ofSeconds(requestBlockSeconds));
        } catch (InterruptedException e) {
            // Ignore
        }

        exchange.sendResponseHeaders(200, response.length());
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }
}