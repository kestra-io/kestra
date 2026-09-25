<template>
    <component
        v-if="presentation !== 'panel'"
        :is="component"
        :icon="CodeTags"
        @click="onShow"
        ref="taskEdit"
    >
        <span v-if="component !== 'KsButton' && !isHidden">{{ $t("show task source") }}</span>
        <KsDrawer
            v-if="isModalOpen"
            v-model="isModalOpen"
            :dirty="isDirty"
            :size="size"
        >
            <template #header>
                <code>{{ taskId || task?.id || $t("add task") }}</code>
            </template>
            <template #footer>
                <div v-ks-loading="isLoading">
                    <ValidationError class="me-2" link :errors="errors" />

                    <KsButton
                        :icon="ContentSave"
                        @click="saveTask"
                        v-if="canSave && !readOnly"
                        :disabled="errors && !!errors.length"
                        type="primary"
                    >
                        {{ $t("save task") }}
                    </KsButton>
                    <KsAlert
                        :closable="false"
                        class="mb-0 mt-3"
                        v-if="revision && revisions?.length !== revision"
                        type="warning"
                    >
                        <strong>{{ $t("seeing old revision", {revision: revision}) }}</strong>
                    </KsAlert>
                </div>
            </template>

            <TaskEditPanes
                :modelValue="taskYaml"
                :activeTab="activeTabs"
                :section="section"
                :readOnly="readOnly"
                :pluginMarkdown="pluginMarkdown"
                :editorPath="editorUri"
                @update:activeTab="activeTabs = $event"
                @input="onInput"
                @save="saveTask"
            />
        </KsDrawer>
    </component>

    <div
        v-else-if="isModalOpen"
        ref="panelRef"
        class="task-edit-panel"
        data-test="task-edit-panel"
        @focusin="onPanelFocusIn"
        @focusout="onPanelFocusOut"
        @dragover.prevent
        @drop="onPanelDrop"
    >
        <div v-if="!hideTabstrip" class="task-edit-tabstrip">
            <div
                class="task-edit-tab"
                draggable="true"
                data-test="task-edit-tab"
                @dragstart="onTabDragStart"
            >
                <TaskIcon class="task-edit-tab-ico" :cls="taskType" :icons="pluginsStore.icons" :loadIcon="pluginsStore.loadIcon" :onlyIcon="true" />
                <span class="task-edit-tab-id">{{ taskId || task?.id || $t("add task") }}</span>
                <KsIconButton
                    class="task-edit-tab-close"
                    :aria-label="$t('close')"
                    :tooltip="$t('close')"
                    data-test="task-edit-tab-close"
                    @click="emit('close')"
                >
                    <Close />
                </KsIconButton>
            </div>
        </div>

        <div class="task-edit-panel-body">
            <TaskEditData
                class="task-edit-col task-edit-col-inputs"
                :class="{'task-edit-col--collapsed': inputsCollapsed}"
                kind="inputs"
                :title="$t('block_editor.inputs')"
                :subtitle="$t('block_editor.inputs_sub')"
                :sections="inputSections"
                :filterable="true"
                :collapsible="true"
                :isCollapsed="inputsCollapsed"
                :stacked="isStacked"
                side="left"
                @toggle="inputsCollapsed = !inputsCollapsed"
                @chip-activate="onChipActivate"
            />

            <div class="task-edit-col task-edit-col-params">
                <div v-if="isRunnable" class="task-edit-params-toolbar">
                    <KsButton
                        size="small"
                        :icon="Play"
                        data-test="task-edit-run"
                        @click="runTask(runnableTaskId)"
                    >
                        {{ $t("playground.run_task") }}
                    </KsButton>
                </div>
                <TaskEditPanes
                    class="task-edit-panes"
                    :modelValue="taskYaml"
                    :activeTab="activeTabs"
                    :section="section"
                    :readOnly="readOnly"
                    :pluginMarkdown="null"
                    :editorPath="editorUri"
                    :hideRunButton="true"
                    @update:activeTab="activeTabs = $event"
                    @input="onInput"
                    @save="saveTask"
                />
            </div>

            <TaskEditData
                v-if="outputSections.length"
                class="task-edit-col task-edit-col-output"
                :class="{'task-edit-col--collapsed': outputCollapsed}"
                kind="output"
                :title="$t('block_editor.output')"
                :subtitle="$t('block_editor.output_sub')"
                :sections="outputSections"
                :collapsible="true"
                :isCollapsed="outputCollapsed"
                :stacked="isStacked"
                :interactive="false"
                side="right"
                @toggle="toggleOutput"
            />

        </div>

        <div v-if="errors && errors.length" v-ks-loading="isLoading" class="task-edit-panel-footer">
            <div class="task-edit-validation-status" role="status" aria-live="polite">
                <ValidationError link :errors="errors" />
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, watch, onMounted, onBeforeUnmount, onDeactivated, provide} from "vue"
    import {useI18n} from "vue-i18n"
    import {SECTIONS, KsIconButton, KsDrawer, KsMessage, copyToClipboard} from "@kestra-io/design-system"
    import TaskIcon from "../plugins/TaskIcon.vue"
    import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"
    import CodeTags from "vue-material-design-icons/CodeTags.vue"
    import ContentSave from "vue-material-design-icons/ContentSave.vue"
    import Close from "vue-material-design-icons/Close.vue"
    import Play from "vue-material-design-icons/Play.vue"
    import TaskEditPanes from "./TaskEditPanes.vue"
    import TaskEditData from "./TaskEditData.vue"
    import {canSaveFlowTemplate} from "../../utils/flowTemplate"
    import {splitValidationErrors} from "../../utils/validationErrors"
    import ValidationError from "./ValidationError.vue"
    import {usePluginsStore} from "../../stores/plugins"
    import {useAuthStore} from "override/stores/auth"
    import {useFlowStore, type FlowRevision} from "../../stores/flow"
    import {usePlaygroundRun} from "../../composables/playground/usePlaygroundRun"
    import {CHIP_DRAG_MIME, isArmableField, insertAtCaret} from "./chipInsertion"
    import {resolveDeclaredOutputProperties, hasDeclaredOutputs as computeHasDeclaredOutputs} from "./taskOutputSchema"
    import {FOCUSED_EXPRESSION_EDITOR_INJECTION_KEY} from "../no-code/injectionKeys"

    interface Props {
        component?: string;
        task?: Record<string, unknown>;
        taskRaw?: string;
        taskId?: string;
        flowId: string;
        namespace: string;
        revision?: number;
        section?: string;
        emitOnly?: boolean;
        emitTaskOnly?: boolean;
        isHidden?: boolean;
        readOnly?: boolean;
        flowSource?: string;
        size?: string;
        presentation?: "drawer" | "panel";
        hideTabstrip?: boolean;
        editorKey?: string;
    }

    const props = withDefaults(defineProps<Props>(), {
        component: "KsButton",
        task: undefined,
        taskRaw: undefined,
        taskId: undefined,
        revision: undefined,
        section: SECTIONS.TASKS,
        emitOnly: false,
        emitTaskOnly: false,
        isHidden: false,
        readOnly: false,
        flowSource: undefined,
        size: undefined,
        presentation: "drawer",
        hideTabstrip: false,
        editorKey: undefined,
    })

    const emit = defineEmits<{
        "update:task": [value: string];
        "close": [];
        "tab-drag-start": [];
        "tab-drop": [];
    }>()

    function onTabDragStart(event: DragEvent) {
        event.dataTransfer?.setData("text/plain", "dock-pane-tab")
        if (event.dataTransfer) event.dataTransfer.effectAllowed = "move"
        emit("tab-drag-start")
    }

    const pluginsStore = usePluginsStore()
    const {t} = useI18n()

    const taskYaml = ref("")
    const taskBaseline = ref("")
    const isModalOpen = ref(false)
    const isDirty = computed(() => taskYaml.value !== taskBaseline.value)
    const activeTabs = ref(props.readOnly ? "source" : "form")
    const inputsCollapsed = defineModel<boolean>("inputsCollapsed", {default: false})
    const outputCollapsed = defineModel<boolean>("outputCollapsed", {default: true})

    const panelRef = ref<HTMLElement>()
    const isStacked = ref(false)
    const panelHasFocus = ref(false)

    const ARMED_FIELD_CLASS = "task-edit-chip-insert-target"
    const armedField = ref<HTMLInputElement | HTMLTextAreaElement | null>(null)
    const focusedExpressionEditorInsert = ref<((text: string) => void) | null>(null)
    provide(FOCUSED_EXPRESSION_EDITOR_INJECTION_KEY, focusedExpressionEditorInsert)

    const onPanelFocusIn = (event: FocusEvent) => {
        panelHasFocus.value = true
        if (isArmableField(event.target)) {
            armedField.value?.classList.remove(ARMED_FIELD_CLASS)
            armedField.value = event.target
            event.target.classList.add(ARMED_FIELD_CLASS)
        }
    }
    const onPanelFocusOut = (event: FocusEvent) => {
        const next = event.relatedTarget as Node | null
        const leavingPanel = !next || !panelRef.value?.contains(next)
        if (leavingPanel) {
            panelHasFocus.value = false
        }
        if (armedField.value && event.target === armedField.value && leavingPanel) {
            armedField.value.classList.remove(ARMED_FIELD_CLASS)
            armedField.value = null
        }
    }

    function insertAndNotify(field: HTMLInputElement | HTMLTextAreaElement, expr: string) {
        insertAtCaret(field, expr)
        field.focus()
        KsMessage.success(t("block_editor.chip_inserted"))
    }

    function onChipActivate(expr: string) {
        if (armedField.value) {
            insertAndNotify(armedField.value, expr)
        } else if (focusedExpressionEditorInsert.value) {
            focusedExpressionEditorInsert.value(expr)
            KsMessage.success(t("block_editor.chip_inserted"))
        } else {
            copyToClipboard(expr)
            KsMessage.success(t("block_editor.chip_copied"))
        }
    }

    function onPanelDrop(event: DragEvent) {
        if (!event.dataTransfer?.types.includes(CHIP_DRAG_MIME)) {
            emit("tab-drop")
            return
        }

        event.preventDefault()
        const expr = event.dataTransfer.getData(CHIP_DRAG_MIME)
        const target = (event.target as HTMLElement | null)?.closest("input, textarea") ?? null
        if (expr && isArmableField(target)) {
            insertAndNotify(target, expr)
        }
    }

    watch(panelRef, (el, _previous, onCleanup) => {
        if (!el) return
        const observer = new ResizeObserver((entries) => {
            isStacked.value = (entries[0]?.contentRect.width ?? el.clientWidth) <= 760
        })
        observer.observe(el)
        onCleanup(() => observer.disconnect())
    }, {immediate: true})

    watch(isStacked, (stacked) => {
        inputsCollapsed.value = stacked
    })
    const type = ref<string>()
    const revisions = ref<FlowRevision[]>()
    const timer = ref<ReturnType<typeof setTimeout>>()
    const lastValidatedValue = ref<string | null>(null)

    const {runTask, playgroundStore} = usePlaygroundRun()

    const propTaskId = computed(() => typeof props.task?.id === "string" ? props.task.id : undefined)
    const propTaskType = computed(() => typeof props.task?.type === "string" ? props.task.type : undefined)

    const runnableTaskId = computed<string | undefined>(() =>
        props.taskId ?? propTaskId.value ?? YAML_UTILS.parse(taskYaml.value)?.id,
    )

    const isRunnable = computed(() =>
        playgroundStore.enabled
        && !props.readOnly
        && props.section?.toLowerCase() !== "triggers"
        && Boolean(runnableTaskId.value),
    )

    const taskType = computed(() => {
        try {
            return YAML_UTILS.parse(taskYaml.value)?.type ?? propTaskType.value ?? ""
        } catch {
            return propTaskType.value ?? ""
        }
    })

    const flowStore = useFlowStore()
    const localTaskError = ref<string | undefined>()
    const errors = computed(() => {
        const split = splitValidationErrors(localTaskError.value)
        return split.length === 0 ? undefined : split
    })
    const pluginMarkdown = computed(() => {
        if (pluginsStore?.plugin?.markdown && YAML_UTILS.parse(taskYaml.value)?.type) {
            return pluginsStore?.plugin.markdown
        }
        return null
    })

    function flattenTaskIds(tasks: unknown, acc: string[]) {
        if (!Array.isArray(tasks)) return
        for (const task of tasks) {
            if (task?.id) acc.push(String(task.id))
            for (const key of ["tasks", "then", "else", "errors", "finally", "defaults"]) flattenTaskIds(task?.[key], acc)
            if (task?.cases && typeof task.cases === "object") {
                for (const branch of Object.values(task.cases)) flattenTaskIds(branch, acc)
            }
        }
    }

    const currentTaskId = computed(() => String(props.taskId ?? props.task?.id ?? ""))

    const editorUri = computed(() => props.editorKey || currentTaskId.value)

    const inputSections = computed(() => {
        const flow = flowStore.flowParsed ?? {}
        const sections: {key: string; label: string; chips: {label: string; expr: string}[]}[] = []

        const inputs = Array.isArray(flow.inputs) ? flow.inputs : []
        if (inputs.length) {
            sections.push({key: "inputs", label: t("block_editor.flow_inputs"), chips: inputs.map((i: {id?: unknown; name?: unknown}) => {
                const id = String(i.id ?? i.name ?? "")
                return {label: id, expr: `{{ inputs.${id} }}`}
            })})
        }

        const ids: string[] = []
        flattenTaskIds(flow.tasks, ids)
        flattenTaskIds(flow.errors, ids)
        flattenTaskIds(flow.finally, ids)
        const upstream = [...new Set(ids)].filter(id => id && id !== currentTaskId.value)
        if (upstream.length) {
            sections.push({key: "outputs", label: t("block_editor.upstream_outputs"), chips: upstream.map(id => ({label: id, expr: `{{ outputs.${id} }}`}))})
        }

        const CONTEXT_FIELDS: Record<string, string[]> = {
            flow: ["id", "namespace", "revision", "tenantId"],
            execution: ["id", "startDate", "state", "originalId"],
            taskrun: ["id", "startDate", "attemptsCount", "parentId", "value", "iteration"],
            task: ["id", "type"],
            trigger: ["id", "date", "type"],
            item: ["value", "key", "index"],
            "item.parent": ["value", "key", "index"],
            error: ["taskId", "message", "stackTrace"],
            kestra: ["environment", "url"],
        }
        const ctx: {label: string; expr: string}[] = []
        for (const [root, fields] of Object.entries(CONTEXT_FIELDS)) {
            for (const field of fields) ctx.push({label: `${root}.${field}`, expr: `{{ ${root}.${field} }}`})
        }
        for (const root of ["labels", "envs", "globals", "parent", "parents", "item.parents"]) {
            ctx.push({label: root, expr: `{{ ${root} }}`})
        }
        ctx.push({label: "now()", expr: "{{ now() }}"})
        if (flow.variables && typeof flow.variables === "object") {
            for (const key of Object.keys(flow.variables)) ctx.push({label: `vars.${key}`, expr: `{{ vars.${key} }}`})
        }
        sections.push({key: "context", label: t("block_editor.execution_context"), chips: ctx})

        return sections
    })

    const declaredOutputProperties = computed(() => resolveDeclaredOutputProperties([
        pluginsStore.plugin?.schema?.outputs?.properties,
        pluginsStore.editorPlugin?.schema?.outputs?.properties,
    ]))

    const outputSections = computed(() => {
        const properties = declaredOutputProperties.value
        if (!properties) return []
        const names = Object.keys(properties)
        if (!names.length) return []
        const chips = names.map(name => ({
            label: name,
            type: String(properties[name]?.type ?? "") || undefined,
        }))
        return [{key: "out", label: t("block_editor.declared_outputs"), chips}]
    })

    const hasDeclaredOutputs = computed(() => computeHasDeclaredOutputs(declaredOutputProperties.value))
    const outputUserOverridden = ref(false)

    watch(hasDeclaredOutputs, (has) => {
        if (outputUserOverridden.value) return
        outputCollapsed.value = !has
    }, {immediate: true})

    function toggleOutput() {
        outputUserOverridden.value = true
        outputCollapsed.value = !outputCollapsed.value
    }

    watch(currentTaskId, () => {
        outputUserOverridden.value = false
        outputCollapsed.value = !hasDeclaredOutputs.value
    })

    const authStore = useAuthStore()

    const canSave = computed(() => {
        const user = authStore.user
        return canSaveFlowTemplate(true, user, {namespace: props.namespace}, "flow")
    })

    const isLoading = computed(() => taskYaml.value === undefined)

    const source = computed(() => {
        return props.revision
            ? revisions.value?.[props.revision - 1]?.source
            : flowStore.flow?.source
    })

    const load = async (taskId: string) => {
        await flowStore.loadFlow({
            namespace: props.namespace,
            id: props.flowId,
            revision: props.revision?.toString(),
        })
        if (props.revision) {
            if (!revisions.value?.[props.revision - 1]) {
                revisions.value = await flowStore.loadRevisions({
                    namespace: props.namespace,
                    id: props.flowId,
                    store: false,
                })
            }
        }
        return YAML_UTILS.extractBlock({
            section: props.section,
            source: source.value ?? "",
            key: taskId,
        })
    }

    const saveTask = () => {
        if (props.presentation === "panel") {
            if (timer.value) {
                clearTimeout(timer.value)
                timer.value = undefined
            }
            emit("update:task", taskYaml.value)
            taskBaseline.value = taskYaml.value
            return
        }
        emit("update:task", taskYaml.value)
        taskYaml.value = ""
        isModalOpen.value = false
    }

    const onShow = async () => {
        isModalOpen.value = true
        if (props.taskId) {
            taskYaml.value = await load(props.taskId) ?? ""
        } else if (props.taskRaw != null) {
            taskYaml.value = props.taskRaw
        } else if (props.task) {
            taskYaml.value = YAML_UTILS.stringify(props.task)
        }
        taskBaseline.value = taskYaml.value
        if (propTaskType.value) {
            pluginsStore.load({cls: propTaskType.value}).catch(() => {})
        }
        if (taskYaml.value) {
            lastValidatedValue.value = taskYaml.value
            flowStore.validateTask({task: taskYaml.value, section: props.section})
                .then((result) => { localTaskError.value = (result as {constraints?: string})?.constraints })
                .catch(() => { localTaskError.value = undefined })
        } else {
            localTaskError.value = undefined
        }
    }

    const commitEdit = () => {
        if (lastValidatedValue.value !== taskYaml.value) {
            lastValidatedValue.value = taskYaml.value
            flowStore.validateTask({
                task: taskYaml.value,
                section: props.section,
            }).then((result) => {
                localTaskError.value = (result as {constraints?: string})?.constraints
            }).catch(() => { /* leave prior errors in place on transient failure */ })
        }
        if (props.presentation === "panel") {
            let parsed: unknown
            try { parsed = YAML_UTILS.parse(taskYaml.value) } catch { parsed = undefined }
            if (!parsed || typeof parsed !== "object") return
            emit("update:task", taskYaml.value)
            taskBaseline.value = taskYaml.value
        }
    }

    const onInput = (value: string) => {
        if (timer.value) {
            clearTimeout(timer.value)
        }

        taskYaml.value = value
        timer.value = setTimeout(commitEdit, 500)
    }

    const flushPendingEdit = () => {
        if (!timer.value) return
        clearTimeout(timer.value)
        timer.value = undefined
        commitEdit()
    }

    const normalizeYaml = (yaml?: string): string | undefined => {
        if (yaml == null) return undefined
        try {
            return JSON.stringify(YAML_UTILS.parse(yaml) ?? {})
        } catch {
            return yaml
        }
    }

    watch([() => props.task, () => props.taskRaw], async ([newTask, raw]) => {
        if (!newTask && raw == null) {
            taskYaml.value = ""
            return
        }
        const incoming = raw ?? YAML_UTILS.stringify(newTask)
        const keepLocalEdit = props.presentation === "panel"
            ? panelHasFocus.value
            : normalizeYaml(incoming) === normalizeYaml(taskBaseline.value)
        if (incoming !== taskYaml.value && !keepLocalEdit) {
            taskYaml.value = incoming
            taskBaseline.value = incoming
        }
        const taskType = newTask?.type ?? YAML_UTILS.parse(incoming)?.type
        if (taskType) {
            await pluginsStore.load({cls: taskType}).catch(() => {})
        }
    }, {immediate: true})

    const typeLoadTimer = ref<ReturnType<typeof setTimeout>>()
    watch(taskYaml, () => {
        const task = YAML_UTILS.parse(taskYaml.value)
        if (task?.type && task.type !== type.value) {
            type.value = task.type
            clearTimeout(typeLoadTimer.value)
            typeLoadTimer.value = setTimeout(() => pluginsStore.load({cls: task.type}).catch(() => {}), 500)
        }
    })

    watch(isModalOpen, () => {
        if (!isModalOpen.value) {
            emit("close")
            activeTabs.value = props.readOnly ? "source" : "form"
        }
    })

    onMounted(() => {
        if (props.presentation === "panel") onShow()
    })

    onBeforeUnmount(flushPendingEdit)
    onDeactivated(flushPendingEdit)

    defineExpose({open: onShow, flushPendingEdit})
