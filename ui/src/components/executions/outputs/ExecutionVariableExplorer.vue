<template>
    <div class="variable-explorer">
        <KsSplitter :layout="isMobile ? 'vertical' : 'horizontal'">
            <!-- Left: searchable list of context variables grouped by source -->
            <KsSplitterPanel v-model:size="leftWidth" :min="'20%'" :max="'40%'" class="variable-explorer__panel">
                <SidebarList
                    :sections="sections"
                    :selectedExpression="selectedBase"
                    @select="selectItem"
                />
            </KsSplitterPanel>

            <!-- Center: tree / raw JSON of the selected value -->
            <KsSplitterPanel class="variable-explorer__panel">
                <div class="viewer">
                    <div class="viewer__header">
                        <KsSegmented
                            v-if="isExpandableValue && !fileSelectedOutput" 
                            v-model="viewMode"
                            :options="viewModes"
                            size="small"
                        />
                        <span v-else-if="selectedBase">{{ selectedBase.split('.').join(' > ') }}</span>
                        <KsIconButton
                            v-if="selectedValue !== undefined && !fileSelectedOutput"
                            :aria-label="$t('copy')"
                            @click="copyValue"
                        >
                            <ContentCopy :size="16" />
                        </KsIconButton>
                    </div>
                    
                    <template v-if="selectedValue === undefined">
                        <KsEmpty :description="$t('variable_explorer.select_prompt')" />
                    </template>

                    <KsEditor
                        v-else-if="viewMode === 'raw' && isExpandableValue"
                        v-bind="editorBindings"
                        :readOnly="true"
                        :inline="true"
                        :navbar="false"
                        :options="{fullHeight: true}"
                        :modelValue="rawValue"
                        lang="json"
                    />

                    <div class="file-preview" v-else-if="fileSelectedOutput && execution?.id">
                        <FilePreview
                            :key="fileSelectedOutput"
                            :path="fileSelectedOutput"
                            :executionId="execution.id"
                        />
                    </div>

                    <VariableTreeView
                        v-else-if="isExpandableValue"
                        :value="selectedValue"
                        :basePath="selectedBase"
                        :selectedPath="expressionPath"
                        @select="onSelectPath"
                    />

                    <div v-else class="viewer__scalar">
                        <code>{{ rawValue }}</code>
                    </div>
                </div>
            </KsSplitterPanel>

            <!-- Right: evaluate a Pebble expression against the live execution -->
            <KsSplitterPanel v-if="!fileSelectedOutput" v-model:size="rightWidth" :min="'20%'" :max="'40%'" class="variable-explorer__panel">
                <div class="debug">
                    <ExpressionDebugger
                        :execution="execution"
                        :expression="expression"
                    />
                </div>
            </KsSplitterPanel>
        </KsSplitter>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, watch} from "vue"
    import {useMediaQuery} from "@vueuse/core"
    import {useI18n} from "vue-i18n"

    import {
        KsSplitter,
        KsSplitterPanel,
        KsEmpty,
        KsSegmented,
        KsIconButton,
        KsEditor,
    } from "@kestra-io/design-system"
    import * as OutputsAPI from "@kestra-io/kestra-sdk/outputs"

    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"

    import {useExecutionsStore} from "../../../stores/executions"
    import {useEditorBindings} from "../../../composables/useEditorBindings"

    import SidebarList, {ExplorerItem, ExplorerSection} from "./SidebarList.vue"
    import VariableTreeView from "./VariableTreeView.vue"
    import ExpressionDebugger from "./ExpressionDebugger.vue"
    import * as Utils from "../../../utils/utils"
    import FilePreview from "../FilePreview.vue"

    const {t} = useI18n({useScope: "global"})
    const editorBindings = useEditorBindings()

    const executionsStore = useExecutionsStore()
    const execution = computed(() => executionsStore.execution)

    /* ----------------------------- Pebble paths ----------------------------- */

    function isValidVariable(key: string): boolean {
        return /^[a-zA-Z][a-zA-Z0-9_]*$/.test(key)
    }

    function formatStep(key: string): string {
        return isValidVariable(key) ? `.${key}` : `["${key}"]`
    }

    function valueType(value: unknown): string {
        if (value === null) return "null"
        if (Array.isArray(value)) return "array"
        return typeof value
    }

    function preview(value: unknown): string {
        if (value === null) return "null"
        if (typeof value === "string") return value
        if (Array.isArray(value)) {
            return value.length === 1
                ? t("variable_explorer.one_item")
                : t("variable_explorer.n_items", {count: value.length})
        }
        if (typeof value === "object") {
            const keys = Object.keys(value as object)
            return `{ ${keys.join(", ")} }`
        }
        return String(value)
    }

    function itemsFromRecord(record: Record<string, unknown> | undefined, prefix: string): ExplorerItem[] {
        if (!record) return []
        return Object.entries(record).map(([label, value]) => ({
            label,
            value,
            type: valueType(value),
            preview: preview(value),
            expression: `${prefix}${formatStep(label)}`,
        }))
    }

    /* ------------------------- Task outputs sourcing ------------------------- */
    // Sourced exactly like Wrapper.vue: the list of task runs that have outputs
    // is fetched from the /outputs/{executionId} endpoint, then each task's
    // values are lazily loaded from /outputs/{executionId}/{taskRunId}.

    const tasksWithOutputs = ref<string[] | undefined>(undefined)
    const taskOutputs = ref<Record<string, Record<string, unknown>>>({})

    watch(
        () => execution.value?.id,
        async (id) => {
            tasksWithOutputs.value = undefined
            taskOutputs.value = {}
            if (!id) return


            const data = await OutputsAPI.taskOutputsInformation({
                executionId: id,
            }, {
                validateStatus: (s: number) => s === 200 || s === 404,
            })
            
            tasksWithOutputs.value = data
                .map((task) => task.taskRunId)
                .filter((taskRunId) => taskRunId !== undefined)
            
        },
        {immediate: true},
    )

    async function loadTaskOutputs(item: ExplorerItem) {
        const id = execution.value?.id
        if (!id || !item.taskRunId || taskOutputs.value[item.taskRunId]) return

        const data = await OutputsAPI.taskRunOutputs({
            taskRunId: item.taskRunId,
            executionId: id,
        }, {
            validateStatus: (s: number) => s === 200 || s === 404,
        })

        taskOutputs.value = {...taskOutputs.value, [item.taskRunId]: data || {}} 
    }

    function isOutputTaskAFile(item: any): item is { uri: string } {
        if(!item || typeof item !== "object") {
            return false
        }
        if(!Utils.isFile(item.uri)) {
            return false
        }
        return true
    }

    const taskItems = computed<ExplorerItem[]>(() => {
        const taskRunList = execution.value?.taskRunList ?? []
        return taskRunList
            .filter((task) => tasksWithOutputs.value?.includes(task.id))
            .map((task) => ({
                label: task.taskId,
                value: taskOutputs.value[task.id],
                type: isOutputTaskAFile(taskOutputs.value[task.id]) ? "file" : "object",
                preview: "",
                expression: `outputs${formatStep(task.taskId)}`,
                taskRunId: task.id,
            }))
    })

    /* ------------------------------- Sections -------------------------------- */

    const sections = computed<ExplorerSection[]>(() => {
        const exec = execution.value
        return [
            {key: "variables", label: t("variables"), items: itemsFromRecord(exec?.variables, "vars")},
            {key: "triggers", label: t("triggers"), items: itemsFromRecord(exec?.trigger as Record<string, unknown> | undefined, "trigger")},
            {key: "inputs", label: t("inputs"), items: itemsFromRecord(exec?.inputs, "inputs")},
            {key: "flowOutputs", label: t("flow_outputs"), items: itemsFromRecord(exec?.outputs, "outputs")},
            {key: "tasksOutputs", label: t("variable_explorer.tasks_outputs"), items: taskItems.value},
        ]
    })

    /* ------------------------------- Selection ------------------------------- */

    const selectedValue = ref<unknown>(undefined)
    const selectedBase = ref<string>("")
    const expressionPath = ref<string>("")
    // Suggested expression handed to the debugger; follows the current selection.
    const expression = ref<string>("")

    const isExpandableValue = computed(
        () => selectedValue.value !== null && typeof selectedValue.value === "object",
    )

    const fileSelectedOutput = computed(() => {
        // if an input file is selected, show the contents of the file
        if(typeof selectedValue.value === "string" && Utils.isFile(selectedValue.value)){
            return selectedValue.value
        }
        if (!isExpandableValue.value) return undefined
        try {
            const fileMetadata = selectedValue.value as {uri?: string}
            if (Utils.isFile(fileMetadata.uri)) {
                return fileMetadata.uri
            }
        } catch {
            // If the value is not an object or doesn't have a `uri` field, just ignore it.
        }   
        return undefined
    })

    const rawValue = computed(() =>
        typeof selectedValue.value === "string"
            ? selectedValue.value
            : JSON.stringify(selectedValue.value, null, 2),
    )

    async function selectItem(item: ExplorerItem) {
        if (item.taskRunId) {
            await loadTaskOutputs(item)
            selectedValue.value = taskOutputs.value[item.taskRunId]
        } else {
            selectedValue.value = item.value
        }
        selectedBase.value = item.expression
        expressionPath.value = item.expression
        // if the selectedValue is in the flow Outputs section,
        // it needs the `execution.` prefix to be debuggable.
        const baseExpressionPath = sections.value.find((section) => 
            section.items.some(i => i.expression === item.expression))?.key === "flowOutputs"
            ? `execution.${item.expression}` 
            : item.expression

        // if there is only one item in the tree, select it by default to save users one click
        // specially useful for files
        if(selectedValue.value && typeof selectedValue.value === "object" && Object.keys(selectedValue.value).length === 1) {
            const onlyKey = Object.keys(selectedValue.value)[0]
            const fullExpressionPath = `${baseExpressionPath}${formatStep(onlyKey)}`
            expression.value = `{{ ${fullExpressionPath} }}`
        }else {
            expression.value = `{{ ${baseExpressionPath} }}`
        }
    }

    function onSelectPath(path: string) {
        expressionPath.value = path
        expression.value = `{{ ${path} }}`
    }

    /* --------------------------------- Viewer -------------------------------- */

    const viewMode = ref<"tree" | "raw">("tree")
    const viewModes = computed(() => [
        {label: t("variable_explorer.tree"), value: "tree"},
        {label: t("variable_explorer.raw_json"), value: "raw"},
    ])

    function copyValue() {
        navigator.clipboard?.writeText(rawValue.value)
    }

    /* --------------------------------- Layout -------------------------------- */

    const leftWidth = ref("25%")
    const rightWidth = ref("30%")
    const isMobile = useMediaQuery("(max-width: 768px)")
