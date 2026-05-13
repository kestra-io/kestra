<template>
    <div class="no-code-workspace">
        <!-- Left: Plugin Picker Panel -->
        <div class="plugin-panel">
            <div class="plugin-panel-header">
                <div class="plugin-panel-label">{{ t("no_code.workspace.plugins") }}</div>
                <KsInput
                    v-model="pluginSearch"
                    :placeholder="t('no_code.workspace.search_plugin')"
                    clearable
                    size="small"
                    class="plugin-search"
                >
                    <template #prefix>
                        <Magnify class="search-icon" />
                    </template>
                </KsInput>
                <div class="kind-tabs">
                    <div
                        v-for="kind in PLUGIN_KINDS"
                        :key="kind.value"
                        class="kind-tab"
                        :class="{active: kindFilter === kind.value}"
                        @click="kindFilter = kind.value"
                    >
                        <span v-if="kind.dot" class="kind-dot" :style="{background: kind.dot}" />
                        {{ kind.label }}
                    </div>
                </div>
            </div>

            <div v-if="filteredPluginCount > 0" class="result-count">
                {{ t("no_code.workspace.plugin_count", {count: filteredPluginCount}) }}
            </div>

            <div class="plugin-scroll">
                <!-- Flat search results -->
                <template v-if="pluginSearch.trim()">
                    <div
                        v-for="entry in flatFilteredPlugins"
                        :key="entry.cls"
                        class="plugin-row"
                        :title="entry.cls"
                        @click="addPlugin(entry)"
                    >
                        <KsTaskIcon :cls="entry.cls" :icons="pluginsStore.icons" onlyIcon class="plugin-task-icon" />
                        <div class="plugin-row-text">
                            <div class="plugin-row-type">{{ shortType(entry.cls) }}</div>
                            <div class="plugin-row-group">{{ entry.group }}</div>
                        </div>
                        <PlusIcon class="plugin-row-add" />
                    </div>
                    <div v-if="flatFilteredPlugins.length === 0" class="plugin-empty">
                        {{ t("no_code.workspace.no_plugins", {query: pluginSearch}) }}
                    </div>
                </template>

                <!-- Grouped view -->
                <template v-else>
                    <div
                        v-for="group in groupedFilteredPlugins"
                        :key="group.name"
                        class="plugin-group"
                    >
                        <div
                            class="plugin-group-header"
                            @click="toggleGroup(group.name)"
                        >
                            <div class="plugin-group-left">
                                <ChevronRight
                                    class="plugin-group-chevron"
                                    :class="{open: openGroups.has(group.name)}"
                                />
                                <span class="plugin-group-name">{{ group.name }}</span>
                            </div>
                            <span class="plugin-group-count">{{ group.entries.length }}</span>
                        </div>

                        <template v-if="openGroups.has(group.name)">
                            <div
                                v-for="entry in group.entries.slice(0, expandedGroups.has(group.name) ? undefined : MAX_PER_GROUP)"
                                :key="entry.cls"
                                class="plugin-row"
                                :title="entry.cls"
                                @click="addPlugin(entry)"
                            >
                                <KsTaskIcon :cls="entry.cls" :icons="pluginsStore.icons" onlyIcon class="plugin-task-icon" />
                                <div class="plugin-row-text">
                                    <div class="plugin-row-type">{{ shortType(entry.cls) }}</div>
                                </div>
                                <PlusIcon class="plugin-row-add" />
                            </div>

                            <button
                                v-if="group.entries.length > MAX_PER_GROUP"
                                class="plugin-group-more"
                                @click.stop="toggleGroupExpand(group.name)"
                            >
                                <template v-if="expandedGroups.has(group.name)">
                                    <ChevronUp class="plugin-group-more-icon" />
                                    {{ t("no_code.workspace.show_fewer") }}
                                </template>
                                <template v-else>
                                    <ChevronDown class="plugin-group-more-icon" />
                                    {{ t("no_code.workspace.show_more", {count: group.entries.length - MAX_PER_GROUP}) }}
                                </template>
                            </button>
                        </template>
                    </div>
                </template>
            </div>
        </div>

        <!-- Center: Flow Editor Canvas -->
        <div class="flow-canvas" ref="scrollContainer">
            <AiCopilotWrapper
                ref="copilotWrapper"
                sticky
                :flow="flowYaml"
                :generationType="aiGenerationTypes.FLOW"
                :namespace="namespace"
                @generated-yaml="onGeneratedYaml"
            >
                <template #default="{aiCopilotAllowed}">
                    <div class="flow-canvas-content" :class="{'flow-canvas-with-ai': aiCopilotAllowed}">
                        <KsForm labelPosition="top" class="flow-form">
                            <!-- Top meta fields: id, namespace, description, inputs -->
                            <Wrapper
                                v-for="v in fieldsFromSchemaTop"
                                :key="v.fieldKey"
                                :merge="shouldMerge(v.schema)"
                                :transparent="v.fieldKey === 'inputs'"
                            >
                                <template #tasks>
                                    <TaskObjectField
                                        v-bind="v"
                                        @update:model-value="(val) => onTaskUpdateField(v.fieldKey, val)"
                                    />
                                </template>
                            </Wrapper>

                            <hr class="section-divider">

                            <!-- Sections: tasks, triggers, errors, finally, afterExecution, pluginDefaults -->
                            <Wrapper
                                v-for="v in fieldsFromSchemaRest"
                                :key="v.fieldKey"
                                :transparent="LIST_FIELDS.includes(v.fieldKey)"
                            >
                                <template #tasks>
                                    <TaskObjectField
                                        v-bind="v"
                                        @update:model-value="(val) => onTaskUpdateField(v.fieldKey, val)"
                                    />
                                </template>
                            </Wrapper>
                        </KsForm>
                    </div>
                </template>
            </AiCopilotWrapper>
        </div>

        <!-- Right: Task Editor Drawer -->
        <Transition name="drawer-slide">
            <div v-if="creatingTask || editingTask" class="task-drawer">
                <!-- Drawer Header -->
                <div class="drawer-header">
                    <div class="drawer-header-title">
                        {{ creatingTask ? t("no_code.workspace.new_task") : t("no_code.workspace.edit_task") }}
                    </div>
                    <KsIconButton
                        :aria-label="t('close')"
                        class="drawer-close-btn"
                        @click="emit('closeTask')"
                    >
                        <CloseIcon />
                    </KsIconButton>
                </div>

                <!-- Drawer Body: three columns -->
                <div class="drawer-body">
                    <!-- Left column: Inputs & Context -->
                    <div class="drawer-col drawer-col-inputs">
                        <div class="drawer-col-head">
                            <div class="drawer-col-title">{{ t("no_code.workspace.drawer_inputs") }}</div>
                            <div class="drawer-col-sub">{{ t("no_code.workspace.drawer_inputs_hint") }}</div>
                        </div>

                        <div class="drawer-col-scroll">
                            <!-- Flow Inputs -->
                            <template v-if="flowInputs.length > 0">
                                <div class="drawer-section-label">{{ t("no_code.workspace.flow_inputs") }}</div>
                                <div
                                    v-for="input in flowInputs"
                                    :key="input.id"
                                    class="context-card"
                                    :title="`{{ inputs.${input.id} }}`"
                                >
                                    <code class="context-card-expr">{{ input.id }}</code>
                                    <KsTag v-if="input.type" size="small" disableTransitions class="context-card-type">
                                        {{ input.type }}
                                    </KsTag>
                                </div>
                            </template>

                            <!-- Execution Context variables -->
                            <div class="drawer-section-label mt-2">{{ t("no_code.workspace.execution_context") }}</div>
                            <div
                                v-for="ctxVar in EXECUTION_CONTEXT_VARS"
                                :key="ctxVar.expr"
                                class="context-card"
                                :title="ctxVar.expr"
                            >
                                <code class="context-card-expr">{{ ctxVar.label }}</code>
                                <KsTag size="small" disableTransitions class="context-card-type">{{ ctxVar.type }}</KsTag>
                            </div>
                        </div>
                    </div>

                    <!-- Center column: Properties -->
                    <div class="drawer-col drawer-col-properties">
                        <div class="drawer-col-head">
                            <div class="drawer-col-title">{{ t("no_code.workspace.drawer_properties") }}</div>
                        </div>
                        <div class="drawer-col-scroll">
                            <Task />
                        </div>
                    </div>

                    <!-- Right column: Outputs -->
                    <div class="drawer-col drawer-col-outputs">
                        <div class="drawer-col-head">
                            <div class="drawer-col-title">{{ t("no_code.workspace.drawer_outputs") }}</div>
                            <div class="drawer-col-sub">{{ t("no_code.workspace.drawer_outputs_hint") }}</div>
                        </div>
                        <div class="drawer-col-scroll">
                            <template v-if="currentTaskOutputs.length > 0">
                                <div class="drawer-section-label">{{ t("no_code.workspace.schema_outputs") }}</div>
                                <div
                                    v-for="output in currentTaskOutputs"
                                    :key="output.name"
                                    class="output-card"
                                >
                                    <div class="output-card-name">{{ output.name }}</div>
                                    <div v-if="output.type" class="output-card-type">{{ output.type }}</div>
                                </div>
                            </template>
                            <KsEmpty v-else />
                        </div>
                    </div>
                </div>
            </div>
        </Transition>
    </div>
