package br.edu.apitester.report;

import java.util.Locale;

final class Text {

    private Text() {
    }

    static String xml(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder output = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '&' -> output.append("&amp;");
                case '<' -> output.append("&lt;");
                case '>' -> output.append("&gt;");
                case '"' -> output.append("&quot;");
                case '\'' -> output.append("&#39;");
                default -> {
                    if (c >= 0x20 || c == '\n' || c == '\r' || c == '\t') {
                        output.append(c);
                    }
                }
            }
        }
        return output.toString();
    }

    static String seconds(long milliseconds) {
        return String.format(Locale.ROOT, "%.3f", milliseconds / 1000.0);
    }
}
