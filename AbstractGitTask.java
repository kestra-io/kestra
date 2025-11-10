package io.kestra.plugin.git;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.git.services.SshTransportConfigCallback;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.SuperBuilder;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;

import javax.net.ssl.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@Getter
public abstract class AbstractGitTask extends Task {
    private static final Pattern PEBBLE_TEMPLATE_PATTERN = Pattern.compile("^\\s*\\{\\{");

    // Replaces the boolean flag with a configuration key to allow reconfiguration when the PEM changes.
    // Possible values: "JVM" or "PEM:<sha256-of-file-bytes>"
    private static final AtomicReference<String> SSL_CONFIGURED_KEY = new AtomicReference<>(null);
    private static final Object SSL_CONFIG_LOCK = new Object();

    @Schema(title = "The URI to clone from")
    protected Property<String> url;

    @Schema(title = "The username or organization")
    protected Property<String> username;

    @Schema(title = "The password or Personal Access Token (PAT) – when you authenticate the task with a PAT, any flows or files pushed to Git from Kestra will be pushed from the user associated with that PAT. This way, you don't need to configure the commit author (the `authorName` and `authorEmail` properties).")
    protected Property<String> password;

    @Schema(
        title = "PEM-format private key content that is paired with a public key registered on Git",
        description = "To generate an ECDSA PEM format key from OpenSSH, use the following command: `ssh-keygen -t ecdsa -b 256 -m PEM`. " +
            "You can then set this property with your private key content and put your public key on Git."
    )
    protected Property<String> privateKey;

    @Schema(title = "The passphrase for the `privateKey`")
    protected Property<String> passphrase;

    @Schema(
        title = "Optional path to a PEM-encoded CA certificate to trust (in addition to the JVM default truststore)",
        description = "Equivalent to `git config http.sslCAInfo <path>`. Use this for self-signed/internal CAs."
    )
    protected Property<String> trustedCaPemPath;

    @Schema(title = "The initial Git branch")
    public abstract Property<String> getBranch();

    @Schema(
        title = "Git configuration to apply to the repository",
        description = """
            Map of Git config keys and values, applied after clone
                few examples:
                - 'core.fileMode': false -> ignore file permission changes
                - 'core.autocrlf': false -> prevent line ending conversion
            """
    )
    protected Property<Map<String, Object>> gitConfig;

    /**
     * Configure a secure SSLContext based on either:
     * - the JVM default truststore ("JVM" key), or
     * - a composite trust manager (PEM trust + JVM trust) when a PEM path is provided ("PEM:<sha256>").
     * This method is idempotent and will reconfigure the global SSLContext if and only if the desired key changes.
     */
    protected void configureEnvironmentWithSsl(RunContext runContext) throws Exception {
        // Render potential PEM path
        String pemPath = trustedCaPemPath == null ? null :
            runContext.render(trustedCaPemPath).as(String.class).orElse(null);

        // Compute desired configuration key for this run
        String desiredKey = computeDesiredSslKey(pemPath);

        // Fast-path: already configured with this key
        if (desiredKey.equals(SSL_CONFIGURED_KEY.get())) {
            runContext.logger().debug("SSLContext already configured with key: {}", desiredKey);
            return;
        }

        synchronized (SSL_CONFIG_LOCK) {
            if (desiredKey.equals(SSL_CONFIGURED_KEY.get())) {
                return;
            }

            SSLContext sslContext = SSLContext.getInstance("TLS");

            if (pemPath != null && !pemPath.isBlank()) {
                // Build composite TrustManager: [custom-from-PEM, jvm-default]
                X509TrustManager customTm = buildTrustManagerFromPem(Path.of(pemPath));
                X509TrustManager jvmTm = buildJvmDefaultTrustManager();
                X509TrustManager composite = new CompositeX509TrustManager(List.of(customTm, jvmTm));
                sslContext.init(null, new TrustManager[]{composite}, new SecureRandom());
                runContext.logger().info("Configured SSLContext with PEM: {}", pemPath);
            } else {
                // JVM default only
                X509TrustManager jvmTm = buildJvmDefaultTrustManager();
                sslContext.init(null, new TrustManager[]{jvmTm}, new SecureRandom());
                runContext.logger().info("Configured SSLContext with JVM default truststore");
            }

            // Apply as global defaults for JGit/HttpClient
            SSLContext.setDefault(sslContext);
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            // Keep default HostnameVerifier (strict)

            SSL_CONFIGURED_KEY.set(desiredKey);
            runContext.logger().debug("SSL configured key now: {}", desiredKey);
        }
    }

    // Builds a key representing the desired SSL configuration.
    // "JVM" when no PEM is used, or "PEM:<sha256-of-bytes>" when a PEM file is provided.
    private static String computeDesiredSslKey(String pemPath) throws Exception {
        if (pemPath == null || pemPath.isBlank()) {
            return "JVM";
        }
        byte[] bytes = Files.readAllBytes(Path.of(pemPath));
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        return "PEM:" + Base64.getEncoder().encodeToString(digest);
    }

