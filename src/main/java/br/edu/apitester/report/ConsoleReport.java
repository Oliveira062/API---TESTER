package br.edu.apitester.report;

import br.edu.apitester.result.Check;
import br.edu.apitester.result.Statistics;
import br.edu.apitester.result.StepResult;
import br.edu.apitester.result.SuiteResult;

import java.io.PrintStream;
import java.util.List;

public class ConsoleReport {

    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String GRAY = "\u001B[90m";
    private static final String BOLD = "\u001B[1m";
    private static final String RESET = "\u001B[0m";

    private final PrintStream out;
    private final boolean colors;

    public ConsoleReport(PrintStream out, boolean colors) {
        this.out = out;
        this.colors = colors;
    }

    public void suiteStarted(String name, String file) {
        out.println();
        out.println(color(BOLD, "== " + name) + color(GRAY, "  (" + file + ")"));
    }

    public void step(StepResult step) {
        String mark = step.passed() ? color(GREEN, "[PASS]") : color(RED, "[FAIL]");
        String status = step.status() == null ? "---" : String.valueOf(step.status());
        out.printf("%s %s %s%n", mark, step.name(), color(GRAY, "(" + status + ", " + step.durationMs() + " ms)"));
        if (step.statistics() != null) {
            Statistics s = step.statistics();
            out.println(color(GRAY, String.format("       %d req | min %d ms | avg %d ms | p95 %d ms | max %d ms",
                    s.total(), s.minMs(), s.avgMs(), s.p95Ms(), s.maxMs())));
        }
        if (step.error() != null) {
            out.println(color(RED, "       " + step.error()));
        }
        for (Check failure : step.failures()) {
            out.println(color(RED, "       expected: " + failure.description()));
            out.println(color(RED, "       actual:   " + failure.detail()));
        }
    }

    public void summary(List<SuiteResult> results) {
        int total = results.stream().mapToInt(SuiteResult::totalSteps).sum();
        int failed = results.stream().mapToInt(SuiteResult::failedSteps).sum();
        long time = results.stream().mapToLong(SuiteResult::durationMs).sum();
        out.println();
        String line = String.format("Result: %d steps, %d passed, %d failed in %d ms", total, total - failed, failed, time);
        out.println(color(BOLD, color(failed == 0 ? GREEN : RED, line)));
    }

    private String color(String code, String text) {
        return colors ? code + text + RESET : text;
    }
}
