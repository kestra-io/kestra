package io.kestra.webserver.controllers.api;

import static io.micronaut.http.HttpRequest.GET;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.FlowForExecution;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.utils.TestsUtils;
import io.kestra.jdbc.JdbcTestUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@KestraTest
public class ExecutionControllerAllFlowsTest {

  @Inject
  private JdbcTestUtils jdbcTestUtils;

  @Inject
  protected LocalFlowRepositoryLoader repositoryLoader;

  @Inject
  @Client("/")
  ReactorHttpClient client;

  @SneakyThrows
  @BeforeEach
  protected void setup() {
    jdbcTestUtils.drop();
    jdbcTestUtils.migrate();

    TestsUtils.loads(repositoryLoader);
  }

  @SuppressWarnings("unchecked")
  @Test
  void getDistinctNamespaceExecutables() {
    List<String> result = client.toBlocking().retrieve(
        GET("/api/v1/executions/namespaces"),
        Argument.of(List.class, String.class)
    );

    assertThat(result.size(), greaterThanOrEqualTo(5));
  }

  @SuppressWarnings("unchecked")
  @Test
  void getFlowFromNamespace() {
    List<FlowForExecution> result = client.toBlocking().retrieve(
        GET("/api/v1/executions/namespaces/io.kestra.tests/flows"),
        Argument.of(List.class, FlowForExecution.class)
    );

    assertThat(result.size(), greaterThan(100));
  }

}
