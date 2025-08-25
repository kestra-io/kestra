<template>
    <div class="outputs">
        <div
            class="d-flex flex-column left"
            :style="{width: leftWidth + '%'}"
        >
            <el-cascader-panel
                ref="cascader"
                v-model="selected"
                :options="options"
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
            </el-cascader-panel>
        </div>
        <div class="right wrapper fixed-right" :style="{width: 100 - leftWidth + '%'}">
            <div class="w-100 overflow-auto debug-wrapper">
                <div class="debug">
                    <div class="debug-title mb-3">
                        <span>{{ $t("eval.render") }}</span>
                    </div>

                    <div class="d-flex flex-column p-3 debug">
                        <Editor
                            ref="debugEditor"
                            :full-height="false"
                            :custom-height="20"
                            :input="true"
                            :navbar="false"
                            :model-value="computedDebugValue"
                            @update:model-value="editorValue = $event"
                            @confirm="onDebugExpression($event)"
                            class="w-100"
                        />

                        <el-button
                            type="primary"
                            :icon="Refresh"
                            @click="
                                onDebugExpression(
                                    editorValue.length > 0 ? editorValue : computedDebugValue,
                                )
                            "
                            class="mt-3"
                        >
                            {{ $t("eval.render") }}
                        </el-button>

                        <Editor
                            v-if="debugExpression"
                            :read-only="true"
                            :input="true"
                            :full-height="false"
                            :custom-height="20"
                            :navbar="false"
                            :model-value="debugExpression"
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
                    :value="selectedValue?.uri ? selectedValue?.uri : selectedValue"
                    :execution="execution"
                />
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, watch} from "vue";
    import {ElCascaderPanel} from "element-plus";
    import CopyToClipboard from "../layout/CopyToClipboard.vue";
    import Editor from "../inputs/Editor.vue";
    import VarValue from "./VarValue.vue";
    import Refresh from "vue-material-design-icons/Refresh.vue";

    interface CascaderOption {
        label: string;
        value: string;
        children?: CascaderOption[];
        path?: string;
        [key: string]: any;
    }

    const props = defineProps<{
        options: CascaderOption[];
        execution: any;
        id?: string;
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
    const leftWidth = ref(70);

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
        
        let currentOptions: CascaderOption[] = props.options;
        let currentNode: CascaderOption | undefined = undefined;
        
        for (const value of selected.value) {
            currentNode = currentOptions?.find(option => option.value === value || option.label === value);
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
            regular: typeof data.value !== "object"
        };
    }

    function onNodeClick(data: any) {
        let path = "";
        
        if (selected.value?.length) {
            path = selected.value.join(".");
        }
        
        if (!path) {
            const findNodePath = (options: CascaderOption[], targetNode: any, currentPath: string[] = []): string[] | null => {
                for (const option of options) {
                    const newPath = [...currentPath, option.value || option.label];
                    
                    if ((option.value === targetNode.value || option.label === targetNode.label) || 
                        (option.value === (targetNode.value || targetNode.label)) ||
                        (option.label === (targetNode.value || targetNode.label))) {
                        return newPath;
                    }
                    
                    if (option.children) {
                        const found = findNodePath(option.children, targetNode, newPath);
                        if (found) return found;
                    }
                }
                return null;
            };
            
            const nodePath = findNodePath(props.options, data);
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
        return Boolean(selectedValue.value && 
            typeof selectedValue.value === "string" && 
            (selectedValue.value.startsWith("kestra://") || 
                selectedValue.value.startsWith("http://") || 
                selectedValue.value.startsWith("https://")));
    }

    function evaluateExpression(expression: string, trigger: any): any {
        try {
            const cleanExpression = expression.replace(/^\{\{\s*/, "").replace(/\s*\}\}$/, "").trim();
            
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

    watch(selected, (newValue) => {
        if (newValue?.length) {
            const path = newValue.join(".");
            expandedValue.value = path;
            debugExpression.value = "";
            debugError.value = "";
            debugStackTrace.value = "";
        }
    }, {deep: true});
</script>

<style scoped lang="scss">
.outputs {
    height: fit-content;
    display: flex;
    position: relative;
}

.left {
    overflow-x: auto;
}

:deep(.el-cascader-panel) {
    min-height: 197px;
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

    &.fixed-right {
        position: sticky;
        right: 0;
        top: 0;
    }

    .debug-wrapper {
        min-height: 197px;
        border: 1px solid var(--ks-border-primary);
        border-left-width: .5px;
        border-radius: 0;
        padding: 0;
        background-color: var(--ks-background-body);
    }

    .debug-title {
        padding: 12px 16px;
        background-color: var(--ks-background-body);
        font-weight: bold;
        font-size: var(--el-font-size-base);
    }
}
</style>