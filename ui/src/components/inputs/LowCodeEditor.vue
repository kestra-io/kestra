<template>
    <div ref="vueFlow" class="vueflow">
        <slot name="top-bar" />
        <Topology
            :id="vueflowId"
            :is-horizontal="isHorizontal"
            :is-read-only="isReadOnly"
            :is-allowed-edit="isAllowedEdit"
            :source="source"
            :toggle-orientation-button="['topology'].includes(viewType)"
            :flow-graph="props.flowGraph"
            :flow-id="flowId"
            :namespace="namespace"
            :expanded-subflows="props.expandedSubflows"
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
            :icons="icons"
        />

        <!-- Drawer to create/add task -->
        <task-edit
            v-if="source"
            component="div"
            is-hidden
            class="node-action"
            :section="taskEditData?.section"
            :task="taskObject"
            :flow-id="flowId"
            size="small"
            :namespace="namespace"
            :revision="execution ? execution.flowRevision : undefined"
            @update:task="confirmEdit"
            @close="closeEdit()"
            :flow-source="source"
            ref="taskEditDomElement"
        />

        <!--    Drawer to task informations (logs, description, ..)   -->
        <!--    Assuming selectedTask is always the id and the required data for the opened drawer    -->
        <drawer v-if="isDrawerOpen && selectedTask" v-model="isDrawerOpen">
            <template #header>
                <code>{{ selectedTask.id }}</code>
            </template>
            <div v-if="isShowLogsOpen">
                <collapse>
                    <el-form-item>
                        <search-field
                            :router="false"
                            @search="onSearch"
                            class="me-2"
                        />
                    </el-form-item>
                    <el-form-item>
                        <log-level-selector
                            :value="logLevel"
                            @update:model-value="onLevelChange"
                        />
                    </el-form-item>
                </collapse>
                <task-run-details
                    v-for="taskRun in selectedTask.taskRuns"
                    :key="taskRun.id"
                    :target-execution-id="selectedTask.execution?.id"
                    :task-run-id="taskRun.id"
                    :filter="logFilter"
                    :exclude-metas="[
                        'namespace',
                        'flowId',
                        'taskId',
                        'executionId',
                    ]"
                    :level="logLevel"
                    @follow="forwardEvent('follow', $event)"
                />
            </div>
            <div v-if="isShowDescriptionOpen">
                <markdown
                    class="markdown-tooltip"
                    :source="selectedTask.description"
                />
            </div>
            <div v-if="isShowConditionOpen">
                <editor
                    :read-only="true"
                    :input="true"
                    :full-height="false"
                    :navbar="false"
                    :minimap="false"
                    :model-value="selectedTask.runIf"
                    lang="yaml"
                    class="mt-3"
                />
            </div>
        </drawer>
    </div>
</template>

