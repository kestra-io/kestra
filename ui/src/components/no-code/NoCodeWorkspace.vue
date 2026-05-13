<template>
    <div class="no-code-workspace">
        <!-- Left: Plugin Picker -->
        <PluginPicker @addPlugin="addPlugin" />

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
                            <template v-for="v in fieldsFromSchemaRest" :key="v.fieldKey">
                                <div
                                    v-if="LIST_FIELDS.includes(v.fieldKey)"
                                    class="droppable-section"
                                    :class="{'drag-over': dragOverSection === v.fieldKey}"
                                    @dragover.prevent="onSectionDragOver(v.fieldKey)"
                                    @dragleave.self="dragOverSection = null"
                                    @drop.prevent="onSectionDrop($event, v.fieldKey)"
                                >
                                    <TaskObjectField
                                        v-bind="v"
                                        @update:model-value="(val) => onTaskUpdateField(v.fieldKey, val)"
                                    />
                                    <div class="drop-hint">
                                        <span>{{ t("no_code.workspace.drop_hint") }}</span>
                                    </div>
                                </div>
                                <Wrapper v-else :merge="shouldMerge(v.schema)">
                                    <template #tasks>
                                        <TaskObjectField
                                            v-bind="v"
                                            @update:model-value="(val) => onTaskUpdateField(v.fieldKey, val)"
                                        />
                                    </template>
                                </Wrapper>
                            </template>
                        </KsForm>
                    </div>
                </template>
            </AiCopilotWrapper>
        </div>

        <!-- Right: Task Editor Drawer -->
        <Transition name="drawer-slide">
            <TaskDrawer
                v-if="creatingTask || editingTask"
                :creatingTask
                @close="emit('closeTask')"
            />
        </Transition>
    </div>
</template>

<script setup lang="ts">
    import {computed, onActivated, provide, ref, watch} from "vue"

    import {flowYamlUtils as YAML_UTILS} from "@kestra-io/design-system"
    import {removeNullAndUndefined} from "./utils/cleanUp"

    import TaskDrawer from "./components/TaskDrawer.vue"
    import PluginPicker, {type PluginEntry, PLUGIN_DRAG_TYPE} from "./components/PluginPicker.vue"
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

    // ── Plugin add / drag-and-drop ──

    /** Append a plugin to a specific flow section. */
    function addPlugin(entry: PluginEntry, targetSection?: string) {
        const section = targetSection ?? (entry.kind === "trigger" ? "triggers" : "tasks")
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

    const dragOverSection = ref<string | null>(null)

    function onSectionDragOver(section: string) {
        dragOverSection.value = section
    }

    function onSectionDrop(event: DragEvent, section: string) {
        dragOverSection.value = null
        const raw = event.dataTransfer?.getData(PLUGIN_DRAG_TYPE)
        if (!raw) return
        try {
            const entry = JSON.parse(raw) as PluginEntry
            addPlugin(entry, section)
        } catch {
            // ignore malformed drag data
        }
    }

    // ── Scroll memory ──

    const panel = ref()
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

// ── Droppable sections ──

.droppable-section {
    position: relative;
    border-radius: 8px;
    transition: box-shadow 0.14s, border-color 0.14s;

    .drop-hint {
        display: none;
    }

    &.drag-over {
        box-shadow: 0 0 0 2px var(--ks-border-focus), 0 0 0 4px rgba(124, 58, 237, 0.09);

        .drop-hint {
            display: flex;
            align-items: center;
            justify-content: center;
            border: 1.5px dashed var(--ks-border-focus);
            border-radius: 7px;
            padding: 0.75rem;
            margin-top: 0.5rem;
            font-size: 0.75rem;
            color: var(--ks-content-link);
            background: var(--ks-background-hover);
        }
    }
}

// ── Transition ──

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
