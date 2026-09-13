package io.kestra.fethr.auth;

/**
 * Published once the first owner has been provisioned in the identity provider.
 *
 * <p>
 * Onboarding records its own completion by reacting to this, rather than a controller orchestrating
 * both. That listener arrives with the onboarding screens; until then the event is published and
 * nothing consumes it, which is harmless and keeps the publishing side whole.
 */
public record OnboardingCompletedEvent(String ownerEmail, String keycloakUserId) {
}
