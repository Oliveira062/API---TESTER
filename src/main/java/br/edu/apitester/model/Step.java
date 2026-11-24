package br.edu.apitester.model;

import java.util.Map;

public record Step(
        String name,
        String method,
        String path,
        Map<String, String> headers,
        String token,
        Object body,
        Expectation expect,
        Map<String, String> capture,
        Integer repeat,
        Integer concurrency,
        Long pauseMs
) {

    public Step {
        method = method == null ? "GET" : method.trim().toUpperCase();
        headers = headers == null ? Map.of() : headers;
        expect = expect == null ? Expectation.empty() : expect;
        capture = capture == null ? Map.of() : capture;
        repeat = repeat == null || repeat < 1 ? 1 : repeat;
        concurrency = concurrency == null || concurrency < 1 ? 1 : concurrency;
        pauseMs = pauseMs == null ? 0L : pauseMs;
    }
}
