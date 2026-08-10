<template>
    <TaskEdit
        v-if="props.editingTask || props.creatingTask"
        ref="inlineTaskEditRef"
        class="block-editor-inline-edit"
        :task="editingTaskData"
        :taskRaw="editingTaskRaw"
        :section="editingSection"
        :flowId="flowId"
        :namespace="namespace"
        :editorKey="editingItemPath"
        :isHidden="true"
        presentation="panel"
        :hideTabstrip="true"
        data-test="block-editor-task-edit"
        @update:task="onInlineTaskEdited"
        @close="emit('closeTask')"
    />
    <FlowPropertiesEdit
        v-else-if="editingFlow"
        class="block-editor-inline-edit"
        @close="editingFlow = false"
    />
    <div
        v-else
        ref="editorEl"
        class="block-editor"
        data-test="block-editor"
        @focusin="onCanvasFocusIn"
    >
        <KsSplitter class="block-editor-split">
            <KsSplitterPanel min="18%">
                <div class="block-editor-main">
                    <div
                        class="block-editor-canvas"
                        data-test="block-editor-canvas"
                        :tabindex="focusedId ? -1 : 0"
                        role="group"
                        :aria-label="t('block_editor.canvas_aria')"
                        @focus="onCanvasEntryFocus"
                    >
                        <BlockSectionCard
                            name="flow"
                            :title="t('no_code.sections.flow')"
                            :icon="FlowIcon"
                            :actionIcon="Cog"
                            hideCount
                            :count="0"
                            :addLabel="t('block_editor.configure')"
                            addTest="block-editor-configure-flow"
                            @add="openFlowProperties"
                        >
                            <button
                                type="button"
                                class="flow-summary"
                                data-test="block-editor-flow-summary"
                                @click="openFlowProperties"
                            >
                                <span class="flow-summary-path">{{ namespace }} / {{ flowId }}</span>
                                <span v-if="flowDescription" class="flow-summary-desc">{{ flowDescription }}</span>
                                <span v-if="flowLabelEntries.length" class="flow-summary-labels">
                                    <KsTag v-for="[key, value] in flowLabelEntries" :key="key">{{ key }}: {{ value }}</KsTag>
                                </span>
                            </button>
                        </BlockSectionCard>

                        <BlockSectionLane
                            v-for="lane in lanes"
                            :key="lane.section"
                            v-bind="lane"
                            :icons="pluginsStore.icons"
                            :selectedId="activeSelectedId"
                            :focusedId="focusedId"
                            :dnd="dndFor(lane.section)"
                            @add="(e) => openTaskPicker(lane.section, e)"
                            @select="(block) => selectBlock(lane.section, block)"
                            @open-split="(block) => selectBlock(lane.section, block, true)"
                            @delete="(id) => onDelete(lane.section, id)"
                            @duplicate="(id) => onDuplicate(lane.section, id)"
                            @run="onRunTask"
                            @select-path="openNestedEdit"
                            @open-split-path="(path) => openNestedEdit(path, true)"
                            @delete-path="onDeleteAtPath"
                            @duplicate-path="onDuplicateAtPath"
                            @add-at-path="openTaskPickerAtPath"
                            @update-depends-on="onUpdateDependsOn"
                            @reorder="onNestedReorder"
                        />
                    </div>
                </div>
            </KsSplitterPanel>
        </KsSplitter>

        <BlockEditorStatusBar
            v-model:shortcutsOpen="shortcutsOpen"
            :shortcutGroups="shortcutGroups"
            :footerContext="footerContext"
            :footerHints="footerHints"
            :undoState="undoState"
            @undo="performUndo"
        />

        <BlockCommandMenu
            v-if="commandMenuOpen"
            :items="commandMenuItems"
            :contextLabel="commandMenuContextLabel"
            @close="commandMenuOpen = false"
        />
    </div>

    <BlockTaskPicker :picker="picker" />

    <TaskEditModal
        v-if="modalTarget"
        :task="modalTaskData"
        :taskRaw="modalTaskRaw"
        :section="modalSection"
        :flowId="flowId"
        :namespace="namespace"
        :editorKey="modalItemPath"
        :parentPath="modalTarget.parentPath"
        :refPath="modalTarget.refPath"
        :blockSchemaPath="modalTarget.blockSchemaPath"
        :crumbs="modalCrumbs"
        @update:task="onModalTaskEdited"
        @close="closeModal"
        @open-in-tabs="onModalOpenInTabs"
        @navigate="popModalTo"
        @select-nested="onModalSelectNested"
    />

    <FlowPropertiesModal
        v-if="flowModalOpen"
        @close="flowModalOpen = false"
    />
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import FlowIcon from "vue-material-design-icons/FileDocumentOutline.vue"
    import Cog from "vue-material-design-icons/Cog.vue"

    import {KsTag} from "@kestra-io/design-system"
    import {flowYamlUtils} from "@kestra-io/topology"

    import {useFlowStore} from "../../../stores/flow"
    import {useCoreStore} from "../../../stores/core"
    import {usePluginsStore} from "../../../stores/plugins"
    import {
        groupValidationIssuesByTask,
        isFlowableType,
        updateBlockAtPath,
        type BlockSection,
    } from "../../../utils/flowableBlockOps"
    import BlockSectionCard from "./BlockSectionCard.vue"
    import FlowPropertiesEdit from "./FlowPropertiesEdit.vue"
    import FlowPropertiesModal from "./FlowPropertiesModal.vue"
    import BlockCommandMenu, {type BlockCommandMenuItem} from "./BlockCommandMenu.vue"
    import TaskEdit from "../../flows/TaskEdit.vue"
    import TaskEditModal from "./TaskEditModal.vue"
    import BlockTaskPicker from "./BlockTaskPicker.vue"
    import BlockEditorStatusBar from "./BlockEditorStatusBar.vue"
    import {useBlockEditorProvides} from "./useBlockEditorProvides"
    import type {Crumb} from "../utils/useFieldNavigation"
    import {taskCrumbAt, useEditTarget} from "./useEditTarget"
    import {useBlockDragAndDrop} from "./useBlockDragAndDrop"
    import {useBlockOperations} from "./useBlockOperations"
    import {modalItemPathOf, useBlockSelection} from "./useBlockSelection"
    import {useBlockMutations} from "./useBlockMutations"
    import {opensInModalByDefault} from "./taskEditMode"
    import BlockSectionLane from "./BlockSectionLane.vue"
    import {buildSectionLanes} from "./blockSectionLanes"
    import {buildCommandMenuContextLabel, buildCommandMenuItems, type BlockCommandMenuContext} from "./blockCommandMenu"
    import {useBlockEditorKeyboard} from "./useBlockEditorKeyboard"
    import {
        isTaskListPath,
        laneDisplayLabelFromPath as laneDisplayLabelFor,
        parentPathFromLaneSentinel,
        sectionDisplayLabel as sectionDisplayLabelFor,
        sectionFromParentPath,
        sectionFromSentinel,
        sectionSentinelId,
    } from "./blockSections"
    import {useYamlUndo} from "./useYamlUndo"
    import {useCanvasFocus} from "./useCanvasFocus"
    import {useTaskPicker} from "./useTaskPicker"
    import {buildFooterHints, buildShortcutGroups, type FooterHint} from "./shortcutHints"
    import {BLOCK_EDITOR_KEYMAP} from "./keymap"
    import type {NoCodeProps} from "../../flows/noCodeTypes"
    import {usePlaygroundRun} from "../../../composables/playground/usePlaygroundRun"

    const {t} = useI18n()
    const flowStore = useFlowStore()
    const coreStore = useCoreStore()
    const pluginsStore = usePluginsStore()
    const {runTask: onRunTask, playgroundStore} = usePlaygroundRun()

    const props = defineProps<NoCodeProps & {
        selectedId?: string
    }>()

    const emit = defineEmits<{
        (e: "update:selectedId", id: string | undefined): void
        (e: "createTask", parentPath: string, blockSchemaPath: string, refPath: number | undefined, position: "after" | "before"): boolean | void
        (e: "editTask", parentPath: string, blockSchemaPath: string, refPath: number | undefined, split?: boolean): boolean | void
        (e: "closeTask"): boolean | void
    }>()

    const flowYaml = computed<string>(() => flowStore.flowYaml ?? "")
    const flowId = computed<string>(() => flowStore.flow?.id ?? "")
    const namespace = computed<string>(() => flowStore.flow?.namespace ?? "")

    const editingFlow = ref(false)
    const flowModalOpen = ref(false)

    function openFlowProperties() {
        if (opensInModalByDefault()) {
            flowModalOpen.value = true
        } else {
            editingFlow.value = true
        }
    }

    const validationIssuesByTask = computed<Map<string, string[]>>(() =>
        groupValidationIssuesByTask(flowStore.flowErrors, flowStore.flowParsed),
    )

    const inlineEditPanel = ref()

    // Only a lane of tasks can be filled from the task picker; every other list needs its own schema-driven form.
    function onCreateBlockInList(parentPath: string, blockSchemaPath: string, refPath: number | undefined, anchorEl?: HTMLElement) {
        if (isTaskListPath(parentPath)) {
            openTaskPickerAtPath(parentPath, refPath ?? -1, undefined, "after", anchorEl)
            return
        }
        flowModalOpen.value = false
        editingFlow.value = false
        emit("createTask", parentPath, blockSchemaPath, refPath, "after")
    }

    useBlockEditorProvides({
        props,
        flowYaml,
        validationIssuesByTask,
        inlineEditPanel,
        createTask: onCreateBlockInList,
        editTask: (parentPath, blockSchemaPath, refPath, split) => emit("editTask", parentPath, blockSchemaPath, refPath, split),
        closeTask: () => emit("closeTask"),
        updateYaml: (yaml: string) => applyYaml(yaml),
    })

    const parsedFlow = computed(() => {
        try {
            return flowYamlUtils.parse<Record<string, unknown>>(flowYaml.value)
        } catch {
            return undefined
        }
    })

    const flowDescription = computed<string | undefined>(() => {
        const description = parsedFlow.value?.description
        return typeof description === "string" ? description : undefined
    })

    const flowLabelEntries = computed<[string, string][]>(() => {
        const labels = parsedFlow.value?.labels
        if (Array.isArray(labels)) {
            return labels
                .filter((label): label is {key: string; value: unknown} => Boolean(label) && typeof label === "object" && "key" in label)
                .map((label) => [String(label.key), String(label.value ?? "")])
        }
        if (labels && typeof labels === "object") {
            return Object.entries(labels).map(([key, value]) => [key, String(value ?? "")])
        }
        return []
    })

    const editingItemPath = computed<string>(() => {
        if (!props.editingTask) return props.parentPath ?? ""
        return props.refPath !== undefined ? `${props.parentPath}[${props.refPath}]` : props.parentPath ?? ""
    })

    const {
        path: editingPath,
        data: editingTaskData,
        raw: editingTaskRaw,
    } = useEditTarget(
        flowYaml,
        editingItemPath,
        computed(() => Boolean(props.editingTask)),
        computed(() => Boolean(props.editingTask) && !props.creatingTask),
    )

    watch(editingTaskRaw, (now, before) => {
        if (props.editingTask && before !== undefined && now === undefined) {
            emit("closeTask")
        }
    })

    const editingSection = computed<BlockSection>(() => sectionFromParentPath(props.parentPath ?? ""))

    function onInlineTaskEdited(newContent: string) {
        if (!editingPath.value) return
        applyYaml(updateBlockAtPath(flowYaml.value, editingPath.value, newContent))
    }

    const modalItemPath = computed<string>(() => {
        const target = modalTarget.value
        return target ? modalItemPathOf(target) : ""
    })

    const modalCrumbs = computed<Crumb[]>(() =>
        modalStack.value.map((target) => taskCrumbAt(flowYaml.value, modalItemPathOf(target))),
    )

    const alwaysResolved = computed(() => true)

    const {
        path: modalPath,
        data: modalTaskData,
        raw: modalTaskRaw,
    } = useEditTarget(flowYaml, modalItemPath, alwaysResolved, alwaysResolved)

    const modalSection = computed<BlockSection>(() => modalTarget.value ? sectionFromParentPath(modalTarget.value.parentPath) : "tasks")

    function onModalTaskEdited(newContent: string) {
        if (!modalPath.value) return
        applyYaml(updateBlockAtPath(flowYaml.value, modalPath.value, newContent))
    }

    function onModalOpenInTabs() {
        const target = modalTarget.value
        if (!target) return
        emit("editTask", target.parentPath, target.blockSchemaPath, target.refPath, true)
        closeModal()
    }

    function onModalSelectNested(parentPath: string, blockSchemaPath: string, refPath: number | undefined, split?: boolean) {
        if (split) {
            emit("editTask", parentPath, blockSchemaPath, refPath, true)
            closeModal()
            return
        }
        pushModalTarget({parentPath, blockSchemaPath, refPath})
    }

    function isFlowable(task: Record<string, unknown>): boolean {
        return isFlowableType(String(task.type ?? ""), pluginsStore.icons)
    }

    const parsedTasks = computed<Record<string, unknown>[]>(() => {
        const tasks = parsedFlow.value?.tasks
        return Array.isArray(tasks) ? tasks : []
    })

    const parsedTriggers = computed<Record<string, unknown>[]>(() => {
        const triggers = parsedFlow.value?.triggers
        return Array.isArray(triggers) ? triggers : []
    })

    const flowLevelErrors = computed<Record<string, unknown>[]>(() => {
        const errors = parsedFlow.value?.errors
        return Array.isArray(errors) ? errors : []
    })

    const flowLevelFinally = computed<Record<string, unknown>[]>(() => {
        const fin = parsedFlow.value?.finally
        return Array.isArray(fin) ? fin : []
    })

    const flowLevelAfterExecution = computed<Record<string, unknown>[]>(() => {
        const after = parsedFlow.value?.afterExecution
        return Array.isArray(after) ? after : []
    })

    function sectionList(section: BlockSection): Record<string, unknown>[] {
        if (section === "triggers") return parsedTriggers.value
        if (section === "errors") return flowLevelErrors.value
        if (section === "finally") return flowLevelFinally.value
        if (section === "afterExecution") return flowLevelAfterExecution.value
        return parsedTasks.value
    }

    const sectionDisplayLabel = (section: BlockSection) => sectionDisplayLabelFor(t, section)

    const laneDisplayLabelFromPath = (parentPath: string) => laneDisplayLabelFor(t, parentPath)

    const editorEl = ref<HTMLElement>()

    const inlineTaskEditRef = ref<InstanceType<typeof TaskEdit>>()
    async function saveFlowWithPendingEdits() {
        inlineTaskEditRef.value?.flushPendingEdit()
        const outcome = await flowStore.save?.()
        if (outcome === "blocked") {
            coreStore.message = {
                variant: "error",
                title: t("block_editor.save_blocked.title"),
                message: flowStore.flowErrors?.join("\n") ?? t("block_editor.save_blocked.message"),
            }
        }
    }
    const focus = useCanvasFocus(editorEl, sectionList)
    const {
        focusedId,
        focusCanvasCard,
        onCanvasFocusIn,
        onCanvasEntryFocus,
        moveFocus,
        stepInto,
        stepOut,
        openFocused,
        actionInFocused,
        focusedBlockPath,
        focusedBlockDisplayName,
    } = focus
    const shortcutsOpen = ref(false)
    const commandMenuOpen = ref(false)
    const flowSchemaRoot = computed(() => pluginsStore.flowSchema?.$ref ?? "")

    const {
        activeSelectedId,
        activeSelectedPath,
        modalStack,
        modalTarget,
        selectBlock,
        openNestedEdit,
        deselectIfCurrent,
        pushModalTarget,
        popModalTo,
        closeModal,
    } = useBlockSelection({
        selectedId: computed(() => props.selectedId),
        editorEl,
        flowYaml,
        flowSchemaRoot,
        sectionList,
        onSelectedIdChange: (id) => emit("update:selectedId", id),
        onEditTask: (parentPath, blockSchemaPath, refPath, split) => emit("editTask", parentPath, blockSchemaPath, refPath, split),
        onCloseTask: () => emit("closeTask"),
    })

    const {undoState, applyYaml, deleteWithUndo, performUndo} = useYamlUndo(
        flowStore,
        (name: string) => t("block_editor.block_deleted", {name}),
    )

    const {
        deleteInSection: onDelete,
        deleteAtPath: onDeleteAtPath,
        duplicateInSection: onDuplicate,
        duplicateAtPath: onDuplicateAtPath,
        updateDependsOn: onUpdateDependsOn,
    } = useBlockMutations({flowYaml, applyYaml, deleteWithUndo, deselectIfCurrent})

    const picker = useTaskPicker({
        pluginsStore,
        editorEl,
        focusedId,
        focusedAnchor: focus.focusedAnchor,
        focusedBlockPath,
        focusCanvasCard,
        sectionList,
        sectionDisplayLabel,
        laneDisplayLabel: laneDisplayLabelFromPath,
        flowYaml,
        applyYaml,
    })

    const {
        taskPickerVisible,
        openTaskPicker,
        openTaskPickerAtPath,
        openTaskPickerForSection,
        openTaskPickerRelativeToFocused,
    } = picker

    function clearSelectionIfPathStale(parentPath: string, from: number, to: number) {
        const path = activeSelectedPath.value
        const id = activeSelectedId.value
        if (!path || !id) return
        const escaped = parentPath.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")
        const match = path.match(new RegExp(`^${escaped}\\[(\\d+)\\]`))
        if (!match) return
        const movedIndex = parseInt(match[1], 10)
        const lo = Math.min(from, to)
        const hi = Math.max(from, to)
        if (movedIndex >= lo && movedIndex <= hi) deselectIfCurrent(id)
    }

    const {dndFor, reorder: onNestedReorder} = useBlockDragAndDrop(flowYaml, applyYaml, clearSelectionIfPathStale)

    const lanes = computed(() => buildSectionLanes(t, {
        triggers: parsedTriggers.value,
        tasks: parsedTasks.value,
        errors: flowLevelErrors.value,
        finally: flowLevelFinally.value,
        afterExecution: flowLevelAfterExecution.value,
    }, playgroundStore.enabled))

    function openFocusedSplit() {
        const path = focusedBlockPath()
        if (path) openNestedEdit(path, true)
    }

    function addAfterFocused() {
        openTaskPickerRelativeToFocused("after")
    }

    function addBeforeFocused() {
        openTaskPickerRelativeToFocused("before")
    }

    function openCommandMenu() {
        picker.ensurePluginData()
        commandMenuOpen.value = true
    }

    function isAnyOverlayOpen(): boolean {
        return shortcutsOpen.value || taskPickerVisible.value || commandMenuOpen.value
            || confirmDialogOpen.value || flowModalOpen.value
    }

    // The dialogs delegate Escape here (closeOnPressEscape is off) so one press only dismisses the topmost layer.
    function closeTopOverlay(): boolean {
        if (commandMenuOpen.value) {
            commandMenuOpen.value = false
            return true
        }
        if (shortcutsOpen.value) {
            shortcutsOpen.value = false
            return true
        }
        if (taskPickerVisible.value) {
            taskPickerVisible.value = false
            return true
        }
        if (flowModalOpen.value) {
            flowModalOpen.value = false
            return true
        }
        if (modalTarget.value) {
            closeModal()
            return true
        }
        return false
    }

    function dispatchBlockEditorAction(id: string, event: KeyboardEvent) {
        if (id === "save") {
            saveFlowWithPendingEdits()
            return
        }
        if (id === "undo") {
            performUndo()
            return
        }
        if (id === "command-menu") {
            openCommandMenu()
            return
        }
        if (id === "clear") {
            if (closeTopOverlay()) return
            if (isConfirmDialogHoldingEscape()) return
            return false
        }
        if (id === "help") {
            shortcutsOpen.value = !shortcutsOpen.value
            return
        }
        if (isAnyOverlayOpen()) return

        if (id === "quick-insert") {
            openCommandMenu()
        } else if (id === "move") {
            moveFocus(event.key === "ArrowDown" || event.key === "j" ? 1 : -1)
        } else if (id === "step-into") {
            stepInto()
        } else if (id === "step-out") {
            stepOut()
        } else if (id === "reorder") {
            const direction = event.key === "ArrowDown" ? "down" : "up"
            if (focusedId.value) {
                moveFocused(direction)
            } else if (activeSelectedId.value) {
                moveSelected(direction)
            }
        } else if (id === "open") {
            const target = event.target as HTMLElement | null
            if (target?.closest("button, a, [role='button']") && !target.closest("[data-block-id]")) return false
            if (focusedId.value) openFocused()
        } else if (id === "open-split") {
            if (focusedId.value) openFocusedSplit()
        } else if (id === "duplicate") {
            if (focusedId.value) {
                actionInFocused("[data-test='block-card-duplicate']")
            } else if (activeSelectedId.value) {
                duplicateSelected()
            }
        } else if (id === "delete") {
            if (focusedId.value) {
                requestDeleteFocused()
            } else if (activeSelectedId.value) {
                requestDeleteSelected()
            }
        } else if (id === "insert-after") {
            addAfterFocused()
        } else if (id === "insert-before") {
            addBeforeFocused()
        }
    }

    useBlockEditorKeyboard({
        keymap: BLOCK_EDITOR_KEYMAP,
        dispatch: dispatchBlockEditorAction,
        isOverlayOpen: isAnyOverlayOpen,
    })

    const {
        confirmDialogOpen,
        isConfirmDialogHoldingEscape,
        requestDeleteFocused,
        requestDeleteSelected,
        duplicateSelected,
        moveFocused,
        moveSelected,
    } = useBlockOperations({
        t,
        flowYaml,
        applyYaml,
        focus,
        selectedId: activeSelectedId,
        selectedPath: activeSelectedPath,
        sectionList,
        isFlowable,
        deleteInSection: onDelete,
        deleteAtPath: onDeleteAtPath,
        duplicateInSection: onDuplicate,
        duplicateAtPath: onDuplicateAtPath,
    })

    const shortcutGroups = computed(buildShortcutGroups)

    const footerContext = computed(() => {
        if (commandMenuOpen.value) return t("block_editor.footer.command_menu")
        if (taskPickerVisible.value) return t("block_editor.footer.inserting")
        const sentinelSection = sectionFromSentinel(focusedId.value)
        if (sentinelSection) return t("block_editor.footer.selected", {name: sectionDisplayLabel(sentinelSection)})
        const laneParentPath = parentPathFromLaneSentinel(focusedId.value)
        if (laneParentPath) return t("block_editor.footer.selected", {name: laneDisplayLabelFromPath(laneParentPath)})
        if (focusedId.value) return t("block_editor.footer.selected", {name: focusedBlockDisplayName()})
        return t("block_editor.footer.canvas")
    })

    const footerHints = computed<FooterHint[]>(() => buildFooterHints({
        overlayOpen: taskPickerVisible.value || commandMenuOpen.value,
        realBlockFocused: Boolean(focusedId.value)
            && !sectionFromSentinel(focusedId.value)
            && !parentPathFromLaneSentinel(focusedId.value),
    }))

    const commandMenuContext = computed<BlockCommandMenuContext>(() => ({
        t,
        focusedId: focusedId.value,
        focusedBlockDisplayName,
        sectionDisplayLabel,
        laneDisplayLabelFromPath,
        close: () => (commandMenuOpen.value = false),
        addAfterFocused,
        addBeforeFocused,
        insertInSection: openTaskPickerForSection,
        openFocused,
        duplicateFocused: () => actionInFocused("[data-test='block-card-duplicate']"),
        deleteFocused: requestDeleteFocused,
        goToSection: (section) => {
            const list = sectionList(section)
            focusCanvasCard(list.length ? String(list[0].id ?? 0) : sectionSentinelId(section))
        },
        saveFlow: saveFlowWithPendingEdits,
        taskEntries: picker.focusedContextEntries.value,
        insertTaskType: picker.insertTaskInFocusedContext,
    }))

    const commandMenuContextLabel = computed(() => buildCommandMenuContextLabel(commandMenuContext.value))

    const commandMenuItems = computed<BlockCommandMenuItem[]>(() => buildCommandMenuItems(commandMenuContext.value))
