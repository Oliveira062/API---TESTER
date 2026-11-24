package br.edu.apitester.model;

import java.util.List;

public record Security(
        List<String> protectedRoutes,
        List<Integer> acceptedStatus,
        HeaderCheck headers
) {

    public Security {
        protectedRoutes = protectedRoutes == null ? List.of() : protectedRoutes;
        acceptedStatus = acceptedStatus == null || acceptedStatus.isEmpty() ? List.of(401, 403) : acceptedStatus;
    }
}
