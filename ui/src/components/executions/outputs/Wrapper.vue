<template>
    <el-row class="flex-grow-1 outputs">
        <el-col
            :xs="24"
            :sm="24"
            :md="multipleSelected || selectedValue ? 16 : 24"
            :lg="multipleSelected || selectedValue ? 16 : 24"
            :xl="multipleSelected || selectedValue ? 18 : 24"
            class="d-flex flex-column"
        >
            <el-cascader-panel
                ref="cascader"
                v-model="selected"
                :options="outputs"
                :border="false"
                class="flex-grow-1 overflow-x-auto cascader"
                @expand-change="() => scrollRight()"
            >
                <template #default="{data}">
                    <div
                        v-if="data.heading"
                        @click="expandedValue = data.path"
                        class="pe-none d-flex fs-5"
                    >
                        <component :is="data.component" class="me-2" />
                        <span>{{ data.label }}</span>
                    </div>

                    <div
                        v-else
                        @click="expandedValue = data.path"
                        class="w-100 d-flex justify-content-between"
                    >
                        <div class="pe-5 d-flex task">
                            <TaskIcon
                                v-if="data.icon"
                                :icons="allIcons"
                                :cls="icons[data.taskId]"
                                only-icon
                            />
                            <span :class="{'ms-3': data.icon}">{{
                                data.label
                            }}</span>
                        </div>
                        <code>
                            <span
                                :class="{
                                    regular: processedValue(data).regular,
                                }"
                            >
                                {{ processedValue(data).label }}
                            </span>
                        </code>
                    </div>
                </template>
            </el-cascader-panel>
        </el-col>
        <el-col
            v-if="multipleSelected || selectedValue"
            :xs="24"
            :sm="24"
            :md="8"
            :lg="8"
            :xl="6"
            class="d-flex wrapper"
        >
            <div @mousedown.prevent.stop="dragSidebar" class="slider" />
            <div class="w-100 overflow-auto p-3">
                <div class="d-flex justify-content-between pe-none fs-5 values">
                    <code class="d-block">
                        {{ selectedNode()?.label ?? "Value" }}
                    </code>
                </div>

                <el-collapse
                    v-model="debugCollapse"
                    class="mb-3 debug bordered"
                >
                    <el-collapse-item name="debug">
                        <template #title>
                            <span>{{ t("eval.title") }}</span>
                        </template>

                        <div class="d-flex flex-column p-3 debug">
                            <editor
                                ref="debugEditor"
                                :full-height="false"
                                :input="true"
                                :navbar="false"
                                :model-value="computedDebugValue"
                                @confirm="onDebugExpression($event)"
                                class="w-100"
                            />

                            <el-button
                                type="primary"
                                @click="
                                    onDebugExpression(
                                        debugEditor.editor.getValue(),
                                    )
                                "
                                class="mt-3"
                            >
                                {{ t("eval.title") }}
                            </el-button>

                            <editor
                                v-if="debugExpression"
                                :read-only="true"
                                :input="true"
                                :full-height="false"
                                :navbar="false"
                                :minimap="false"
                                :model-value="debugExpression"
                                :lang="isJSON ? 'json' : ''"
                                class="mt-3"
                            />
                        </div>
                    </el-collapse-item>
                </el-collapse>

                <el-alert
                    v-if="debugError"
                    type="error"
                    :closable="false"
                    class="overflow-auto"
                >
                    <p>
                        <strong>{{ debugError }}</strong>
                    </p>
                    <div class="my-2">
                        <CopyToClipboard
                            :text="debugError"
                            label="Copy Error"
                            class="d-inline-block me-2"
                        />
                        <CopyToClipboard
                            :text="debugStackTrace"
                            label="Copy Stack Trace"
                            class="d-inline-block"
                        />
                    </div>
                    <pre class="mb-0" style="overflow: scroll">{{
                        debugStackTrace
                    }}</pre>
                </el-alert>

                <VarValue
                    v-if="displayVarValue()"
                    :value="selectedValue"
                    :execution="execution"
                />
                <SubFlowLink
                    v-if="selectedNode().label === 'executionId'"
                    :execution-id="selectedNode().value"
                />
            </div>
        </el-col>
    </el-row>
</template>

