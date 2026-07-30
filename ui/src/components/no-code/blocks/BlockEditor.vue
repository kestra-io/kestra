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

                        <BlockSectionCard
                            name="triggers"
                            :title="t('no_code.sections.triggers')"
                            :icon="TriggerIcon"
                            :count="parsedTriggers.length"
                            :addLabel="t('block_editor.add_trigger')"
                            @add="(e) => openTaskPicker('triggers', e)"
                        >
                            <div class="block-section-list" data-test="block-editor-trigger-list">
                                <template v-for="(trigger, index) in parsedTriggers" :key="resolveBlockDomId(parsedTriggers, index)">
                                    <BlockCard
                                        :block="trigger"
                                        :selected="activeSelectedId === String(trigger.id)"
                                        :focused="focusedId === resolveBlockDomId(parsedTriggers, index)"
                                        :draggable="true"
                                        :dragOver="triggerDragOverIndex === index"
                                        :icons="pluginsStore.icons"
                                        :data-block-id="resolveBlockDomId(parsedTriggers, index)"
                                        @select="selectBlock('triggers', trigger)"
                                        @delete="onDelete('triggers', trigger.id)"
                                        @duplicate="onDuplicate('triggers', trigger.id)"
                                        @open-split="selectBlock('triggers', trigger, true)"
                                        @drag-start="handleTriggerDragStart($event, index)"
                                        @drag-over="handleTriggerDragOver($event, index)"
                                        @drop="handleTriggerDrop($event, index)"
                                        @drag-end="handleTriggerDragEnd"
                                    />
                                </template>
                                <BlockEmptyDrop
                                    v-if="parsedTriggers.length === 0"
                                    variant="empty"
                                    :label="t('block_editor.trigger_noun')"
                                    :data-block-id="sectionSentinelId('triggers')"
                                    :class="{'block-kbd-focused': focusedId === sectionSentinelId('triggers')}"
                                    :tabindex="focusedId === sectionSentinelId('triggers') ? 0 : -1"
                                    :aria-selected="focusedId === sectionSentinelId('triggers')"
                                    @add="(e) => openTaskPicker('triggers', e)"
                                />
                                <BlockEmptyDrop
                                    v-else
                                    variant="inline"
                                    tabindex="-1"
                                    :label="t('block_editor.trigger_noun')"
                                    @add="(e) => openTaskPicker('triggers', e)"
                                />
                            </div>
                        </BlockSectionCard>

                        <BlockSectionCard
                            name="tasks"
                            :title="t('no_code.sections.tasks')"
                            :icon="TasksIcon"
                            :count="parsedTasks.length"
                            :addLabel="t('block_editor.add_task')"
                            addTest="block-editor-add-task"
                            @add="(e) => openTaskPicker('tasks', e)"
                        >
                            <div
                                class="block-section-list"
                                data-test="block-editor-task-list"
                                @dragend="handleTaskDragEnd"
                            >
                                <template v-for="(task, index) in parsedTasks" :key="resolveBlockDomId(parsedTasks, index)">
                                    <FlowableClusterCard
                                        v-if="isFlowable(task)"
                                        :block="task"
                                        :path="`tasks[${index}]`"
                                        :icons="pluginsStore.icons"
                                        :selectedId="activeSelectedId"
                                        :focusedId="focusedId"
                                        :domId="resolveBlockDomId(parsedTasks, index)"
                                        :depth="0"
                                        :playgroundEnabled="playgroundStore.enabled"
                                        :data-block-id="resolveBlockDomId(parsedTasks, index)"
                                        data-test="block-card"
                                        @select="openNestedEdit"
                                        @open-split="(p) => openNestedEdit(p, true)"
                                        @delete="onDeleteAtPath"
                                        @duplicate="onDuplicateAtPath"
                                        @run="onRunTask"
                                        @add-at-path="openTaskPickerAtPath"
                                        @update-depends-on="onUpdateDependsOn"
                                        @reorder="onNestedReorder"
                                        @dragover.prevent="handleTaskDragOver($event, index)"
                                        @drop.prevent="handleTaskDrop($event, index)"
                                    />
                                    <BlockCard
                                        v-else
                                        :block="task"
                                        :selected="activeSelectedId === String(task.id)"
                                        :focused="focusedId === resolveBlockDomId(parsedTasks, index)"
                                        :draggable="true"
                                        :dragOver="taskDragOverIndex === index"
                                        :runnable="playgroundStore.enabled"
                                        :icons="pluginsStore.icons"
                                        :data-block-id="resolveBlockDomId(parsedTasks, index)"
                                        @select="selectBlock('tasks', task)"
                                        @delete="onDelete('tasks', task.id)"
                                        @duplicate="onDuplicate('tasks', task.id)"
                                        @open-split="selectBlock('tasks', task, true)"
                                        @run="onRunTask(String(task.id))"
                                        @drag-start="handleTaskDragStart($event, index)"
                                        @drag-over="handleTaskDragOver($event, index)"
                                        @drop="handleTaskDrop($event, index)"
                                        @drag-end="handleTaskDragEnd"
                                    />
                                </template>

                                <BlockEmptyDrop
                                    v-if="parsedTasks.length === 0"
                                    variant="empty"
                                    dataTest="block-editor-tasks-end"
                                    :label="t('block_editor.task_noun')"
                                    :hint="t('block_editor.empty_add_hint')"
                                    :data-block-id="sectionSentinelId('tasks')"
                                    :class="{'block-kbd-focused': focusedId === sectionSentinelId('tasks')}"
                                    :tabindex="focusedId === sectionSentinelId('tasks') ? 0 : -1"
                                    :aria-selected="focusedId === sectionSentinelId('tasks')"
                                    @add="(e) => openTaskPicker('tasks', e)"
                                />
                                <BlockEmptyDrop
                                    v-else
                                    variant="inline"
                                    dataTest="block-editor-tasks-end"
                                    tabindex="-1"
                                    :label="t('block_editor.task_noun')"
                                    :hint="t('block_editor.empty_add_hint')"
                                    @add="(e) => openTaskPicker('tasks', e)"
                                />
                            </div>
                        </BlockSectionCard>

                        <BlockSectionCard
                            name="errors"
                            :title="t('block_editor.lane_errors')"
                            :icon="ErrorIcon"
                            :count="flowLevelErrors.length"
                            :addLabel="t('block_editor.add_task')"
                            tone="error"
                            @add="(e) => openTaskPicker('errors', e)"
                        >
                            <div class="block-section-list">
                                <template v-for="(task, index) in flowLevelErrors" :key="resolveBlockDomId(flowLevelErrors, index)">
                                    <FlowableClusterCard
                                        v-if="isFlowable(task)"
                                        :block="task"
                                        :path="`errors[${index}]`"
                                        :icons="pluginsStore.icons"
                                        :selectedId="activeSelectedId"
                                        :focusedId="focusedId"
                                        :domId="resolveBlockDomId(flowLevelErrors, index)"
                                        :depth="0"
                                        :playgroundEnabled="playgroundStore.enabled"
                                        :data-block-id="resolveBlockDomId(flowLevelErrors, index)"
                                        data-test="block-card"
                                        @select="openNestedEdit"
                                        @open-split="(p) => openNestedEdit(p, true)"
                                        @delete="onDeleteAtPath"
                                        @duplicate="onDuplicateAtPath"
                                        @run="onRunTask"
                                        @add-at-path="openTaskPickerAtPath"
                                        @update-depends-on="onUpdateDependsOn"
                                        @reorder="onNestedReorder"
                                    />
                                    <BlockCard
                                        v-else
                                        :block="task"
                                        :selected="activeSelectedId === String(task.id)"
                                        :focused="focusedId === resolveBlockDomId(flowLevelErrors, index)"
                                        :icons="pluginsStore.icons"
                                        :data-block-id="resolveBlockDomId(flowLevelErrors, index)"
                                        :runnable="playgroundStore.enabled"
                                        :draggable="true"
                                        :dragOver="errorsDragOverIndex === index"
                                        @select="selectBlock('errors', task)"
                                        @delete="onDelete('errors', task.id)"
                                        @duplicate="onDuplicate('errors', task.id)"
                                        @open-split="selectBlock('errors', task, true)"
                                        @run="onRunTask(String(task.id))"
                                        @drag-start="errorsDragStart($event, index)"
                                        @drag-over="errorsDragOver($event, index)"
                                        @drop="errorsDrop($event, index)"
                                        @drag-end="errorsDragEnd"
                                    />
                                </template>
                                <BlockEmptyDrop
                                    v-if="flowLevelErrors.length === 0"
                                    variant="empty"
                                    :label="t('block_editor.task_noun')"
                                    :data-block-id="sectionSentinelId('errors')"
                                    :class="{'block-kbd-focused': focusedId === sectionSentinelId('errors')}"
                                    :tabindex="focusedId === sectionSentinelId('errors') ? 0 : -1"
                                    :aria-selected="focusedId === sectionSentinelId('errors')"
                                    @add="(e) => openTaskPicker('errors', e)"
                                />
                                <BlockEmptyDrop
                                    v-else
                                    variant="inline"
                                    tabindex="-1"
                                    :label="t('block_editor.task_noun')"
                                    @add="(e) => openTaskPicker('errors', e)"
                                />
                            </div>
                        </BlockSectionCard>

                        <BlockSectionCard
                            name="finally"
                            :title="t('block_editor.lane_finally')"
                            :icon="FinallyIcon"
                            :count="flowLevelFinally.length"
                            :addLabel="t('block_editor.add_task')"
                            tone="warning"
                            @add="(e) => openTaskPicker('finally', e)"
                        >
                            <div class="block-section-list">
                                <template v-for="(task, index) in flowLevelFinally" :key="resolveBlockDomId(flowLevelFinally, index)">
                                    <FlowableClusterCard
                                        v-if="isFlowable(task)"
                                        :block="task"
                                        :path="`finally[${index}]`"
                                        :icons="pluginsStore.icons"
                                        :selectedId="activeSelectedId"
                                        :focusedId="focusedId"
                                        :domId="resolveBlockDomId(flowLevelFinally, index)"
                                        :depth="0"
                                        :playgroundEnabled="playgroundStore.enabled"
                                        :data-block-id="resolveBlockDomId(flowLevelFinally, index)"
                                        data-test="block-card"
                                        @select="openNestedEdit"
                                        @open-split="(p) => openNestedEdit(p, true)"
                                        @delete="onDeleteAtPath"
                                        @duplicate="onDuplicateAtPath"
                                        @run="onRunTask"
                                        @add-at-path="openTaskPickerAtPath"
                                        @update-depends-on="onUpdateDependsOn"
                                        @reorder="onNestedReorder"
                                    />
                                    <BlockCard
                                        v-else
                                        :block="task"
                                        :selected="activeSelectedId === String(task.id)"
                                        :focused="focusedId === resolveBlockDomId(flowLevelFinally, index)"
                                        :icons="pluginsStore.icons"
                                        :data-block-id="resolveBlockDomId(flowLevelFinally, index)"
                                        :runnable="playgroundStore.enabled"
                                        :draggable="true"
                                        :dragOver="finallyDragOverIndex === index"
                                        @select="selectBlock('finally', task)"
                                        @delete="onDelete('finally', task.id)"
                                        @duplicate="onDuplicate('finally', task.id)"
                                        @open-split="selectBlock('finally', task, true)"
                                        @run="onRunTask(String(task.id))"
                                        @drag-start="finallyDragStart($event, index)"
                                        @drag-over="finallyDragOver($event, index)"
                                        @drop="finallyDrop($event, index)"
                                        @drag-end="finallyDragEnd"
                                    />
                                </template>
                                <BlockEmptyDrop
                                    v-if="flowLevelFinally.length === 0"
                                    variant="empty"
                                    :label="t('block_editor.task_noun')"
                                    :data-block-id="sectionSentinelId('finally')"
                                    :class="{'block-kbd-focused': focusedId === sectionSentinelId('finally')}"
                                    :tabindex="focusedId === sectionSentinelId('finally') ? 0 : -1"
                                    :aria-selected="focusedId === sectionSentinelId('finally')"
                                    @add="(e) => openTaskPicker('finally', e)"
                                />
                                <BlockEmptyDrop
                                    v-else
                                    variant="inline"
                                    tabindex="-1"
                                    :label="t('block_editor.task_noun')"
                                    @add="(e) => openTaskPicker('finally', e)"
                                />
                            </div>
                        </BlockSectionCard>

                        <BlockSectionCard
                            name="afterExecution"
                            :title="t('no_code.sections.afterExecution')"
                            :icon="AfterExecutionIcon"
                            :count="flowLevelAfterExecution.length"
                            :addLabel="t('block_editor.add_task')"
                            @add="(e) => openTaskPicker('afterExecution', e)"
                        >
                            <div class="block-section-list">
                                <template v-for="(task, index) in flowLevelAfterExecution" :key="resolveBlockDomId(flowLevelAfterExecution, index)">
                                    <FlowableClusterCard
                                        v-if="isFlowable(task)"
                                        :block="task"
                                        :path="`afterExecution[${index}]`"
                                        :icons="pluginsStore.icons"
                                        :selectedId="activeSelectedId"
                                        :focusedId="focusedId"
                                        :domId="resolveBlockDomId(flowLevelAfterExecution, index)"
                                        :depth="0"
                                        :playgroundEnabled="playgroundStore.enabled"
                                        :data-block-id="resolveBlockDomId(flowLevelAfterExecution, index)"
                                        data-test="block-card"
                                        @select="openNestedEdit"
                                        @open-split="(p) => openNestedEdit(p, true)"
                                        @delete="onDeleteAtPath"
                                        @duplicate="onDuplicateAtPath"
                                        @run="onRunTask"
                                        @add-at-path="openTaskPickerAtPath"
                                        @update-depends-on="onUpdateDependsOn"
                                        @reorder="onNestedReorder"
                                    />
                                    <BlockCard
                                        v-else
                                        :block="task"
                                        :selected="activeSelectedId === String(task.id)"
                                        :focused="focusedId === resolveBlockDomId(flowLevelAfterExecution, index)"
                                        :icons="pluginsStore.icons"
                                        :data-block-id="resolveBlockDomId(flowLevelAfterExecution, index)"
                                        :runnable="playgroundStore.enabled"
                                        :draggable="true"
                                        :dragOver="afterExecutionDragOverIndex === index"
                                        @select="selectBlock('afterExecution', task)"
                                        @delete="onDelete('afterExecution', task.id)"
                                        @duplicate="onDuplicate('afterExecution', task.id)"
                                        @open-split="selectBlock('afterExecution', task, true)"
                                        @run="onRunTask(String(task.id))"
                                        @drag-start="afterExecutionDragStart($event, index)"
                                        @drag-over="afterExecutionDragOver($event, index)"
                                        @drop="afterExecutionDrop($event, index)"
                                        @drag-end="afterExecutionDragEnd"
                                    />
                                </template>
                                <BlockEmptyDrop
                                    v-if="flowLevelAfterExecution.length === 0"
                                    variant="empty"
                                    :label="t('block_editor.task_noun')"
                                    :data-block-id="sectionSentinelId('afterExecution')"
                                    :class="{'block-kbd-focused': focusedId === sectionSentinelId('afterExecution')}"
                                    :tabindex="focusedId === sectionSentinelId('afterExecution') ? 0 : -1"
                                    :aria-selected="focusedId === sectionSentinelId('afterExecution')"
                                    @add="(e) => openTaskPicker('afterExecution', e)"
                                />
                                <BlockEmptyDrop
                                    v-else
                                    variant="inline"
                                    tabindex="-1"
                                    :label="t('block_editor.task_noun')"
                                    @add="(e) => openTaskPicker('afterExecution', e)"
                                />
                            </div>
                        </BlockSectionCard>
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
        @update:task="onModalTaskEdited"
        @close="modalTarget = undefined"
        @open-in-tabs="onModalOpenInTabs"
    />

    <FlowPropertiesModal
        v-if="flowModalOpen"
        @close="flowModalOpen = false"
    />
