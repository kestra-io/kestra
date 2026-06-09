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
                            v-model="viewMode"
                            :options="viewModes"
                            size="small"
                        />
                        <KsIconButton
                            v-if="selectedValue !== undefined"
                            :aria-label="$t('copy')"
                            @click="copyValue"
                        >
                            <ContentCopy :size="16" />
                        </KsIconButton>
                    </div>

                    <KsScrollbar class="viewer__body">
                        <template v-if="selectedValue === undefined">
                            <KsEmpty :description="$t('variable_explorer.select_prompt')" />
                        </template>

                        <KsEditor
                            v-else-if="viewMode === 'raw'"
                            v-bind="editorBindings"
                            :readOnly="true"
                            :inline="true"
                            :navbar="false"
                            :options="{fullHeight: true}"
                            :modelValue="rawValue"
                            lang="json"
                        />

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
                    </KsScrollbar>
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
        KsScrollbar,
        KsEmpty,
        KsSegmented,
        KsIconButton,
        KsEditor,
    } from "@kestra-io/design-system"
    import {useClient} from "@kestra-io/kestra-sdk"

    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"

    import {useExecutionsStore} from "../../../stores/executions"
    import {useEditorBindings} from "../../../composables/useEditorBindings"
    import {apiUrl} from "override/utils/route"

    import SidebarList from "./SidebarList.vue"
    import VariableTreeView from "./VariableTreeView.vue"
    import type {ExplorerItem, ExplorerSection} from "./types"

    const {t} = useI18n({useScope: "global"})
    const editorBindings = useEditorBindings()
    const axios = useClient()

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

            const {data, status} = await axios.get(`${apiUrl()}/outputs/${id}`, {
                validateStatus: (s: number) => s === 200 || s === 404,
            })
            if (status === 200 && Array.isArray(data)) {
                tasksWithOutputs.value = data
                    .filter((task) => task.taskRunId)
                    .map((task) => task.taskRunId)
            }
        },
        {immediate: true},
    )

    async function loadTaskOutputs(item: ExplorerItem) {
        const id = execution.value?.id
        if (!id || !item.taskRunId || taskOutputs.value[item.taskRunId]) return

        const {data, status} = await axios.get(`${apiUrl()}/outputs/${id}/${item.taskRunId}`, {
            validateStatus: (s: number) => s === 200 || s === 404,
        })
        if (status === 200) {
            taskOutputs.value = {...taskOutputs.value, [item.taskRunId]: data}
        }
    }

    const taskItems = computed<ExplorerItem[]>(() => {
        const taskRunList = execution.value?.taskRunList ?? []
        return taskRunList
            .filter((task) => tasksWithOutputs.value?.includes(task.id))
            .map((task) => ({
                label: task.taskId,
                value: taskOutputs.value[task.id],
                type: "object",
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
    // const expression = ref<string>("") // re-enable with the debug panel below
    const expressionPath = ref<string>("")

    const isExpandableValue = computed(
        () => selectedValue.value !== null && typeof selectedValue.value === "object",
    )

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
        // expression.value = `{{ ${item.expression} }}`
        // clearResult()
    }

    function onSelectPath(path: string) {
        expressionPath.value = path
        // expression.value = `{{ ${path} }}`
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

    /* --------------------------------- Debug --------------------------------- */
    // Disabled along with the debug panel in the template above. Re-enable both
    // together (and the `expression` ref / VarValue import) to restore the
    // "Debug Expression" evaluation feature.
    /*
    const result = ref<string | undefined>(undefined)
    const resultIsJSON = ref(false)
    const error = ref<string | undefined>(undefined)
    const stackTrace = ref<string | undefined>(undefined)

    const isFileResult = computed(() => result.value !== undefined && Utils.isFile(result.value))

    function clearResult() {
        result.value = undefined
        error.value = undefined
        stackTrace.value = undefined
    }

    function onDebug() {
        const id = execution.value?.id
        if (!id || !expression.value) return

        clearResult()

        axios
            .post(`${apiUrl()}/executions/${id}/actions/eval`, expression.value, {
                headers: {"Content-type": "text/plain"},
            })
            .then((response) => {
                if (response.data.error) {
                    error.value = response.data.error
                    stackTrace.value = response.data.stackTrace
                    return
                }

                try {
                    result.value = JSON.stringify(JSON.parse(response.data.result), null, 2)
                    resultIsJSON.value = true
                } catch {
                    result.value = response.data.result
                    resultIsJSON.value = false
                }
            })
            .catch((err) => {
                error.value = err.message ?? "Failed to evaluate expression"
            })
    }
    */

    /* --------------------------------- Layout -------------------------------- */

    const leftWidth = ref("25%")
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
    }
}

.debug {
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-3);
    width: 100%;
    height: 100%;
    min-height: 0;
    padding: var(--ks-spacing-4);
    overflow-y: auto;

    &__button {
        align-self: stretch;
    }

    &__error {
        overflow: auto;
    }

    &__stack {
        margin-top: var(--ks-spacing-2);
        margin-bottom: 0;
        white-space: pre-wrap;
        word-break: break-word;
        font-size: var(--ks-font-size-xs);
    }
}

@media (max-width: 768px) {
    :deep(.kel-splitter-bar) {
        height: 4px !important;
        width: auto !important;
    }
}
</style>