<script setup lang="ts">
    import {ref, computed, shallowRef, onMounted} from "vue";
    import {ElTree} from "element-plus";

    import {useStore} from "vuex";
    const store = useStore();

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import {apiUrl} from "override/utils/route";

    import CopyToClipboard from "../../layout/CopyToClipboard.vue";

    import Editor from "../../inputs/Editor.vue";
    const debugCollapse = ref("");
    const debugEditor = ref(null);
    const debugExpression = ref("");
    const computedDebugValue = computed(() => {
        const formatTask = (task) => {
            if (!task) return "";
            return task.includes("-") ? `["${task}"]` : `.${task}`;
        };

        const formatPath = (path) => {
            if (!path.includes("-")) return `.${path}`;

            const bracketIndex = path.indexOf("[");
            const task = path.substring(0, bracketIndex);
            const rest = path.substring(bracketIndex);

            return `["${task}"]${rest}`;
        }

        let task = selectedTask()?.taskId;
        if (!task) return "";

        let path = expandedValue.value;
        if (!path) return `{{ outputs${formatTask(task)} }}`;

        return `{{ outputs${formatPath(path)} }}`;
    });

    // TODO: To be implemented properly
    const dragSidebar = () => {};

    const debugError = ref("");
    const debugStackTrace = ref("");
    const isJSON = ref(false);
    const selectedTask = () => {
        const filter = selected.value?.length
            ? selected.value[0]
            : (cascader.value as any).menuList?.[0]?.panel?.expandingNode?.label;
        const taskRunList = [...execution.value.taskRunList];
        return taskRunList.find((e) => e.taskId === filter);
    };
    const onDebugExpression = (expression: string) => {
        const taskRun = selectedTask();

        if (!taskRun) return;

        const URL = `${apiUrl(store)}/executions/${taskRun?.executionId}/eval/${taskRun.id}`;
        store.$http
            .post(URL, expression, {headers: {"Content-type": "text/plain"}})
            .then((response) => {
                try {
                    const parsedResult = JSON.parse(response.data.result);
                    const debugOutput = JSON.stringify(parsedResult, null, 2);
                    debugExpression.value = debugOutput;

                    selected.value.push(debugOutput);

                    isJSON.value = true;
                } catch {
                    debugExpression.value = response.data.result;

                    // Parsing failed, therefore, copy raw result
                    if (response.status === 200 && response.data.result)
                        selected.value.push(response.data.result);
                }

                debugError.value = response.data.error;
                debugStackTrace.value = response.data.stackTrace;
            });
    };

    import VarValue from "../VarValue.vue";
    import SubFlowLink from "../../flows/SubFlowLink.vue";

    import {TaskIcon} from "@kestra-io/ui-libs";

    import TimelineTextOutline from "vue-material-design-icons/TimelineTextOutline.vue";
    import TextBoxSearchOutline from "vue-material-design-icons/TextBoxSearchOutline.vue";

    const cascader = ref<InstanceType<typeof ElTree> | null>(null);
    const scrollRight = () =>
        setTimeout(
            () =>
                ((cascader.value as any).$el.scrollLeft = (
                    cascader.value as any
                ).$el.offsetWidth),
            10,
        );
    const multipleSelected = computed(
        () => (cascader.value as any)?.menus?.length > 1,
    );

    const execution = computed(() => store.state.execution.execution);

    function isValidURL(url) {
        try {
            new URL(url);
            return true;
        } catch {
            return false;
        }
    }

    const processedValue = (data) => {
        const regular = false;

        if (!data.value && !data.children?.length) {
            return {label: data.value, regular};
        } else if (data?.children?.length) {
            const message = (length) => ({label: `${length} items`, regular});
            const length = data.children.length;

            return data.children[0].isFirstPass
                ? message(length - 1)
                : message(length);
        }

        // Check if the value is a valid URL and not an internal "kestra:///" link
        if (isValidURL(data.value)) {
            return data.value.startsWith("kestra:///")
                ? {label: "Internal link", regular}
                : {label: "External link", regular};
        }

        return {label: trim(data.value), regular: true};
    };

    const expandedValue = ref([]);
    const selected = ref<string[]>([]);

    onMounted(() => {
        const task = outputs.value?.[1];
        if (!task) return;

        selected.value = [task.value];
        expandedValue.value = task.value;

        const child = task.children?.[1];
        if (child) {
            selected.value.push(child.value);
            expandedValue.value = child.path;

            const grandChild = child.children?.[1];
            if (grandChild) {
                selected.value.push(grandChild.value);
                expandedValue.value = grandChild.path;
            }
        }

        debugCollapse.value = "debug";
    });

    const selectedValue = computed(() => {
        if (selected.value?.length)
            return selected.value[selected.value.length - 1];
        return undefined;
    });
    const selectedNode = () => {
        const node = cascader.value?.getCheckedNodes();

        if (!node?.length) return {label: undefined, value: undefined};

        const {label, value} = node[0];

        return {label, value};
    };

    const transform = (o, isFirstPass, path = "") => {
        const result = Object.keys(o).map((key) => {
            const value = o[key];
            const isObject = typeof value === "object" && value !== null;

            const currentPath = `${path}["${key}"]`;

            // If the value is an array with exactly one element, use that element as the value
            if (Array.isArray(value) && value.length === 1) {
                return {
                    label: key,
                    value: value[0],
                    children: [],
                    path: currentPath,
                };
            }

            return {
                label: key,
                value: isObject && !Array.isArray(value) ? key : value,
                children: isObject ? transform(value, false, currentPath) : [],
                path: currentPath,
            };
        });

        if (isFirstPass) {
            const OUTPUTS = {
                label: t("outputs"),
                heading: true,
                component: shallowRef(TextBoxSearchOutline),
                isFirstPass: true,
                path: path,
            };
            result.unshift(OUTPUTS);
        }

        return result;
    };
    const outputs = computed(() => {
        const tasks = store.state.execution?.execution?.taskRunList?.map((task) => {
            return {
                label: task.taskId,
                value: task.taskId,
                ...task,
                icon: true,
                children: task?.outputs
                    ? transform(task.outputs, true, task.taskId)
                    : [],
            };
        });

        const HEADING = {
            label: t("tasks"),
            heading: true,
            component: shallowRef(TimelineTextOutline),
        };
        tasks?.unshift(HEADING);

        return tasks;
    });

    const allIcons = computed(() => store.state.plugin.icons);
    const icons = computed(() => {
        // TODO: https://github.com/kestra-io/kestra/issues/5643
        const getTaskIcons = (tasks, mapped) => {
            tasks.forEach((task) => {
                mapped[task.id] = task.type;
                if (task.tasks && task.tasks.length > 0) {
                    getTaskIcons(task.tasks, mapped);
                }
            });
        };

        const mapped = {};

        getTaskIcons(store.state.execution?.flow?.tasks || [], mapped);
        getTaskIcons(store.state.execution?.flow?.errors || [], mapped);
        getTaskIcons(store.state.execution?.flow?.finally || [], mapped);

        return mapped;
    });

    const trim = (value) =>
        typeof value !== "string" || value.length < 16
            ? value
            : `${value.substring(0, 16)}...`;
    const isFile = (value) =>
        typeof value === "string" && value.startsWith("kestra:///");
    const displayVarValue = () =>
        isFile(selectedValue.value) ||
        selectedValue.value !== debugExpression.value;
