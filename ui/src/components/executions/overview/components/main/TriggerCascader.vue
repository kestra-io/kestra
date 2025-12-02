<template>
    <div :id="`cascader-${props.title}`">
        <div class="header">
            <el-text truncated>
                {{ props.title }}
            </el-text>
            <el-input
                v-if="props.elements"
                v-model="filter"
                :placeholder="$t('search')"
                :suffixIcon="Magnify"
            />
        </div>

        <el-splitter
            v-if="props.elements"
            :layout="verticalLayout ? 'vertical' : 'horizontal'"
        >
            <el-splitter-panel
                v-model:size="leftWidth"
                :min="'30%'"
                :max="'70%'"
            >
                <div class="d-flex flex-column overflow-x-auto left">
                    <ElCascaderPanel
                        ref="cascader"
                        v-model="selected"
                        :options="filteredOptions"
                        :border="false"
                        class="flex-grow-1 cascader"
                        @change="onSelectionChange"
                    >
                        <template #default="{data}">
                            <div
                                class="w-100 d-flex justify-content-between"
                                @click="onNodeClick(data)"
                            >
                                <div class="pe-5 d-flex">
                                    <span>{{ data.label }}</span>
                                </div>
                                <code>
                                    <span class="regular">
                                        {{ processedValue(data).label }}
                                    </span>
                                </code>
                            </div>
                        </template>
                    </ElCascaderPanel>
                </div>
            </el-splitter-panel>
            <el-splitter-panel v-model:size="rightWidth">
                <div class="right wrapper">
                    <div class="w-100 overflow-auto debug-wrapper">
                        <div class="debug">
                            <div class="debug-title mb-3">
                                <span>{{ $t("eval.render") }}</span>
                            </div>

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
                                    :icon="Refresh"
                                    @click="
                                        onDebugExpression(
                                            editorValue.length > 0
                                                ? editorValue
                                                : computedDebugValue,
                                        )
                                    "
                                    class="mt-3"
                                >
                                    {{ $t("eval.render") }}
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
                        </div>

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
                            v-if="selectedValue && displayVarValue()"
                            :value="
                                selectedValue?.uri
                                    ? selectedValue?.uri
                                    : selectedValue
                            "
                            :execution="execution"
                        />
                    </div>
                </div>
            </el-splitter-panel>
        </el-splitter>
        <span v-else class="empty">{{ props.empty }}</span>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, watch, onMounted} from "vue";
    import {ElCascaderPanel} from "element-plus";
    import CopyToClipboard from "../../../../layout/CopyToClipboard.vue";
    import Magnify from "vue-material-design-icons/Magnify.vue";
    import Editor from "../../../../inputs/Editor.vue";
    import VarValue from "../../../VarValue.vue";
    import Refresh from "vue-material-design-icons/Refresh.vue";

    onMounted(() => {
        if (props.elements) formatted.value = format(props.elements);

        // Open first node by default on page mount
        if (cascader?.value) {
            const nodes = cascader.value.$el.querySelectorAll(".el-cascader-node");
            if (nodes.length > 0) (nodes[0] as HTMLElement).click();
        }
    });

    interface CascaderOption {
        label: string;
        value: string;
        children?: CascaderOption[];
        path?: string;
        [key: string]: any;
    }

    const props = defineProps<{
        title: string;
        empty: string;
        elements?: CascaderOption;
        execution: any;
    }>();

    const cascader = ref<any>(null);
    const debugEditor = ref<InstanceType<typeof Editor>>();
    const selected = ref<string[]>([]);
    const editorValue = ref("");
    const debugExpression = ref("");
    const debugError = ref("");
    const debugStackTrace = ref("");
    const isJSON = ref(false);
    const expandedValue = ref("");

    import {useBreakpoints, breakpointsElement} from "@vueuse/core";
    const verticalLayout = useBreakpoints(breakpointsElement).smallerOrEqual("md");
    
    const leftWidth = verticalLayout ? ref("50%") : ref("80%");
    const rightWidth = verticalLayout ? ref("50%") : ref("20%");

    const formatted = ref<Node[]>([]);
    const format = (obj: Record<string, any>): Node[] => {
        return Object.entries(obj).map(([key, value]) => {
            const children =
                typeof value === "object" && value !== null
                    ? Object.entries(value).map(([k, v]) => format({[k]: v})[0])
                    : [{label: value, value: value}];

            // Filter out children with undefined label and value
            const filteredChildren = children.filter(
                (child) => child.label !== undefined || child.value !== undefined,
            );

            // Return node with or without children based on existence
            const node = {label: key, value: key};

            // Include children only if there are valid entries
            if (filteredChildren.length) {
                node.children = filteredChildren;
            }

            return node;
        });
    };
    const filter = ref("");
    const filteredOptions = computed(() => {
        if (filter.value === "") return formatted.value;

        const lowercase = filter.value.toLowerCase();
        return formatted.value.filter((node) => {
            const matchesNode = node.label.toLowerCase().includes(lowercase);

            if (!node.children) return matchesNode;

            const matchesChildren = node.children.some((c) =>
                c.label.toLowerCase().includes(lowercase),
            );

            return matchesNode || matchesChildren;
        });
    });

    const selectedValue = computed(() => {
        if (!selected.value?.length) return null;

        const node = selectedNode();
        return node?.value || node?.label;
    });

    const computedDebugValue = computed(() => {
        if (selected.value?.length) {
            const path = selected.value.join(".");
            return `{{ trigger.${path} }}`;
        }

        if (expandedValue.value) {
            return `{{ trigger.${expandedValue.value} }}`;
        }

        return "{{ trigger }}";
    });

    function selectedNode(): CascaderOption | null {
        if (!selected.value?.length) return null;

        let currentOptions: CascaderOption[] = props.elements;
        let currentNode: CascaderOption | undefined = undefined;

        for (const value of selected.value) {
            currentNode = currentOptions?.find(
                (option) => option.value === value || option.label === value,
            );
            if (currentNode?.children) {
                currentOptions = currentNode.children;
            }
        }

        return currentNode || null;
    }

    function processedValue(data: any) {
        const trim = (value: any) =>
            typeof value !== "string" || value.length < 16
                ? value
                : `${value.substring(0, 16)}...`;

        return {
            label: trim(data.value || data.label),
            regular: typeof data.value !== "object",
        };
    }

    function onNodeClick(data: any) {
        let path = "";

        if (selected.value?.length) {
            path = selected.value.join(".");
        }

        if (!path) {
            const findNodePath = (
                options: Record<string, any>[],
                targetNode: any,
                currentPath: string[] = [],
            ): string[] | null => {
                const localOptions = Array.isArray(options)
                    ? options
                    : [options]
                for (const option of localOptions) {
                    const newPath = [...currentPath, option.value || option.label];

                    if (
                        option.value === targetNode.value ||
                        option.label === targetNode.label ||
                        option.value === (targetNode.value || targetNode.label) ||
                        option.label === (targetNode.value || targetNode.label)
                    ) {
                        return newPath;
                    }

                    if (option.children) {
                        const found = findNodePath(
                            option.children ?? [],
                            targetNode,
                            newPath,
                        );
                        if (found) return found;
                    }
                }
                return null;
            };

            const nodePath = findNodePath(props.elements ?? [], data);
            path = nodePath ? nodePath.join(".") : "";
        }

        if (path) {
            expandedValue.value = path;
            debugExpression.value = "";
            debugError.value = "";
            debugStackTrace.value = "";
        }
    }

    function onSelectionChange(value: any) {
        if (value?.length) {
            const path = value.join(".");
            expandedValue.value = path;
            debugExpression.value = "";
            debugError.value = "";
            debugStackTrace.value = "";
        }
    }

    function displayVarValue(): boolean {
        return Boolean(
            selectedValue.value &&
                typeof selectedValue.value === "string" &&
                (selectedValue.value.startsWith("kestra://") ||
                    selectedValue.value.startsWith("http://") ||
                    selectedValue.value.startsWith("https://")),
        );
    }

    function evaluateExpression(expression: string, trigger: any): any {
        try {
            const cleanExpression = expression
                .replace(/^\{\{\s*/, "")
                .replace(/\s*\}\}$/, "")
                .trim();

            if (cleanExpression === "trigger") {
                return trigger;
            }

            if (!cleanExpression.startsWith("trigger.")) {
                throw new Error("Expression must start with \"trigger.\"");
            }

            const path = cleanExpression.substring(8);
            const parts = path.split(".");
            let result = trigger;

            for (const part of parts) {
                if (result && typeof result === "object" && part in result) {
                    result = result[part];
                } else {
                    throw new Error(`Property "${part}" not found`);
                }
            }

            return result;
        } catch (error: any) {
            throw new Error(`Failed to evaluate expression: ${error.message}`);
        }
    }

    function onDebugExpression(expression: string): void {
        try {
            debugError.value = "";
            debugStackTrace.value = "";

            const result = evaluateExpression(expression, props.execution?.trigger);

            try {
                if (typeof result === "object" && result !== null) {
                    debugExpression.value = JSON.stringify(result, null, 2);
                    isJSON.value = true;
                } else {
                    debugExpression.value = String(result);
                    isJSON.value = false;
                }
            } catch {
                debugExpression.value = String(result);
                isJSON.value = false;
            }
        } catch (error: any) {
            debugError.value = error.message || "Failed to evaluate expression";
            debugStackTrace.value = error.stack || "";
            debugExpression.value = "";
            isJSON.value = false;
        }
    }

    watch(
        selected,
        (newValue) => {
            if (newValue?.length) {
                const path = newValue.join(".");
                expandedValue.value = path;
                debugExpression.value = "";
                debugError.value = "";
                debugStackTrace.value = "";
            }
        },
        {deep: true},
    );
