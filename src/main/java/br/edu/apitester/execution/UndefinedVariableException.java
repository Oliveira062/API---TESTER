package br.edu.apitester.execution;

public class UndefinedVariableException extends RuntimeException {

    public UndefinedVariableException(String name) {
        super("Undefined variable: ${" + name + "}. Define it under 'variables', capture it in an earlier step "
                + "or pass it with --var " + name + "=value.");
    }
}