</script>

<style scoped lang="scss">
    .block-editor {
        position: relative;
        height: 100%;
        overflow: hidden;
        background: var(--ks-bg-base);
    }

    .block-editor-split {
        height: 100%;
    }

    .block-editor-main {
        height: 100%;
        overflow-y: auto;
        padding: var(--ks-spacing-6) var(--ks-spacing-4) calc(2.25rem + var(--ks-spacing-6));
    }

    .block-editor-inline-edit {
        height: 100%;
        min-width: 0;
        min-height: 0;
    }

    .flow-summary {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: var(--ks-spacing-2);
        width: 100%;
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        text-align: left;
        background: transparent;
        border: 1px solid var(--ks-border-subtle);
        border-radius: var(--ks-radius-base);
        cursor: pointer;
        transition: background-color 0.12s, border-color 0.12s;

        &:hover {
            background: var(--ks-bg-hover);
            border-color: var(--ks-border-default);
        }

        &:focus-visible {
            outline: 2px solid var(--ks-border-focus);
            outline-offset: 1px;
        }
    }

    .flow-summary-path {
        font-size: var(--ks-font-size-sm);
        font-weight: 600;
        color: var(--ks-text-primary);
    }

    .flow-summary-desc {
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-secondary);
        overflow: hidden;
        text-overflow: ellipsis;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
    }

    .flow-summary-labels {
        display: flex;
        flex-wrap: wrap;
        gap: var(--ks-spacing-1);
    }

    .block-editor-canvas {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-4);
        max-width: 880px;
        margin: 0 auto;
    }

</style>
