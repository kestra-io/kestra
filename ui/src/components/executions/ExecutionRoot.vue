<template>
  <template v-if="ready">
    <ExecutionRootTopBar :routeInfo="routeInfo" />
    <Tabs
      :routeName="$route.params && $route.params.id ? 'executions/update' : ''"
      @follow="follow"
      :tabs="tabs"
    />
  </template>
  <div v-else class="full-space" v-loading="true">
    {{ executionsStore.execution?.id }}
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useCoreStore } from "../../stores/core";
import { useExecutionsStore } from "../../stores/executions";
import { useFlowStore } from "../../stores/flow";
import { useAuthStore } from "override/stores/auth";

import Gantt from "./Gantt.vue";
import Overview from "./Overview.vue";
import Logs from "./Logs.vue";
import Topology from "./Topology.vue";
import ExecutionOutput from "./outputs/Wrapper.vue";
import ExecutionMetric from "./ExecutionMetric.vue";
import ExecutionRootTopBar from "./ExecutionRootTopBar.vue";
import DemoAuditLogs from "../demo/AuditLogs.vue";
import Dependencies from "../dependencies/Dependencies.vue";

import Tabs from "../../components/Tabs.vue";

import permission from "../../models/permission";
import action from "../../models/action";

const route = useRoute();
const router = useRouter();

const coreStore = useCoreStore();
const executionsStore = useExecutionsStore();
const flowStore = useFlowStore();
const authStore = useAuthStore();

const previousExecutionId = ref<string | undefined>(undefined);
const dependenciesCount = ref<number | undefined>(undefined);

const follow = () => {
  previousExecutionId.value = route.params.id as string;
  executionsStore.followExecution(route.params as any, coreStore.$t);
};

const getTabs = () => [
  {
    name: undefined,
    component: Overview,
    title: coreStore.$t("overview"),
  },
  {
    name: "gantt",
    component: Gantt,
    title: coreStore.$t("gantt"),
  },
  {
    name: "logs",
    component: Logs,
    title: coreStore.$t("logs"),
  },
  {
    name: "topology",
    component: Topology,
    title: coreStore.$t("topology"),
  },
  {
    name: "outputs",
    component: ExecutionOutput,
    title: coreStore.$t("outputs"),
    maximized: true,
  },
  {
    name: "metrics",
    component: ExecutionMetric,
    title: coreStore.$t("metrics"),
  },
  {
    name: "dependencies",
    component: Dependencies,
    title: coreStore.$t("dependencies"),
    count: dependenciesCount.value,
    maximized: true,
    props: {
      isReadOnly: true,
    },
  },
  {
    name: "auditlogs",
    component: DemoAuditLogs,
    title: coreStore.$t("auditlogs"),
    maximized: true,
    locked: true,
  },
];

const tabs = computed(() => getTabs());

const routeInfo = computed(() => {
  const ns = route.params.namespace as string;
  const flowId = route.params.flowId as string;

  if (!ns || !flowId) {
    return {};
  }

  return {
    title: route.params.id,
    breadcrumb: [
      {
        label: coreStore.$t("flows"),
        link: {
          name: "flows/list",
          query: {
            namespace: ns,
          },
        },
      },
      {
        label: `${ns}.${flowId}`,
        link: {
          name: "flows/update",
          params: {
            namespace: ns,
            id: flowId,
          },
        },
      },
      {
        label: coreStore.$t("executions"),
        link: {
          name: "flows/update",
          params: {
            namespace: ns,
            id: flowId,
            tab: "executions",
          },
        },
      },
    ],
  };
});

const isAllowedTrigger = computed(() => {
  return (
    executionsStore.execution &&
    authStore.user?.isAllowed(
      permission.EXECUTION,
      action.CREATE,
      executionsStore.execution.namespace
    )
  );
});

const isAllowedEdit = computed(() => {
  return (
    executionsStore.execution &&
    authStore.user?.isAllowed(
      permission.FLOW,
      action.UPDATE,
      executionsStore.execution.namespace
    )
  );
});

const canDelete = computed(() => {
  return (
    executionsStore.execution &&
    authStore.user?.isAllowed(
      permission.EXECUTION,
      action.DELETE,
      executionsStore.execution.namespace
    )
  );
});

const ready = computed(() => executionsStore.execution !== undefined);

if (!route.params.tab) {
  const tab = localStorage.getItem("executeDefaultTab") || undefined;
  router.replace({ name: "executions/update", params: { ...route.params, tab } });
}

follow();

window.addEventListener("popstate", follow);

flowStore
  .loadDependencies({
    namespace: route.params.namespace as string,
    id: route.params.flowId as string,
  })
  .then((res: { count: number }) => {
    dependenciesCount.value = res.count;
  });

onMounted(() => {
  previousExecutionId.value = route.params.id as string;
});

watch(
  () => route.fullPath,
  () => {
    executionsStore.taskRun = undefined;

    if (previousExecutionId.value !== route.params.id) {
      flowStore.flow = undefined;
      flowStore.flowGraph = undefined;
      follow();
    }
  }
);

onBeforeUnmount(() => {
  executionsStore.closeSSE();
  window.removeEventListener("popstate", follow);
  executionsStore.execution = undefined;
  flowStore.flow = undefined;
  flowStore.flowGraph = undefined;
});
</script>

<style scoped lang="scss">
.full-space {
  flex: 1 1 auto;
}
</style>
