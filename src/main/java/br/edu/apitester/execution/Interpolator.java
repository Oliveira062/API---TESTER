package br.edu.apitester.execution;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Interpolator {

    private static final Pattern VARIABLE = Pattern.compile("\\$\\{([^}]+)}");

    private final Function<String, String> environment;

    public Interpolator() {
        this(System::getenv);
    }

    public Interpolator(Function<String, String> environment) {
        this.environment = environment;
    }

    public Object interpolate(Object value, Context context) {
        if (value instanceof String text) {
            return interpolateText(text, context);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), interpolate(entry.getValue(), context));
            }
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            for (Object item : list) {
                result.add(interpolate(item, context));
            }
            return result;
        }
        return value;
    }

    public String text(String value, Context context) {
        Object result = interpolate(value, context);
        return result == null ? null : String.valueOf(result);
    }

    private Object interpolateText(String text, Context context) {
        Matcher whole = VARIABLE.matcher(text);
        if (whole.matches()) {
            return resolve(whole.group(1).trim(), context);
        }
        Matcher matcher = VARIABLE.matcher(text);
        StringBuilder output = new StringBuilder();
        while (matcher.find()) {
            Object value = resolve(matcher.group(1).trim(), context);
            matcher.appendReplacement(output, Matcher.quoteReplacement(String.valueOf(value)));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private Object resolve(String name, Context context) {
        if (name.equals("random")) {
            return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        }
        if (name.equals("uuid")) {
            return UUID.randomUUID().toString();
        }
        if (name.equals("now")) {
            return Instant.now().toString();
        }
        if (name.equals("timestamp")) {
            return System.currentTimeMillis();
        }
        if (name.startsWith("env.")) {
            String variable = name.substring(4);
            String value = environment.apply(variable);
            if (value == null) {
                throw new UndefinedVariableException(name);
            }
            return value;
        }
        if (!context.has(name)) {
            throw new UndefinedVariableException(name);
        }
        return context.get(name);
    }
}