</script>

<style scoped lang="scss">
.outputs {
    height: fit-content;
    display: flex;
    position: relative;
}

.left {
    overflow-x: auto;
    height: 100%;
    display: flex;
    flex-direction: column;
}

:deep(.el-cascader-panel) {
    min-height: 197px;
    height: 100%;
    border: 1px solid var(--ks-border-primary);
    border-radius: 0;
    overflow-x: auto !important;
    overflow-y: hidden !important;

    .el-scrollbar.el-cascader-menu:nth-of-type(-n + 2) ul li:first-child {
        pointer-events: auto !important;
        margin: 0 !important;
    }

    .el-cascader-node {
        pointer-events: auto !important;
        cursor: pointer !important;
    }

    .el-cascader-panel__wrap {
        overflow-x: auto !important;
        display: flex !important;
        min-width: max-content !important;
    }

    .el-cascader-menu {
        min-width: 300px;
        max-width: 300px;
        flex-shrink: 0;

        &:last-child {
            border-right: 1px solid var(--ks-border-primary);
        }

        .el-cascader-menu__wrap {
            height: 100%;
        }

        .el-cascader-node {
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

            code span.regular {
                color: var(--ks-content-primary);
            }
        }
    }
}

:deep(.el-cascader-node) {
    cursor: pointer;
    margin: 0 !important;
}

