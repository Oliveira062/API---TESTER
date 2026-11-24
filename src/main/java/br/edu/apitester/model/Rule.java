package br.edu.apitester.model;

public record Rule(
        String path,
        Object equals,
        Object notEquals,
        Boolean exists,
        Object contains,
        Double greaterThan,
        Double lessThan,
        Integer length,
        String type,
        String regex
) {
}
