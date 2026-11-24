package br.edu.apitester.report;

import br.edu.apitester.result.Check;
import br.edu.apitester.result.ScenarioResult;
import br.edu.apitester.result.StepResult;
import br.edu.apitester.result.SuiteResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class JUnitReport implements Report {

    @Override
    public Path generate(List<SuiteResult> results, Path folder) throws IOException {
        int total = results.stream().mapToInt(SuiteResult::totalSteps).sum();
        int failures = results.stream().mapToInt(SuiteResult::failedSteps).sum();
        long time = results.stream().mapToLong(SuiteResult::durationMs).sum();

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append(String.format("<testsuites name=\"api-tester\" tests=\"%d\" failures=\"%d\" time=\"%s\">%n",
                total, failures, Text.seconds(time)));

        for (SuiteResult suite : results) {
            xml.append(String.format("  <testsuite name=\"%s\" tests=\"%d\" failures=\"%d\" time=\"%s\">%n",
                    Text.xml(suite.name()), suite.totalSteps(), suite.failedSteps(), Text.seconds(suite.durationMs())));
            for (ScenarioResult scenario : suite.scenarios()) {
                for (StepResult step : scenario.steps()) {
                    xml.append(String.format("    <testcase classname=\"%s\" name=\"%s\" time=\"%s\"",
                            Text.xml(suite.name() + "." + scenario.name()), Text.xml(step.name()),
                            Text.seconds(step.durationMs())));
                    if (step.passed()) {
                        xml.append("/>\n");
                        continue;
                    }
                    xml.append(">\n");
                    String message = step.error() != null ? step.error() : step.failures().stream()
                            .map(Check::description).collect(Collectors.joining("; "));
                    String details = step.failures().stream()
                            .map(c -> "expected: " + c.description() + " | actual: " + c.detail())
                            .collect(Collectors.joining("\n"));
                    xml.append(String.format("      <failure message=\"%s\">%s</failure>%n",
                            Text.xml(message), Text.xml(details)));
                    xml.append("    </testcase>\n");
                }
            }
            xml.append("  </testsuite>\n");
        }
        xml.append("</testsuites>\n");

        Files.createDirectories(folder);
        Path file = folder.resolve("junit.xml");
        Files.writeString(file, xml.toString(), StandardCharsets.UTF_8);
        return file;
    }
}