</template>

<script setup lang="ts">
    import {computed, onActivated, provide, ref, watch} from "vue"

    import {flowYamlUtils as YAML_UTILS, KsTaskIcon} from "@kestra-io/design-system"
    import {removeNullAndUndefined} from "./utils/cleanUp"

    import Task from "./segments/Task.vue"
    import Wrapper from "./components/tasks/Wrapper.vue"
    import TaskObjectField from "./components/tasks/TaskObjectField.vue"
    import {
        BLOCK_SCHEMA_PATH_INJECTION_KEY,
        CLOSE_TASK_FUNCTION_INJECTION_KEY,
        CREATE_TASK_FUNCTION_INJECTION_KEY,
        CREATING_FLOW_INJECTION_KEY,
        CREATING_TASK_INJECTION_KEY,
        DEFAULT_NAMESPACE_INJECTION_KEY,
        EDIT_TASK_FUNCTION_INJECTION_KEY,
        EDITING_TASK_INJECTION_KEY,
        FIELDNAME_INJECTION_KEY,
        FULL_SCHEMA_INJECTION_KEY,
        FULL_SOURCE_INJECTION_KEY,
        PANEL_INJECTION_KEY,
        PARENT_PATH_INJECTION_KEY,
        POSITION_INJECTION_KEY,
        REF_PATH_INJECTION_KEY,
        ROOT_SCHEMA_INJECTION_KEY,
        SCHEMA_DEFINITIONS_INJECTION_KEY,
        UPDATE_YAML_FUNCTION_INJECTION_KEY,
    } from "./injectionKeys"
    import {useFlowFields} from "./utils/useFlowFields"
    import debounce from "lodash/debounce"
    import {NoCodeProps} from "../flows/noCodeTypes"
    import {useFlowStore} from "../../stores/flow"
    import {usePluginsStore} from "../../stores/plugins"
    import {useKeyboardSave} from "./utils/useKeyboardSave"
    import {deepEqual} from "../../utils/utils"
    import {useScrollMemory} from "../../composables/useScrollMemory"
    import {defaultNamespace} from "../../composables/useNamespaces"
    import {LIST_FIELDS} from "./components/tasks/getTaskComponent"
    import {aiGenerationTypes} from "../../utils/constants"
    import AiCopilotWrapper from "../ai/AiCopilotWrapper.vue"
    import {extractPluginElements, isPluginMatched} from "../../utils/pluginUtils"
    import Magnify from "vue-material-design-icons/Magnify.vue"
    import PlusIcon from "vue-material-design-icons/Plus.vue"
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import ChevronUp from "vue-material-design-icons/ChevronUp.vue"
    import CloseIcon from "vue-material-design-icons/Close.vue"
    import {useI18n} from "vue-i18n"

    const {t} = useI18n()

    const props = defineProps<NoCodeProps>()

    const copilotWrapper = ref<InstanceType<typeof AiCopilotWrapper>>()
    const namespace = computed(() => flowStore.flow?.namespace)

    // ── AI Copilot ──

    function onGeneratedYaml(yaml: string) {
        editorUpdate(yaml)
        copilotWrapper.value?.resetConversation()
    }

    // ── Field helpers ──

    function shouldMerge(schema: any): boolean {
        const complexObject = ["object", "array"].includes(schema?.type) || schema?.$ref || schema?.oneOf || schema?.anyOf || schema?.allOf
        return !complexObject
    }

    function onTaskUpdateField(key: string, val: any) {
        const realValue = val === null || val === undefined ? undefined :
            typeof val === "object" && !Array.isArray(val)
                ? removeNullAndUndefined(val)
                : val

        editorUpdate(YAML_UTILS.replaceBlockWithPath({
            source: flowYaml.value,
            path: key,
            newContent: YAML_UTILS.stringify(realValue),
        }))
    }

    const lastValidFlowYaml = computed<string>(
        (oldValue) => {
            try {
                YAML_UTILS.parse(flowYaml.value)
                return flowYaml.value
            } catch {
                return oldValue ?? ""
            }
        },
    )

    const {
        fieldsFromSchemaTop,
        fieldsFromSchemaRest,
    } = useFlowFields(lastValidFlowYaml)

    useKeyboardSave()

    const flowStore = useFlowStore()
    const pluginsStore = usePluginsStore()
    const flowYaml = computed<string>(() => flowStore.flowYaml ?? "")

    const validateFlow = debounce(() => {
        flowStore.validateFlow({flow: flowYaml.value})
    }, 500)

    const timeout = ref()

    const editorUpdate = (source: string) => {
        let parsedSource: any = {}
        try {
            parsedSource = YAML_UTILS.parse(source)
        } catch {
            return
        }

        if (deepEqual(parsedSource, flowStore.flowParsed)) {
            return
        }
        flowStore.flowYaml = source
        validateFlow()

        clearTimeout(timeout.value)
        timeout.value = setTimeout(() => {
            flowStore.onEdit({
                source,
                topologyVisible: true,
            })
        }, 1000)
    }

    onActivated(() => {
        pluginsStore.updateDocumentation()
    })

    watch(
        () => flowStore.flowYaml,
        (newVal, oldVal) => {
            if (newVal !== oldVal) {
                editorUpdate(newVal)
            }
        },
    )

    // ── Plugin Picker ──

    const MAX_PER_GROUP = 8
    const pluginSearch = ref("")
    const kindFilter = ref<"all" | "task" | "trigger">("all")
    const openGroups = ref(new Set<string>())
    const expandedGroups = ref(new Set<string>())

    const PLUGIN_KINDS = [
        {value: "all" as const, label: t("no_code.workspace.kind_all")},
        {value: "task" as const, label: t("no_code.workspace.kind_tasks"), dot: "var(--ks-chart-purple)"},
        {value: "trigger" as const, label: t("no_code.workspace.kind_triggers"), dot: "var(--ks-chart-yellow)"},
    ]

    interface PluginEntry {
        cls: string;
        kind: "task" | "trigger";
        group: string;
    }

    const allPluginEntries = computed<PluginEntry[]>(() => {
        if (!pluginsStore.plugins) return []
        return pluginsStore.plugins.flatMap(plugin => {
            const elements = extractPluginElements(plugin)
            return Object.entries(elements).flatMap(([kind, clsList]) => {
                const normalizedKind = kind.toLowerCase().includes("trigger") ? "trigger" : "task"
                return clsList.map(cls => ({cls, kind: normalizedKind, group: plugin.title || plugin.name}))
            })
        })
    })

    const filteredPluginEntries = computed<PluginEntry[]>(() => {
        const q = pluginSearch.value.trim().toLowerCase()
        return allPluginEntries.value.filter(entry => {
            const kindOk = kindFilter.value === "all" || entry.kind === kindFilter.value
            const queryOk = !q || entry.cls.toLowerCase().includes(q) || entry.group.toLowerCase().includes(q)
            return kindOk && queryOk
        })
    })

    const flatFilteredPlugins = computed(() => filteredPluginEntries.value)

    const groupedFilteredPlugins = computed(() => {
        const map = new Map<string, PluginEntry[]>()
        filteredPluginEntries.value.forEach(entry => {
            if (!map.has(entry.group)) map.set(entry.group, [])
            map.get(entry.group)!.push(entry)
        })
        return Array.from(map.entries()).map(([name, entries]) => ({name, entries}))
    })

    const filteredPluginCount = computed(() => filteredPluginEntries.value.length)

    function shortType(cls: string): string {
        return cls.split(".").pop() ?? cls
    }

    function toggleGroup(name: string) {
        if (openGroups.value.has(name)) {
            openGroups.value.delete(name)
        } else {
            openGroups.value.add(name)
        }
        openGroups.value = new Set(openGroups.value)
    }

    function toggleGroupExpand(name: string) {
        if (expandedGroups.value.has(name)) {
            expandedGroups.value.delete(name)
        } else {
            expandedGroups.value.add(name)
        }
        expandedGroups.value = new Set(expandedGroups.value)
    }

    function addPlugin(entry: PluginEntry) {
        const section = entry.kind === "trigger" ? "triggers" : "tasks"
        const currentCount = (() => {
            try {
                const parsed = YAML_UTILS.parse(flowYaml.value) ?? {}
                return Array.isArray(parsed[section]) ? parsed[section].length : 0
            } catch {
                return 0
            }
        })()
        emit("createTask", section, entry.cls, currentCount > 0 ? currentCount - 1 : undefined, "after")
    }

    // ── Drawer: Inputs Panel ──

    const parsedFlow = computed(() => {
        try {
            return YAML_UTILS.parse(lastValidFlowYaml.value) ?? {}
        } catch {
            return {}
        }
    })

    const flowInputs = computed<Array<{id: string; type?: string}>>(() => {
        const inputs = parsedFlow.value?.inputs
        if (!Array.isArray(inputs)) return []
        return inputs.filter(Boolean).map(i => ({id: i.id ?? "", type: i.type}))
    })

    const EXECUTION_CONTEXT_VARS = [
        {label: "execution.id", expr: "{{ execution.id }}", type: "String"},
        {label: "execution.startDate", expr: "{{ execution.startDate }}", type: "DateTime"},
        {label: "flow.id", expr: "{{ flow.id }}", type: "String"},
        {label: "flow.namespace", expr: "{{ flow.namespace }}", type: "String"},
        {label: "trigger.date", expr: "{{ trigger.date }}", type: "DateTime"},
    ]

    // ── Drawer: Outputs Panel ──

    const currentTaskOutputs = computed<Array<{name: string; type?: string}>>(() => {
        const outputs = pluginsStore.pluginAllProps?.outputs?.properties
        if (!outputs || typeof outputs !== "object") return []
        return Object.entries(outputs).map(([name, schema]: [string, any]) => ({
            name,
            type: schema?.type ?? schema?.$ref?.split("/").pop(),
        }))
    })

    // ── Panel ──

    const panel = ref()

    // ── Scroll memory ──

    const scrollContainer = ref<HTMLDivElement | null>(null)

    const flowIdentity = computed(() => {
        const ns = flowStore.flow?.namespace ?? ""
        const flowId = flowStore.flow?.id ?? ""
        return `${ns}/${flowId}`
    })

    const scrollKey = computed(() => {
        const base = `nocode-workspace:${flowIdentity.value}`
        if (!props.creatingTask && !props.editingTask) return `${base}:home`
        const action = props.creatingTask ? "create" : "edit"
        const parentPath = props.parentPath ?? ""
        const refPath = props.refPath ?? ""
        const fieldName = props.fieldName ?? ""
        return `${base}:task:${action}:parentPath:${parentPath}:refPath:${refPath}:fieldName:${fieldName}`
    })

    useScrollMemory(scrollKey, scrollContainer)

    // ── Emits ──

    const emit = defineEmits<{
        (e: "createTask", parentPath: string, blockSchemaPath: string, refPath: number | undefined, position: "after" | "before"): boolean | void;
        (e: "editTask", parentPath: string, blockSchemaPath: string, refPath: number | undefined): boolean | void;
        (e: "closeTask"): boolean | void;
    }>()

    // ── Provide ──

    provide(FULL_SOURCE_INJECTION_KEY, computed(() => lastValidFlowYaml.value))
    provide(PARENT_PATH_INJECTION_KEY, props.parentPath ?? "")
    provide(REF_PATH_INJECTION_KEY, props.refPath)
    provide(PANEL_INJECTION_KEY, panel)
    provide(POSITION_INJECTION_KEY, props.position ?? "after")
    provide(CREATING_FLOW_INJECTION_KEY, flowStore.isCreating ?? false)
    provide(DEFAULT_NAMESPACE_INJECTION_KEY, computed(() => flowStore.flow?.namespace ?? defaultNamespace() ?? "company.team"))
    provide(CREATING_TASK_INJECTION_KEY, props.creatingTask)
    provide(EDITING_TASK_INJECTION_KEY, props.editingTask)
    provide(FIELDNAME_INJECTION_KEY, props.fieldName)
    provide(BLOCK_SCHEMA_PATH_INJECTION_KEY, computed(() => props.blockSchemaPath ?? pluginsStore.flowSchema?.$ref ?? ""))
    provide(FULL_SCHEMA_INJECTION_KEY, computed(() => pluginsStore.flowSchema ?? {}))
    provide(ROOT_SCHEMA_INJECTION_KEY, computed(() => pluginsStore.flowRootSchema ?? {}))
    provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => pluginsStore.flowDefinitions ?? {}))

    provide(CLOSE_TASK_FUNCTION_INJECTION_KEY, () => {
        emit("closeTask")
    })

    provide(UPDATE_YAML_FUNCTION_INJECTION_KEY, (yaml) => {
        editorUpdate(yaml)
    })

    provide(CREATE_TASK_FUNCTION_INJECTION_KEY, (parentPath, blockSchemaPath, refPath) => {
        emit("createTask", parentPath, blockSchemaPath, refPath, "after")
    })

    provide(EDIT_TASK_FUNCTION_INJECTION_KEY, (parentPath, blockSchemaPath, refPath) => {
        emit("editTask", parentPath, blockSchemaPath, refPath)
    })
