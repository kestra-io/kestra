import aspire.*;
import java.nio.file.Files;
import java.nio.file.Path;

void main(String[] args) throws Exception {
    var builder = DistributedApplication.CreateBuilder(args);

    var dataRoot = Path.of(builder.appHostDirectory(), ".data");
    var postgresDataPath = dataRoot.resolve("postgres");
    var storagePath = dataRoot.resolve("storage");
    var tempPath = dataRoot.resolve("tmp");
    var storageDir = storagePath.toAbsolutePath().toString().replace("\\", "/");
    var tempDir = tempPath.resolve("tmp").toAbsolutePath().toString().replace("\\", "/");
    var javaHome = System.getProperty("java.home");
    var gradleJavaHome = "-Dorg.gradle.java.home=" + javaHome;
    var kestraExecutable = "./build/executable/kestra-2.0.0-SNAPSHOT";
    var kestraConfigDir = Path.of(builder.appHostDirectory(), "build", "executable", "confs");

    Files.createDirectories(postgresDataPath);
    Files.createDirectories(storagePath);
    Files.createDirectories(tempPath.resolve("tmp"));
    Files.createDirectories(kestraConfigDir);
    var kestraConfiguration = """
        micronaut:
          server:
            port: ${KESTRA_HTTP_PORT}
        datasources:
          default:
            url: ${KESTRA_PG_URL}
            driverClassName: org.postgresql.Driver
            username: ${KESTRA_PG_USERNAME}
            password: ${KESTRA_PG_PASSWORD}
          postgres:
            url: ${KESTRA_PG_URL}
            driverClassName: org.postgresql.Driver
            username: ${KESTRA_PG_USERNAME}
            password: ${KESTRA_PG_PASSWORD}
        endpoints:
          all:
            port: ${KESTRA_MANAGEMENT_PORT}
        kestra:
          repository:
            type: postgres
          executor:
            thread-count: 1
          storage:
            type: local
            local:
              base-path: "%s"
          queue:
            type: postgres
          tasks:
            tmp-dir:
              path: %s
          url: http://localhost:${KESTRA_HTTP_PORT}/
        """.formatted(storageDir, tempDir);

    var postgresUser = builder.addParameterWithValue("postgres-user", "kestra");
    var postgresPassword = builder.addParameterWithValue("postgres-password", "k3str4", new AddParameterWithValueOptions().secret(true));

    var postgres = builder.addPostgres("postgres")
        .withUserName(postgresUser)
        .withPassword(postgresPassword)
        .withImageTag("18")
        .withDataBindMount(postgresDataPath.toString());

    var kestraDatabase = postgres.addDatabase("kestra-db", "kestra");
    var kestraBuild = builder.addExecutable("kestra-build", "./gradlew", builder.appHostDirectory(), new String[] { "writeExecutableJar", "--no-daemon" })
        .excludeFromManifest()
        .withEnvironment("JAVA_HOME", javaHome)
        .withEnvironment("GRADLE_OPTS", gradleJavaHome);

    var kestra = builder.addExecutable("kestra", "/bin/sh", builder.appHostDirectory(), new String[] { kestraExecutable, "server", "standalone", "--worker-thread", "1", "--no-indexer", "--no-tutorials" })
        .withOtlpExporter()
        .withEnvironment("JAVA_HOME", javaHome)
        .withEnvironment("JAVA_OPTS", "-Xms256m -Xmx768m")
        .withEnvironment("KESTRA_CONFIGURATION", kestraConfiguration)
        .withEnvironment("KESTRA_PG_URL", kestraDatabase.jdbcConnectionString())
        .withEnvironment("KESTRA_PG_USERNAME", postgres.userNameReference())
        .withEnvironment("KESTRA_PG_PASSWORD", postgresPassword)
        .withHttpEndpoint(new WithHttpEndpointOptions().name("http").env("KESTRA_HTTP_PORT"))
        .withHttpEndpoint(new WithHttpEndpointOptions().name("management").env("KESTRA_MANAGEMENT_PORT"))
        .withExternalHttpEndpoints()
        .withHttpHealthCheck(new WithHttpHealthCheckOptions().path("/health").endpointName("management"))
        .waitForCompletion(kestraBuild)
        .waitFor(kestraDatabase);

    builder.build().run();
}
