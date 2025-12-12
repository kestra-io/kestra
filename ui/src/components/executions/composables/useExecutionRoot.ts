import {ref, computed, onMounted, onUnmounted, watch} from "vue";
import {useRoute, useRouter} from "vue-router";
import {useI18n} from "vue-i18n";

import {useFlowStore} from "../../../stores/flow";
import {useExecutionsStore} from "../../../stores/executions";

//@ts-expect-error no declaration file
import Logs from "../Logs.vue";
//@ts-expect-error no declaration file
import Gantt from "../Gantt.vue";
//@ts-expect-error no declaration file
import Topology from "../Topology.vue";
import Overview from "../overview/Overview.vue";
import DemoAuditLogs from "../../demo/AuditLogs.vue";
import ExecutionMetric from "../ExecutionMetric.vue";
import ExecutionOutput from "../outputs/Wrapper.vue";
import Dependencies from "../../dependencies/Dependencies.vue";

export function useExecutionRoot() {
    const {t} = useI18n();
    const route = useRoute();
    const router = useRouter();

    const flowStore = useFlowStore();
    const executionsStore = useExecutionsStore();

    const dependenciesCount = ref<number>();
    const previousExecutionId = ref<string>();

    const routeInfo = computed(() => {
        const ns = route.params.namespace as string;
        const flowId = route.params.flowId as string;

        if (!ns || !flowId) {
            return {title: ""};
        }

        return {
            title: route.params.id as string,
            breadcrumb: [
                {
                    label: t("executions"),
                    link: {
                        name: "executions/list"
                    }
                },
                {
                    label: `${ns}.${flowId}`,
                    link: {
                        name: "flows/update",
                        params: {
                            namespace: ns,
                            id: flowId
                        }
                    }
                }
            ]
        };
    });

    const routeName = computed(() => route.params && route.params.id ? "executions/update" : "");

    const ready = computed(() => {
        return executionsStore.execution !== undefined;
    });

    const follow = () => {
        previousExecutionId.value = route.params.id as string;
        executionsStore.followExecution(route.params as any, t);
    };

    const getBaseTabs = () => {
        return [
            {
                name: "overview",
                component: Overview,
                title: t("overview"),
                maximized: true,
                noOverflow: true
            },
            {
                name: "gantt",
                component: Gantt,
                title: t("gantt")
            },
            {
                name: "logs",
                component: Logs,
                title: t("logs")
            },
            {
                name: "topology",
                component: Topology,
                title: t("topology")
            },
            {
                name: "outputs",
                component: ExecutionOutput,
                title: t("outputs"),
                maximized: true
            },
            {
                name: "metrics",
                component: ExecutionMetric,
                title: t("metrics")
            },
            {
                name: "dependencies",
                component: Dependencies,
                title: t("dependencies"),
                count: dependenciesCount.value,
                maximized: true,
                props: {
                    isReadOnly: true,
                },
            },
            {
                name: "auditlogs",
                component: DemoAuditLogs,
                title: t("auditlogs"),
                maximized: true,
                locked: true
            }
        ];
    };

    const tabs = computed(() => getBaseTabs());

    const setupLifecycle = () => {
        onMounted(async () => {
            if (!route.params.tab) {
                const tab = localStorage.getItem("executeDefaultTab") || undefined;
                router.replace({name: "executions/update", params: {...route.params, tab}});
            }

            follow();
            window.addEventListener("popstate", follow);

            dependenciesCount.value = (await flowStore.loadDependencies({namespace: route.params.namespace as string, id: route.params.flowId as string, subtype: "FLOW"}, true)).count;
            previousExecutionId.value = route.params.id as string;
        });

        watch(route, () => {
            executionsStore.taskRun = undefined;
            if (previousExecutionId.value !== route.params.id) {
                flowStore.flow = undefined;
                flowStore.flowGraph = undefined;
                follow();
            }
        });

        onUnmounted(() => {
            executionsStore.closeSSE();
            window.removeEventListener("popstate", follow);
            executionsStore.execution = undefined;
            flowStore.flow = undefined;
            flowStore.flowGraph = undefined;
        });
    };

    return {
        tabs,
        ready,
        routeInfo,
        routeName,
        dependenciesCount,
        previousExecutionId,
        follow,
        getBaseTabs,
        setupLifecycle
    };
}