    private static X509TrustManager buildJvmDefaultTrustManager() throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init((KeyStore) null); // use default JVM truststore
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager) {
                return (X509TrustManager) tm;
            }
        }
        throw new IllegalStateException("No X509TrustManager found in JVM default TrustManagerFactory");
    }

    private static X509TrustManager buildTrustManagerFromPem(Path pemFile) throws Exception {
        byte[] bytes = Files.readAllBytes(pemFile);

        // Try to load one or multiple certs from the PEM
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Collection<X509Certificate> certs = new ArrayList<>();
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
            // Works for single PEM and also multiple certs concatenated in many JDKs
            var cert = (X509Certificate) cf.generateCertificate(bais);
            certs.add(cert);
            while (bais.available() > 0) {
                X509Certificate c = (X509Certificate) cf.generateCertificate(bais);
                if (c != null) certs.add(c);
            }
        } catch (Exception e) {
            // Fallback: try generateCertificates (PKCS#7 or multiple certs)
            try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
                for (var c : cf.generateCertificates(bais)) {
                    certs.add((X509Certificate) c);
                }
            }
        }

        if (certs.isEmpty()) {
            throw new IllegalArgumentException("No X.509 certificate found in PEM file: " + pemFile);
        }

        // Put certs in an in-memory KeyStore
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null);
        int i = 0;
        for (X509Certificate c : certs) {
            ks.setCertificateEntry("custom-ca-" + (i++), c);
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);

        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager) {
                return (X509TrustManager) tm;
            }
        }
        throw new IllegalStateException("No X509TrustManager found in custom TrustManagerFactory from PEM");
    }

    /**
     * Simple composite X509TrustManager that tries each delegate in order and succeeds if any trusts the chain.
     */
    private static final class CompositeX509TrustManager implements X509TrustManager {
        private final List<X509TrustManager> delegates;

        private CompositeX509TrustManager(List<X509TrustManager> delegates) {
            this.delegates = List.copyOf(delegates);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
            java.security.cert.CertificateException last = null;
            for (X509TrustManager tm : delegates) {
                try {
                    tm.checkClientTrusted(chain, authType);
                    return;
                } catch (java.security.cert.CertificateException e) {
                    last = e;
                }
            }
            throw (last != null) ? last : new java.security.cert.CertificateException("No trust manager accepted client chain");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
            java.security.cert.CertificateException last = null;
            for (X509TrustManager tm : delegates) {
                try {
                    tm.checkServerTrusted(chain, authType);
                    return;
                } catch (java.security.cert.CertificateException e) {
                    last = e;
                }
            }
            throw (last != null) ? last : new java.security.cert.CertificateException("No trust manager accepted server chain");
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            List<X509Certificate> all = new ArrayList<>();
            for (X509TrustManager tm : delegates) {
                for (X509Certificate c : tm.getAcceptedIssuers()) {
                    all.add(c);
                }
            }
            return all.toArray(new X509Certificate[0]);
        }
    }

    public <T extends TransportCommand<T, ?>> T authentified(T command, RunContext runContext) throws Exception {
        if (this.username != null && this.password != null) {
            command.setCredentialsProvider(new UsernamePasswordCredentialsProvider(
                runContext.render(this.username).as(String.class).orElseThrow(),
                runContext.render(this.password).as(String.class).orElseThrow()
            ));
        }

        if (this.privateKey != null) {
            command.setTransportConfigCallback(new SshTransportConfigCallback(
                runContext.render(this.privateKey).as(String.class).orElseThrow().getBytes(StandardCharsets.UTF_8),
                runContext.render(this.passphrase).as(String.class).orElse(null)
            ));
        }

        return command;
    }

    /**
     * Apply Git configuration settings to a repository
     */
    public void applyGitConfig(Repository repository, RunContext runContext) throws Exception {
        Map<String, Object> rGitConfig = runContext.render(this.gitConfig).asMap(String.class, Object.class);
        if (rGitConfig.isEmpty()) {
            return;
        }

        StoredConfig gitRepoConfig = repository.getConfig();

        for (Map.Entry<String, Object> entry : rGitConfig.entrySet()) {
            String key = entry.getKey();
            Object entryValue = entry.getValue();

            String[] parts = key.split("\\.");
            if (parts.length < 2) {
                // The name is actually the section and the key separated by a dot.
                // Example: "core.fileMode" -> section = "core", name = "fileMode"
                runContext.logger().warn("invalid git gitRepoConfig key format: {}. The name is actually the section and the key separated by a dot. " +
                    "Expected 'section.name' or 'section.subsection.name'", key);
                continue;
            }

            String section = parts[0].toLowerCase(Locale.ROOT);
            String name = parts[parts.length - 1];
            String subsection = (parts.length > 2)
                ? String.join(".", Arrays.copyOfRange(parts, 1, parts.length - 1))
                : null;

            if (entryValue == null || (entryValue instanceof String s && s.trim().isEmpty())) {
                gitRepoConfig.unset(section, subsection, name);
                runContext.logger().info("Unset git gitRepoConfig {}", key);
                continue;
            }

            switch (entryValue) {
                case Boolean b -> gitRepoConfig.setBoolean(section, subsection, name, b);

                case Number n -> {
                    long lValue = n.longValue();
                    if (n.doubleValue() == (double) lValue && lValue >= Integer.MIN_VALUE && lValue <= Integer.MAX_VALUE) {
                        gitRepoConfig.setInt(section, subsection, name, (int) lValue);
                    } else {
                        gitRepoConfig.setString(section, subsection, name, n.toString());
                    }
                }

                case Collection<?> col -> {
                    List<String> values = col.stream().map(String::valueOf).toList();
                    gitRepoConfig.setStringList(section, subsection, name, values);
                }

                case String s -> {
                    String trimmed = s.trim();
                    gitRepoConfig.setString(section, subsection, name, trimmed);
                }

                default -> gitRepoConfig.setString(section, subsection, name, entryValue.toString());
            }

            runContext.logger().info("Applied git config {} = {}", key, entryValue);
        }

        gitRepoConfig.save();
        runContext.logger().info("Applied {} git configuration settings", rGitConfig.size());
    }

    protected URI createIonDiff(RunContext runContext, Git git) throws IOException, GitAPIException {
        File diffFile = runContext.workingDir().createTempFile(".ion").toFile();

        try (BufferedWriter diffWriter = new BufferedWriter(new FileWriter(diffFile));
             DiffFormatter diffFormatter = new DiffFormatter(new ByteArrayOutputStream())) {

            diffFormatter.setRepository(git.getRepository());

            // we check if the HEAD is null to handle the initial commit, if it is null we use an empty tree iterator
            var diff = git.diff().setCached(true);
            if (git.getRepository().resolve(Constants.HEAD) == null) {
                diff.setOldTree(new EmptyTreeIterator());
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonFactory factory = mapper.getFactory();
            try (JsonGenerator generator = factory.createGenerator(diffWriter)) {
                for (DiffEntry de : diff.call()) {
                    EditList editList = diffFormatter.toFileHeader(de).toEditList();
                    int additions = 0, deletions = 0, changes = 0;

                    for (Edit edit : editList) {
                        int mods = edit.getLengthB() - edit.getLengthA();
                        if (mods > 0) additions += mods;
                        else if (mods < 0) deletions += -mods;
                        else changes += edit.getLengthB();
                    }

                    generator.writeStartObject();
                    generator.writeStringField("file", getPath(de));
                    generator.writeNumberField("additions", additions);
                    generator.writeNumberField("deletions", deletions);
                    generator.writeNumberField("changes", changes);
                    generator.writeEndObject();
                    generator.writeRaw('\n');
                }
            }
        }

        return runContext.storage().putFile(diffFile);
    }

    private static String getPath(DiffEntry diffEntry) {
        return diffEntry.getChangeType() == DiffEntry.ChangeType.DELETE
            ? diffEntry.getOldPath()
            : diffEntry.getNewPath();
    }

    protected String buildCommitUrl(String httpUrl, String branch, String commitId) {

        if (commitId == null || httpUrl == null) {
            return null;
        }

        // Clean URL (remove .git if present)
        httpUrl = httpUrl.replaceAll("\\.git$", "");

        String commitSubroute = httpUrl.contains("bitbucket.org") ? "commits" : "commit";
        String commitUrl = httpUrl + "/" + commitSubroute + "/" + commitId;

        if (httpUrl.contains("azure.com")) {
            commitUrl += "?refName=refs%2Fheads%2F" + branch;
        }

        return commitUrl;
    }

    @Getter
    @AllArgsConstructor
    protected static class DiffLine {
        private String file;
        private String key;
        private Kind kind;
        private Action action;

        public static DiffLine added(String file, String key, Kind kind) {
            return new DiffLine(file, key, kind, Action.ADDED);
        }

        public static DiffLine updatedGit(String file, String key, Kind kind) {
            return new DiffLine(file, key, kind, Action.UPDATED_GIT);
        }

        public static DiffLine updatedKestra(String file, String key, Kind kind) {
            return new DiffLine(file, key, kind, Action.UPDATED_KES);
        }

        public static DiffLine unchanged(String file, String key, Kind kind) {
            return new DiffLine(file, key, kind, Action.UNCHANGED);
        }

        public static DiffLine deletedGit(String file, String key, Kind kind) {
            return new DiffLine(file, key, kind, Action.DELETED_GIT);
        }

        public static DiffLine deletedKestra(String file, String key, Kind kind) {
            return new DiffLine(file, key, kind, Action.DELETED_KES);
        }

        @SneakyThrows
        public static URI writeIonFile(RunContext runContext, List<DiffLine> diffs) {
            byte[] ionContent = JacksonMapper.ofIon().writeValueAsBytes(diffs);
            try (ByteArrayInputStream input = new ByteArrayInputStream(ionContent)) {
                return runContext.storage().putFile(input, "diffs.ion");
            }
        }
    }

    protected enum Kind {
        FLOW,
        FILE,
        DASHBOARD
    }

    protected enum Action {
        ADDED,
        UPDATED_GIT,
        UPDATED_KES,
        UNCHANGED,
        DELETED_GIT,
        DELETED_KES
    }
}
