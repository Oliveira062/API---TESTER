package br.edu.apitester.execution;

import java.util.LinkedHashMap;
import java.util.Map;

public class Context {

    private final Map<String, Object> variables = new LinkedHashMap<>();

    public void set(String name, Object value) {
        variables.put(name, value);
    }

    public boolean has(String name) {
        return variables.containsKey(name);
    }

    public Object get(String name) {
        return variables.get(name);
    }

    public Map<String, Object> all() {
        return Map.copyOf(variables);
    }
}
