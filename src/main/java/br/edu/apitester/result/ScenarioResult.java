package br.edu.apitester.result;

import java.util.List;

public record ScenarioResult(String name, List<StepResult> steps) {

    public boolean passed() {
        return steps.stream().allMatch(StepResult::passed);
    }

    public long durationMs() {
        return steps.stream().mapToLong(StepResult::durationMs).sum();
    }
}
