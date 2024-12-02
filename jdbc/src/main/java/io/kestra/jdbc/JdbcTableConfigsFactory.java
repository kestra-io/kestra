package io.kestra.jdbc;

import io.kestra.core.models.Setting;
import io.kestra.core.models.dashboards.Dashboard;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.sla.SLAMonitor;
import io.kestra.core.models.templates.Template;
import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.kestra.core.runners.*;
import io.kestra.core.server.ServiceInstance;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Named;

@Factory
@Requires(missingProperty = "kestra.jdbc.tables")
public class JdbcTableConfigsFactory {

    @Bean
    @Named("queues")
    public InstantiableJdbcTableConfig queues() {
        return new InstantiableJdbcTableConfig("queues", null, "queues");
    }

    @Bean
    @Named("flows")
    public InstantiableJdbcTableConfig flows() {
        return new InstantiableJdbcTableConfig("flows", Flow.class, "flows");
    }

    @Bean
    @Named("executions")
    public InstantiableJdbcTableConfig executions() {
        return new InstantiableJdbcTableConfig("executions", Execution.class, "executions");
    }

    @Bean
    @Named("templates")
    public InstantiableJdbcTableConfig templates() {
        return new InstantiableJdbcTableConfig("templates", Template.class, "templates");
    }

    @Bean
    @Named("triggers")
    public InstantiableJdbcTableConfig triggers() {
        return new InstantiableJdbcTableConfig("triggers", Trigger.class, "triggers");
    }

    @Bean
    @Named("logs")
    public InstantiableJdbcTableConfig logs() {
        return new InstantiableJdbcTableConfig("logs", LogEntry.class, "logs");
    }

    @Bean
    @Named("metrics")
    public InstantiableJdbcTableConfig metrics() {
        return new InstantiableJdbcTableConfig("metrics", MetricEntry.class, "metrics");
    }

    @Bean
    @Named("multipleconditions")
    public InstantiableJdbcTableConfig multipleConditions() {
        return new InstantiableJdbcTableConfig("multipleconditions", MultipleConditionWindow.class, "multipleconditions");
    }

    @Bean
    @Named("subflow-executions")
    public InstantiableJdbcTableConfig subflowExecutions() {
        return new InstantiableJdbcTableConfig("subflow-executions", SubflowExecution.class, "subflow_executions");
    }

    @Bean
    @Named("executorstate")
    public InstantiableJdbcTableConfig executorState() {
        return new InstantiableJdbcTableConfig("executorstate", ExecutorState.class, "executorstate");
    }

    @Bean
    @Named("executordelayed")
    public InstantiableJdbcTableConfig executorDelayed() {
        return new InstantiableJdbcTableConfig("executordelayed", ExecutionDelay.class, "executordelayed");
    }

    @Bean
    @Named("settings")
    public InstantiableJdbcTableConfig settings() {
        return new InstantiableJdbcTableConfig("settings", Setting.class, "settings");
    }

    @Bean
    @Named("flowtopologies")
    public InstantiableJdbcTableConfig flowTopologies() {
        return new InstantiableJdbcTableConfig("flowtopologies", FlowTopology.class, "flow_topologies");
    }

    @Bean
    @Named("serviceinstance")
    public InstantiableJdbcTableConfig serviceInstance() {
        return new InstantiableJdbcTableConfig("serviceinstance", ServiceInstance.class, "service_instance");
    }

    @Bean
    @Named("workerjobrunning")
    public InstantiableJdbcTableConfig workerJobRunning() {
        return new InstantiableJdbcTableConfig("workerjobrunning", WorkerJobRunning.class, "worker_job_running");
    }

    @Bean
    @Named("executionqueued")
    public InstantiableJdbcTableConfig executionQueued() {
        return new InstantiableJdbcTableConfig("executionqueued", ExecutionQueued.class, "execution_queued");
    }

    @Bean
    @Named("slamonitor")
    public InstantiableJdbcTableConfig slaMonitor() {
        return new InstantiableJdbcTableConfig("slamonitor", SLAMonitor.class, "sla_monitor");
    }

    @Bean
    @Named("dashboards")
    public InstantiableJdbcTableConfig dashboards() {
        return new InstantiableJdbcTableConfig("dashboards", Dashboard.class, "dashboards");
    }

    public static class InstantiableJdbcTableConfig extends JdbcTableConfig {
        public InstantiableJdbcTableConfig(String name, @Nullable Class<?> cls, String table) {
            super(name, cls, table);
        }
    }
}
