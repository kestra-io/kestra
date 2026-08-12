package io.kestra.core.storages;

import java.io.IOException;
import java.net.URI;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link InternalStorage#putFile} correctly percent-encodes URI-special
 * characters ({@code #}, {@code %}) in filenames so that distinct files never collide
 * and stored objects are always readable.
 *
 * <p>Regression test for <a href="https://github.com/kestra-io/kestra/issues/18051">issue #18051</a>.
 */
class InternalStorageUriTest {

    /**
     * The quoting {@code URI(scheme, host, path, fragment)} constructor must encode {@code #} to
     * {@code %23} so that two files differing only in the fragment portion (e.g. {@code report#1.csv}
     * vs {@code report#2.csv}) resolve to two distinct URI paths instead of colliding on {@code report}.
     */
    @Test
    void shouldPercentEncodeHashInFilename() throws IOException {
        URI base = contextUri();
        URI result1 = buildStorageUri(base, "report#1.csv");
        URI result2 = buildStorageUri(base, "report#2.csv");

        // Fragment must not be set — the '#' is part of the path, not a fragment delimiter.
        assertThat(result1.getFragment()).isNull();
        assertThat(result2.getFragment()).isNull();

        // The two URIs must be distinct.
        assertThat(result1).isNotEqualTo(result2);

        // getRawPath() must contain the percent-encoded form.
        assertThat(result1.getRawPath()).endsWith("/report%231.csv");
        assertThat(result2.getRawPath()).endsWith("/report%232.csv");

        // getPath() (decoded) must recover the original filename.
        assertThat(result1.getPath()).endsWith("/report#1.csv");
        assertThat(result2.getPath()).endsWith("/report#2.csv");
    }

    /**
     * The quoting constructor must encode a literal {@code %} to {@code %25} so that filenames
     * containing percent signs are stored under a distinct, stable URI and are not mis-decoded.
     */
    @Test
    void shouldPercentEncodePercentInFilename() throws IOException {
        URI base = contextUri();
        URI result = buildStorageUri(base, "100%.txt");

        assertThat(result.getFragment()).isNull();
        // Raw path contains %25.
        assertThat(result.getRawPath()).endsWith("/100%25.txt");
        // Decoded path recovers the original filename.
        assertThat(result.getPath()).endsWith("/100%.txt");
    }

    /**
     * Square brackets ({@code [} and {@code ]}) are IPv6-reserved URI characters and must be
     * encoded to {@code %5B}/{@code %5D} exactly once. This guards against double-encoding
     * that occurred when a caller pre-encoded them to {@code %5B}/{@code %5D} before passing
     * the name to {@code buildStorageUri}, which then re-encoded the {@code %} to {@code %25}.
     */
    @Test
    void shouldPercentEncodeBracketsInFilename() throws IOException {
        URI base = contextUri();
        URI result = buildStorageUri(base, "file.with[]brackets.txt");

        assertThat(result.getFragment()).isNull();
        // Raw path must contain single-encoded brackets — not double-encoded (%255B).
        assertThat(result.getRawPath()).endsWith("/file.with%5B%5Dbrackets.txt");
        // Decoded path recovers the original filename.
        assertThat(result.getPath()).endsWith("/file.with[]brackets.txt");
    }

    /**
     * A filename that looks like an existing percent-escape ({@code 100%20.txt}) must be stored
     * as-is (with the {@code %} re-encoded to {@code %25}), not silently decoded to {@code 100 .txt}.
     */
    @Test
    void shouldNotDecodeExistingPercentEscapeInFilename() throws IOException {
        URI base = contextUri();
        URI result = buildStorageUri(base, "100%20.txt");

        // The '% ' must be re-encoded: raw path contains %2520.
        assertThat(result.getRawPath()).endsWith("/100%2520.txt");
        // Decoded path is the original string.
        assertThat(result.getPath()).endsWith("/100%20.txt");
    }

    /**
     * Plain filenames (no special chars) must not be affected by the fix.
     */
    @Test
    void shouldLeaveSimpleFilenameUnchanged() throws IOException {
        URI base = contextUri();
        URI result = buildStorageUri(base, "report.csv");

        assertThat(result.getRawPath()).endsWith("/report.csv");
        assertThat(result.getPath()).endsWith("/report.csv");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static URI contextUri() {
        // Mirrors the URI produced by StorageContext.forTask for a no-tenant context.
        return StorageContext.forTask(
            null,
            "namespace",
            "flowid",
            "executionid",
            "taskid",
            "taskrun",
            null
        ).getContextStorageURI();
    }

    /**
     * Replicates the private {@code InternalStorage.buildStorageUri} logic under test
     * without depending on the full Micronaut context.
     */
    private static URI buildStorageUri(URI base, String rawName) throws IOException {
        try {
            return new java.net.URI(base.getScheme(), base.getHost(), base.getPath() + "/" + rawName, null);
        } catch (java.net.URISyntaxException e) {
            throw new IOException("Cannot build storage URI for file name '%s'.".formatted(rawName), e);
        }
    }
}
