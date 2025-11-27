<template>
    <div class="outputs">
        <el-splitter :layout="isMobile ? 'vertical' : 'horizontal'">
            <el-splitter-panel v-model:size="leftWidth" :min="'30%'" :max="'70%'" class="outputs-top">
                <div class="d-flex flex-column overflow-x-auto left">
                    <el-cascader-panel
                        ref="cascader"
                        v-model="selected"
                        :options="outputs"
                        :border="false"
                        class="flex-grow-1 cascader"
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
                                        :icons="pluginsStore.icons"
                                        :cls="icons[data.taskId]"
                                        onlyIcon
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
                </div>
            </el-splitter-panel>
            <el-splitter-panel>
                <div class="right wrapper">
                    <div
                        v-if="multipleSelected || selectedValue"
                        class="w-100 overflow-auto p-3 content-container"
                    >
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
                                    <Editor
                                        ref="debugEditor"
                                        :fullHeight="false"
                                        :customHeight="20"
                                        :input="true"
                                        :navbar="false"
                                        :modelValue="computedDebugValue"
                                        @update:model-value="editorValue = $event"
                                        @confirm="onDebugExpression($event)"
                                        class="w-100"
                                    />

                                    <el-button
                                        type="primary"
                                        @click="
                                            onDebugExpression(
                                                editorValue.length > 0 ? editorValue : computedDebugValue,
                                            )
                                        "
                                        class="mt-3 el-button--wrap"
                                    >
                                        {{ t("eval.title") }}
                                    </el-button>

                                    <Editor
                                        v-if="debugExpression"
                                        :readOnly="true"
                                        :input="true"
                                        :fullHeight="false"
                                        :customHeight="20"
                                        :navbar="false"
                                        :modelValue="debugExpression"
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
                                    :text="`${debugError}\n\n${debugStackTrace}`"
                                    label="Copy Error"
                                    class="d-inline-block me-2"
                                />
                            </div>
                            <pre class="mb-0" style="overflow: scroll">{{
                                debugStackTrace
                            }}</pre>
                        </el-alert>

                        <VarValue
                            v-if="displayVarValue()"
                            :value="selectedValue?.uri ? selectedValue?.uri : selectedValue"
                            :execution="execution"
                        />
                        <SubFlowLink
                            v-if="selectedNode().label === 'executionId'"
                            :executionId="selectedNode().value"
                        />
                    </div>
                </div>
            </el-splitter-panel>
        </el-splitter>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, shallowRef, onMounted, watch} from "vue";
    import {ElTree} from "element-plus";
    import {useExecutionsStore} from "../../../stores/executions";
    import {usePluginsStore} from "../../../stores/plugins";

    import {useI18n} from "vue-i18n";
    import {apiUrl} from "override/utils/route";
    import {TaskIcon} from "@kestra-io/ui-libs";

    import CopyToClipboard from "../../layout/CopyToClipboard.vue";
    import Editor from "../../inputs/Editor.vue";
    import VarValue from "../VarValue.vue";
    import SubFlowLink from "../../flows/SubFlowLink.vue";
    import TimelineTextOutline from "vue-material-design-icons/TimelineTextOutline.vue";
    import TextBoxSearchOutline from "vue-material-design-icons/TextBoxSearchOutline.vue";
    import {useAxios} from "../../../utils/axios";
    import {useMediaQuery} from "@vueuse/core";

    const {t} = useI18n({useScope: "global"});

    const editorValue = ref<string>("");
    const debugCollapse = ref<string>("");
    const debugEditor = ref<InstanceType<typeof Editor>>();
    const debugExpression = ref<string>("");

    const computedDebugValue = computed(() => {
        const formatTask = (task: string) => {
            if (!task) return "";
            return task.includes("-") ? `["${task}"]` : `.${task}`;
        };

        const formatPath = (path: string) => {
            if (!path.includes("-")) return `.${path}`;

            const bracketIndex = path.indexOf("[");
            const task = path.substring(0, bracketIndex);
            const rest = path.substring(bracketIndex);

            return `["${task}"]${rest}`;
        };

        let task = selectedTask()?.taskId;
        if (!task) return "";

        let path = expandedValue.value;
        if (!path) return `{{ outputs${formatTask(task)} }}`;

        return `{{ outputs${formatPath(path)} }}`;
    });

    const debugError = ref("");
    const debugStackTrace = ref("");
    const isJSON = ref(false);
    const selectedTask = () => {
        const filter = selected.value?.length
            ? selected.value[0]
            : (cascader.value as any).menuList?.[0]?.panel?.expandingNode?.label;
        const taskRunList = [...execution.value?.taskRunList ?? []];
        return taskRunList.find((e) => e.taskId === filter);
    };

    const axios = useAxios();
    const onDebugExpression = (expression: string) => {
        const taskRun = selectedTask();

        if (!taskRun) return;

        const URL = `${apiUrl()}/executions/${taskRun?.executionId}/eval/${taskRun.id}`;
        axios
            .post(URL, expression, {headers: {"Content-type": "text/plain"}})
            .then((response) => {
                try {
                    const parsedResult = JSON.parse(response.data.result);
                    const debugOutput = JSON.stringify(parsedResult, null, 2);
                    debugExpression.value = debugOutput;

                    if (response.status === 200 && debugOutput !== null && debugOutput !== undefined) {
                        selected.value.push(debugOutput);
                    }
                    isJSON.value = true;
                } catch {
                    debugExpression.value = response.data.result;

                    // Parsing failed, therefore, copy raw result
                    if (response.status === 200 && response.data.result !== null && response.data.result !== undefined)
                        selected.value.push(response.data.result);
                }

                debugError.value = response.data.error;
                debugStackTrace.value = response.data.stackTrace;
            });
    };

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

    const executionsStore = useExecutionsStore();

    const execution = computed(() => executionsStore.execution);

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

    const expandedValue = ref("");
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

    watch(selectedValue, () => {
        debugError.value = "";
        debugStackTrace.value = "";
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
        const tasks = executionsStore?.execution?.taskRunList?.map((task) => {
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

    const pluginsStore = usePluginsStore();

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

        getTaskIcons(executionsStore?.flow?.tasks || [], mapped);
        getTaskIcons(executionsStore?.flow?.errors || [], mapped);
        getTaskIcons(executionsStore?.flow?.finally || [], mapped);

        return mapped;
    });

    const trim = (value) =>
        typeof value !== "string" || value.length < 16
            ? value
            : `${value.substring(0, 16)}...`;
    const isFile = (value) =>
        typeof value === "string" && (value.startsWith("kestra:///") || value.startsWith("file://") || value.startsWith("nsfile://"));
    const displayVarValue = () =>
        isFile(selectedValue.value) ||
        selectedValue.value !== debugExpression.value;

    const leftWidth = ref("70%");
    const isMobile = useMediaQuery("(max-width: 768px)");
</script>

<style scoped lang="scss">
.outputs {
    display: flex;
    width: 100%;
    height: 100vh;
    overflow: hidden;
}

:deep(.el-splitter-bar) {
    width: 3px !important;
    background-color: var(--ks-border-primary);

    &:hover {
        background-color: var(--ks-border-active);
    }
}

:deep(.el-scrollbar.el-cascader-menu:nth-of-type(-n + 2) ul li:first-child),
.values {
    pointer-events: none;
    margin: 0.75rem 0 1.25rem 0;
}

:deep(.el-cascader-menu__list) {
    min-height: 100vh;
}

:deep(.el-cascader-panel) {
    height: 100%;
}

.debug {
    background: var(--ks-background-body);
}

.bordered {
    border: 1px solid var(--ks-border-primary);
}

.bordered > :deep(.el-collapse-item) {
    margin-bottom: 0px !important;
}

.wrapper {
    background: var(--ks-background-card);
    position: relative;
    z-index: 1;
}

:deep(.el-cascader-menu) {
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
.content-container {
    height: calc(100vh - 0px);
    overflow-y: scroll;
    overflow-x: hidden;
    scrollbar-gutter: stable;
    word-wrap: break-word;
    word-break: break-word;
    position: relative;
    z-index: 0;
}

:deep(.el-collapse) {
    .el-collapse-item__wrap {
        max-height: none !important;
    }

    .el-collapse-item__content {
        word-wrap: break-word;
        word-break: break-word;
    }
}

:deep(.var-value) {
    word-wrap: break-word;
    word-break: break-word;
}

:deep(pre) {
    white-space: pre-wrap !important;
    word-wrap: break-word !important;
    word-break: break-word !important;
    overflow-wrap: break-word !important;
}

:deep(.monaco-editor),
:deep(.editor-container),
:deep(.complex-value-editor) {
    position: relative !important;
    z-index: auto !important;
}

//Mobile Version
@media (max-width: 768px) {
    :deep(.el-splitter) { 
        .outputs-top {
            margin: 10px;
            border: 2px solid var(--ks-border-primary);
            box-sizing: border-box;
            overflow: auto;
            flex: 1 1 0 !important;
            min-height: 0 !important;
        }
    }
    :deep(.el-splitter-bar){
        height: 4px !important;
        width: auto !important;
        
    }
}
</style>