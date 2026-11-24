package br.edu.apitester.execution;

import br.edu.apitester.model.HeaderCheck;
import br.edu.apitester.model.Security;
import br.edu.apitester.model.Suite;
import br.edu.apitester.result.Check;
import br.edu.apitester.result.ScenarioResult;
import br.edu.apitester.result.StepResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SecurityScan {

    private static final String MALFORMED_TOKEN = "this.is.not-a-token";

    private final HttpSender sender;
    private final Interpolator interpolator;

    public SecurityScan(HttpSender sender, Interpolator interpolator) {
        this.sender = sender;
        this.interpolator = interpolator;
    }

    public ScenarioResult run(Suite suite, Context context) {
        Security security = suite.security();
        List<StepResult> steps = new ArrayList<>();

        Map<String, String> attempts = new LinkedHashMap<>();
        attempts.put("no token", null);
        attempts.put("malformed token", MALFORMED_TOKEN);
        attempts.put("token with alg none", unsignedToken());

        for (String route : security.protectedRoutes()) {
            String[] parts = route.trim().split("\\s+", 2);
            String method = parts.length == 2 ? parts[0].toUpperCase() : "GET";
            String path = parts.length == 2 ? parts[1] : parts[0];
            for (Map.Entry<String, String> attempt : attempts.entrySet()) {
                steps.add(testRoute(suite, context, method, path, attempt.getKey(), attempt.getValue(),
                        security.acceptedStatus()));
            }
        }

        if (security.headers() != null) {
            steps.add(testHeaders(suite, context, security.headers()));
        }
        return new ScenarioResult("Security scan", steps);
    }

    private StepResult testRoute(Suite suite, Context context, String method, String path, String description,
                                 String token, List<Integer> accepted) {
        String name = method + " " + path + " (" + description + ")";
        String url = null;
        try {
            url = StepRunner.buildUrl(suite.baseUrl(), interpolator.text(path, context));
            Map<String, String> headers = new LinkedHashMap<>();
            if (token != null) {
                headers.put("Authorization", "Bearer " + token);
            }
            String body = null;
            if (!method.equals("GET") && !method.equals("DELETE")) {
                headers.put("Content-Type", "application/json");
                body = "{}";
            }
            Response response = sender.send(method, url, headers, body, suite.timeoutMs());
            String rule = "blocked with status in " + accepted;
            Check check = accepted.contains(response.status())
                    ? Check.ok(rule)
                    : Check.fail(rule, "the route answered " + response.status() + " and should have refused access");
            return new StepResult(name, method, url, response.status(), response.durationMs(), List.of(check),
                    null, null);
        } catch (IOException | RuntimeException e) {
            return new StepResult(name, method, url, null, 0, List.of(), "Test failed: " + e.getMessage(), null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new StepResult(name, method, url, null, 0, List.of(), "Run interrupted.", null);
        }
    }

    private StepResult testHeaders(Suite suite, Context context, HeaderCheck config) {
        String name = "Security headers on GET " + config.path();
        String url = null;
        try {
            url = StepRunner.buildUrl(suite.baseUrl(), interpolator.text(config.path(), context));
            Response response = sender.send("GET", url, Map.of(), null, suite.timeoutMs());
            List<Check> checks = new ArrayList<>();
            for (String header : config.require()) {
                checks.add(response.header(header) != null
                        ? Check.ok("header " + header + " present")
                        : Check.fail("header " + header + " present", "missing from the response"));
            }
            return new StepResult(name, "GET", url, response.status(), response.durationMs(), checks, null, null);
        } catch (IOException | RuntimeException e) {
            return new StepResult(name, "GET", url, null, 0, List.of(), "Test failed: " + e.getMessage(), null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new StepResult(name, "GET", url, null, 0, List.of(), "Run interrupted.", null);
        }
    }

    private String unsignedToken() {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = encoder.encodeToString(
                "{\"sub\":\"1\",\"role\":\"ADMIN\",\"exp\":4102444800}".getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".";
    }
}
