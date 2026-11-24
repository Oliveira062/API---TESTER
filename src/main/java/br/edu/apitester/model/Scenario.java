package br.edu.apitester.model;

import java.util.List;

public record Scenario(String name, List<String> tags, List<Step> steps) {

    public Scenario {
        tags = tags == null ? List.of() : tags;
        steps = steps == null ? List.of() : steps;
    }

    public boolean hasAnyTag(List<String> filter) {
        if (filter == null || filter.isEmpty()) {
            return true;
        }
        return tags.stream().anyMatch(filter::contains);
    }
}