</script>

<style scoped lang="scss">
.no-code-workspace {
    display: flex;
    height: 100%;
    overflow: hidden;
    background: var(--ks-background-default);
}

// ── Left: Plugin Panel ──

.plugin-panel {
    width: 280px;
    min-width: 220px;
    flex-shrink: 0;
    display: flex;
    flex-direction: column;
    overflow: hidden;
    background: var(--ks-background-card);
    border-right: 1px solid var(--ks-border-primary);
}

.plugin-panel-header {
    padding: 0.75rem 0.875rem 0.625rem;
    border-bottom: 1px solid var(--ks-border-secondary);
    flex-shrink: 0;
}

.plugin-panel-label {
    font-size: 0.625rem;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.07em;
    color: var(--ks-content-secondary);
    margin-bottom: 0.5rem;
}

.plugin-search {
    width: 100%;
    margin-bottom: 0.5rem;

    :deep(.kel-input__wrapper) {
        background: var(--ks-background-input);
    }
}

.search-icon {
    font-size: 0.875rem;
    color: var(--ks-content-tertiary);
}

.kind-tabs {
    display: flex;
    gap: 0;
    margin-top: 0.5rem;
    background: var(--ks-background-default);
    border-radius: 7px;
    padding: 3px;
}

.kind-tab {
    flex: 1;
    padding: 0.3rem 0;
    text-align: center;
    font-size: 0.6875rem;
    font-weight: 500;
    cursor: pointer;
    border-radius: 5px;
    color: var(--ks-content-secondary);
    transition: all 0.14s;
    user-select: none;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 0.25rem;

    &:hover:not(.active) {
        color: var(--ks-content-primary);
        background: var(--ks-background-hover);
    }

    &.active {
        background: var(--ks-background-card);
        color: var(--ks-content-link);
        box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);
    }
}

