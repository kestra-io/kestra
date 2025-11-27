<template>
    <div ref="vueFlow" class="vueflow">
        <slot name="top-bar" />
        <Topology
            :id="vueflowId"
            :isHorizontal="isHorizontal"
            :isReadOnly="isReadOnly"
            :isAllowedEdit="isAllowedEdit"
            :source="source"
            :toggleOrientationButton="toggleOrientationButton"
            :flowGraph="playgroundStore.enabled ? (executionsStore.flowGraph ?? props.flowGraph) : props.flowGraph"
            :flowId="flowId"
            :namespace="namespace"
            :expandedSubflows="props.expandedSubflows"
            :icons="pluginsStore.icons"
            :execution="executionsStore.execution"
            :subflowsExecutions="executionsStore.subflowsExecutions"
            :playgroundEnabled="playgroundStore.enabled"
            :playgroundReadyToStart="playgroundStore.readyToStart"
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

        <Drawer v-if="isDrawerOpen && selectedTask" v-model="isDrawerOpen">
            <template #header>
                <code>{{ selectedTask.id }}</code>
            </template>
            <div v-if="isShowLogsOpen">
                <Collapse>
                    <el-form-item>
                        <SearchField
                            :router="false"
                            @search="onSearch"
                            class="me-2"
                        />
                    </el-form-item>
                    <el-form-item>
                        <LogLevelSelector
                            :value="logLevel"
                            @update:model-value="onLevelChange"
                        />
                    </el-form-item>
                </Collapse>
                <TaskRunDetails
                    v-for="taskRun in selectedTask.taskRuns"
                    :key="taskRun.id"
                    :targetExecutionId="selectedTask.execution?.id"
                    :taskRunId="taskRun.id"
                    :filter="logFilter"
                    :excludeMetas="[
                        'namespace',
                        'flowId',
                        'taskId',
                        'executionId',
                    ]"
                    :level="logLevel"
                    @follow="emit('follow', $event)"
                />
            </div>
            <div v-if="isShowDescriptionOpen">
                <Markdown
                    :source="selectedTask.description"
                />
            </div>
            <div v-if="isShowConditionOpen">
                <Editor
                    :readOnly="true"
                    :input="true"
                    :fullHeight="false"
                    :navbar="false"
                    :modelValue="selectedTask.runIf"
                    lang="yaml"
                    class="mt-3"
                />
            </div>
        </Drawer>
    </div>
</template>

<script setup lang="ts">
    import {nextTick, onMounted, ref, inject, watch} from "vue";

    import {useI18n} from "vue-i18n";
    import {useStorage} from "@vueuse/core";
    import {useRouter} from "vue-router";
    import {useVueFlow} from "@vue-flow/core";

    import SearchField from "../layout/SearchField.vue";
    import LogLevelSelector from "../logs/LogLevelSelector.vue";
    // @ts-expect-error no types for TaskRunDetails yet
    import TaskRunDetails from "../logs/TaskRunDetails.vue";
    import Collapse from "../layout/Collapse.vue";
    import Drawer from "../Drawer.vue";
    import Markdown from "../layout/Markdown.vue";
    import Editor from "./Editor.vue";

    import {Topology} from "@kestra-io/ui-libs";
    import {SECTIONS} from "@kestra-io/ui-libs";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";

    import {TOPOLOGY_CLICK_INJECTION_KEY} from "../no-code/injectionKeys";
    import {useCoreStore} from "../../stores/core";
    import {usePluginsStore} from "../../stores/plugins";
    import {useExecutionsStore} from "../../stores/executions";
    import {usePlaygroundStore} from "../../stores/playground";
    import {useToast} from "../../utils/toast";

    const router = useRouter();

    const vueflowId = ref(Math.random().toString());
    const {fitView} = useVueFlow(vueflowId.value);

    const topologyClick = inject(TOPOLOGY_CLICK_INJECTION_KEY, ref());

    const executionsStore = useExecutionsStore();
    const playgroundStore = usePlaygroundStore();

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

    const coreStore = useCoreStore();
    const toast = useToast();
    const {t} = useI18n();

    const pluginsStore = usePluginsStore();

    const isHorizontalLS = useStorage("topology-orientation", props.horizontalDefault);
    const isHorizontal = ref(props.horizontalDefault ?? (isHorizontalLS.value?.toString() === "true"));
    const vueFlow = ref<HTMLDivElement>();
    const timer = ref<ReturnType<typeof setTimeout>>();
    const taskEditData = ref();
    const taskEditDomElement = ref();
    const isShowLogsOpen = ref(false);
    const logFilter = ref("");
    const logLevel = ref(localStorage.getItem("defaultLogLevel") || "INFO");
    const isDrawerOpen = ref(false);
    const isShowDescriptionOpen = ref(false);
    const isShowConditionOpen = ref(false);
    const selectedTask = ref();

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

    const onDelete = (event: any) => {
        const flowParsed = YAML_UTILS.parse(props.source);
        toast.confirm(
            t("delete task confirm", {taskId: event.id}),
            async () => {
                const section = event.section ? event.section.toLowerCase() : SECTIONS.TASKS.toLowerCase();
                if (
                    section === SECTIONS.TASKS.toLowerCase() &&
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
            }
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

    const onSearch = (search: string) => {
        logFilter.value = search;
    };

    const onLevelChange = (level: string) => {
        logLevel.value = level;
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
