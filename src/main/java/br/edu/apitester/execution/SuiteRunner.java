package br.edu.apitester.execution;

import br.edu.apitester.model.Scenario;
import br.edu.apitester.model.Step;
import br.edu.apitester.model.Suite;
import br.edu.apitester.result.ScenarioResult;
import br.edu.apitester.result.StepResult;
import br.edu.apitester.result.SuiteResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class SuiteRunner {

    private final StepRunner stepRunner;
    private final SecurityScan securityScan;
    private final Interpolator interpolator;

    public SuiteRunner(StepRunner stepRunner, SecurityScan securityScan, Interpolator interpolator) {
        this.stepRunner = stepRunner;
        this.securityScan = securityScan;
        this.interpolator = interpolator;
    }

    public SuiteResult run(Suite suite, String file, Map<String, String> extraVariables, List<String> tags,
                           boolean failFast, Consumer<StepResult> onStepFinished) {
        long start = System.currentTimeMillis();
        Context context = new Context();
        List<ScenarioResult> scenarios = new ArrayList<>();

        String variableError = loadVariables(suite, extraVariables, context);
        if (variableError != null) {
            StepResult failure = new StepResult("Load variables", "-", null, null, 0, List.of(), variableError, null);
            onStepFinished.accept(failure);
            scenarios.add(new ScenarioResult("Setup", List.of(failure)));
            return new SuiteResult(nameOf(suite, file), file, scenarios, System.currentTimeMillis() - start);
        }

        boolean stop = false;
        for (Scenario scenario : suite.scenarios()) {
            if (!scenario.hasAnyTag(tags)) {
                continue;
            }
            List<StepResult> steps = new ArrayList<>();
            for (Step step : scenario.steps()) {
                StepResult result = stepRunner.run(step, suite, context);
                steps.add(result);
                onStepFinished.accept(result);
                if (!result.passed()) {
                    stop = failFast;
                    break;
                }
            }
            scenarios.add(new ScenarioResult(scenario.name(), steps));
            if (stop) {
                break;
            }
        }

        if (!stop && suite.security() != null) {
            ScenarioResult scanResult = securityScan.run(suite, context);
            scanResult.steps().forEach(onStepFinished);
            scenarios.add(scanResult);
        }

        return new SuiteResult(nameOf(suite, file), file, scenarios, System.currentTimeMillis() - start);
    }

    private String loadVariables(Suite suite, Map<String, String> extras, Context context) {
        extras.forEach(context::set);
        try {
            for (Map.Entry<String, Object> variable : suite.variables().entrySet()) {
                if (!extras.containsKey(variable.getKey())) {
                    context.set(variable.getKey(), interpolator.interpolate(variable.getValue(), context));
                }
            }
            return null;
        } catch (UndefinedVariableException e) {
            return e.getMessage();
        }
    }

    private String nameOf(Suite suite, String file) {
        return suite.name() == null || suite.name().isBlank() ? file : suite.name();
    }
}
