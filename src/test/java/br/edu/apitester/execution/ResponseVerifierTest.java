package br.edu.apitester.execution;

import br.edu.apitester.model.Expectation;
import br.edu.apitester.model.Rule;
import br.edu.apitester.result.Check;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseVerifierTest {

    private static final String BODY = """
            {"id": 7, "name": "Croquette", "price": 6.00, "active": true,
             "tags": ["savory", "fried"], "items": [{"qty": 2}, {"qty": 3}]}
            """;

    private final ResponseVerifier verifier = new ResponseVerifier();

    private Rule rule(String path) {
        return new Rule(path, null, null, null, null, null, null, null, null, null);
    }

    private List<Check> verify(Expectation expect, int status, String body) {
        return verifier.verify(expect, new Response(status, Map.of(), body, 10), true);
    }

    private boolean allPassed(List<Check> checks) {
        return checks.stream().allMatch(Check::passed);
    }

    @Test
    void checksStatusAndTime() {
        Expectation expect = new Expectation(List.of(200, 201), 50L, null, null, null, null, null);
        assertTrue(allPassed(verify(expect, 201, BODY)));
        assertFalse(allPassed(verify(expect, 500, BODY)));
    }

    @Test
    void comparesNumbersRegardlessOfDecimalPlaces() {
        Rule price = new Rule("$.price", 6, null, null, null, null, null, null, null, null);
        Expectation expect = new Expectation(null, null, null, null, null, null, List.of(price));
        assertTrue(allPassed(verify(expect, 200, BODY)));
    }

    @Test
    void appliesTypeLengthContainsAndRegexRules() {
        List<Rule> rules = List.of(
                new Rule("$.tags", null, null, null, "fried", null, null, 2, "array", null),
                new Rule("$.name", null, "Pastry", null, null, null, null, null, "string", "^Cro"),
                new Rule("$.items[*].qty", null, null, null, 3, null, null, null, null, null),
                new Rule("$.id", null, null, null, null, 5.0, 10.0, null, "number", null),
                new Rule("$.active", true, null, null, null, null, null, null, "boolean", null));
        Expectation expect = new Expectation(null, null, null, null, null, null, rules);
        List<Check> result = verify(expect, 200, BODY);
        assertTrue(allPassed(result), result.toString());
    }

    @Test
    void detectsMissingAndPresentFields() {
        Rule noPassword = new Rule("$.password", null, null, false, null, null, null, null, null, null);
        Rule hasId = new Rule("$.id", null, null, true, null, null, null, null, null, null);
        Expectation expect = new Expectation(null, null, null, null, null, null, List.of(noPassword, hasId));
        assertTrue(allPassed(verify(expect, 200, BODY)));

        Expectation onlyPath = new Expectation(null, null, null, null, null, null, List.of(rule("$.missing")));
        assertTrue(allPassed(verify(onlyPath, 200, BODY)));
    }

    @Test
    void failsWhenTheConditionDoesNotMatch() {
        Rule wrong = new Rule("$.name", "Pastry", null, null, null, null, null, null, null, null);
        List<Check> result = verify(new Expectation(null, null, null, null, null, null, List.of(wrong)), 200, BODY);
        assertEquals(1, result.size());
        assertFalse(result.getFirst().passed());
        assertTrue(result.getFirst().detail().contains("Croquette"));
    }

    @Test
    void flagsStackTraceLeaksInErrors() {
        String leaked = "java.lang.NullPointerException: x\n\tat com.example.App.method(App.java:42)";
        assertFalse(allPassed(verify(Expectation.empty(), 500, leaked)));
        assertTrue(allPassed(verify(Expectation.empty(), 500, "{\"message\": \"Internal error\"}")));
    }

    @Test
    void checksHeaderPresence() {
        Expectation expect = new Expectation(null, null, null, null, null, Map.of("content-type", "json"), null);
        Response response = new Response(200, Map.of("Content-Type", List.of("application/json")), "{}", 5);
        assertTrue(allPassed(verifier.verify(expect, response, true)));
    }
}
