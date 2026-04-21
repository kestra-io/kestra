import aspire.*;
import java.nio.file.Files;
import java.nio.file.Path;

void main(String[] args) throws Exception {
    var builder = DistributedApplication.CreateBuilder(args);

    var dataRoot = Path.of(builder.appHostDirectory(), ".data");
    var postgresDataPath = dataRoot.resolve("postgres");
    var storagePath = dataRoot.resolve("storage");
    var tempPath = dataRoot.resolve("tmp");

    Files.createDirectories(postgresDataPath);
    Files.createDirectories(storagePath);
    Files.createDirectories(tempPath.resolve("tmp"));

    var postgresUser = builder.addParameterWithValue("postgres-user", "kestra");
    var postgresPassword = builder.addParameterWithValue("postgres-password", "k3str4", new AddParameterWithValueOptions().secret(true));

    var postgres = builder.addPostgres("postgres")
        .withUserName(postgresUser)
        .withPassword(postgresPassword)
        .withImageTag("18")
        .withDataBindMount(postgresDataPath.toString());

    var kestraDatabase = postgres.addDatabase("kestra-db", "kestra");

    var kestra = builder.addContainer("kestra", "kestra/kestra:latest")
        .withArgs(new String[] { "server", "standalone", "--worker-thread", "1", "--no-indexer", "--no-tutorials" })
        .withImagePullPolicy(ImagePullPolicy.ALWAYS)
        .withOtlpExporter()
        .withBindMount(storagePath.toString(), "/app/storage")
        .withBindMount(tempPath.toString(), "/tmp/kestra-wd")
        .withEnvironment("JAVA_OPTS", "-Xms256m -Xmx768m")
        .withEnvironment("KESTRA_CONFIGURATION", """
            datasources:
              postgres:
                url: jdbc:postgresql://postgres:5432/kestra
                driverClassName: org.postgresql.Driver
                username: kestra
                password: k3str4
            kestra:
              repository:
                type: postgres
              executor:
                thread-count: 1
              storage:
                type: local
                local:
                  base-path: "/app/storage"
              queue:
                type: postgres
              tasks:
                tmp-dir:
                  path: /tmp/kestra-wd/tmp
              url: http://localhost:8080/
            """)
        .withHttpEndpoint(new WithHttpEndpointOptions().name("http").targetPort(8080.0))
        .withHttpEndpoint(new WithHttpEndpointOptions().name("management").targetPort(8081.0))
        .withExternalHttpEndpoints()
        .withHttpHealthCheck(new WithHttpHealthCheckOptions().path("/health").endpointName("management"))
        .waitFor(kestraDatabase);

    var dockerSocket = Path.of("/var/run/docker.sock");
    if (Files.exists(dockerSocket)) {
        kestra.withBindMount(dockerSocket.toString(), "/var/run/docker.sock");
    }

    builder.build().run();
}
