package br.edu.apitester;

import br.edu.apitester.execution.HttpSender;
import br.edu.apitester.execution.Interpolator;
import br.edu.apitester.execution.ResponseVerifier;
import br.edu.apitester.execution.SecurityScan;
import br.edu.apitester.execution.StepRunner;
import br.edu.apitester.execution.SuiteRunner;
import br.edu.apitester.reading.InvalidFileException;
import br.edu.apitester.reading.SuiteReader;
import br.edu.apitester.model.Suite;
import br.edu.apitester.report.ConsoleReport;
import br.edu.apitester.report.HtmlReport;
import br.edu.apitester.report.JUnitReport;
import br.edu.apitester.result.SuiteResult;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Application {

    public static void main(String[] args) {
        System.exit(run(args, new PrintStream(System.out, true, StandardCharsets.UTF_8)));
    }

    public static int run(String[] args, PrintStream out) {
        Options options;
        try {
            options = Options.parse(args);
        } catch (IllegalArgumentException e) {
            out.println("Error: " + e.getMessage());
            out.println();
            out.println(Options.helpText());
            return 2;
        }
        if (options.help() || options.inputs().isEmpty()) {
            out.println(Options.helpText());
            return options.help() ? 0 : 2;
        }

        SuiteReader reader = new SuiteReader();
        List<Path> files;
        List<Suite> suites = new ArrayList<>();
        try {
            files = reader.findFiles(options.inputs());
            for (Path file : files) {
                Suite suite = reader.read(file);
                suites.add(options.baseUrl() == null ? suite : suite.withBaseUrl(options.baseUrl()));
            }
        } catch (InvalidFileException | IOException e) {
            out.println("Error: " + e.getMessage());
            return 2;
        }

        HttpSender sender = new HttpSender();
        Interpolator interpolator = new Interpolator();
        SuiteRunner runner = new SuiteRunner(
                new StepRunner(sender, interpolator, new ResponseVerifier()),
                new SecurityScan(sender, interpolator),
                interpolator);
        ConsoleReport console = new ConsoleReport(out, options.colors());

        List<SuiteResult> results = new ArrayList<>();
        for (int i = 0; i < suites.size(); i++) {
            Suite suite = suites.get(i);
            String file = files.get(i).getFileName().toString();
            console.suiteStarted(suite.name() == null ? file : suite.name(), file);
            results.add(runner.run(suite, file, options.variables(), options.tags(), options.failFast(), console::step));
        }
        console.summary(results);

        try {
            Path folder = Path.of(options.reportsFolder());
            Path html = new HtmlReport().generate(results, folder);
            Path junit = new JUnitReport().generate(results, folder);
            out.println("Reports: " + html.toAbsolutePath() + " and " + junit.toAbsolutePath());
        } catch (IOException e) {
            out.println("Warning: could not save the reports: " + e.getMessage());
        }

        return results.stream().allMatch(SuiteResult::passed) ? 0 : 1;
    }
}
