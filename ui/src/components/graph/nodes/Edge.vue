<script lang="ts" setup>
    import type {EdgeProps, Position} from '@vue-flow/core'
    import {EdgeLabelRenderer, getSmoothStepPath, useEdge} from '@vue-flow/core'
    import type {CSSProperties} from 'vue'
    import {computed, getCurrentInstance, ref, watch} from 'vue'
    import TaskEditor from "../../flows/TaskEditor.vue"
    import Help from "vue-material-design-icons/Help.vue";
    import HelpCircle from "vue-material-design-icons/HelpCircle.vue";
    import Exclamation from "vue-material-design-icons/Exclamation.vue";
    import Reload from "vue-material-design-icons/Reload.vue";
    import ViewParallelOutline from "vue-material-design-icons/ViewParallelOutline.vue";
    import ViewSequentialOutline from "vue-material-design-icons/ViewSequentialOutline.vue";
    import Plus from "vue-material-design-icons/Plus.vue";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
    import yamlUtils from "../../../utils/yamlUtils.js";
    import YamlUtils from "../../../utils/yamlUtils.js";
    import {useStore} from "vuex";
    import ValidationError from "../../flows/ValidationError.vue";

    const store = useStore();
    const t = getCurrentInstance().appContext.config.globalProperties.$t;

    interface CustomEdgeProps<T = any> extends EdgeProps<T> {
        id: string
        sourceX: number
        sourceY: number
        targetX: number
        targetY: number
        sourcePosition: Position
        targetPosition: Position
        data: T
        markerEnd: string
        style: CSSProperties,
        yamlSource: String,
        flowablesIds: Array<String>,
    }

    const props = defineProps<CustomEdgeProps>()
    const isHover = ref(false);
    const isOpen = ref(false);
    const {edge} = useEdge()
    const emit = defineEmits(["edit"])
    const taskYaml = ref("");
    const execution = store.getters["execution/execution"];
    const timer = ref(undefined);
    const taskError = ref(store.getters["flow/taskError"])

    watch(() => store.getters["flow/taskError"], async () => {
        taskError.value = store.getters["flow/taskError"];
    });

    const isBorderEdge = () => {
        if (!props.data.haveAdd && props.data.isFlowable) {
            return false
        }
        const task1 = props.id.split("|")[0]
        const task2 = props.id.split("|")[1]
        // Check if relation is root > task or task > end or if it contains a haveAdd
        return (task1.includes("_root") && yamlUtils.extractTask(props.yamlSource, task2)) || (task2.includes("_end") && yamlUtils.extractTask(props.yamlSource, task1)) || props.data.haveAdd
    }

    const getEdgeLabel = (relation) => {
        let label = "";
        if (relation.relationType) {
            label = relation.relationType;
            if (relation.relationType === "CHOICE" && relation.value) {
                label += ` : ${relation.value}`;
            }
        } else if (isBorderEdge()) {
            label += "SEQUENTIAL"
        }
        return label;
    };

    const getEdgeIcon = (relation) => {
        if (relation.relationType) {
            if (relation.relationType === "ERROR") {
                return Exclamation;
            } else if (relation.relationType === "DYNAMIC") {
                return Reload;
            } else if (relation.relationType === "CHOICE") {
                return Help;
            } else if (relation.relationType === "PARALLEL") {
                return ViewParallelOutline;
            } else {
                return ViewSequentialOutline;
            }
        } else if (isBorderEdge()) {
            return ViewSequentialOutline;
        }

        return HelpCircle;
    };

    const getClassName = computed(() => {
        return {
            [props.data.edge.relation.relationType]: true,
            hover: isHover
        }
    })

    const path = computed(() => getSmoothStepPath(props))

    const onMouseOver = (edge) => {
        isHover.value = true;
    }

    const onMouseLeave = (edge) => {
        isHover.value = false;
    }

    const updateTask = (task) => {
        taskYaml.value = task;
        clearTimeout(timer.value);
        timer.value = setTimeout(() => {
            store.dispatch("flow/validateTask", {task: task})
        }, 500);
    }

    const getAddTaskInformation = () => {
        // end to end edge case
        if (props.data.haveAdd) {
            return {taskId: props.data.haveAdd[0], taskYaml: taskYaml.value, insertPosition: props.data.haveAdd[1]};
        }

        let leftNodeIsFlowable = false;

        const leftNodeIsTask = YamlUtils.extractTask(props.yamlSource, props.id.split("|")[0]) !== undefined;
        if (leftNodeIsTask) {
            leftNodeIsFlowable = props.flowablesIds.includes(props.id.split("|")[0])
        }
        // If left node is a flowable task or is not a task, then we insert
        // the new task before the right task node
        const [taskId, insertPosition] = leftNodeIsTask && !leftNodeIsFlowable ? [props.id.split("|")[0], "after"] : [props.data.nextTaskId, "before"];

        return {taskId: taskId, taskYaml: taskYaml.value, insertPosition: insertPosition}
    }

    const taskHaveId = () => {
        return taskYaml.value.length > 0 ? YamlUtils.parse(taskYaml.value).id ? true : false : false;
    }

    const checkTaskExist = () => {
        return yamlUtils.checkTaskAlreadyExist(props.yamlSource, YamlUtils.parse(taskYaml.value).id)
    }

    const forwardTask = () => {
        if (!checkTaskExist()) {
            emit("edit", getAddTaskInformation());
            isOpen.value = false;
        } else {
            store.dispatch("core/showMessage", {
                variant: "error",
                title: t("task id already exist"),
                message: t(`Task Id already exist in the flow`, {taskId: YamlUtils.parse(taskYaml.value).id})
            });
        }
    };

    const addTooltip = () => {
        const addInformation = getAddTaskInformation();
        const taskId = addInformation.insertPosition === 'before' ? props.data.nextTaskId : addInformation.taskId;

        if (execution || !taskId) {
            return ;
        }

        if (!props.data.initTask) {
            return t("add at position", {
                position: t(addInformation.insertPosition),
                task: taskId
            })
        } else {
            return t("create first task");
        }
    }
