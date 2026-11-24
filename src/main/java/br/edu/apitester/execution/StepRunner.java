package br.edu.apitester.execution;

import br.edu.apitester.model.Expectation;
import br.edu.apitester.model.Rule;
import br.edu.apitester.model.Step;
import br.edu.apitester.model.Suite;
import br.edu.apitester.result.Check;
import br.edu.apitester.result.Statistics;
import br.edu.apitester.result.StepResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class StepRunner {

    private final HttpSender sender;
    private final Interpolator interpolator;
    private final ResponseVerifier verifier;
    private final ObjectMapper json = new ObjectMapper();

    public StepRunner(HttpSender sender, Interpolator interpolator, ResponseVerifier verifier) {
        this.sender = sender;
        this.interpolator = interpolator;
        this.verifier = verifier;
    }

    public StepResult run(Step step, Suite suite, Context context) {
        String name = step.name() != null ? step.name() : step.method() + " " + step.path();
        String url = null;
        try {
            url = buildUrl(suite.baseUrl(), interpolator.text(step.path(), context));
            Map<String, String> headers = buildHeaders(step, suite, context);
            String body = buildBody(step, context, headers);
            Expectation expect = interpolateExpectation(step.expect(), context);

            StepResult result = step.repeat() == 1
                    ? runOnce(name, step, expect, suite, url, headers, body, context)
                    : runRepeated(name, step, expect, suite, url, headers, body, context);

            if (step.pauseMs() > 0) {
                Thread.sleep(step.pauseMs());
            }
            return result;
        } catch (UndefinedVariableException e) {
            return error(name, step, url, e.getMessage());
        } catch (HttpTimeoutException e) {
            return error(name, step, url, "Timeout of " + suite.timeoutMs() + " ms exceeded.");
        } catch (ConnectException e) {
            return error(name, step, url, "Could not connect to " + url + ". Is the API running?");
        } catch (IOException e) {
            return error(name, step, url, "Communication failure: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return error(name, step, url, "Run interrupted.");
        } catch (IllegalArgumentException e) {
            return error(name, step, url, "Invalid request: " + e.getMessage());
        }
    }

    private StepResult runOnce(String name, Step step, Expectation expect, Suite suite, String url,
                               Map<String, String> headers, String body, Context context)
            throws IOException, InterruptedException {
        Response response = sender.send(step.method(), url, headers, body, suite.timeoutMs());
        List<Check> checks = new ArrayList<>(verifier.verify(expect, response, suite.checkLeaks()));
        checks.addAll(capture(step.capture(), response, context));
        return new StepResult(name, step.method(), url, response.status(), response.durationMs(), checks, null, null);
    }

    private StepResult runRepeated(String name, Step step, Expectation expect, Suite suite, String url,
                                   Map<String, String> headers, String body, Context context)
            throws InterruptedException {
        List<Long> durations = Collections.synchronizedList(new ArrayList<>());
        List<String> problems = Collections.synchronizedList(new ArrayList<>());
        List<Response> responses = Collections.synchronizedList(new ArrayList<>());

        long start = System.currentTimeMillis();
        try (ExecutorService executor = Executors.newFixedThreadPool(step.concurrency())) {
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < step.repeat(); i++) {
                tasks.add(executor.submit(() -> {
                    try {
                        Response response = sender.send(step.method(), url, headers, body, suite.timeoutMs());
                        durations.add(response.durationMs());
                        responses.add(response);
                        verifier.verify(expect, response, suite.checkLeaks()).stream()
                                .filter(c -> !c.passed())
                                .findFirst()
                                .ifPresent(c -> problems.add(c.description() + ": " + c.detail()));
                    } catch (IOException e) {
                        problems.add("communication failure: " + e.getMessage());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
            }
            for (Future<?> task : tasks) {
                try {
                    task.get();
                } catch (ExecutionException e) {
                    problems.add("unexpected error: " + e.getCause().getMessage());
                }
            }
        }
        long totalDuration = System.currentTimeMillis() - start;

        Statistics statistics = Statistics.calculate(durations, problems.size());
        List<Check> checks = new ArrayList<>();
        String description = step.repeat() + " requests meet the expectations";
        checks.add(problems.isEmpty()
                ? Check.ok(description)
                : Check.fail(description, problems.size() + " failed. First one: " + problems.getFirst()));

        if (expect.p95MaxMs() != null) {
            String p95Description = "p95 up to " + expect.p95MaxMs() + " ms";
            checks.add(statistics.p95Ms() <= expect.p95MaxMs()
                    ? Check.ok(p95Description)
                    : Check.fail(p95Description, "measured p95: " + statistics.p95Ms() + " ms"));
        }

        Integer status = null;
        if (!responses.isEmpty()) {
            Response first = responses.getFirst();
            status = first.status();
            checks.addAll(capture(step.capture(), first, context));
        }
        return new StepResult(name, step.method(), url, status, totalDuration, checks, null, statistics);
    }

    private List<Check> capture(Map<String, String> captures, Response response, Context context) {
        List<Check> checks = new ArrayList<>();
        for (Map.Entry<String, String> capture : captures.entrySet()) {
            String description = "capture " + capture.getKey() + " from " + capture.getValue();
            try {
                Object value = JsonPath.read(response.body(), capture.getValue());
                context.set(capture.getKey(), value);
                checks.add(Check.ok(description));
            } catch (RuntimeException e) {
                checks.add(Check.fail(description, "value not found in the response"));
            }
        }
        return checks;
    }

    private Expectation interpolateExpectation(Expectation expect, Context context) {
        List<Rule> rules = new ArrayList<>();
        for (Rule r : expect.body()) {
            rules.add(new Rule(r.path(), interpolator.interpolate(r.equals(), context),
                    interpolator.interpolate(r.notEquals(), context), r.exists(),
                    interpolator.interpolate(r.contains(), context), r.greaterThan(), r.lessThan(), r.length(),
                    r.type(), interpolator.text(r.regex(), context)));
        }
        Map<String, String> headers = new LinkedHashMap<>();
        expect.headers().forEach((k, v) -> headers.put(k, interpolator.text(v, context)));
        return new Expectation(expect.status(), expect.maxTimeMs(), expect.p95MaxMs(),
                interpolator.text(expect.bodyContains(), context), interpolator.text(expect.bodyNotContains(), context),
                headers, rules);
    }

    private Map<String, String> buildHeaders(Step step, Suite suite, Context context) {
        Map<String, String> headers = new LinkedHashMap<>();
        suite.headers().forEach((k, v) -> headers.put(k, interpolator.text(v, context)));
        step.headers().forEach((k, v) -> headers.put(k, interpolator.text(v, context)));
        if (step.token() != null && !step.token().isBlank()) {
            headers.put("Authorization", "Bearer " + interpolator.text(step.token(), context));
        }
        return headers;
    }

    private String buildBody(Step step, Context context, Map<String, String> headers) {
        if (step.body() == null) {
            return null;
        }
        Object body = interpolator.interpolate(step.body(), context);
        if (body instanceof String text) {
            return text;
        }
        boolean hasContentType = headers.keySet().stream().anyMatch(k -> k.equalsIgnoreCase("Content-Type"));
        if (!hasContentType) {
            headers.put("Content-Type", "application/json");
        }
        try {
            return json.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("could not convert the body to JSON");
        }
    }

    static String buildUrl(String baseUrl, String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("the step has no 'path'");
        }
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("set 'baseUrl' in the suite or use --base-url");
        }
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + (path.startsWith("/") ? path : "/" + path);
    }

    private StepResult error(String name, Step step, String url, String message) {
        return new StepResult(name, step.method(), url, null, 0, List.of(), message, null);
    }
}
