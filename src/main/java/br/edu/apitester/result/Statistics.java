package br.edu.apitester.result;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record Statistics(int total, int failures, long minMs, long avgMs, long p95Ms, long maxMs) {

    public static Statistics calculate(List<Long> durations, int failures) {
        if (durations.isEmpty()) {
            return new Statistics(0, failures, 0, 0, 0, 0);
        }
        List<Long> sorted = new ArrayList<>(durations);
        Collections.sort(sorted);
        long sum = 0;
        for (long d : sorted) {
            sum += d;
        }
        int p95Index = (int) Math.ceil(sorted.size() * 0.95) - 1;
        return new Statistics(sorted.size(), failures, sorted.getFirst(), sum / sorted.size(),
                sorted.get(Math.max(0, p95Index)), sorted.getLast());
    }
}
