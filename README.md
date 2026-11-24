# api-tester

Command-line tool to test any REST API from YAML or JSON files.
It does not depend on the language the API was written in: it works for Java, Python, Node, PHP or anything else.

## Build and run

```bash
mvn package
java -jar target/api-tester.jar examples/template.yaml
```

Or use `./run.sh` (Linux/macOS) or `run.bat` (Windows), which build the jar the first time.

You need JDK 21 and Maven 3.9 or newer installed.

## What it does

- runs scenarios in order and keeps values from responses (such as the token) for the next steps
- checks status, response time, headers and JSON fields (JSONPath)
- repeats requests in parallel and measures min, average, p95 and max
- scans protected routes for security (no token, malformed token and unsigned token)
- checks security headers and flags error responses that leak stack traces
- generates `report.html` and `junit.xml` (accepted by GitHub Actions, GitLab and Jenkins)
- returns exit code 0, 1 or 2, for continuous integration

## Example

```yaml
name: My API
baseUrl: http://localhost:8080

scenarios:
  - name: Public menu
    steps:
      - name: List products
        path: /api/products
        expect:
          status: 200
          maxTimeMs: 1000
          body:
            - path: $[*].name
              contains: Coffee

security:
  protectedRoutes:
    - GET /api/orders
```

## Options

```
--base-url URL        overrides the baseUrl in the files
--var name=value      defines variables (repeatable)
--tags a,b            runs only the scenarios with these tags
--reports FOLDER      reports folder (default: reports)
--fail-fast           stops at the first failed step
--no-color            plain output
```

## Built-in variables

| Variable | Value |
|---|---|
| `${random}` | 8 random characters |
| `${uuid}` | UUID |
| `${now}` | Current date and time (ISO) |
| `${timestamp}` | Milliseconds since 1970 |
| `${env.NAME}` | Environment variable |

## Body rules

`equals`, `notEquals`, `exists`, `contains`, `greaterThan`, `lessThan`, `length`, `type`
(`string`, `number`, `boolean`, `array`, `object`, `null`) and `regex`.