</template>

<script setup lang="ts">
    import {computed, nextTick, ref, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import FlowIcon from "vue-material-design-icons/FileDocumentOutline.vue"
    import Cog from "vue-material-design-icons/Cog.vue"
    import TriggerIcon from "vue-material-design-icons/LightningBoltOutline.vue"
    import TasksIcon from "vue-material-design-icons/FormatListBulleted.vue"
    import ErrorIcon from "vue-material-design-icons/AlertCircleOutline.vue"
    import FinallyIcon from "vue-material-design-icons/FlagOutline.vue"
    import AfterExecutionIcon from "vue-material-design-icons/FlagCheckered.vue"

    import {KsMessageBox, KsTag} from "@kestra-io/design-system"
    import {flowYamlUtils} from "@kestra-io/topology"

    import {useFlowStore} from "../../../stores/flow"
    import {useCoreStore} from "../../../stores/core"
    import {usePluginsStore} from "../../../stores/plugins"
    import {
        deleteBlock,
        deleteBlockAtPath,
        displayTaskOf,
        duplicateBlock,
        duplicateBlockAtPath,
        groupValidationIssuesByTask,
        isFlowableType,
        moveBlockAtPath,
        reorderAtPath,
        resolveBlockDomId,
        updateBlockAtPath,
        type BlockSection,
    } from "../../../utils/flowableBlockOps"
    import {useDragAndDrop} from "../../../composables/useDragAndDrop"
    import BlockCard from "./BlockCard.vue"
    import BlockSectionCard from "./BlockSectionCard.vue"
    import FlowPropertiesEdit from "./FlowPropertiesEdit.vue"
    import FlowPropertiesModal from "./FlowPropertiesModal.vue"
    import BlockEmptyDrop from "./BlockEmptyDrop.vue"
    import BlockCommandMenu, {type BlockCommandMenuItem} from "./BlockCommandMenu.vue"
    import FlowableClusterCard from "./FlowableClusterCard.vue"
    import TaskEdit from "../../flows/TaskEdit.vue"
    import TaskEditModal from "./TaskEditModal.vue"
    import BlockTaskPicker from "./BlockTaskPicker.vue"
    import BlockEditorStatusBar from "./BlockEditorStatusBar.vue"
    import {useBlockEditorProvides} from "./useBlockEditorProvides"
    import {useEditTarget} from "./useEditTarget"
    import {buildCommandMenuContextLabel, buildCommandMenuItems, type BlockCommandMenuContext} from "./blockCommandMenu"
    import {useBlockEditorKeyboard} from "./useBlockEditorKeyboard"
    import {
        ALL_SECTIONS,
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
    import {storageKeys, taskEditDefaultModes} from "../../../utils/constants"
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

    useBlockEditorProvides({
        props,
        flowYaml,
        validationIssuesByTask,
        inlineEditPanel,
        createTask: (parentPath, refPath, anchorEl) => openTaskPickerAtPath(parentPath, refPath ?? -1, undefined, "after", anchorEl),
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

    const modalTarget = ref<{parentPath: string; blockSchemaPath: string; refPath?: number} | undefined>(undefined)

    const modalItemPath = computed<string>(() => {
        const target = modalTarget.value
        if (!target) return ""
        return target.refPath !== undefined ? `${target.parentPath}[${target.refPath}]` : target.parentPath
    })

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
        modalTarget.value = undefined
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
    const {
        focusedId,
        navigableCards,
        focusedCard,
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
        focusedBlockIsFlowable,
    } = useCanvasFocus(editorEl, sectionList)
    const shortcutsOpen = ref(false)
    const commandMenuOpen = ref(false)
    const confirmDialogOpen = ref(false)
    const CONFIRM_DIALOG_ESCAPE_GRACE_MS = 100
    let lastConfirmDialogCloseAt = 0
    const internalSelectedId = ref<string | undefined>(props.selectedId)

    const activeSelectedId = computed({
        get: () => internalSelectedId.value,
        set: (v: string | undefined) => {
            internalSelectedId.value = v
            emit("update:selectedId", v)
        },
    })

    const activeSelectedPath = ref<string | undefined>()

    watch(() => props.selectedId, async (id) => {
        internalSelectedId.value = id
        if (!id || !editorEl.value) return
        await nextTick()
        const card = editorEl.value.querySelector(`[data-block-id="${id}"]`) as HTMLElement | null
        const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches
        card?.scrollIntoView({block: "nearest", behavior: reduceMotion ? "auto" : "smooth"})
    })

    const flowSchemaRoot = computed(() => pluginsStore.flowSchema?.$ref ?? "")

    function blockSchemaPathFor(section: BlockSection): string {
        return [flowSchemaRoot.value, "properties", section, "items"].join("/")
    }

    function opensInModalByDefault(): boolean {
        return (localStorage.getItem(storageKeys.TASK_EDIT_DEFAULT_MODE) || taskEditDefaultModes.MODAL) !== taskEditDefaultModes.TAB
    }

    function selectBlock(section: BlockSection, block: Record<string, unknown>, split = false) {
        const strId = block.id != null ? String(block.id) : undefined
        if (!strId) return
        const list = sectionList(section)
        const index = list.findIndex(item => item === block)
        if (index < 0) return
        activeSelectedId.value = strId
        activeSelectedPath.value = undefined
        if (!split && opensInModalByDefault()) {
            modalTarget.value = {parentPath: section, blockSchemaPath: blockSchemaPathFor(section), refPath: index}
        } else {
            emit("editTask", section, blockSchemaPathFor(section), index, split)
        }
    }

    function openNestedEdit(itemPath: string, split = false) {
        const itemYaml = flowYamlUtils.extractBlockWithPath({source: flowYaml.value, path: itemPath})
        if (!itemYaml) return
        const item = flowYamlUtils.parse<Record<string, unknown>>(itemYaml)
        if (!item) return

        const parsed = displayTaskOf(item)
        if (!parsed || !parsed.id) return

        const match = itemPath.match(/^(.*)\[(\d+)\]$/)
        if (!match) return
        const parentPath = match[1]
        const refPath = parseInt(match[2], 10)
        const section = sectionFromParentPath(parentPath)
        activeSelectedId.value = String(parsed.id)
        activeSelectedPath.value = itemPath
        if (!split && opensInModalByDefault()) {
            modalTarget.value = {parentPath, blockSchemaPath: blockSchemaPathFor(section), refPath}
        } else {
            emit("editTask", parentPath, blockSchemaPathFor(section), refPath, split)
        }
    }

    const {undoState, applyYaml, deleteWithUndo, performUndo} = useYamlUndo(
        flowStore,
        (name: string) => t("block_editor.block_deleted", {name}),
    )

    function deselectIfCurrent(id: string) {
        if (activeSelectedId.value !== id) return
        activeSelectedId.value = undefined
        activeSelectedPath.value = undefined
        emit("closeTask")
    }

    function onDelete(section: BlockSection, id: unknown) {
        if (typeof id !== "string") return
        deleteWithUndo(id, () => {
            const newYaml = deleteBlock(flowYaml.value, section, id)
            deselectIfCurrent(id)
            applyYaml(newYaml)
        })
    }

    function onDeleteAtPath(path: string) {
        const blockYaml = flowYamlUtils.extractBlockWithPath({source: flowYaml.value, path})
        const parsed = blockYaml ? flowYamlUtils.parse<Record<string, unknown>>(blockYaml) : null
        const name = parsed?.id ? String(parsed.id) : path
        deleteWithUndo(name, () => {
            const newYaml = deleteBlockAtPath(flowYaml.value, path)
            if (parsed?.id) deselectIfCurrent(String(parsed.id))
            applyYaml(newYaml)
        })
    }

    function onDuplicate(section: BlockSection, id: unknown) {
        if (typeof id !== "string") return
        applyYaml(duplicateBlock(flowYaml.value, section, id))
    }

    function onDuplicateAtPath(path: string) {
        applyYaml(duplicateBlockAtPath(flowYaml.value, path))
    }

    function onUpdateDependsOn(itemPath: string, dependsOn: string[]) {
        const newContent = dependsOn.length > 0 ? flowYamlUtils.stringify(dependsOn) : ""
        applyYaml(flowYamlUtils.replaceBlockWithPath({source: flowYaml.value, path: `${itemPath}.dependsOn`, newContent}))
    }

    const picker = useTaskPicker({
        pluginsStore,
        editorEl,
        focusedId,
        focusedCard,
        focusedBlockPath,
        focusCanvasCard,
        sectionList,
        sectionDisplayLabel,
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

    const {
        dragOverIndex: taskDragOverIndex,
        handleDragStart: handleTaskDragStart,
        handleDragOver: handleTaskDragOver,
        handleDragEnd: handleTaskDragEnd,
        handleDrop: handleTaskDropBase,
    } = useDragAndDrop()

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

    function handleTaskDrop(event: DragEvent, targetIndex: number) {
        handleTaskDropBase(event, targetIndex, (from, to) => {
            clearSelectionIfPathStale("tasks", from, to)
            applyYaml(reorderAtPath(flowYaml.value, "tasks", from, to))
        })
    }

    const {
        dragOverIndex: triggerDragOverIndex,
        handleDragStart: handleTriggerDragStart,
        handleDragOver: handleTriggerDragOver,
        handleDragEnd: handleTriggerDragEnd,
        handleDrop: handleTriggerDropBase,
    } = useDragAndDrop()

    function handleTriggerDrop(event: DragEvent, targetIndex: number) {
        handleTriggerDropBase(event, targetIndex, (from, to) => {
            applyYaml(reorderAtPath(flowYaml.value, "triggers", from, to))
        })
    }

    function useSectionDnd(section: string) {
        const dnd = useDragAndDrop()
        function handleDrop(event: DragEvent, targetIndex: number) {
            dnd.handleDrop(event, targetIndex, (from, to) => {
                clearSelectionIfPathStale(section, from, to)
                applyYaml(reorderAtPath(flowYaml.value, section, from, to))
            })
        }
        return {
            dragOverIndex: dnd.dragOverIndex,
            handleDragStart: dnd.handleDragStart,
            handleDragOver: dnd.handleDragOver,
            handleDragEnd: dnd.handleDragEnd,
            handleDrop,
        }
    }

    const {
        dragOverIndex: errorsDragOverIndex,
        handleDragStart: errorsDragStart,
        handleDragOver: errorsDragOver,
        handleDragEnd: errorsDragEnd,
        handleDrop: errorsDrop,
    } = useSectionDnd("errors")

    const {
        dragOverIndex: finallyDragOverIndex,
        handleDragStart: finallyDragStart,
        handleDragOver: finallyDragOver,
        handleDragEnd: finallyDragEnd,
        handleDrop: finallyDrop,
    } = useSectionDnd("finally")

    const {
        dragOverIndex: afterExecutionDragOverIndex,
        handleDragStart: afterExecutionDragStart,
        handleDragOver: afterExecutionDragOver,
        handleDragEnd: afterExecutionDragEnd,
        handleDrop: afterExecutionDrop,
    } = useSectionDnd("afterExecution")

    function onNestedReorder(parentPath: string, from: number, to: number) {
        clearSelectionIfPathStale(parentPath, from, to)
        applyYaml(reorderAtPath(flowYaml.value, parentPath, from, to))
    }

    function openFocusedSplit() {
        const path = focusedBlockPath()
        if (path) openNestedEdit(path, true)
    }

    function confirmDelete(name: string, isFlowableBlock: boolean, onConfirm: () => void) {
        const message = isFlowableBlock
            ? t("block_editor.confirm_delete.message_group", {name})
            : t("block_editor.confirm_delete.message", {name})
        confirmDialogOpen.value = true
        KsMessageBox.confirm(message, t("block_editor.confirm_delete.title", {name}), {
            type: "warning",
            confirmButtonText: t("block_editor.delete"),
            cancelButtonText: t("cancel"),
        }).then(onConfirm).catch(() => {}).finally(() => {
            confirmDialogOpen.value = false
            lastConfirmDialogCloseAt = performance.now()
        })
    }

    function requestDeleteFocused() {
        if (!focusedId.value) return
        if (sectionFromSentinel(focusedId.value) || parentPathFromLaneSentinel(focusedId.value)) return
        const name = focusedBlockDisplayName()
        const isFlowableBlock = focusedBlockIsFlowable()
        confirmDelete(name, isFlowableBlock, () => {
            const cards = navigableCards()
            const current = cards.find(el => el.getAttribute("data-block-id") === focusedId.value)
            const index = current ? cards.indexOf(current) : -1
            const neighbor = cards.slice(index + 1).find(el => !current?.contains(el)) ?? cards[index - 1]
            actionInFocused("[data-test='block-card-delete']")
            focusCanvasCard(neighbor?.getAttribute("data-block-id") ?? undefined)
        })
    }

    function addAfterFocused() {
        openTaskPickerRelativeToFocused("after")
    }

    function addBeforeFocused() {
        openTaskPickerRelativeToFocused("before")
    }

    function isAnyOverlayOpen(): boolean {
        return shortcutsOpen.value || taskPickerVisible.value || commandMenuOpen.value || confirmDialogOpen.value
    }

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
            commandMenuOpen.value = true
            return
        }
        if (id === "clear") {
            if (closeTopOverlay()) return
            if (confirmDialogOpen.value || performance.now() - lastConfirmDialogCloseAt < CONFIRM_DIALOG_ESCAPE_GRACE_MS) return
            return false
        }
        if (id === "help") {
            shortcutsOpen.value = !shortcutsOpen.value
            return
        }
        if (isAnyOverlayOpen()) return

        if (id === "quick-insert") {
            commandMenuOpen.value = true
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

    function sectionOfSelected(id: string): BlockSection | undefined {
        return ALL_SECTIONS.find(section => sectionList(section).some(item => String(item.id) === id))
    }

    function selectedBlockData(): Record<string, unknown> | undefined {
        const id = activeSelectedId.value
        if (!id) return undefined
        if (activeSelectedPath.value) {
            const blockYaml = flowYamlUtils.extractBlockWithPath({source: flowYaml.value, path: activeSelectedPath.value})
            const item = blockYaml ? flowYamlUtils.parse<Record<string, unknown>>(blockYaml) : undefined
            return item ? displayTaskOf(item) : undefined
        }
        const section = sectionOfSelected(id)
        return section ? sectionList(section).find(item => String(item.id) === id) : undefined
    }

    function deleteSelected() {
        const id = activeSelectedId.value
        if (!id) return
        if (activeSelectedPath.value) {
            onDeleteAtPath(activeSelectedPath.value)
            return
        }
        const section = sectionOfSelected(id)
        if (section) onDelete(section, id)
    }

    function requestDeleteSelected() {
        const id = activeSelectedId.value
        const data = selectedBlockData()
        if (!id || !data) return
        const isFlowableBlock = isFlowable(data)
        confirmDelete(id, isFlowableBlock, () => deleteSelected())
    }

    function duplicateSelected() {
        const id = activeSelectedId.value
        if (!id) return
        if (activeSelectedPath.value) {
            onDuplicateAtPath(activeSelectedPath.value)
            return
        }
        const section = sectionOfSelected(id)
        if (section) onDuplicate(section, id)
    }

    function moveFocused(direction: "up" | "down") {
        const path = focusedBlockPath()
        if (!path) return
        const newYaml = moveBlockAtPath(flowYaml.value, path, direction)
        if (newYaml === flowYaml.value) return
        applyYaml(newYaml)
        nextTick(() => focusedCard()?.scrollIntoView({block: "nearest"}))
    }

    function moveSelected(direction: "up" | "down") {
        const id = activeSelectedId.value
        if (!id) return
        const path = activeSelectedPath.value
        if (!path) {
            const section = sectionOfSelected(id)
            if (!section) return
            const idx = sectionList(section).findIndex(item => String(item.id) === id)
            if (idx < 0) return
            const syntheticPath = `${section}[${idx}]`
            applyYaml(moveBlockAtPath(flowYaml.value, syntheticPath, direction))
        } else {
            const newYaml = moveBlockAtPath(flowYaml.value, path, direction)
            if (newYaml === flowYaml.value) return
            const match = path.match(/^(.*)\[(\d+)\]$/)
            if (match) {
                const newIndex = direction === "up" ? parseInt(match[2], 10) - 1 : parseInt(match[2], 10) + 1
                activeSelectedPath.value = `${match[1]}[${newIndex}]`
            }
            applyYaml(newYaml)
        }
    }

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

    .block-section-list {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
    }

</style>