<script setup>
// Core
    import {getCurrentInstance, nextTick, onMounted, ref, watch} from "vue";
    import {useStore} from "vuex";
    import {useVueFlow} from "@vue-flow/core";
    import {useRouter} from "vue-router";

    import TaskEdit from "../flows/TaskEdit.vue";
    import SearchField from "../layout/SearchField.vue";
    import LogLevelSelector from "../logs/LogLevelSelector.vue";
    import TaskRunDetails from "../logs/TaskRunDetails.vue";
    import Collapse from "../layout/Collapse.vue";
    import Drawer from "../Drawer.vue";

    // Topology
    import {Topology} from "@kestra-io/ui-libs";

    // Utils
    import {YamlUtils} from "@kestra-io/ui-libs";
    import {SECTIONS} from "../../utils/constants";
    import Markdown from "../layout/Markdown.vue";
    import Editor from "./Editor.vue";

    const router = useRouter();

    const vueflowId = ref(Math.random().toString());
    // Vue flow methods to interact with Graph
    const {fitView} = useVueFlow({id: vueflowId.value});

    // props
    const props = defineProps({
        flowGraph: {
            type: Object,
            required: true,
        },
        flowId: {
            type: String,
            required: false,
            default: undefined,
        },
        namespace: {
            type: String,
            required: false,
            default: undefined,
        },
        execution: {
            type: Object,
            default: undefined,
        },
        isReadOnly: {
            type: Boolean,
            default: false,
        },
        source: {
            type: String,
            default: undefined,
        },
        isAllowedEdit: {
            type: Boolean,
            default: false,
        },
        viewType: {
            type: String,
            default: undefined,
        },
        expandedSubflows: {
            type: Array,
            default: () => [],
        },
    });

    const emit = defineEmits([
        "follow",
        "on-edit",
        "loading",
        "expand-subflow",
        "swapped-task",
        "openNoCode",
    ]);

    // Vue instance variables
    const store = useStore();
    const toast = getCurrentInstance().appContext.config.globalProperties.$toast();
    const t = getCurrentInstance().appContext.config.globalProperties.$t;

    // Init variables functions
    const isHorizontalDefault = () => {
        return props.viewType === "source-topology"
            ? false
            : props.viewType?.indexOf("blueprint") !== -1
                ? true
                : localStorage.getItem("topology-orientation") === "1";
    };

    // Components variables
    const isHorizontal = ref(isHorizontalDefault());
    const vueFlow = ref(null);
    const timer = ref(null);
    const icons = ref(store.getters["plugin/getIcons"]);
    const taskObject = ref(null);
    const taskEditData = ref(null);
    const taskEditDomElement = ref(null);
    const isShowLogsOpen = ref(false);
    const logFilter = ref("");
    const logLevel = ref(localStorage.getItem("defaultLogLevel") || "INFO");
    const isDrawerOpen = ref(false);
    const isShowDescriptionOpen = ref(false);
    const isShowConditionOpen = ref(false);
    const selectedTask = ref(null);

    // Init components
    onMounted(() => {
        // Regenerate graph on window resize
        observeWidth();
        store.dispatch("plugin/icons").then(() => {
            icons.value = store.getters["plugin/getIcons"];
        });
    });

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

    watch(
        () => props.viewType,
        () => {
            isHorizontal.value =
                props.viewType === "source-topology"
                    ? false
                    : props.viewType?.indexOf("blueprint") !== -1
                        ? true
                        : localStorage.getItem("topology-orientation") === "1";
        },
    );

    // Event listeners & Watchers
    const observeWidth = () => {
        const resizeObserver = new ResizeObserver(function () {
            clearTimeout(timer.value);
            timer.value = setTimeout(() => {
                nextTick(() => {
                    fitView();
                });
            }, 50);
        });
        resizeObserver.observe(vueFlow.value);
    };

    const forwardEvent = (type, event) => {
        emit(type, event);
    };
    // Source edit functions

    const onDelete = (event) => {
        const flowParsed = YamlUtils.parse(props.source);
        toast.confirm(
            t("delete task confirm", {taskId: event.id}),
            () => {
                const section = event.section ? event.section : SECTIONS.TASKS;
                if (
                    section === SECTIONS.TASKS &&
                    flowParsed.tasks.length === 1 &&
                    flowParsed.tasks.map((e) => e.id).includes(event.id)
                ) {
                    store.dispatch("core/showMessage", {
                        variant: "error",
                        title: t("can not delete"),
                        message: t("can not have less than 1 task"),
                    });
                    return;
                }
                emit(
                    "on-edit",
                    YamlUtils.deleteTask(props.source, event.id, section),
                    true,
                );
            },
            () => {},
        );
    };

    const onCreateNewTask = (details) => {
        emit("openNoCode", {
            section: SECTIONS.TASKS.toLowerCase(),
            identifier: "new",
            target: details[0],
            position: details[1],
        });
    };

    const onEditTask = (event) => {
        emit("openNoCode", {
            section: event.section
                ? event.section.toLowerCase()
                : SECTIONS.TASKS.toLowerCase(),
            identifier: event.task.id,
            type: event.task.type,
        });
    };

    const onAddFlowableError = (event) => {
        taskEditData.value = {
            action: "add_flowable_error",
            taskId: event.task.id,
        };
        taskEditDomElement.value.$refs.taskEdit.click();
    };

    const confirmEdit = (event) => {
        const source = props.source;
        const task = YamlUtils.extractTask(props.source, YamlUtils.parse(event).id);
        if (
            task === undefined ||
            (task && YamlUtils.parse(event).id === taskEditData.value.oldTaskId)
        ) {
            switch (taskEditData.value.action) {
            case "create_task":
                emit(
                    "on-edit",
                    YamlUtils.insertTask(
                        source,
                        taskEditData.value.insertionDetails[0],
                        event,
                        taskEditData.value.insertionDetails[1],
                    ),
                    true,
                );
                return;
            case "edit_task":
                emit(
                    "on-edit",
                    YamlUtils.replaceTaskInDocument(
                        source,
                        taskEditData.value.oldTaskId,
                        event,
                    ),
                    true,
                );
                return;
            case "add_flowable_error":
                emit(
                    "on-edit",
                    YamlUtils.insertErrorInFlowable(
                        props.source,
                        event,
                        taskEditData.value.taskId,
                    ),
                    true,
                );
                return;
            }
        } else {
            store.dispatch("core/showMessage", {
                variant: "error",
                title: t("error detected"),
                message: t("Task Id already exist in the flow", {
                    taskId: YamlUtils.parse(event).id,
                }),
            });
        }
        taskEditData.value = null;
        taskObject.value = null;
    };

    const closeEdit = () => {
        taskEditData.value = null;
        taskObject.value = null;
    };

    const toggleOrientation = () => {
        localStorage.setItem(
            "topology-orientation",
            localStorage.getItem("topology-orientation") !== "0" ? "0" : "1",
        );
        isHorizontal.value = localStorage.getItem("topology-orientation") === "1";
        fitView();
    };

    const openFlow = (data) => {
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

    const showLogs = (event) => {
        selectedTask.value = event;
        isShowLogsOpen.value = true;
        isDrawerOpen.value = true;
    };

    const onSearch = (search) => {
        logFilter.value = search;
    };

    const onLevelChange = (level) => {
        logLevel.value = level;
    };

    const showDescription = (event) => {
        selectedTask.value = event;
        isShowDescriptionOpen.value = true;
        isDrawerOpen.value = true;
    };

    const showCondition = (event) => {
        selectedTask.value = event.task;
        isShowConditionOpen.value = true;
        isDrawerOpen.value = true;
    };

    const onSwappedTask = (event) => {
        emit("swapped-task", event.swappedTasks);
        emit("on-edit", event.newSource, true);
    };

    const message = (event) => {
        store.dispatch("core/showMessage", {
            variant: event.variant,
            title: t(event.title),
            message: t(event.message),
        });
    };

    const expandSubflow = (event) => {
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
