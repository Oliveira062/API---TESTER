package br.edu.apitester.execution;

import java.util.List;
import java.util.Map;

public record Response(int status, Map<String, List<String>> headers, String body, long durationMs) {

    public String header(String name) {
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return String.join(", ", entry.getValue());
            }
        }
        return null;
    }
}