.kind-dot {
    display: inline-block;
    width: 6px;
    height: 6px;
    border-radius: 50%;
    flex-shrink: 0;
}

.result-count {
    padding: 0.3rem 0.875rem 0.2rem;
    font-size: 0.625rem;
    color: var(--ks-content-tertiary);
    flex-shrink: 0;
}

.plugin-scroll {
    flex: 1;
    overflow-y: auto;
}

.plugin-group-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0.5rem 0.875rem;
    position: sticky;
    top: 0;
    z-index: 2;
    background: var(--ks-background-card);
    cursor: pointer;
    transition: background 0.1s;
    user-select: none;
    border-radius: 6px;

    &:hover {
        background: var(--ks-background-hover);
    }
}

.plugin-group-left {
    display: flex;
    align-items: center;
    gap: 0.4rem;
}

.plugin-group-chevron {
    color: var(--ks-content-tertiary);
    font-size: 0.875rem;
    transition: transform 0.15s;

    &.open {
        transform: rotate(90deg);
    }
}

.plugin-group-name {
    font-size: 0.6875rem;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.06em;
    color: var(--ks-content-link);
}

.plugin-group-count {
    font-size: 0.625rem;
    color: var(--ks-content-tertiary);
}

.plugin-row {
    display: flex;
    align-items: center;
    gap: 0.625rem;
    padding: 0.5rem 0.875rem;
    cursor: pointer;
    border-bottom: 1px solid var(--ks-border-secondary);
    transition: background 0.1s;

    &:hover {
        background: var(--ks-background-hover);

        .plugin-row-add {
            opacity: 1;
        }
    }
}