</script>

<style scoped lang="scss">
    .task-edit-panel {
        display: flex;
        flex-direction: column;
        min-height: 0;
        background: var(--ks-bg-surface);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-lg);
        overflow: hidden;
        container-type: inline-size;
    }

    .task-edit-tabstrip {
        display: flex;
        align-items: flex-end;
        gap: var(--ks-spacing-1);
        padding: var(--ks-spacing-2) var(--ks-spacing-2) 0;
        background: var(--ks-bg-base);
        border-bottom: 1px solid var(--ks-border-subtle);
    }

    .task-edit-tab {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        background: var(--ks-bg-surface);
        border: 1px solid var(--ks-border-subtle);
        border-bottom: none;
        border-radius: var(--ks-radius-base) var(--ks-radius-base) 0 0;
    }

    .task-edit-tab-ico {
        flex-shrink: 0;
        width: var(--ks-icon-size-base);
        height: var(--ks-icon-size-base);
    }

    .task-edit-tab-id {
        font-size: var(--ks-font-size-sm);
        font-weight: 600;
        font-family: var(--ks-font-family-mono);
        color: var(--ks-text-primary);
    }

    .task-edit-panel-body {
        flex: 1;
        min-height: 0;
        display: flex;
    }

    .task-edit-col {
        min-width: 0;
        min-height: 0;
        height: 100%;
    }

    .task-edit-col-inputs {
        flex: 0 0 220px;
        border-right: 1px solid var(--ks-border-subtle);
    }

    .task-edit-col-output {
        flex: 0 0 230px;
        border-left: 1px solid var(--ks-border-subtle);
    }

    .task-edit-col-inputs.task-edit-col--collapsed,
    .task-edit-col-output.task-edit-col--collapsed {
        flex: 0 0 2.5rem;
    }

    .task-edit-col-params {
        position: relative;
        flex: 1 1 0;
        display: flex;
        flex-direction: column;
    }

    @mixin task-edit-stacked {
        flex-direction: column;

        .task-edit-col {
            flex: none;
            width: 100%;
            height: auto;
            border: none;
            border-bottom: 1px solid var(--ks-border-subtle);
        }

        .task-edit-col-inputs,
        .task-edit-col-output {
            max-height: 30%;
        }

        .task-edit-col-params {
            order: -1;
            flex: 1 1 auto;
            min-height: 55%;
        }

        .task-edit-col-inputs.task-edit-col--collapsed,
        .task-edit-col-output.task-edit-col--collapsed {
            flex: none;
            width: 100%;
            max-height: none;
        }
    }

    @container (max-width: 760px) {
        .task-edit-panel-body {
            @include task-edit-stacked;
        }
    }

    .task-edit-params-toolbar {
        position: absolute;
        top: var(--ks-spacing-5);
        right: var(--ks-spacing-5);
        z-index: 1;
        display: flex;
        gap: var(--ks-spacing-2);
    }

    @container (max-width: 550px) {
        .task-edit-params-toolbar {
            position: static;
            justify-content: flex-end;
            padding: var(--ks-spacing-3) var(--ks-spacing-5) 0;
        }
    }

    .task-edit-panes {
        flex: 1;
        min-height: 0;
        padding: var(--ks-spacing-5) 0 var(--ks-spacing-6);
    }

    .task-edit-panel-footer {
        display: flex;
        align-items: center;
        justify-content: flex-start;
        gap: var(--ks-spacing-3);
        flex-shrink: 0;
        padding: var(--ks-spacing-3) var(--ks-spacing-4);
        border-top: 1px solid var(--ks-border-subtle);
        background: var(--ks-bg-surface);
    }

    .task-edit-validation-status {
        display: flex;
        align-items: center;
    }

    :global(.task-edit-chip-insert-target) {
        outline: 2px solid var(--ks-border-focus);
        outline-offset: -1px;
    }
</style>
