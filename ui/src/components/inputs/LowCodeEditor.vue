<template>
    <div ref="vueFlow" class="vueflow">
        <slot name="top-bar" />
        <Topology
            :id="vueflowId"
            :is-horizontal="isHorizontal"
            :is-read-only="isReadOnly"
            :is-allowed-edit="isAllowedEdit"
            :source="source"
            :toggle-orientation-button="toggleOrientationButton"
            :flow-graph="playgroundStore.enabled ? (executionsStore.flowGraph ?? props.flowGraph) : props.flowGraph"
            :flow-id="flowId"
            :namespace="namespace"
            :expanded-subflows="props.expandedSubflows"
            :icons="pluginsStore.icons"
            :execution="executionsStore.execution"
            :subflows-executions="executionsStore.subflowsExecutions"
            :playground-enabled="playgroundStore.enabled"
            :playground-ready-to-start="playgroundStore.readyToStart"
            @toggle-orientation="toggleOrientation"
            @edit="onEditTask"
            @delete="onDelete"
            @open-link="openFlow"
            @show-logs="showLogs"
            @show-description="showDescription"
            @show-condition="showCondition"
            @on-add-flowable-error="onAddFlowableError"
            @add-task="onCreateNewTask"
            @swapped-task="onSwappedTask"
            @message="message"
            @expand-subflow="expandSubflow"
            @run-task="playgroundStore.runUntilTask($event.task.id)"
        />
    </div>
</template>

