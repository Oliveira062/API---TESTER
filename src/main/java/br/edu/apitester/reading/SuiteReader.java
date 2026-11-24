package br.edu.apitester.reading;

import br.edu.apitester.model.Scenario;
import br.edu.apitester.model.Step;
import br.edu.apitester.model.Suite;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class SuiteReader {

    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    private final ObjectMapper json = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    public List<Path> findFiles(List<String> inputs) throws IOException {
        List<Path> files = new ArrayList<>();
        for (String input : inputs) {
            Path path = Path.of(input);
            if (Files.isDirectory(path)) {
                try (Stream<Path> content = Files.list(path)) {
                    content.filter(SuiteReader::isSuiteFile).sorted().forEach(files::add);
                }
            } else if (Files.isRegularFile(path)) {
                files.add(path);
            } else {
                throw new InvalidFileException("File or folder not found: " + input);
            }
        }
        if (files.isEmpty()) {
            throw new InvalidFileException("No .yaml, .yml or .json file found.");
        }
        return files;
    }

    public Suite read(Path file) {
        Suite suite;
        try {
            ObjectMapper reader = file.toString().toLowerCase().endsWith(".json") ? json : yaml;
            suite = reader.readValue(file.toFile(), Suite.class);
        } catch (IOException e) {
            throw new InvalidFileException("Could not read " + file.getFileName() + ": " + summarize(e.getMessage()));
        }
        validate(suite, file);
        return suite;
    }

    private void validate(Suite suite, Path file) {
        String fileName = file.getFileName().toString();
        if (suite.scenarios().isEmpty() && suite.security() == null) {
            throw new InvalidFileException(fileName + ": the suite needs 'scenarios' or 'security'.");
        }
        for (int i = 0; i < suite.scenarios().size(); i++) {
            Scenario scenario = suite.scenarios().get(i);
            String where = fileName + ", scenario " + (i + 1);
            if (scenario.name() == null || scenario.name().isBlank()) {
                throw new InvalidFileException(where + ": the scenario needs a 'name'.");
            }
            if (scenario.steps().isEmpty()) {
                throw new InvalidFileException(where + " (" + scenario.name() + "): the scenario has no 'steps'.");
            }
            for (int j = 0; j < scenario.steps().size(); j++) {
                Step step = scenario.steps().get(j);
                String stepWhere = where + ", step " + (j + 1);
                if (step.path() == null || step.path().isBlank()) {
                    throw new InvalidFileException(stepWhere + ": set the 'path', e.g. /api/products.");
                }
                if (!METHODS.contains(step.method())) {
                    throw new InvalidFileException(stepWhere + ": unknown method '" + step.method() + "'.");
                }
            }
        }
    }

    private static boolean isSuiteFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".json");
    }

    private static String summarize(String message) {
        if (message == null) {
            return "invalid format";
        }
        int cut = message.indexOf("\n at [Source");
        String text = cut > 0 ? message.substring(0, cut) : message;
        return text.replace("Unrecognized field", "Unknown field").trim();
    }
}