</script>

<style scoped lang="scss">
.variable-explorer {
    display: flex;
    width: 100%;
    height: 100%;
    min-height: 0;
    overflow: hidden;

    &__panel {
        display: flex;
        min-height: 0;
        overflow: hidden;
    }
}

:deep(.kel-splitter),
:deep(.kel-splitter-panel) {
    height: 100%;
    min-height: 0;
}

.viewer {
    display: flex;
    flex-direction: column;
    width: 100%;
    height: 100%;
    min-height: 0;
    background-color: var(--ks-bg-surface);

    &__header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-3) var(--ks-spacing-4);
        border-bottom: 1px solid var(--ks-border-default);
    }

    &__body {
        flex: 1 1 0;
        min-height: 0;
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
    }

    &__scalar {
        font-family: var(--ks-font-family-mono);
        font-size: var(--ks-font-size-sm);
        word-break: break-word;
        padding: .5rem 1rem;
    }

    .file-preview{
        padding: 1rem;
    }
}

.debug {
    width: 100%;
    height: 100%;
    min-height: 0;
    padding: var(--ks-spacing-4);
    overflow-y: auto;
}

@media (max-width: 768px) {
    :deep(.kel-splitter-bar) {
        height: 4px !important;
        width: auto !important;
    }
}
</style>