<script lang="ts" setup>
    // Core
    import {getCurrentInstance, nextTick, onMounted, ref, inject, watch} from "vue";

    import {useI18n} from "vue-i18n";
    import {useStorage} from "@vueuse/core";
    import {useRouter} from "vue-router";
    import {useVueFlow} from "@vue-flow/core";

    // Topology
    import {Topology} from "@kestra-io/ui-libs";

    // Utils
    import {SECTIONS} from "@kestra-io/ui-libs";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";

    const router = useRouter();

    const vueflowId = ref(Math.random().toString());
    const {fitView} = useVueFlow(vueflowId.value);

    import {TOPOLOGY_CLICK_INJECTION_KEY} from "../code/injectionKeys";
    import {useCoreStore} from "../../stores/core";
    import {usePluginsStore} from "../../stores/plugins";
    import {useExecutionsStore} from "../../stores/executions";
    import {usePlaygroundStore} from "../../stores/playground";
    const topologyClick = inject(TOPOLOGY_CLICK_INJECTION_KEY, ref());

    const executionsStore = useExecutionsStore();
    const playgroundStore = usePlaygroundStore();

    // props
    const props = withDefaults(
        defineProps<{
            flowGraph: Record<string, any>;
            flowId?: string;
            namespace?: string;
            execution?: Record<string, any>;
            isReadOnly?: boolean;
            source?: string;
            isAllowedEdit?: boolean;
            horizontalDefault?: boolean;
            toggleOrientationButton?: boolean;
            expandedSubflows?: string[];
        }>(),
        {
            flowId: undefined,
            namespace: undefined,
            execution: undefined,
            isReadOnly: false,
            source: "",
            isAllowedEdit: false,
            horizontalDefault: undefined,
            toggleOrientationButton: true,
            expandedSubflows: () => [],
        })

    const emit = defineEmits([
        "follow",
        "on-edit",
        "loading",
        "expand-subflow",
        "swapped-task",
    ]);

    // Vue instance variables
    const coreStore = useCoreStore();
    const toast = getCurrentInstance()?.appContext.config.globalProperties.$toast();
    const {t} = useI18n();

    const pluginsStore = usePluginsStore();

    // Components variables
    const isHorizontalLS = useStorage("topology-orientation", props.horizontalDefault);
    const isHorizontal = ref(props.horizontalDefault ?? (isHorizontalLS.value?.toString() === "true"));
    const vueFlow = ref<HTMLDivElement>();
    const timer = ref<ReturnType<typeof setTimeout>>();
    const taskEditData = ref();
    const taskEditDomElement = ref();
    const isShowLogsOpen = ref(false);
    const isDrawerOpen = ref(false);
    const isShowDescriptionOpen = ref(false);
    const isShowConditionOpen = ref(false);
    const selectedTask = ref();

    // Init components
    onMounted(() => {
        // Regenerate graph on window resize
        observeWidth();
        pluginsStore.fetchIcons()
    });

    watch(() => executionsStore.execution?.id, (id) => {
        if (id) {
            executionsStore.loadAugmentedGraph({
                id,
            });
        }
    }, {immediate: true});

    watch(
        () => isDrawerOpen.value,
        () => {
            if (!isDrawerOpen.value) {
                isShowDescriptionOpen.value = false;
                isShowLogsOpen.value = false;
                selectedTask.value = null;
            }
        },
    );

    // Event listeners & Watchers
    const observeWidth = () => {
        if(vueFlow.value){
            const resizeObserver = new ResizeObserver(function () {
                clearTimeout(timer.value);
                timer.value = setTimeout(() => {
                    nextTick(() => {
                        fitView();
                    });
                }, 50) as any;
            });
            resizeObserver.observe(vueFlow.value);
        }
    };

    // Source edit functions
    const onDelete = (event: any) => {
        const flowParsed = YAML_UTILS.parse(props.source);
        toast.confirm(
            t("delete task confirm", {taskId: event.id}),
            () => {
                const section = event.section ? event.section : SECTIONS.TASKS;
                if (
                    section === SECTIONS.TASKS &&
                    flowParsed.tasks.length === 1 &&
                    flowParsed.tasks.map((e: any) => e.id).includes(event.id)
                ) {
                    coreStore.message = {
                        variant: "error",
                        title: t("can not delete"),
                        message: t("can not have less than 1 task"),
                    };
                    return;
                }
                const updatedYmlSource = YAML_UTILS.deleteBlock({
                    source: props.source ?? "",
                    section,
                    key: event.id,
                })
                emit(
                    "on-edit",
                    updatedYmlSource,
                    true,
                );
            },
            () => {},
        );
    };

    const onCreateNewTask = (event: [string, "before" | "after"]) => {
        topologyClick.value = {
            action: "create",
            params: {
                section: SECTIONS.TASKS.toLowerCase() as any,
                position: event[1],
                id: event[0],
            }
        };
    };

    const onEditTask = (event: {
        task: Record<string, any>;
        section?: string;
    }) => {
        topologyClick.value = {
            action: "edit",
            params: {
                section: (event.section ?? SECTIONS.TASKS).toLowerCase() as any,
                id: event.task.id,
            }
        };
    };

    const onAddFlowableError = (event: any) => {
        taskEditData.value = {
            action: "add_flowable_error",
            taskId: event.task.id,
        };
        taskEditDomElement.value.$refs.taskEdit.click();
    };

    const fitViewOrientation = () => {
        if(vueFlow.value){
            const resizeObserver = new ResizeObserver(() => {
                clearTimeout(timer.value);
                nextTick(() => {
                    fitView();
                });
            });
            resizeObserver.observe(vueFlow.value);
        }
    };

    const toggleOrientation = () => {
        isHorizontal.value = !isHorizontal.value;
        isHorizontalLS.value = isHorizontal.value;
        fitViewOrientation();
    };

    const openFlow = (data: any) => {
        if (data.link.executionId) {
            window.open(
                router.resolve({
                    name: "executions/update",
                    params: {
                        namespace: data.link.namespace,
                        flowId: data.link.id,
                        tab: "topology",
                        id: data.link.executionId,
                    },
                }).href,
                "_blank",
            );
        } else {
            window.open(
                router.resolve({
                    name: "flows/update",
                    params: {
                        namespace: data.link.namespace,
                        id: data.link.id,
                        tab: "overview",
                    },
                }).href,
                "_blank",
            );
        }
    };

    const showLogs = (event: string) => {
        selectedTask.value = event;
        isShowLogsOpen.value = true;
        isDrawerOpen.value = true;
    };

    const showDescription = (event: string) => {
        selectedTask.value = event;
        isShowDescriptionOpen.value = true;
        isDrawerOpen.value = true;
    };

    const showCondition = (event: {task: string}) => {
        selectedTask.value = event.task;
        isShowConditionOpen.value = true;
        isDrawerOpen.value = true;
    };

    const onSwappedTask = (event: any) => {
        emit("swapped-task", event.swappedTasks);
        emit("on-edit", event.newSource, true);
    };

    const message = (event: any) => {
        coreStore.message = {
            variant: event.variant,
            title: t(event.title),
            message: t(event.message),
        };
    };

    const expandSubflow = (event: any) => {
        emit("expand-subflow", event);
    };
</script>

<style scoped lang="scss">
.vueflow {
    height: 100%;
    width: 100%;
    position: relative;
}
</style>
