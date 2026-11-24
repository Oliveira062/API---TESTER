package br.edu.apitester.result;

public record Check(String description, boolean passed, String detail) {

    public static Check ok(String description) {
        return new Check(description, true, null);
    }

    public static Check fail(String description, String detail) {
        return new Check(description, false, detail);
    }
}