.plugin-task-icon {
    width: 28px;
    height: 28px;
    flex-shrink: 0;
}

.plugin-row-text {
    flex: 1;
    min-width: 0;
}

.plugin-row-type {
    font-size: 0.71875rem;
    color: var(--ks-content-primary);
    font-family: var(--ks-font-monospace, ui-monospace, monospace);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    line-height: 1.3;
}

.plugin-row-group {
    font-size: 0.65625rem;
    color: var(--ks-content-tertiary);
    margin-top: 1px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.plugin-row-add {
    color: var(--ks-content-tertiary);
    font-size: 0.875rem;
    flex-shrink: 0;
    opacity: 0;
    transition: opacity 0.12s;
}

.plugin-group-more {
    width: 100%;
    padding: 0.2rem 0.875rem 0.5rem;
    background: none;
    border: none;
    cursor: pointer;
    font-size: 0.6875rem;
    color: var(--ks-content-link);
    text-align: left;
    display: flex;
    align-items: center;
    gap: 0.25rem;
    transition: color 0.12s;

    &:hover {
        color: var(--ks-button-primary-background);
    }
}

.plugin-group-more-icon {
    font-size: 0.625rem;
}

.plugin-empty {
    padding: 1.75rem 1rem;
    text-align: center;
    font-size: 0.75rem;
    color: var(--ks-content-tertiary);
}

// ── Center: Flow Canvas ──

.flow-canvas {
    flex: 1;
    overflow-y: auto;
    background: var(--ks-background-default);
    min-width: 0;
}

.flow-canvas-content {
    padding: 1.25rem 1.75rem;
}

.flow-form {
    max-width: 800px;
}

.section-divider {
    margin: 1rem 0;
    border: none;
    border-top: 1px solid var(--ks-border-primary);
}

// ── Right: Task Drawer ──

.task-drawer {
    width: 860px;
    display: flex;
    flex-direction: column;
    overflow: hidden;
    background: var(--ks-background-card);
    border-left: 1px solid var(--ks-border-primary);
    flex-shrink: 0;
}

.drawer-header {
    display: flex;
    align-items: center;
    gap: 0.625rem;
    padding: 0 1.25rem;
    height: 50px;
    flex-shrink: 0;
    border-bottom: 1px solid var(--ks-border-primary);
    background: var(--ks-background-card);
}

.drawer-header-title {
    font-size: 0.9375rem;
    font-weight: 600;
    color: var(--ks-content-primary);
    flex: 1;
}

.drawer-close-btn {
    margin-left: auto;
}

.drawer-body {
    display: grid;
    grid-template-columns: 260px 1fr 260px;
    flex: 1;
    overflow: hidden;
    min-height: 0;
}

.drawer-col {
    display: flex;
    flex-direction: column;
    overflow: hidden;
    border-right: 1px solid var(--ks-border-primary);

    &:last-child {
        border-right: none;
    }
}

.drawer-col-head {
    padding: 0.75rem 1.125rem 0.5rem;
    border-bottom: 1px solid var(--ks-border-secondary);
    flex-shrink: 0;
}

.drawer-col-title {
    font-size: 0.8125rem;
    font-weight: 600;
    color: var(--ks-content-primary);
    margin-bottom: 2px;
}

.drawer-col-sub {
    font-size: 0.6875rem;
    color: var(--ks-content-secondary);
}

.drawer-col-scroll {
    flex: 1;
    overflow-y: auto;
    padding: 0.875rem 1.125rem;
}

.drawer-section-label {
    font-size: 0.625rem;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.07em;
    color: var(--ks-content-secondary);
    margin-bottom: 0.5rem;

    &.mt-2 {
        margin-top: 1rem;
    }
}

.context-card {
    display: flex;
    align-items: center;
    gap: 0.3rem;
    background: var(--ks-background-default);
    border: 1px solid var(--ks-border-secondary);
    border-radius: 6px;
    padding: 0.3rem 0.5rem;
    margin-bottom: 0.25rem;
    cursor: default;
    transition: border-color 0.1s;

    &:hover {
        border-color: var(--ks-border-focus);
    }
}

.context-card-expr {
    flex: 1;
    min-width: 0;
    font-size: 0.65625rem;
    color: var(--ks-content-link);
    font-family: var(--ks-font-monospace, ui-monospace, monospace);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.context-card-type {
    flex-shrink: 0;
}

.output-card {
    background: var(--ks-background-default);
    border: 1px solid var(--ks-border-secondary);
    border-radius: 7px;
    padding: 0.5625rem 0.6875rem;
    margin-bottom: 0.375rem;
}

.output-card-name {
    font-size: 0.75rem;
    font-weight: 500;
    color: var(--ks-content-primary);
    font-family: var(--ks-font-monospace, ui-monospace, monospace);
}

.output-card-type {
    font-size: 0.625rem;
    color: var(--ks-content-secondary);
    margin-top: 1px;
}

// ── Transitions ──

.drawer-slide-enter-active,
.drawer-slide-leave-active {
    transition: transform 0.2s cubic-bezier(0.22, 0.68, 0, 1.2), opacity 0.18s ease;
}

.drawer-slide-enter-from,
.drawer-slide-leave-to {
    transform: translateX(24px);
    opacity: 0;
}
</style>