</script>

<script lang="ts">
    export default {
        inheritAttrs: false,
    }
</script>

<template>
    <path
        :id="id"
        :style="style"
        class="vue-flow__edge-path"
        :class="getClassName"
        :d="path[0]"
        :marker-end="markerEnd"
    />

    <!-- hidden path to have largest hover region -->
    <path
        :d="path[0]"
        fill="none"
        stroke-opacity="0"
        stroke-width="20"
        @mouseover="onMouseOver"
        @mouseleave="onMouseLeave"
    />

    <EdgeLabelRenderer style="z-index: 10">
        <div
            v-if="getEdgeLabel(props.data.edge.relation) !== ''"
            @mouseover="onMouseOver"
            @mouseleave="onMouseLeave"
            :style="{
                pointerEvents: 'all',
                position: 'absolute',
                transform: `translate(-50%, -50%) translate(${path[1]}px,${path[2]}px)`,
            }"
            class="nodrag nopan"
            :class="props.data.edge.relation.relationType"

        >
            <el-tooltip placement="bottom" :persistent="false" transition="" :hide-after="0">
                <template #content>
                    <template v-if="!isHover">
                        {{ getEdgeLabel(props.data.edge.relation) }}
                    </template>
                    <template v-else>
                        {{ getEdgeLabel(props.data.edge.relation) }}<br/>
                        <span v-html="addTooltip()" />
                    </template>
                </template>
                <span>
                    <el-button v-if="!isHover" :icon="getEdgeIcon(props.data.edge.relation)" link/>
                    <el-button v-else :icon="Plus" link @click="isOpen = true"/>
                </span>
            </el-tooltip>


            <el-drawer
                v-if="isOpen"
                v-model="isOpen"
                title="Add a task"
                destroy-on-close
                size=""
                :append-to-body="true"
            >
                <el-form label-position="top">
                    <task-editor
                        section="tasks"
                        @update:model-value="updateTask($event)"
                    />
                </el-form>
                <template #footer>
                    <ValidationError link :error="taskError"/>
                    <el-button :disabled="!taskHaveId() || taskError" :icon="ContentSave" @click="forwardTask" type="primary">
                        {{ $t("save") }}
                    </el-button>
                </template>
            </el-drawer>
        </div>
    </EdgeLabelRenderer>

</template>

<style lang="scss">
    .vue-flow__edge-path {
        &.ERROR {
            stroke: var(--bs-danger);
        }

        &.DYNAMIC {
            stroke: var(--bs-teal);
        }

        &.CHOICE {
            stroke: var(--bs-orange);
        }
    }

    .vue-flow__edge-labels > div {
        border-radius: 50%;
        height: 18px;
        width: 18px;
        background: var(--bs-purple);

        .el-button {
            margin-top: -9px;
            margin-left: -1px;
            font-size: var(--font-size-sm);

            &:hover, &:active {
                color: var(--el-color-white) !important;
            }
        }

        &.ERROR {
            background: var(--bs-danger);
        }

        &.DYNAMIC {
            background: var(--bs-teal);
        }

        &.CHOICE {
            background: var(--bs-orange);
        }
    }
</style>
