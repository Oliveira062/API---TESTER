package br.edu.apitester;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record Options(List<String> inputs, String baseUrl, Map<String, String> variables, List<String> tags,
                      String reportsFolder, boolean failFast, boolean colors, boolean help) {

    public static Options parse(String[] args) {
        List<String> inputs = new ArrayList<>();
        Map<String, String> variables = new LinkedHashMap<>();
        List<String> tags = new ArrayList<>();
        String baseUrl = null;
        String folder = "reports";
        boolean failFast = false;
        boolean colors = System.console() != null;
        boolean help = args.length == 0;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--base-url" -> baseUrl = valueOf(args, ++i, arg);
                case "--reports" -> folder = valueOf(args, ++i, arg);
                case "--tags" -> tags.addAll(List.of(valueOf(args, ++i, arg).split(",")));
                case "--fail-fast" -> failFast = true;
                case "--no-color" -> colors = false;
                case "--help", "-h" -> help = true;
                case "--var" -> {
                    String pair = valueOf(args, ++i, arg);
                    int equalsSign = pair.indexOf('=');
                    if (equalsSign <= 0) {
                        throw new IllegalArgumentException("Use --var name=value (got: " + pair + ")");
                    }
                    variables.put(pair.substring(0, equalsSign).trim(), pair.substring(equalsSign + 1));
                }
                default -> {
                    if (arg.startsWith("--")) {
                        throw new IllegalArgumentException("Unknown option: " + arg);
                    }
                    inputs.add(arg);
                }
            }
        }
        tags.replaceAll(String::trim);
        tags.removeIf(String::isEmpty);
        return new Options(inputs, baseUrl, variables, tags, folder, failFast, colors, help);
    }

    private static String valueOf(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException("The option " + option + " needs a value.");
        }
        return args[index];
    }

    public static String helpText() {
        return """
                api-tester - runs REST API tests described in YAML or JSON files

                Usage:
                  java -jar api-tester.jar <file-or-folder>... [options]

                Options:
                  --base-url URL        overrides the baseUrl defined in the files
                  --var name=value      defines or overrides a variable (repeatable)
                  --tags a,b            runs only the scenarios with one of these tags
                  --reports FOLDER      where to save report.html and junit.xml (default: reports)
                  --fail-fast           stops the suite at the first failed step
                  --no-color            plain output (useful in CI)
                  --help                shows this message

                Exit codes:
                  0 all steps passed | 1 some step failed | 2 usage or file error
                """;
    }
}
