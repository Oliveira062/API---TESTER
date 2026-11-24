package br.edu.apitester.report;

import br.edu.apitester.result.SuiteResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface Report {

    Path generate(List<SuiteResult> results, Path folder) throws IOException;
}