.el-cascader-menu__list {
    padding: 6px;
}

.wrapper {
    height: fit-content;
    overflow: hidden;
    z-index: 1000;
    height: 100%;
    display: flex;
    flex-direction: column;

    .debug-wrapper {
        min-height: 197px;
        border: 1px solid var(--ks-border-primary);
        border-left-width: 0.5px;
        border-radius: 0;
        padding: 0;
        background-color: var(--ks-background-body);
        flex: 1;
    }

    .debug-title {
        padding: 12px 16px;
        background-color: var(--ks-background-body);
        font-weight: bold;
        font-size: var(--el-font-size-base);
    }
}

@media (max-width: 768px) {
    .outputs {
        height: 600px;
        margin-top: 15px;
    }
    :deep(.el-cascader-panel) {
        height: 100%;
    }
}


@import "@kestra-io/ui-libs/src/scss/variables";

[id^="cascader-"] {
    overflow: hidden;

    .header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding-bottom: $spacer;

        > .el-text {
            width: 100%;
            display: flex;
            align-items: center;
            font-size: $font-size-xl;
        }

        > .el-input {
            display: flex;
            align-items: center;
            width: calc($spacer * 16);
        }
    }

    .el-cascader-panel {
        overflow: auto;
    }

    .empty {
        font-size: $font-size-sm;
        color: var(--ks-content-secondary);
    }

    :deep(.el-cascader-menu) {
        min-width: 300px;
        max-width: 300px;

        .el-cascader-menu__list {
            padding: 0;
        }

        .el-cascader-menu__wrap {
            height: 100%;
        }

        .node {
            width: 100%;
            display: flex;
            justify-content: space-between;
        }

        & .el-cascader-node {
            height: 36px;
            line-height: 36px;
            font-size: $font-size-sm;
            color: var(--ks-content-primary);
            padding: 0 30px 0 5px;

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

            code span.regular {
                color: var(--ks-content-primary);
            }
        }
    }
}
</style>
