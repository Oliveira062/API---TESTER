package br.edu.apitester.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.List;
import java.util.Map;

public record Expectation(
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        List<Integer> status,
        Long maxTimeMs,
        Long p95MaxMs,
        String bodyContains,
        String bodyNotContains,
        Map<String, String> headers,
        List<Rule> body
) {

    public Expectation {
        status = status == null ? List.of() : status;
        headers = headers == null ? Map.of() : headers;
        body = body == null ? List.of() : body;
    }

    public static Expectation empty() {
        return new Expectation(null, null, null, null, null, null, null);
    }
}
