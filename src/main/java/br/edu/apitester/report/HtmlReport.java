package br.edu.apitester.report;

import br.edu.apitester.result.Check;
import br.edu.apitester.result.ScenarioResult;
import br.edu.apitester.result.Statistics;
import br.edu.apitester.result.StepResult;
import br.edu.apitester.result.SuiteResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class HtmlReport implements Report {

    private static final String STYLE = """
            body { font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; margin: 0;
                   background: #f5f6f8; color: #1f2933; }
            header { background: #1f2933; color: #fff; padding: 24px 32px; }
            header h1 { margin: 0 0 4px; font-size: 22px; }
            header p { margin: 0; color: #b8c2cc; font-size: 14px; }
            main { padding: 24px 32px; max-width: 1100px; }
            .numbers { display: flex; gap: 16px; margin-bottom: 24px; flex-wrap: wrap; }
            .number { background: #fff; border-radius: 8px; padding: 16px 20px; min-width: 140px;
                      border: 1px solid #e4e7eb; }
            .number strong { display: block; font-size: 26px; }
            .number span { color: #616e7c; font-size: 13px; }
            .ok strong { color: #1e7e34; }
            .fail strong { color: #c62828; }
            section { background: #fff; border: 1px solid #e4e7eb; border-radius: 8px; margin-bottom: 20px; }
            section h2 { font-size: 17px; margin: 0; padding: 14px 18px; border-bottom: 1px solid #e4e7eb; }
            section h2 small { color: #616e7c; font-weight: normal; }
            h3 { font-size: 14px; margin: 16px 18px 6px; color: #3e4c59; }
            table { width: 100%; border-collapse: collapse; font-size: 13px; }
            td { padding: 8px 18px; border-top: 1px solid #f0f2f4; vertical-align: top; }
            td.mark { width: 70px; font-weight: 600; }
            tr.passed td.mark { color: #1e7e34; }
            tr.failed td.mark { color: #c62828; }
            tr.failed { background: #fff5f5; }
            td.info { color: #616e7c; white-space: nowrap; width: 160px; text-align: right; }
            .url { color: #7b8794; font-size: 12px; word-break: break-all; }
            .detail { color: #c62828; margin-top: 6px; font-size: 12px; }
            .stats { color: #3e4c59; margin-top: 4px; font-size: 12px; }
            """;

    @Override
    public Path generate(List<SuiteResult> results, Path folder) throws IOException {
        int total = results.stream().mapToInt(SuiteResult::totalSteps).sum();
        int failed = results.stream().mapToInt(SuiteResult::failedSteps).sum();
        long time = results.stream().mapToLong(SuiteResult::durationMs).sum();
        String when = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
                .append("<title>API test report</title>\n<style>").append(STYLE).append("</style>\n")
                .append("</head>\n<body>\n<header>\n<h1>API test report</h1>\n")
                .append("<p>Generated on ").append(when).append(" by api-tester</p>\n</header>\n<main>\n");

        html.append("<div class=\"numbers\">\n")
                .append(number(String.valueOf(total), "steps run", ""))
                .append(number(String.valueOf(total - failed), "passed", "ok"))
                .append(number(String.valueOf(failed), "failed", failed > 0 ? "fail" : ""))
                .append(number(time + " ms", "total time", ""))
                .append("</div>\n");

        for (SuiteResult suite : results) {
            html.append("<section>\n<h2>").append(Text.xml(suite.name())).append(" <small>")
                    .append(Text.xml(suite.file())).append(" &middot; ")
                    .append(suite.totalSteps() - suite.failedSteps()).append("/").append(suite.totalSteps())
                    .append(" passed</small></h2>\n");
            for (ScenarioResult scenario : suite.scenarios()) {
                html.append("<h3>").append(Text.xml(scenario.name())).append("</h3>\n<table>\n");
                for (StepResult step : scenario.steps()) {
                    html.append(row(step));
                }
                html.append("</table>\n");
            }
            html.append("</section>\n");
        }
        html.append("</main>\n</body>\n</html>\n");

        Files.createDirectories(folder);
        Path file = folder.resolve("report.html");
        Files.writeString(file, html.toString(), StandardCharsets.UTF_8);
        return file;
    }

    private String number(String value, String label, String cssClass) {
        return "<div class=\"number " + cssClass + "\"><strong>" + value + "</strong><span>" + label + "</span></div>\n";
    }

    private String row(StepResult step) {
        StringBuilder html = new StringBuilder();
        html.append("<tr class=\"").append(step.passed() ? "passed" : "failed").append("\">")
                .append("<td class=\"mark\">").append(step.passed() ? "PASS" : "FAIL").append("</td><td>")
                .append(Text.xml(step.name()));
        if (step.url() != null) {
            html.append("<div class=\"url\">").append(Text.xml(step.method())).append(" ")
                    .append(Text.xml(step.url())).append("</div>");
        }
        Statistics s = step.statistics();
        if (s != null) {
            html.append("<div class=\"stats\">").append(s.total()).append(" requests &middot; min ")
                    .append(s.minMs()).append(" ms &middot; avg ").append(s.avgMs()).append(" ms &middot; p95 ")
                    .append(s.p95Ms()).append(" ms &middot; max ").append(s.maxMs()).append(" ms</div>");
        }
        if (step.error() != null) {
            html.append("<div class=\"detail\">").append(Text.xml(step.error())).append("</div>");
        }
        for (Check failure : step.failures()) {
            html.append("<div class=\"detail\">Expected: ").append(Text.xml(failure.description()))
                    .append("<br>Actual: ").append(Text.xml(failure.detail())).append("</div>");
        }
        String status = step.status() == null ? "no response" : "HTTP " + step.status();
        html.append("</td><td class=\"info\">").append(status).append(" &middot; ").append(step.durationMs())
                .append(" ms</td></tr>\n");
        return html.toString();
    }
}
