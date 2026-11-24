package br.edu.apitester.result;

import java.util.List;

public record StepResult(String name, String method, String url, Integer status, long durationMs,
                         List<Check> checks, String error, Statistics statistics) {

    public boolean passed() {
        return error == null && checks.stream().allMatch(Check::passed);
    }

    public List<Check> failures() {
        return checks.stream().filter(c -> !c.passed()).toList();
    }
}