</script>

<style lang="scss">
.outputs {
    .el-scrollbar.el-cascader-menu:nth-of-type(-n + 2) ul li:first-child,
    .values {
        pointer-events: none;
        margin: 0.75rem 0 1.25rem 0;
    }

    .debug {
        background: var(--ks-background-body);
    }

    .bordered {
        border: 1px solid var(--ks-border-primary);
    }

    .bordered > .el-collapse-item {
        margin-bottom: 0px !important;
    }

    .cascader {
        &::-webkit-scrollbar {
            height: 5px;
        }

        &::-webkit-scrollbar-track {
            background: var(--ks-background-card);
        }

        &::-webkit-scrollbar-thumb {
            background: var(--ks-button-background-primary);
            border-radius: 0px;
        }
    }

    .wrapper {
        background: var(--ks-background-card);
    }

    .el-cascader-menu {
        min-width: 300px;
        max-width: 300px;

        &:last-child {
            border-right: 1px solid var(--ks-border-primary);
        }

        .el-cascader-menu__wrap {
            height: 100%;
        }

        & .el-cascader-node {
            height: 36px;
            line-height: 36px;
            font-size: var(--el-font-size-small);
            color: var(--ks-content-primary);

            &[aria-haspopup="false"] {
                padding-right: 0.5rem !important;
            }

            &:hover {
                background-color: var(--ks-border-primary);
            }

            &.in-active-path,
            &.is-active {
                background-color: var(--ks-border-primary);
                font-weight: normal;
            }

            .el-cascader-node__prefix {
                display: none;
            }

            .task .wrapper {
                align-self: center;
                height: var(--el-font-size-small);
                width: var(--el-font-size-small);
            }

            code span.regular {
                color: var(--ks-content-primary);
            }
        }
    }
}

.slider {
    flex: 0 0 3px;
    border-radius: 0.15rem;
    margin: 0 4px;
    background-color: var(--ks-border-primary);
    border: none;
    cursor: col-resize;
    user-select: none; /* disable selection */

    &:hover {
        background-color: var(--ks-border-active);
    }
}
</style>
