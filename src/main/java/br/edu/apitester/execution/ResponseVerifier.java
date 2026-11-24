package br.edu.apitester.execution;

import br.edu.apitester.model.Expectation;
import br.edu.apitester.model.Rule;
import br.edu.apitester.result.Check;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.InvalidJsonException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class ResponseVerifier {

    private static final Pattern LEAK_SIGNS = Pattern.compile(
            "(\\bat [a-zA-Z0-9_.$]+\\([A-Za-z0-9_]+\\.java:\\d+\\))|(Exception:)|(Traceback \\(most recent call last\\))"
                    + "|(SQLSTATE)|(org\\.hibernate\\.)|(psycopg2\\.)|(ORA-\\d{5})");

    private static final int EXCERPT_LIMIT = 200;

    public List<Check> verify(Expectation expect, Response response, boolean checkLeaks) {
        List<Check> checks = new ArrayList<>();

        if (!expect.status().isEmpty()) {
            String description = "status " + join(expect.status());
            checks.add(expect.status().contains(response.status())
                    ? Check.ok(description)
                    : Check.fail(description, "got " + response.status() + bodyExcerpt(response)));
        }

        if (expect.maxTimeMs() != null) {
            String description = "time up to " + expect.maxTimeMs() + " ms";
            checks.add(response.durationMs() <= expect.maxTimeMs()
                    ? Check.ok(description)
                    : Check.fail(description, "took " + response.durationMs() + " ms"));
        }

        String body = response.body() == null ? "" : response.body();
        if (expect.bodyContains() != null) {
            String description = "body contains \"" + expect.bodyContains() + "\"";
            checks.add(body.contains(expect.bodyContains())
                    ? Check.ok(description)
                    : Check.fail(description, "text not found" + bodyExcerpt(response)));
        }
        if (expect.bodyNotContains() != null) {
            String description = "body does not contain \"" + expect.bodyNotContains() + "\"";
            checks.add(!body.contains(expect.bodyNotContains())
                    ? Check.ok(description)
                    : Check.fail(description, "text found"));
        }

        for (Map.Entry<String, String> expected : expect.headers().entrySet()) {
            String description = "header " + expected.getKey() + " contains \"" + expected.getValue() + "\"";
            String received = response.header(expected.getKey());
            checks.add(received != null && received.contains(expected.getValue())
                    ? Check.ok(description)
                    : Check.fail(description, received == null ? "header missing" : "got: " + received));
        }

        if (!expect.body().isEmpty()) {
            verifyJsonRules(expect.body(), body, checks);
        }

        if (checkLeaks && response.status() >= 400 && LEAK_SIGNS.matcher(body).find()) {
            checks.add(Check.fail("error response without internal details",
                    "the response exposes a stack trace or an internal message" + bodyExcerpt(response)));
        }

        return checks;
    }

    private void verifyJsonRules(List<Rule> rules, String body, List<Check> checks) {
        DocumentContext document;
        try {
            document = JsonPath.parse(body);
        } catch (InvalidJsonException | IllegalArgumentException e) {
            checks.add(Check.fail("body is JSON", "the response is not valid JSON"));
            return;
        }
        for (Rule rule : rules) {
            checks.addAll(applyRule(rule, document));
        }
    }

    List<Check> applyRule(Rule rule, DocumentContext document) {
        List<Check> result = new ArrayList<>();
        String path = rule.path();
        if (path == null || path.isBlank()) {
            result.add(Check.fail("rule without path", "every rule needs a 'path', e.g. $.id"));
            return result;
        }

        Object value;
        boolean found;
        try {
            value = document.read(path);
            found = true;
        } catch (PathNotFoundException e) {
            value = null;
            found = false;
        } catch (RuntimeException e) {
            result.add(Check.fail(path, "invalid JSONPath expression: " + e.getMessage()));
            return result;
        }

        if (rule.exists() != null) {
            boolean expected = rule.exists();
            result.add(found == expected
                    ? Check.ok(path + (expected ? " exists" : " does not exist"))
                    : Check.fail(path + (expected ? " exists" : " does not exist"),
                    expected ? "field missing" : "field present with value " + value));
        }

        boolean hasOtherRules = rule.equals() != null || rule.notEquals() != null || rule.contains() != null
                || rule.greaterThan() != null || rule.lessThan() != null || rule.length() != null
                || rule.type() != null || rule.regex() != null;
        if (!hasOtherRules) {
            return result;
        }
        if (!found) {
            result.add(Check.fail(path, "field missing from the response"));
            return result;
        }

        if (rule.equals() != null) {
            String description = path + " = " + rule.equals();
            result.add(areEqual(value, rule.equals())
                    ? Check.ok(description)
                    : Check.fail(description, "got: " + value));
        }
        if (rule.notEquals() != null) {
            String description = path + " ≠ " + rule.notEquals();
            result.add(!areEqual(value, rule.notEquals())
                    ? Check.ok(description)
                    : Check.fail(description, "got exactly " + value));
        }
        if (rule.contains() != null) {
            String description = path + " contains " + rule.contains();
            result.add(contains(value, rule.contains())
                    ? Check.ok(description)
                    : Check.fail(description, "got: " + value));
        }
        if (rule.greaterThan() != null) {
            String description = path + " > " + rule.greaterThan();
            BigDecimal number = asNumber(value);
            result.add(number != null && number.compareTo(BigDecimal.valueOf(rule.greaterThan())) > 0
                    ? Check.ok(description)
                    : Check.fail(description, "got: " + value));
        }
        if (rule.lessThan() != null) {
            String description = path + " < " + rule.lessThan();
            BigDecimal number = asNumber(value);
            result.add(number != null && number.compareTo(BigDecimal.valueOf(rule.lessThan())) < 0
                    ? Check.ok(description)
                    : Check.fail(description, "got: " + value));
        }
        if (rule.length() != null) {
            String description = path + " with length " + rule.length();
            Integer length = lengthOf(value);
            result.add(length != null && length.equals(rule.length())
                    ? Check.ok(description)
                    : Check.fail(description, "length received: " + length));
        }
        if (rule.type() != null) {
            String description = path + " of type " + rule.type();
            String actualType = typeOf(value);
            result.add(actualType.equalsIgnoreCase(rule.type().trim())
                    ? Check.ok(description)
                    : Check.fail(description, "type received: " + actualType));
        }
        if (rule.regex() != null) {
            String description = path + " matches " + rule.regex();
            result.add(value != null && Pattern.compile(rule.regex()).matcher(String.valueOf(value)).find()
                    ? Check.ok(description)
                    : Check.fail(description, "got: " + value));
        }
        return result;
    }

    static boolean areEqual(Object received, Object expected) {
        BigDecimal a = asNumber(received);
        BigDecimal b = asNumber(expected);
        if (a != null && b != null && (received instanceof Number || expected instanceof Number)) {
            return a.compareTo(b) == 0;
        }
        return String.valueOf(received).equals(String.valueOf(expected));
    }

    private static boolean contains(Object value, Object wanted) {
        if (value instanceof Collection<?> collection) {
            return collection.stream().anyMatch(item -> areEqual(item, wanted));
        }
        return value != null && String.valueOf(value).contains(String.valueOf(wanted));
    }

    private static BigDecimal asNumber(Object value) {
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof String text) {
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static Integer lengthOf(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.size();
        }
        if (value instanceof Map<?, ?> map) {
            return map.size();
        }
        if (value instanceof String text) {
            return text.length();
        }
        return null;
    }

    private static String typeOf(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return "string";
        }
        if (value instanceof Number) {
            return "number";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof Collection<?>) {
            return "array";
        }
        if (value instanceof Map<?, ?>) {
            return "object";
        }
        return value.getClass().getSimpleName();
    }

    private static String join(List<Integer> status) {
        return status.size() == 1 ? String.valueOf(status.getFirst()) : "in " + status;
    }

    private static String bodyExcerpt(Response response) {
        String body = response.body();
        if (body == null || body.isBlank()) {
            return "";
        }
        String clean = body.replaceAll("\\s+", " ").trim();
        return " | body: " + (clean.length() > EXCERPT_LIMIT ? clean.substring(0, EXCERPT_LIMIT) + "..." : clean);
    }
}
