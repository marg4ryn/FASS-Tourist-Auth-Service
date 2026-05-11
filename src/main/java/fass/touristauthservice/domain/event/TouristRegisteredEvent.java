package fass.touristauthservice.domain.event;

import java.util.UUID;

public record TouristRegisteredEvent(
        UUID touristId,
        String email,
        String name,
        String activationToken
) {}
