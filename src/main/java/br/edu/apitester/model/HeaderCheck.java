package br.edu.apitester.model;

import java.util.List;

public record HeaderCheck(String path, List<String> require) {

    public HeaderCheck {
        path = path == null ? "/" : path;
        require = require == null || require.isEmpty()
                ? List.of("X-Content-Type-Options", "X-Frame-Options")
                : require;
    }
}
