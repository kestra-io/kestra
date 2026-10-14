package io.kestra.webserver.services;

public final class BasicAuthServiceTestUtils {
    private BasicAuthServiceTestUtils() {
    }

    /**
     * Runs {@code test} as if no credentials were set in the configuration file, keeping the configured realm and open URLs,
     * so the Basic Authentication endpoint can be exercised from a test context whose configuration does set them.
     */
    public static void withoutConfiguredCredentials(BasicAuthService basicAuthService, Runnable test) {
        BasicAuthService.BasicAuthConfiguration configured = basicAuthService.basicAuthConfiguration;
        basicAuthService.basicAuthConfiguration = new BasicAuthService.BasicAuthConfiguration(null, null, configured.getRealm(), configured.getOpenUrls());
        try {
            test.run();
        } finally {
            basicAuthService.basicAuthConfiguration = configured;
        }
    }
}
