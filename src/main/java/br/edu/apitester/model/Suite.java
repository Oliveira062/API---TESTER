package br.edu.apitester.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record Suite(
        String name,
        String baseUrl,
        Long timeoutMs,
        Map<String, Object> variables,
        Map<String, String> headers,
        Boolean checkLeaks,
        Security security,
        List<Scenario> scenarios
) {

    public Suite {
        timeoutMs = timeoutMs == null ? 10_000L : timeoutMs;
        variables = variables == null ? new LinkedHashMap<>() : variables;
        headers = headers == null ? Map.of() : headers;
        checkLeaks = checkLeaks == null || checkLeaks;
        scenarios = scenarios == null ? List.of() : scenarios;
    }

    public Suite withBaseUrl(String newBaseUrl) {
        return new Suite(name, newBaseUrl, timeoutMs, variables, headers, checkLeaks, security, scenarios);
    }
}
