package br.edu.apitester;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FullRunTest {

    private HttpServer server;
    private String baseUrl;

    @TempDir
    Path folder;

    @BeforeEach
    void startFakeApi() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/login", exchange -> reply(exchange, 200, "{\"token\": \"abc123\"}"));
        server.createContext("/items", exchange -> {
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            if (!"Bearer abc123".equals(authorization)) {
                reply(exchange, 401, "{\"error\": \"no access\"}");
                return;
            }
            reply(exchange, 200, "[{\"id\": 1, \"name\": \"Pen\"}, {\"id\": 2, \"name\": \"Pencil\"}]");
        });
        server.createContext("/broken", exchange ->
                reply(exchange, 500, "java.lang.IllegalStateException: failed\n\tat app.Service.run(Service.java:10)"));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopFakeApi() {
        server.stop(0);
    }

    private void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.getResponseHeaders().add("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private int run(String yaml, ByteArrayOutputStream output) throws IOException {
        Path file = folder.resolve("suite.yaml");
        Files.writeString(file, yaml);
        return Application.run(new String[]{file.toString(), "--base-url", baseUrl, "--no-color",
                "--reports", folder.resolve("reports").toString()}, new PrintStream(output, true, StandardCharsets.UTF_8));
    }

    @Test
    void runsAFlowWithCaptureAndGeneratesReports() throws IOException {
        String yaml = """
                name: Fake API
                scenarios:
                  - name: Login and listing
                    steps:
                      - name: Login
                        method: POST
                        path: /login
                        body:
                          user: test
                        expect:
                          status: 200
                        capture:
                          token: $.token
                      - name: List
                        path: /items
                        token: ${token}
                        expect:
                          status: 200
                          headers:
                            Content-Type: json
                          body:
                            - path: $
                              length: 2
                            - path: $[*].name
                              contains: Pen
                      - name: Load
                        path: /items
                        token: ${token}
                        repeat: 20
                        concurrency: 4
                        expect:
                          status: 200
                security:
                  protectedRoutes:
                    - GET /items
                  headers:
                    path: /items
                    require: [X-Content-Type-Options]
                """;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int code = run(yaml, output);

        String text = output.toString(StandardCharsets.UTF_8);
        assertEquals(0, code, text);
        assertTrue(text.contains("0 failed"), text);
        assertTrue(Files.exists(folder.resolve("reports/report.html")));
        String junit = Files.readString(folder.resolve("reports/junit.xml"));
        assertTrue(junit.contains("failures=\"0\""), junit);
    }

    @Test
    void failsAndReturnsOneWhenSomethingFails() throws IOException {
        String yaml = """
                name: Fake API
                scenarios:
                  - name: Errors
                    steps:
                      - name: Route that leaks a stack trace
                        path: /broken
                        expect:
                          status: 500
                """;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int code = run(yaml, output);
        String text = output.toString(StandardCharsets.UTF_8);
        assertEquals(1, code, text);
        assertTrue(text.contains("internal details"), text);
        assertTrue(Files.readString(folder.resolve("reports/junit.xml")).contains("<failure"));
    }

    @Test
    void invalidFileReturnsTwo() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int code = run("name: no scenarios\nmadeUpField: 1\n", output);
        assertEquals(2, code);
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Unknown field"));
    }
}
