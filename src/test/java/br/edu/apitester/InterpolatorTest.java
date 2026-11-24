package br.edu.apitester;

import br.edu.apitester.execution.Context;
import br.edu.apitester.execution.Interpolator;
import br.edu.apitester.execution.UndefinedVariableException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InterpolatorTest {

    private final Interpolator interpolator = new Interpolator(name -> name.equals("PASSWORD") ? "secret" : null);

    @Test
    void replacesVariableInsideText() {
        Context context = new Context();
        context.set("id", 42);
        assertEquals("/api/orders/42/items", interpolator.text("/api/orders/${id}/items", context));
    }

    @Test
    void keepsTheTypeWhenTheTextIsOnlyTheVariable() {
        Context context = new Context();
        context.set("id", 42);
        assertEquals(42, interpolator.interpolate("${id}", context));
    }

    @Test
    void walksThroughMapsAndLists() {
        Context context = new Context();
        context.set("name", "Ann");
        Object result = interpolator.interpolate(Map.of("customer", List.of("${name}", "fixed")), context);
        assertEquals(Map.of("customer", List.of("Ann", "fixed")), result);
    }

    @Test
    void readsEnvironmentVariables() {
        assertEquals("secret", interpolator.text("${env.PASSWORD}", new Context()));
    }

    @Test
    void generatesDifferentRandomValues() {
        Context context = new Context();
        assertNotEquals(interpolator.text("${random}", context), interpolator.text("${random}", context));
    }

    @Test
    void warnsWhenTheVariableDoesNotExist() {
        assertThrows(UndefinedVariableException.class, () -> interpolator.text("${token}", new Context()));
        assertThrows(UndefinedVariableException.class, () -> interpolator.text("${env.MISSING}", new Context()));
    }
}
