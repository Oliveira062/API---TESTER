package br.edu.apitester.result;

import java.util.List;

public record SuiteResult(String name, String file, List<ScenarioResult> scenarios, long durationMs) {

    public boolean passed() {
        return scenarios.stream().allMatch(ScenarioResult::passed);
    }

    public int totalSteps() {
        return scenarios.stream().mapToInt(s -> s.steps().size()).sum();
    }

    public int failedSteps() {
        return (int) scenarios.stream().flatMap(s -> s.steps().stream()).filter(s -> !s.passed()).count();
    }
}
