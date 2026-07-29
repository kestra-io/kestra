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
                    <!-- Roving-tabindex entry point: while no card holds the
                    keyboard focus yet, the canvas itself is the composite's
                    single Tab stop and delegates focus to its first card. -->
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

        <KsDialog v-model="shortcutsOpen" :title="t('block_editor.shortcuts.title')" data-test="block-editor-shortcuts">
            <div class="block-editor-shortcuts">
                <div v-for="group in shortcutGroups" :key="group.group" class="block-editor-shortcuts-col">
                    <span class="block-editor-shortcuts-heading">{{ t(`block_editor.shortcuts.group_${group.group}`) }}</span>
                    <div v-for="binding in group.bindings" :key="binding.id" class="block-editor-shortcut">
                        <span class="block-editor-shortcut-keys">
                            <kbd v-for="key in displayKeys(binding.keys)" :key="key">{{ key }}</kbd>
                            <template v-if="binding.alt?.length">
                                <span class="block-editor-shortcut-or">{{ t('block_editor.shortcuts.or') }}</span>
                                <kbd v-for="key in displayKeys(binding.alt)" :key="key">{{ key }}</kbd>
                            </template>
                        </span>
                        <span>{{ t(binding.i18nKey) }}</span>
                    </div>
                </div>
            </div>
        </KsDialog>

        <button
            type="button"
            class="block-editor-help"
            :aria-label="t('block_editor.shortcuts.title')"
            :title="t('block_editor.shortcuts.title')"
            data-test="block-editor-help"
            @click="shortcutsOpen = true"
        >
            <Keyboard class="block-editor-help-ico" />
            <kbd class="block-editor-help-kbd">?</kbd>
        </button>

        <div v-if="!shortcutsOpen" class="block-editor-footer" role="status" data-test="block-editor-footer">
            <span class="block-editor-footer-context">{{ footerContext }}</span>
            <span v-for="hint in footerHints" :key="hint.id" class="block-editor-footer-hint">
                <kbd v-for="key in displayKeys(hint.keys)" :key="key">{{ key }}</kbd>
                {{ t(hint.i18nKey) }}
            </span>
        </div>

        <Transition name="block-editor-undo">
            <div v-if="undoState" class="block-editor-undo" role="status" aria-live="polite">
                <span class="block-editor-undo-label">{{ undoState.label }}</span>
                <button
                    type="button"
                    class="block-editor-undo-btn"
                    data-test="block-editor-undo"
                    @click="performUndo"
                >
                    {{ t("block_editor.undo") }}
                </button>
            </div>
        </Transition>

        <BlockCommandMenu
            v-if="commandMenuOpen"
            :items="commandMenuItems"
            :contextLabel="commandMenuContextLabel"
            @close="commandMenuOpen = false"
        />
    </div>

    <Teleport to="body">
        <div
            v-if="taskPickerVisible"
            class="block-editor-picker-overlay"
            @click="taskPickerVisible = false"
        >
            <div
                class="block-editor-picker"
                :style="pickerStyle"
                data-test="block-editor-picker"
                @click.stop
                @keydown="onPickerKeydown"
            >
                <p class="block-editor-picker-context">{{ t('block_editor.inserting_into', {section: sectionLabel}) }}</p>

                <KsInput
                    ref="pickerSearchInput"
                    v-model="taskPickerSearch"
                    :placeholder="t('block_editor.search_task_placeholder')"
                    :aria-label="t('block_editor.search_task_placeholder')"
                    aria-controls="block-editor-picker-listbox"
                    :aria-activedescendant="pickerFocusedIndex >= 0 ? `block-editor-picker-option-${pickerFocusedIndex}` : undefined"
                    clearable
                    data-test="block-editor-picker-search"
                />

                <div v-if="!hasSearch" class="block-editor-picker-tabs" role="tablist">
                    <button
                        v-for="tab in PICKER_TABS"
                        :key="tab.id"
                        type="button"
                        role="tab"
                        class="block-editor-picker-tab"
                        :class="{'block-editor-picker-tab--active': pickerTab === tab.id}"
                        :aria-selected="pickerTab === tab.id"
                        :data-test="`block-editor-picker-tab-${tab.id}`"
                        @click="setPickerTab(tab.id)"
                    >
                        <component :is="tab.icon" class="block-editor-picker-tab-ico" />
                        {{ t(tab.labelKey) }}
                        <span v-if="tab.id === 'apps'" class="block-editor-picker-tab-count">{{ appGroups.length }}</span>
                    </button>
                </div>

                <div
                    id="block-editor-picker-listbox"
                    v-ks-loading="pluginsLoading"
                    class="block-editor-picker-list"
                    :class="{'block-editor-picker-list--loading': pluginsLoading}"
                    :aria-label="t('block_editor.pick_task_type')"
                    data-test="block-editor-picker-list"
                    role="listbox"
                >
                    <template v-if="!hasSearch && pickerTab === 'apps' && !appFilter">
                        <button
                            v-for="grp in appGroups"
                            :key="grp.group"
                            type="button"
                            class="block-editor-picker-app"
                            @click="appFilter = grp.group"
                        >
                            <TaskIcon class="block-editor-picker-icon" :cls="grp.sampleFqcn" :icons="pluginsStore.icons" :loadIcon="pluginsStore.loadIcon" :onlyIcon="true" />
                            <span class="block-editor-picker-app-name">{{ grp.group }}</span>
                            <span class="block-editor-picker-app-count">{{ t('block_editor.app_actions', {count: grp.count}) }}</span>
                        </button>
                    </template>

                    <template v-else>
                        <div
                            v-if="appFilter && !hasSearch"
                            class="block-editor-picker-back"
                            role="button"
                            tabindex="0"
                            @click="appFilter = undefined"
                            @keydown.enter="appFilter = undefined"
                        >
                            <ChevronLeft class="block-editor-picker-back-ico" />
                            {{ t('block_editor.all_apps') }}
                        </div>

                        <button
                            v-for="(type, idx) in displayedEntries"
                            :id="`block-editor-picker-option-${idx}`"
                            :key="type.fqcn"
                            class="block-editor-picker-row"
                            :class="{'block-editor-picker-row--focused': pickerFocusedIndex === idx}"
                            type="button"
                            role="option"
                            :aria-selected="pickerFocusedIndex === idx"
                            @click="insertTask(type.fqcn)"
                            @mouseenter="pickerFocusedIndex = idx"
                        >
                            <TaskIcon class="block-editor-picker-icon" :cls="type.fqcn" :icons="pluginsStore.icons" :loadIcon="pluginsStore.loadIcon" :onlyIcon="true" />
                            <span class="block-editor-picker-main">
                                <span class="block-editor-picker-name">{{ type.name }}</span>
                                <span class="block-editor-picker-desc">{{ type.label }}</span>
                            </span>
                            <span class="block-editor-picker-app-badge">{{ type.group }}</span>
                        </button>

                        <p v-if="!pluginsLoading && displayedEntries.length === 0" class="block-editor-picker-empty">
                            {{ (!hasSearch && pickerTab === "recent") ? t("block_editor.no_recent") : t("block_editor.no_task_results") }}
                        </p>

                        <p v-else-if="hasSearch && pickerHiddenCount > 0" class="block-editor-picker-more">
                            {{ t("block_editor.picker_more_results", {count: pickerHiddenCount}) }}
                        </p>
                    </template>
                </div>

                <div class="block-editor-picker-footer" aria-hidden="true">
                    <span><kbd>↑</kbd><kbd>↓</kbd> {{ t('block_editor.kbd_navigate') }}</span>
                    <span><kbd>↵</kbd> {{ t('block_editor.kbd_add') }}</span>
                    <span><kbd>esc</kbd> {{ t('block_editor.kbd_close') }}</span>
                </div>
            </div>
        </div>
    </Teleport>

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
    import {computed, nextTick, provide, ref, watch, type Component} from "vue"
    import {useI18n} from "vue-i18n"
    import FlowIcon from "vue-material-design-icons/FileDocumentOutline.vue"
    import Cog from "vue-material-design-icons/Cog.vue"
    import TriggerIcon from "vue-material-design-icons/LightningBoltOutline.vue"
    import TasksIcon from "vue-material-design-icons/FormatListBulleted.vue"
    import ErrorIcon from "vue-material-design-icons/AlertCircleOutline.vue"
    import FinallyIcon from "vue-material-design-icons/FlagOutline.vue"
    import AfterExecutionIcon from "vue-material-design-icons/FlagCheckered.vue"
    import SuggestedIcon from "vue-material-design-icons/Creation.vue"
    import AppsIcon from "vue-material-design-icons/ViewGridOutline.vue"
    import RecentIcon from "vue-material-design-icons/History.vue"
    import ChevronLeft from "vue-material-design-icons/ChevronLeft.vue"
    import Keyboard from "vue-material-design-icons/Keyboard.vue"
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"
    import DeleteOutline from "vue-material-design-icons/DeleteOutline.vue"
    import ArrowRightBold from "vue-material-design-icons/ArrowRightBold.vue"
    import ContentSave from "vue-material-design-icons/ContentSave.vue"
    import PlusCircleOutline from "vue-material-design-icons/PlusCircleOutline.vue"
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue"

    import {KsInput, KsMessageBox, KsTag, vKsLoading} from "@kestra-io/design-system"
    import TaskIcon from "../../plugins/TaskIcon.vue"
    import {flowYamlUtils} from "@kestra-io/topology"

    import {useFlowStore} from "../../../stores/flow"
    import {useCoreStore} from "../../../stores/core"
    import {usePluginsStore} from "../../../stores/plugins"
    import {isEntryAPluginElementPredicate, type PluginElement} from "../../../utils/pluginUtils"
    import {
        addBlock,
        addBlockAtPath,
        buildMinimalTask,
        collectAllIds,
        deleteBlock,
        deleteBlockAtPath,
        displayTaskOf,
        duplicateBlock,
        duplicateBlockAtPath,
        groupValidationIssuesByTask,
        isFlowableType,
        isWrapperLane,
        moveBlockAtPath,
        reorderAtPath,
        resolveBlockDomId,
        taskEditPathFor,
        updateBlockAtPath,
        wrapAsDagTask,
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
    import {useBlockEditorKeyboard} from "./useBlockEditorKeyboard"
    import {BLOCK_EDITOR_KEYMAP, blockEditorKeymapByGroup, findBlockEditorBinding, type BlockEditorKeymapGroup} from "./keymap"
    import type {NoCodeProps} from "../../flows/noCodeTypes"
    import {
        BLOCK_SCHEMA_PATH_INJECTION_KEY,
        BLOCK_VALIDATION_ISSUES_INJECTION_KEY,
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
    } from "../injectionKeys"
    import {defaultNamespace} from "../../../composables/useNamespaces"
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

    // Flow-level "Configure" honors the same "Default Task Edit Mode" preference
    // as a block click: modal by default, inline dock panel when set to tab.
    function openFlowProperties() {
        if (opensInModalByDefault()) {
            flowModalOpen.value = true
        } else {
            editingFlow.value = true
        }
    }

    // Each block card surfaces its own missing/invalid fields, grouped from the
    // flow's validation constraints by task id.
    const validationIssuesByTask = computed<Map<string, string[]>>(() =>
        groupValidationIssuesByTask(flowStore.flowErrors, flowStore.flowParsed),
    )

    const inlineEditPanel = ref()
    provide(FULL_SOURCE_INJECTION_KEY, flowYaml)
    provide(BLOCK_VALIDATION_ISSUES_INJECTION_KEY, validationIssuesByTask)
    provide(PARENT_PATH_INJECTION_KEY, props.parentPath ?? "")
    provide(REF_PATH_INJECTION_KEY, props.refPath)
    provide(PANEL_INJECTION_KEY, inlineEditPanel)
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
    provide(CREATE_TASK_FUNCTION_INJECTION_KEY, (parentPath, _blockSchemaPath, refPath, anchorEl) => {
        openTaskPickerAtPath(parentPath, refPath ?? -1, undefined, "after", anchorEl)
    })
    provide(EDIT_TASK_FUNCTION_INJECTION_KEY, (parentPath, blockSchemaPath, refPath, split) => {
        emit("editTask", parentPath, blockSchemaPath, refPath, split)
    })
    provide(CLOSE_TASK_FUNCTION_INJECTION_KEY, () => emit("closeTask"))
    provide(UPDATE_YAML_FUNCTION_INJECTION_KEY, (yaml: string) => applyYaml(yaml))

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

    // Mirrors useNoCodePanels.ts's getTabFromNoCodeTab: the block currently
    // being edited/created is resolved from parentPath/refPath against the
    // live flow YAML, the same contract NoCode.vue implements via injection.
    const editingItemPath = computed<string>(() => {
        if (!props.editingTask) return props.parentPath ?? ""
        return props.refPath !== undefined ? `${props.parentPath}[${props.refPath}]` : props.parentPath ?? ""
    })

    // A DAG lane item is a {task, dependsOn} wrapper — editing/creating always
    // targets the inner task, so every consumer below reads/writes one level
    // deeper via taskEditPathFor rather than each re-deriving this on its own.
    const editingPath = computed<string>(() => {
        const itemPath = editingItemPath.value
        if (!props.editingTask || !itemPath) return itemPath
        const itemYaml = flowYamlUtils.extractBlockWithPath({source: flowYaml.value, path: itemPath})
        const item = itemYaml ? flowYamlUtils.parse<Record<string, unknown>>(itemYaml) : undefined
        return item ? taskEditPathFor(itemPath, item) : itemPath
    })

    const editingTaskData = computed<Record<string, unknown> | undefined>(() => {
        if (props.creatingTask) return undefined
        if (!props.editingTask || !editingPath.value) return undefined
        const blockYaml = flowYamlUtils.extractBlockWithPath({source: flowYaml.value, path: editingPath.value})
        if (!blockYaml) return undefined
        try {
            return flowYamlUtils.parse<Record<string, unknown>>(blockYaml)
        } catch {
            return undefined
        }
    })

    // Raw YAML slice of the edited block — preserves comments and exact string
    // quoting that a parse/stringify round-trip would drop, so the Source tab is
    // faithful to Flow Code.
    const editingTaskRaw = computed<string | undefined>(() => {
        if (props.creatingTask) return undefined
        if (!props.editingTask || !editingPath.value) return undefined
        return flowYamlUtils.extractBlockWithPath({source: flowYaml.value, path: editingPath.value}) || undefined
    })

    // When the task open in this edit tab is deleted from the canvas, its block
    // no longer resolves at the path — close the tab instead of leaving a blank
    // orphan. editingTaskRaw is the raw extraction, so a transiently-invalid YAML
    // edit (block still present) does not trigger a close; only an actual removal.
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

    // Default click opens a modal on this SAME canvas instance instead of a dock
    // tab (experimental: disambiguates task-edit from the flow-level Save button,
    // which otherwise sits visually at the same level as a task opened in a tab).
    // "Open in split" keeps going through emit("editTask", ..., true) unchanged.
    const modalTarget = ref<{parentPath: string; blockSchemaPath: string; refPath?: number} | undefined>(undefined)

    const modalItemPath = computed<string>(() => {
        const target = modalTarget.value
        if (!target) return ""
        return target.refPath !== undefined ? `${target.parentPath}[${target.refPath}]` : target.parentPath
    })

    // Mirrors editingPath above (DAG-lane-wrapper unwrap via taskEditPathFor) but
    // scoped to modalTarget instead of the editingTask props of a dock-tab instance.
    const modalPath = computed<string>(() => {
        const itemPath = modalItemPath.value
        if (!itemPath) return itemPath
        const itemYaml = flowYamlUtils.extractBlockWithPath({source: flowYaml.value, path: itemPath})
        const item = itemYaml ? flowYamlUtils.parse<Record<string, unknown>>(itemYaml) : undefined
        return item ? taskEditPathFor(itemPath, item) : itemPath
    })

    const modalTaskData = computed<Record<string, unknown> | undefined>(() => {
        if (!modalPath.value) return undefined
        const blockYaml = flowYamlUtils.extractBlockWithPath({source: flowYaml.value, path: modalPath.value})
        if (!blockYaml) return undefined
        try {
            return flowYamlUtils.parse<Record<string, unknown>>(blockYaml)
        } catch {
            return undefined
        }
    })

    const modalTaskRaw = computed<string | undefined>(() => {
        if (!modalPath.value) return undefined
        return flowYamlUtils.extractBlockWithPath({source: flowYaml.value, path: modalPath.value}) || undefined
    })

    const modalSection = computed<BlockSection>(() => modalTarget.value ? sectionFromParentPath(modalTarget.value.parentPath) : "tasks")

    function onModalTaskEdited(newContent: string) {
        if (!modalPath.value) return
        applyYaml(updateBlockAtPath(flowYaml.value, modalPath.value, newContent))
    }

    // "Open in tabs" header button: promotes the modal's task into the dock via
    // the exact same emit the "open in split" card action uses (split=true), then
    // closes the modal.
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

    function sectionDisplayLabel(section: BlockSection): string {
        if (section === "triggers") return t("no_code.sections.triggers")
        if (section === "errors") return t("block_editor.lane_errors")
        if (section === "finally") return t("block_editor.lane_finally")
        if (section === "afterExecution") return t("no_code.sections.afterExecution")
        return t("no_code.sections.tasks")
    }

    // An empty section has no task to anchor focus on, so it renders its
    // BlockEmptyDrop placeholder with this sentinel as its data-block-id —
    // keyboard nav (j/k, "Go to X") can then land on it like any other card,
    // and "a"/Enter there opens the picker for that section instead of acting
    // on a real block.
    function sectionSentinelId(section: BlockSection): string {
        return `__section:${section}`
    }

    const ALL_SECTIONS: BlockSection[] = ["tasks", "triggers", "errors", "finally", "afterExecution"]

    function sectionFromSentinel(id: string | undefined): BlockSection | undefined {
        if (!id?.startsWith("__section:")) return undefined
        const section = id.slice("__section:".length) as BlockSection
        return ALL_SECTIONS.includes(section) ? section : undefined
    }

    // Same idea as the section sentinel, but for an empty lane INSIDE a
    // flowable block (e.g. a Sequential task's own, currently-empty "errors"
    // lane) — that lane doesn't map to a fixed BlockSection, so it carries
    // its own parent path instead (see BranchLane.vue's data-block-id).
    function parentPathFromLaneSentinel(id: string | undefined): string | undefined {
        if (!id?.startsWith("__lane:")) return undefined
        return id.slice("__lane:".length)
    }

    // Mirrors BranchLane.vue's own laneLabel computed, but derived from a
    // parent path (e.g. "tasks[0].errors" or "tasks[0].cases.foo") since the
    // lane sentinel only carries the path, not the lane name directly.
    function laneDisplayLabelFromPath(parentPath: string): string {
        const casesMatch = parentPath.match(/\.cases\.([^.]+)$/)
        if (casesMatch) return t("block_editor.lane_case", {key: casesMatch[1]})
        const laneName = parentPath.slice(parentPath.lastIndexOf(".") + 1)
        if (laneName === "then") return t("block_editor.lane_then")
        if (laneName === "else") return t("block_editor.lane_else")
        if (laneName === "errors") return t("block_editor.lane_errors")
        if (laneName === "finally") return t("block_editor.lane_finally")
        if (laneName === "defaults") return t("block_editor.lane_defaults")
        if (laneName === "tasks") return t("block_editor.lane_tasks")
        return laneName.toUpperCase()
    }

    const NESTED_BLOCK_KEYS = ["tasks", "then", "else", "finally", "errors", "defaults"]

    function findNestedPath(items: Record<string, unknown>[], id: string, prefix: string): string | undefined {
        for (let index = 0; index < items.length; index++) {
            const item = items[index]
            if (!item || typeof item !== "object") continue
            const path = `${prefix}[${index}]`
            if (String(item.id) === id) return path
            for (const key of NESTED_BLOCK_KEYS) {
                const branch = item[key]
                if (Array.isArray(branch)) {
                    const found = findNestedPath(branch as Record<string, unknown>[], id, `${path}.${key}`)
                    if (found) return found
                }
            }
            const cases = item.cases
            if (cases && typeof cases === "object" && !Array.isArray(cases)) {
                for (const caseKey of Object.keys(cases as Record<string, unknown>)) {
                    const branch = (cases as Record<string, unknown>)[caseKey]
                    if (Array.isArray(branch)) {
                        const found = findNestedPath(branch as Record<string, unknown>[], id, flowYamlUtils.appendKeyToPath(`${path}.cases`, caseKey))
                        if (found) return found
                    }
                }
            }
        }
        return undefined
    }

    const editorEl = ref<HTMLElement>()

    // Flushed before a save so a debounced edit typed just before Cmd/Ctrl+S
    // in the inline edit form is never silently dropped.
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
    const focusedId = ref<string | undefined>()
    const shortcutsOpen = ref(false)
    const commandMenuOpen = ref(false)
    const confirmDialogOpen = ref(false)
    let lastConfirmDialogCloseAt = 0
    const internalSelectedId = ref<string | undefined>(props.selectedId)

    const activeSelectedId = computed({
        get: () => internalSelectedId.value,
        set: (v: string | undefined) => {
            internalSelectedId.value = v
            emit("update:selectedId", v)
        },
    })

    // The path of the currently-selected block when it's a nested one (opened
    // via openNestedEdit) — undefined for a top-level block. Only used to
    // detect when a drag-reorder invalidates the selection, since the shared
    // dock (not this component) now owns the actual open tab.
    const activeSelectedPath = ref<string | undefined>()

    watch(() => props.selectedId, async (id) => {
        internalSelectedId.value = id
        if (!id || !editorEl.value) return
        await nextTick()
        const card = editorEl.value.querySelector(`[data-block-id="${id}"]`) as HTMLElement | null
        const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches
        card?.scrollIntoView({block: "nearest", behavior: reduceMotion ? "auto" : "smooth"})
    })

    // The flow schema's root $ref, needed to compute a block's blockSchemaPath
    // the same way useTopologyPanels.ts does when it opens the shared dock.
    const flowSchemaRoot = computed(() => pluginsStore.flowSchema?.$ref ?? "")

    function blockSchemaPathFor(section: BlockSection): string {
        return [flowSchemaRoot.value, "properties", section, "items"].join("/")
    }

    // Settings > Main Configuration > "Default Task Edit Mode" - read fresh on
    // every click rather than cached reactively, since it only ever changes from
    // that settings page (a different tab/session), never during a canvas session.
    function opensInModalByDefault(): boolean {
        return (localStorage.getItem(storageKeys.TASK_EDIT_DEFAULT_MODE) || taskEditDefaultModes.MODAL) !== taskEditDefaultModes.TAB
    }

    // Opening a block's editor now hands off to the shared dock (the flow
    // editor's MultiPanelTabs, via useNoCodePanels.ts) instead of hosting its
    // own pane — mirrors useTopologyPanels.ts's click-to-edit wiring exactly.
    // EXCEPT the default (non-split) click, which opens the local modal instead
    // (see modalTarget above) when opensInModalByDefault() - "open in split" is
    // untouched, always a dock tab regardless of the setting.
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

        // A DAG lane item is a {task, dependsOn} wrapper: edit the inner task, not
        // the wrapper. parentPath/refPath keep describing "the lane array + its
        // index" exactly like every flat lane (that's what the parent tab label
        // and the nested editingPath contract both expect) — the nested
        // BlockEditor/NoCode instance is the one that re-appends `.task`.
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

    const onEditTimeout = ref<ReturnType<typeof setTimeout>>()

    const undoHistory = ref<string[]>([])
    let applyingUndo = false

    function applyYaml(newYaml: string) {
        if (!applyingUndo) {
            const previous = flowStore.flowYaml
            if (typeof previous === "string" && previous !== newYaml) {
                undoHistory.value.push(previous)
                if (undoHistory.value.length > 100) undoHistory.value.shift()
            }
            dismissDeleteBadge()
        }
        flowStore.flowYaml = newYaml
        clearTimeout(onEditTimeout.value)
        onEditTimeout.value = setTimeout(() => {
            flowStore.onEdit({source: newYaml, topologyVisible: true})
        }, 1000)
    }

    const undoState = ref<{label: string} | null>(null)
    let undoTimer: ReturnType<typeof setTimeout> | undefined

    function deleteWithUndo(name: string, mutate: () => void) {
        mutate()
        undoState.value = {label: t("block_editor.block_deleted", {name})}
        clearTimeout(undoTimer)
        undoTimer = setTimeout(dismissDeleteBadge, 6000)
    }

    function performUndo() {
        if (!undoHistory.value.length) return
        const previous = undoHistory.value.pop() as string
        applyingUndo = true
        try {
            applyYaml(previous)
        } finally {
            applyingUndo = false
        }
        dismissDeleteBadge()
    }

    function dismissDeleteBadge() {
        undoState.value = null
        clearTimeout(undoTimer)
    }

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

    // Persists a DAG sub-task's dependsOn at its wrapper's own `.dependsOn` key
    // (sibling to `.task`), never touching the wrapped task itself. An empty
    // selection deletes the key instead of writing `dependsOn: []`, since Dag
    // treats "no dependencies" as the key's absence.
    function onUpdateDependsOn(itemPath: string, dependsOn: string[]) {
        const newContent = dependsOn.length > 0 ? flowYamlUtils.stringify(dependsOn) : ""
        applyYaml(flowYamlUtils.replaceBlockWithPath({source: flowYaml.value, path: `${itemPath}.dependsOn`, newContent}))
    }

    const taskPickerVisible = ref(false)
    const pickerAnchor = ref<HTMLElement>()
    const pickerSearchInput = ref<InstanceType<typeof KsInput>>()
    const taskPickerSearch = ref("")
    const debouncedSearch = ref("")
    let searchTimer: ReturnType<typeof setTimeout> | undefined
    watch(taskPickerSearch, (value) => {
        clearTimeout(searchTimer)
        searchTimer = setTimeout(() => {
            debouncedSearch.value = value
        }, 150)
    })
    const hasSearch = computed(() => debouncedSearch.value.trim().length > 0)
    const taskPickerSection = ref<BlockSection>("tasks")
    const taskPickerParentPath = ref<string | undefined>(undefined)
    const taskPickerAfterIndex = ref<number | undefined>(undefined)
    const taskPickerPosition = ref<"before" | "after">("after")
    const pluginsLoading = ref(false)
    const pickerFocusedIndex = ref(-1)

    type PickerTab = "suggested" | "apps" | "recent"
    const pickerTab = ref<PickerTab>("suggested")
    const appFilter = ref<string | undefined>(undefined)
    const recentFqcns = ref<string[]>([])

    const PICKER_TABS: ReadonlyArray<{id: PickerTab; labelKey: string; icon: Component}> = [
        {id: "suggested", labelKey: "block_editor.tab_suggested", icon: SuggestedIcon},
        {id: "apps", labelKey: "block_editor.tab_apps", icon: AppsIcon},
        {id: "recent", labelKey: "block_editor.tab_recent", icon: RecentIcon},
    ]

    const RECENT_KEY = "blockEditor.recentTaskTypes"
    const SUGGESTED_FQCNS_BY_SECTION: Record<BlockSection, string[]> = {
        tasks: [
            "io.kestra.plugin.core.log.Log",
            "io.kestra.plugin.core.http.Request",
            "io.kestra.plugin.scripts.python.Script",
            "io.kestra.plugin.scripts.shell.Commands",
            "io.kestra.plugin.core.flow.Subflow",
            "io.kestra.plugin.core.flow.If",
            "io.kestra.plugin.core.flow.Switch",
            "io.kestra.plugin.core.flow.Loop",
            "io.kestra.plugin.core.flow.Parallel",
            "io.kestra.plugin.core.flow.Dag",
        ],
        triggers: [
            "io.kestra.plugin.core.trigger.Schedule",
            "io.kestra.plugin.core.trigger.Webhook",
            "io.kestra.plugin.core.trigger.Flow",
        ],
        errors: [
            "io.kestra.plugin.core.log.Log",
            "io.kestra.plugin.core.execution.Fail",
            "io.kestra.plugin.core.http.Request",
        ],
        finally: [
            "io.kestra.plugin.core.log.Log",
            "io.kestra.plugin.core.storage.PurgeCurrentExecutionFiles",
            "io.kestra.plugin.core.http.Request",
        ],
        afterExecution: [
            "io.kestra.plugin.core.log.Log",
            "io.kestra.plugin.core.http.Request",
        ],
    }

    function anchorFrom(evt?: Event, explicitEl?: HTMLElement) {
        // Keyboard-triggered opens (no evt) have no click target to anchor to. Falling
        // back to editorEl (the whole scrollable panel) pins the picker near the top of
        // the panel's layout box regardless of scroll position, which renders it
        // off-screen for any focused block that isn't near the top. Anchor to the
        // focused card instead so the picker opens next to the actual insertion point.
        pickerAnchor.value = explicitEl ?? (evt?.currentTarget as HTMLElement) ?? focusedCard() ?? editorEl.value ?? undefined
    }

    function resetPickerView() {
        taskPickerSearch.value = ""
        clearTimeout(searchTimer)
        debouncedSearch.value = ""
        pickerTab.value = "suggested"
        appFilter.value = undefined
        loadRecent()
        pickerFocusedIndex.value = displayedEntries.value.length > 0 ? 0 : -1
    }

    function focusPickerSearch() {
        nextTick(() => pickerSearchInput.value?.focus())
    }

    function openTaskPicker(section: BlockSection, evt?: Event, anchorEl?: HTMLElement) {
        anchorFrom(evt, anchorEl)
        taskPickerSection.value = section
        taskPickerParentPath.value = undefined
        taskPickerAfterIndex.value = undefined
        taskPickerPosition.value = "after"
        resetPickerView()
        taskPickerVisible.value = true
        ensurePluginData()
        focusPickerSearch()
    }

    function sectionFromParentPath(parentPath: string): BlockSection {
        const lane = parentPath.split(".").pop() ?? ""
        if (lane === "errors") return "errors"
        if (lane === "finally") return "finally"
        if (lane === "afterExecution") return "afterExecution"
        // Without this, pressing "a" on a focused trigger anchored the picker on
        // path "triggers[i]" but offered TASK types — inserting a task into the
        // triggers array and producing an invalid flow.
        if (lane === "triggers") return "triggers"
        return "tasks"
    }

    function openTaskPickerAtPath(
        parentPath: string,
        refIndex: number,
        evt?: Event,
        position: "before" | "after" = "after",
        anchorEl?: HTMLElement,
    ) {
        anchorFrom(evt, anchorEl)
        taskPickerSection.value = sectionFromParentPath(parentPath)
        taskPickerParentPath.value = parentPath
        taskPickerAfterIndex.value = refIndex >= 0 ? refIndex : undefined
        taskPickerPosition.value = position
        resetPickerView()
        taskPickerVisible.value = true
        ensurePluginData()
        focusPickerSearch()
    }

    function focusedBlockPath(): string | undefined {
        const id = focusedId.value
        if (!id) return undefined
        for (const section of ALL_SECTIONS) {
            const found = findNestedPath(sectionList(section), id, section)
            if (found) return found
        }
        return undefined
    }

    // No focused block to anchor to: anchor the picker to the end-of-tasks drop
    // point (scrolled into view) rather than leaving it unanchored, which pins it
    // to the top of the panel and clips it off-screen.
    function openTaskPickerAtTasksEnd() {
        const endDrop = editorEl.value?.querySelector<HTMLElement>("[data-test='block-editor-tasks-end']") ?? undefined
        endDrop?.scrollIntoView({block: "nearest"})
        openTaskPicker("tasks", undefined, endDrop)
    }

    // Anchor the picker to the target section's header — its left edge is the
    // start of the section field, and it sits at the field's top — rather than
    // leaving it unanchored (which pinned it to the tasks add-point) or anchoring
    // to the right-aligned add button (which pushed it off to the right). Scroll
    // the header into view first so the picker opens inside a section below the fold.
    function openTaskPickerForSection(section: BlockSection) {
        const anchor = editorEl.value?.querySelector<HTMLElement>(`[data-test='block-editor-section-head-${section}']`) ?? undefined
        anchor?.scrollIntoView({block: "nearest"})
        openTaskPicker(section, undefined, anchor)
    }

    function openTaskPickerAnchoredAfterFocused() {
        const sentinelSection = sectionFromSentinel(focusedId.value)
        if (sentinelSection) {
            openTaskPicker(sentinelSection)
            return
        }
        const laneParentPath = parentPathFromLaneSentinel(focusedId.value)
        if (laneParentPath) {
            openTaskPickerAtPath(laneParentPath, -1)
            return
        }
        const path = focusedBlockPath()
        if (!path) {
            openTaskPickerAtTasksEnd()
            return
        }
        const match = path.match(/^(.*)\[(\d+)\]$/)
        if (!match) {
            openTaskPickerAtTasksEnd()
            return
        }
        openTaskPickerAtPath(match[1], parseInt(match[2], 10))
    }

    function openTaskPickerAnchoredBeforeFocused() {
        const sentinelSection = sectionFromSentinel(focusedId.value)
        if (sentinelSection) {
            openTaskPicker(sentinelSection)
            return
        }
        const laneParentPath = parentPathFromLaneSentinel(focusedId.value)
        if (laneParentPath) {
            openTaskPickerAtPath(laneParentPath, -1)
            return
        }
        const path = focusedBlockPath()
        if (!path) {
            openTaskPickerAtTasksEnd()
            return
        }
        const match = path.match(/^(.*)\[(\d+)\]$/)
        if (!match) {
            openTaskPickerAtTasksEnd()
            return
        }
        // Anchor on the focused block's own index with position "before" — an
        // undefined ref (what index - 1 would produce for the first item) resolves
        // to "the last item" in insertBlockWithPath, not "the first", so a real
        // ref + explicit "before" is required to land ahead of index 0.
        openTaskPickerAtPath(match[1], parseInt(match[2], 10), undefined, "before")
    }

    const pickerStyle = computed(() => {
        const anchor = pickerAnchor.value
        if (!anchor) return {}
        const rect = anchor.getBoundingClientRect()
        const gap = 4
        const margin = 8
        const maxHeight = 420
        const preferredWidth = 440
        const minWidth = 280
        const maxRight = window.innerWidth - margin
        // Keep the picker's left edge at the anchor's start (the field's left) and
        // shrink its width to fit the viewport, rather than sliding it left off the
        // field. Only pull it left when even the minimum width would overflow.
        const left = Math.max(margin, Math.min(rect.left, maxRight - minWidth))
        const width = Math.max(minWidth, Math.min(preferredWidth, maxRight - left))
        const spaceBelow = window.innerHeight - rect.bottom - gap - margin
        const spaceAbove = rect.top - gap - margin
        const openUp = spaceBelow < Math.min(maxHeight, 280) && spaceAbove > spaceBelow
        const available = Math.max(200, Math.min(maxHeight, openUp ? spaceAbove : spaceBelow))
        return {
            left: `${left}px`,
            width: `${width}px`,
            maxHeight: `${available}px`,
            ...(openUp
                ? {bottom: `${window.innerHeight - rect.top + gap}px`}
                : {top: `${rect.bottom + gap}px`}),
        }
    })

    function ensurePluginData() {
        if (pluginsStore.plugins) return
        pluginsLoading.value = true
        pluginsStore.ensurePlugins().finally(() => {
            pluginsLoading.value = false
        })
    }

    interface PickerEntry {
        fqcn: string
        name: string
        label: string
        group: string
    }

    const activeEntryKind = computed(() => taskPickerSection.value === "triggers" ? "triggers" : "tasks")

    const allPickerEntries = computed<PickerEntry[]>(() => {
        if (!pluginsStore.plugins) return []
        const entries: PickerEntry[] = []
        const seen = new Set<string>()
        const kind = activeEntryKind.value
        for (const plugin of pluginsStore.plugins) {
            const value = plugin[kind]
            if (!isEntryAPluginElementPredicate(kind, value)) continue
            for (const el of value as PluginElement[]) {
                if (el.deprecated || seen.has(el.cls)) continue
                seen.add(el.cls)
                const parts = el.cls.split(".")
                entries.push({
                    fqcn: el.cls,
                    name: parts[parts.length - 1] ?? el.cls,
                    label: el.title ?? parts[parts.length - 1] ?? el.cls,
                    group: plugin.title ?? plugin.name ?? "",
                })
            }
        }
        return entries
    })

    const PICKER_MAX_RESULTS = 50

    const filteredMatches = computed<PickerEntry[]>(() => {
        const search = debouncedSearch.value.trim().toLowerCase()
        const source = allPickerEntries.value
        if (!search) return source
        return source.filter(
            entry =>
                entry.label.toLowerCase().includes(search) ||
                entry.fqcn.toLowerCase().includes(search) ||
                entry.group.toLowerCase().includes(search),
        )
    })

    const filteredCommonTypes = computed<PickerEntry[]>(() =>
        filteredMatches.value.slice(0, PICKER_MAX_RESULTS),
    )

    const pickerHiddenCount = computed(() =>
        Math.max(0, filteredMatches.value.length - PICKER_MAX_RESULTS),
    )

    const entryByFqcn = computed(() => {
        const map = new Map<string, PickerEntry>()
        for (const entry of allPickerEntries.value) map.set(entry.fqcn, entry)
        return map
    })

    const suggestedEntries = computed<PickerEntry[]>(() =>
        SUGGESTED_FQCNS_BY_SECTION[taskPickerSection.value]
            .map(fqcn => entryByFqcn.value.get(fqcn))
            .filter((e): e is PickerEntry => Boolean(e)),
    )

    const recentEntries = computed<PickerEntry[]>(() =>
        recentFqcns.value.map(fqcn => entryByFqcn.value.get(fqcn)).filter((e): e is PickerEntry => Boolean(e)),
    )

    const appGroups = computed(() => {
        const groups = new Map<string, {group: string; count: number; sampleFqcn: string}>()
        for (const entry of allPickerEntries.value) {
            const existing = groups.get(entry.group)
            if (existing) existing.count++
            else groups.set(entry.group, {group: entry.group, count: 1, sampleFqcn: entry.fqcn})
        }
        return [...groups.values()].sort((a, b) => b.count - a.count)
    })

    const displayedEntries = computed<PickerEntry[]>(() => {
        if (hasSearch.value) return filteredCommonTypes.value
        if (pickerTab.value === "suggested") return suggestedEntries.value
        if (pickerTab.value === "recent") return recentEntries.value
        if (pickerTab.value === "apps" && appFilter.value) {
            return allPickerEntries.value.filter(e => e.group === appFilter.value).slice(0, PICKER_MAX_RESULTS)
        }
        return []
    })

    const sectionLabel = computed(() => sectionDisplayLabel(taskPickerSection.value))

    function setPickerTab(tab: PickerTab) {
        pickerTab.value = tab
        appFilter.value = undefined
        pickerFocusedIndex.value = displayedEntries.value.length > 0 ? 0 : -1
    }

    function loadRecent() {
        try {
            const raw = localStorage.getItem(RECENT_KEY)
            recentFqcns.value = raw ? JSON.parse(raw) : []
        } catch {
            recentFqcns.value = []
        }
    }

    function pushRecent(fqcn: string) {
        const next = [fqcn, ...recentFqcns.value.filter(f => f !== fqcn)].slice(0, 8)
        recentFqcns.value = next
        try {
            localStorage.setItem(RECENT_KEY, JSON.stringify(next))
        } catch {
            // localStorage may be unavailable; recency is best-effort
        }
    }

    // Keyboard-first: a fresh result list always has its top entry pre-highlighted,
    // so typing a search and pressing Enter inserts immediately (mirrors
    // BlockCommandMenu's default-highlighted-first-item behavior).
    watch(displayedEntries, (items) => {
        pickerFocusedIndex.value = items.length > 0 ? 0 : -1
    })

    function onPickerKeydown(event: KeyboardEvent) {
        const list = displayedEntries.value
        if (list.length === 0) return
        if (event.key === "ArrowDown") {
            event.preventDefault()
            pickerFocusedIndex.value = Math.min(pickerFocusedIndex.value + 1, list.length - 1)
        } else if (event.key === "ArrowUp") {
            event.preventDefault()
            pickerFocusedIndex.value = Math.max(pickerFocusedIndex.value - 1, 0)
        } else if (event.key === "Enter" && pickerFocusedIndex.value >= 0) {
            event.preventDefault()
            const entry = list[pickerFocusedIndex.value]
            if (entry) insertTask(entry.fqcn)
        }
    }

    function insertTask(fqcn: string) {
        pushRecent(fqcn)
        const block = buildMinimalTask(fqcn, collectAllIds(flowYaml.value))

        if (taskPickerParentPath.value !== undefined) {
            const parentPath = taskPickerParentPath.value
            const blockToInsert = isWrapperLane(flowYaml.value, parentPath) ? wrapAsDagTask(block) : block
            applyYaml(addBlockAtPath(flowYaml.value, parentPath, blockToInsert, taskPickerAfterIndex.value, taskPickerPosition.value))
        } else {
            const section = taskPickerSection.value
            const list = sectionList(section)
            const lastId = list.length > 0
                ? String(list[list.length - 1].id ?? "")
                : undefined
            applyYaml(addBlock(flowYaml.value, section, block, lastId))
        }
        // Move focus onto the block that was just created so the keyboard flow
        // continues naturally (edit it, reorder it, insert after it again)
        // instead of leaving the ring on whatever was focused before insertion.
        focusCanvasCard(String(block.id))
        taskPickerVisible.value = false
    }

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

    // Top-level leaf cards in errors / finally / afterExecution get the same
    // drag-to-reorder affordance as the tasks section. One bundle per section so
    // a drag in one never lights up the drop indicator of another.
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

    function navigableCards(): HTMLElement[] {
        if (!editorEl.value) return []
        return [...editorEl.value.querySelectorAll<HTMLElement>("[data-block-id]")].filter(el => el.offsetParent !== null)
    }

    function focusedCard(): HTMLElement | undefined {
        return navigableCards().find(el => el.getAttribute("data-block-id") === focusedId.value)
    }

    // The element that actually holds the card's roving tabindex — the card
    // root for leaf cards and sentinels, the header for flowable clusters.
    function cardFocusTarget(card: HTMLElement): HTMLElement {
        if (card.hasAttribute("tabindex") || card.tagName === "BUTTON") return card
        return card.querySelector<HTMLElement>("[data-test='flowable-cluster-header']") ?? card
    }

    // Single entry point for moving the canvas focus: keeps the virtual ring
    // (focusedId) and the REAL DOM focus in lockstep, so native Tab always
    // continues from wherever arrow-key navigation left off — one focus model,
    // not two (roving tabindex).
    function focusCanvasCard(id: string | undefined) {
        focusedId.value = id
        if (!id) return
        nextTick(() => {
            const card = focusedCard()
            if (!card) return
            cardFocusTarget(card).focus({preventScroll: true})
            card.scrollIntoView({block: "nearest"})
        })
    }

    // The reverse sync: Tab or a click landing anywhere inside a canvas card
    // moves the ring there, so shortcuts (a, d, Enter…) act on what the user
    // actually reached, not on a stale virtual position.
    function onCanvasFocusIn(event: FocusEvent) {
        const target = event.target as HTMLElement | null
        if (!target) return
        const id = target.closest("[data-block-id]")?.getAttribute("data-block-id")
        if (id) focusedId.value = id
    }

    // Tab entry point while nothing is focused yet: the canvas container is
    // the composite's single Tab stop and delegates to its first card.
    function onCanvasEntryFocus() {
        const first = navigableCards()[0]
        if (first) focusCanvasCard(first.getAttribute("data-block-id") ?? undefined)
    }

    function moveFocus(direction: 1 | -1) {
        const cards = navigableCards()
        if (!cards.length) return
        const ids = cards.map(el => el.getAttribute("data-block-id") ?? "")
        const current = focusedId.value ? ids.indexOf(focusedId.value) : -1
        const next = current < 0 ? (direction > 0 ? 0 : cards.length - 1) : (current + direction + cards.length) % cards.length
        focusCanvasCard(ids[next] || undefined)
    }

    function focusedClusterHeader(): HTMLElement | undefined {
        const card = focusedCard()
        if (!card) return undefined
        return card.matches("[data-test='flowable-cluster-header']")
            ? card
            : (card.querySelector<HTMLElement>("[data-test='flowable-cluster-header']") ?? undefined)
    }

    function stepInto() {
        const header = focusedClusterHeader()
        if (!header) return
        if (header.getAttribute("aria-expanded") === "false") {
            header.click()
            return
        }
        nextTick(() => {
            const card = focusedCard()
            const cards = navigableCards()
            const current = focusedId.value ? cards.findIndex(el => el.getAttribute("data-block-id") === focusedId.value) : -1
            const next = cards[current + 1]
            if (card && next && current >= 0 && card.contains(next)) {
                focusCanvasCard(next.getAttribute("data-block-id") ?? focusedId.value)
            }
        })
    }

    function stepOut() {
        const header = focusedClusterHeader()
        if (header?.getAttribute("aria-expanded") === "true") {
            header.click()
            return
        }
        const card = focusedCard()
        const parent = card?.parentElement?.closest<HTMLElement>("[data-block-id]")
        if (parent) {
            focusCanvasCard(parent.getAttribute("data-block-id") ?? focusedId.value)
        }
    }

    function openFocused() {
        const card = focusedCard()
        if (!card) return
        const clusterHeader = card.querySelector<HTMLElement>("[data-test='flowable-cluster-header']")
        if (clusterHeader) {
            clusterHeader.click()
        } else {
            card.click()
        }
    }

    function openFocusedSplit() {
        const path = focusedBlockPath()
        if (path) openNestedEdit(path, true)
    }

    function actionInFocused(selector: string) {
        focusedCard()?.querySelector<HTMLElement>(selector)?.click()
    }

    function focusedBlockDisplayName(): string {
        const card = focusedCard()
        return card?.querySelector<HTMLElement>("[data-test='block-card-id']")?.textContent?.trim() || focusedId.value || ""
    }

    function focusedBlockIsFlowable(): boolean {
        return Boolean(focusedCard()?.querySelector("[data-test='flowable-cluster-header']"))
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
            // KsMessageBox resolves/rejects its promise through several microtask
            // checkpoints that browsers run between bubble-phase DOM listeners, so by
            // the time the Escape that dismissed it reaches our window-level listener
            // (the outermost, and therefore last, bubble target) confirmDialogOpen has
            // already flipped back to false. A short grace window is the only reliable
            // way to recognize "this Escape just closed the confirm dialog" from here.
            lastConfirmDialogCloseAt = performance.now()
        })
    }

    function requestDeleteFocused() {
        if (!focusedId.value) return
        // Sentinels (empty sections/lanes) aren't real blocks — there is nothing
        // to delete, and the confirm dialog would leak the internal __section:/
        // __lane: id as the block "name".
        if (sectionFromSentinel(focusedId.value) || parentPathFromLaneSentinel(focusedId.value)) return
        const name = focusedBlockDisplayName()
        const isFlowableBlock = focusedBlockIsFlowable()
        confirmDelete(name, isFlowableBlock, () => {
            // Hand focus to a neighbor before the card disappears, so keyboard
            // navigation continues from the deletion point instead of resetting
            // to the top of the canvas. A flowable's children sit between it and
            // its true next sibling in DOM order and disappear with it — skip
            // anything the deleted card contains.
            const cards = navigableCards()
            const current = cards.find(el => el.getAttribute("data-block-id") === focusedId.value)
            const index = current ? cards.indexOf(current) : -1
            const neighbor = cards.slice(index + 1).find(el => !current?.contains(el)) ?? cards[index - 1]
            actionInFocused("[data-test='block-card-delete']")
            focusCanvasCard(neighbor?.getAttribute("data-block-id") ?? undefined)
        })
    }

    function addAfterFocused() {
        openTaskPickerAnchoredAfterFocused()
    }

    function addBeforeFocused() {
        openTaskPickerAnchoredBeforeFocused()
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
        // NoCode.vue's useKeyboardSave() is NOT mounted on this page, so the
        // footer's advertised Cmd/Ctrl+S has to be honored here.
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
            // KsMessageBox owns its own Escape-to-cancel and isn't tracked by
            // closeTopOverlay(). Its promise settles through microtask checkpoints
            // that run between bubble-phase listeners, so confirmDialogOpen has
            // already flipped back to false by the time this (window-level, outermost)
            // handler sees the same Escape — hence the timestamp grace window instead
            // of a reactive-state check.
            if (confirmDialogOpen.value || performance.now() - lastConfirmDialogCloseAt < 100) return
            // Nothing to close: leave native Escape untouched instead of swallowing it.
            return false
        }
        if (id === "help") {
            shortcutsOpen.value = !shortcutsOpen.value
            return
        }
        if (isAnyOverlayOpen()) return

        if (id === "quick-insert") {
            // "/" opens the command menu, where typing "task"/"error"/"trigger"
            // surfaces the matching "Insert …" command; picking one opens the
            // task picker for that section.
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
            // With roving focus, Enter/Space can land on a native interactive
            // element that isn't a canvas card (a section's Add button, a link…)
            // — let the browser activate it instead of opening the stale ring.
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

    // Resolves the top-level section a selected (not nested) block id lives in
    // — the dock tab used to carry this directly; now it's derived on demand.
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
        // focusedId tracks the block by id, not by position, so the ring already
        // follows it after the reorder — just keep it scrolled into view.
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

    const KEY_DISPLAY: Record<string, string> = {
        ArrowUp: "↑",
        ArrowDown: "↓",
        ArrowLeft: "←",
        ArrowRight: "→",
        Enter: "↵",
        "Meta+Enter": "⌘↵",
        "Control+Enter": "⌘↵",
        " ": "Space",
        Backspace: "⌫",
        Delete: "⌦",
        "Meta+Shift+p": "⌘⇧P",
        "Control+Shift+p": "⌘⇧P",
        "Meta+s": "⌘S",
        "Control+s": "⌘S",
        "Meta+z": "⌘Z",
        "Control+z": "⌘Z",
        "Alt+ArrowUp": "⌥↑",
        "Alt+ArrowDown": "⌥↓",
    }

    function displayKeys(keys: string[]): string[] {
        const seen = new Set<string>()
        const result: string[] = []
        for (const key of keys) {
            const display = KEY_DISPLAY[key] ?? key
            if (seen.has(display)) continue
            seen.add(display)
            result.push(display)
        }
        return result
    }

    const SHORTCUT_GROUP_ORDER: BlockEditorKeymapGroup[] = ["navigate", "insert", "edit", "global"]

    const HIDDEN_SHORTCUT_IDS = new Set(["clear"])

    const shortcutGroups = computed(() =>
        SHORTCUT_GROUP_ORDER.map(group => ({
            group,
            bindings: blockEditorKeymapByGroup(group).filter(binding => !HIDDEN_SHORTCUT_IDS.has(binding.id)),
        })),
    )

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

    interface FooterHint {
        id: string
        keys: string[]
        i18nKey: string
    }

    // Keys for canvas-rebindable actions are looked up from BLOCK_EDITOR_KEYMAP (the
    // single source of truth) instead of being duplicated here, so the footer can never
    // drift from the actual dispatch table. Enter/Escape are left literal where they
    // describe generic modal-navigation UX (confirm/close a list) rather than a specific
    // rebindable canvas action.
    function keysFor(id: string): string[] {
        return findBlockEditorBinding(id)?.keys ?? []
    }

    const footerHints = computed<FooterHint[]>(() => {
        if (taskPickerVisible.value || commandMenuOpen.value) {
            return [
                {id: "move", keys: ["ArrowUp", "ArrowDown"], i18nKey: "block_editor.kbd_navigate"},
                {id: "run", keys: ["Enter"], i18nKey: "block_editor.kbd_add"},
                {id: "close", keys: ["Escape"], i18nKey: "block_editor.kbd_close"},
            ]
        }
        // A real block (not an empty section's sentinel) additionally supports
        // inserting before it and reordering it — surface those here too, since
        // they were previously only discoverable through the "?" help overlay.
        const isRealBlockFocused = Boolean(focusedId.value)
            && !sectionFromSentinel(focusedId.value)
            && !parentPathFromLaneSentinel(focusedId.value)
        return [
            {id: "help", keys: keysFor("help"), i18nKey: "block_editor.shortcuts.toggle"},
            {id: "move", keys: keysFor("move"), i18nKey: "block_editor.shortcuts.move_between"},
            {id: "open", keys: keysFor("open"), i18nKey: "block_editor.shortcuts.open"},
            {id: "insert", keys: keysFor("insert-after"), i18nKey: "block_editor.shortcuts.add_after"},
            ...(isRealBlockFocused
                ? [
                    {id: "insert-before", keys: keysFor("insert-before"), i18nKey: "block_editor.shortcuts.add_before"},
                    {id: "reorder", keys: keysFor("reorder"), i18nKey: "block_editor.shortcuts.reorder"},
                ]
                : []),
            {id: "command-menu", keys: keysFor("command-menu"), i18nKey: "block_editor.shortcuts.command_palette"},
        ]
    })

    const commandMenuContextLabel = computed(() => {
        const sentinelSection = sectionFromSentinel(focusedId.value)
        if (sentinelSection) return t("block_editor.command_menu.context_selected", {name: sectionDisplayLabel(sentinelSection)})
        const laneParentPath = parentPathFromLaneSentinel(focusedId.value)
        if (laneParentPath) return t("block_editor.command_menu.context_selected", {name: laneDisplayLabelFromPath(laneParentPath)})
        return focusedId.value
            ? t("block_editor.command_menu.context_selected", {name: focusedBlockDisplayName()})
            : t("block_editor.command_menu.context_flow")
    })

    const commandMenuItems = computed<BlockCommandMenuItem[]>(() => {
        const items: BlockCommandMenuItem[] = []
        const focusedSentinelSection = sectionFromSentinel(focusedId.value)
        const focusedLaneSentinel = parentPathFromLaneSentinel(focusedId.value)
        const insertLabel = focusedSentinelSection
            ? t("block_editor.command_menu.insert_in_section", {section: sectionDisplayLabel(focusedSentinelSection)})
            : focusedLaneSentinel
                ? t("block_editor.command_menu.insert_in_section", {section: laneDisplayLabelFromPath(focusedLaneSentinel)})
                : focusedId.value
                    ? t("block_editor.command_menu.insert_after", {name: focusedBlockDisplayName()})
                    : t("block_editor.command_menu.insert_at_end")
        items.push({
            id: "insert",
            group: t("block_editor.command_menu.group_insert"),
            title: insertLabel,
            icon: PlusCircleOutline,
            shortcut: "A",
            run: () => {
                commandMenuOpen.value = false
                addAfterFocused()
            },
        })

        if (focusedId.value && !focusedSentinelSection && !focusedLaneSentinel) {
            items.push({
                id: "insert-before",
                group: t("block_editor.command_menu.group_insert"),
                title: t("block_editor.command_menu.insert_before", {name: focusedBlockDisplayName()}),
                icon: PlusCircleOutline,
                shortcut: "⇧A",
                run: () => {
                    commandMenuOpen.value = false
                    addBeforeFocused()
                },
            })
        }

        const insertKinds: BlockSection[] = ["triggers", "tasks", "errors", "finally", "afterExecution"]
        for (const section of insertKinds) {
            items.push({
                id: `insert-${section}`,
                group: t("block_editor.command_menu.group_insert"),
                title: t("block_editor.command_menu.insert_kind", {kind: sectionDisplayLabel(section)}),
                icon: PlusCircleOutline,
                run: () => {
                    commandMenuOpen.value = false
                    openTaskPickerForSection(section)
                },
            })
        }

        if (focusedId.value && !focusedSentinelSection && !focusedLaneSentinel) {
            const name = focusedBlockDisplayName()
            items.push({
                id: "open",
                group: t("block_editor.command_menu.group_block"),
                title: t("block_editor.command_menu.open", {name}),
                icon: OpenInNew,
                shortcut: "↵",
                run: () => {
                    commandMenuOpen.value = false
                    openFocused()
                },
            })
            items.push({
                id: "duplicate",
                group: t("block_editor.command_menu.group_block"),
                title: t("block_editor.command_menu.duplicate", {name}),
                icon: ContentCopy,
                shortcut: "D",
                run: () => {
                    commandMenuOpen.value = false
                    actionInFocused("[data-test='block-card-duplicate']")
                },
            })
            items.push({
                id: "delete",
                group: t("block_editor.command_menu.group_block"),
                title: t("block_editor.command_menu.delete", {name}),
                icon: DeleteOutline,
                shortcut: "⌫",
                run: () => {
                    commandMenuOpen.value = false
                    requestDeleteFocused()
                },
            })
        }

        const sections: {section: BlockSection; labelKey: string}[] = [
            {section: "triggers", labelKey: "no_code.sections.triggers"},
            {section: "tasks", labelKey: "no_code.sections.tasks"},
            {section: "errors", labelKey: "block_editor.lane_errors"},
            {section: "finally", labelKey: "block_editor.lane_finally"},
            {section: "afterExecution", labelKey: "no_code.sections.afterExecution"},
        ]
        for (const {section, labelKey} of sections) {
            items.push({
                id: `goto-${section}`,
                group: t("block_editor.command_menu.group_goto"),
                title: t("block_editor.command_menu.goto", {section: t(labelKey)}),
                icon: ArrowRightBold,
                run: () => {
                    commandMenuOpen.value = false
                    const list = sectionList(section)
                    focusCanvasCard(list.length ? String(list[0].id ?? 0) : sectionSentinelId(section))
                },
            })
        }

        items.push({
            id: "save",
            group: t("block_editor.command_menu.group_flow"),
            title: t("block_editor.command_menu.save"),
            icon: ContentSave,
            shortcut: "⌘S",
            run: () => {
                commandMenuOpen.value = false
                saveFlowWithPendingEdits()
            },
        })

        return items
    })
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

    .block-editor-picker-overlay {
        position: fixed;
        inset: 0;
        z-index: 3000;
    }

    .block-editor-picker {
        position: fixed;
        z-index: 3001;
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
        max-height: 420px;
        padding: var(--ks-spacing-3);
        background: var(--ks-bg-elevated);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        box-shadow: var(--ks-shadow-lg);
    }

    .block-editor-picker-context {
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-muted);
        margin: 0;
    }

    .block-editor-picker-tabs {
        display: flex;
        gap: var(--ks-spacing-1);
        border-bottom: 1px solid var(--ks-border-subtle);
    }

    .block-editor-picker-tab {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        padding: var(--ks-spacing-1) var(--ks-spacing-2);
        background: transparent;
        border: none;
        border-bottom: 2px solid transparent;
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-xs);
        cursor: pointer;
        transition: color 0.12s, border-color 0.12s;
    }

    .block-editor-picker-tab:hover {
        color: var(--ks-text-primary);
    }

    .block-editor-picker-tab--active {
        color: var(--ks-text-link);
        border-bottom-color: var(--ks-text-link);
        font-weight: 600;
    }

    .block-editor-picker-tab-ico {
        display: flex;
        font-size: var(--ks-font-size-sm);
    }

    .block-editor-picker-tab-count {
        font-size: var(--ks-font-size-xs);
        font-family: var(--ks-font-family-mono);
        padding: 0 var(--ks-spacing-1);
        border-radius: var(--ks-radius-lg);
        background: var(--ks-bg-tag);
        color: var(--ks-text-muted);
    }

    .block-editor-picker-list {
        position: relative;
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-1);
        flex: 1;
        min-height: 0;
        max-height: 320px;
        overflow-y: auto;

        &--loading {
            min-height: var(--ks-spacing-10);
        }
    }

    .block-editor-picker-row {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-3);
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        border: none;
        border-radius: var(--ks-radius-base);
        background: transparent;
        cursor: pointer;
        text-align: left;
        transition: background-color 0.15s;

        &:hover,
        &--focused {
            background: var(--ks-bg-hover);
        }
    }

    .block-editor-picker-icon {
        flex-shrink: 0;
        box-sizing: border-box;
        width: 1.5rem;
        height: 1.5rem;
        padding: 2px;
        background: var(--ks-bg-plugin-icon);
        border: 1px solid var(--ks-border-subtle);
        border-radius: var(--ks-radius-sm);
    }

    .block-editor-picker-main {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: 1px;
    }

    .block-editor-picker-name {
        font-size: var(--ks-font-size-sm);
        font-weight: 600;
        font-family: var(--ks-font-family-mono);
        color: var(--ks-text-primary);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .block-editor-picker-desc {
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-muted);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .block-editor-picker-app-badge {
        flex-shrink: 0;
        max-width: 40%;
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-muted);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .block-editor-picker-app {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-3);
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        border: none;
        border-radius: var(--ks-radius-base);
        background: transparent;
        cursor: pointer;
        text-align: left;
        transition: background-color 0.12s;
    }

    .block-editor-picker-app:hover {
        background: var(--ks-bg-hover);
    }

    .block-editor-picker-app-name {
        flex: 1;
        min-width: 0;
        font-size: var(--ks-font-size-sm);
        font-weight: 500;
        color: var(--ks-text-primary);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .block-editor-picker-app-count {
        flex-shrink: 0;
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-muted);
    }

    .block-editor-picker-back {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        padding: var(--ks-spacing-1) var(--ks-spacing-2);
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-secondary);
        cursor: pointer;
        border-radius: var(--ks-radius-base);
    }

    .block-editor-picker-back:hover {
        color: var(--ks-text-link);
    }

    .block-editor-picker-back:focus-visible {
        outline: 2px solid var(--ks-border-focus);
        outline-offset: -2px;
    }

    .block-editor-picker-back-ico {
        display: flex;
    }

    .block-editor-picker-footer {
        display: flex;
        gap: var(--ks-spacing-4);
        padding-top: var(--ks-spacing-2);
        border-top: 1px solid var(--ks-border-subtle);
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-muted);
    }

    .block-editor-picker-footer kbd {
        font-family: var(--ks-font-family-mono);
        background: var(--ks-bg-tag);
        border-radius: var(--ks-radius-sm);
        padding: 0 var(--ks-spacing-1);
        margin-right: 2px;
        color: var(--ks-text-secondary);
    }

    .block-editor-picker-empty {
        color: var(--ks-text-muted);
        font-size: var(--ks-font-size-sm);
        text-align: center;
        padding: var(--ks-spacing-4);
        margin: 0;
    }

    .block-editor-picker-more {
        color: var(--ks-text-muted);
        font-size: var(--ks-font-size-xs);
        text-align: center;
        padding: var(--ks-spacing-2);
        margin: 0;
    }

    .block-editor-shortcuts {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: var(--ks-spacing-5);
    }

    .block-editor-shortcuts-col {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
    }

    .block-editor-shortcuts-heading {
        font-size: var(--ks-font-size-xs);
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.04em;
        color: var(--ks-text-secondary);
    }

    .block-editor-shortcut {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--ks-spacing-3);
        font-size: var(--ks-font-size-sm);
        color: var(--ks-text-primary);
    }

    .block-editor-shortcut-keys {
        display: inline-flex;
        gap: var(--ks-spacing-1);
        flex-shrink: 0;
    }

    .block-editor-shortcut-keys kbd {
        font-family: var(--ks-font-family-mono);
        font-size: var(--ks-font-size-xs);
        background: var(--ks-bg-tag-inactive);
        border: 1px solid var(--ks-border-subtle);
        border-radius: var(--ks-radius-sm);
        padding: 1px var(--ks-spacing-1);
        color: var(--ks-text-secondary);
        min-width: 18px;
        text-align: center;
    }

    .block-editor-shortcut-or {
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-muted);
        padding: 0 1px;
    }

    .block-editor-footer {
        position: absolute;
        left: 0;
        right: 0;
        bottom: 0;
        z-index: 9;
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-4);
        height: 2.25rem;
        padding: 0 var(--ks-spacing-4);
        background: var(--ks-bg-surface);
        border-top: 1px solid var(--ks-border-subtle);
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-secondary);
        overflow: hidden;
    }

    .block-editor-footer::after {
        content: "";
        position: absolute;
        top: 0;
        right: 0;
        bottom: 0;
        width: var(--ks-spacing-8);
        background: linear-gradient(to right, transparent, var(--ks-bg-surface));
        pointer-events: none;
    }

    .block-editor-footer-context {
        margin-right: auto;
        flex-shrink: 1;
        color: var(--ks-text-muted);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .block-editor-footer-hint {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        flex-shrink: 0;
        white-space: nowrap;
    }

    .block-editor-footer-hint kbd {
        font-family: var(--ks-font-family-mono);
        font-size: var(--ks-font-size-xs);
        background: var(--ks-bg-tag-inactive);
        border: 1px solid var(--ks-border-subtle);
        border-radius: var(--ks-radius-sm);
        padding: 1px var(--ks-spacing-1);
        min-width: 18px;
        text-align: center;
        color: var(--ks-text-secondary);
    }

    .block-editor-help {
        position: absolute;
        right: var(--ks-spacing-4);
        bottom: calc(2.25rem + var(--ks-spacing-3));
        z-index: 10;
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-1) var(--ks-spacing-2);
        background: var(--ks-bg-elevated);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-lg);
        box-shadow: var(--ks-shadow-sm);
        color: var(--ks-text-secondary);
        cursor: pointer;
        transition: color 0.15s, border-color 0.15s, background-color 0.15s;
    }

    .block-editor-help:hover {
        color: var(--ks-text-primary);
        border-color: var(--ks-border-strong);
        background: var(--ks-bg-surface);
    }

    .block-editor-help:focus-visible {
        outline: 2px solid var(--ks-border-focus);
        outline-offset: 2px;
    }

    .block-editor-help-ico {
        display: flex;
        font-size: 1rem;
    }

    .block-editor-help-kbd {
        font-family: var(--ks-font-family-mono);
        font-size: var(--ks-font-size-xs);
        background: var(--ks-bg-tag-inactive);
        border: 1px solid var(--ks-border-subtle);
        border-radius: var(--ks-radius-sm);
        padding: 1px var(--ks-spacing-1);
        min-width: 18px;
        text-align: center;
        color: var(--ks-text-secondary);
    }

    .block-editor-undo {
        position: absolute;
        bottom: calc(2.25rem + var(--ks-spacing-3));
        left: 50%;
        transform: translateX(-50%);
        z-index: 11;
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-3);
        padding: var(--ks-spacing-2) var(--ks-spacing-2) var(--ks-spacing-2) var(--ks-spacing-4);
        background: var(--ks-bg-elevated);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-lg);
        box-shadow: var(--ks-shadow-sm);
        font-size: var(--ks-font-size-sm);
        color: var(--ks-text-primary);
    }

    .block-editor-undo-label {
        white-space: nowrap;
    }

    .block-editor-undo-btn {
        border: none;
        background: transparent;
        color: var(--ks-text-link);
        font-weight: 600;
        font-size: var(--ks-font-size-sm);
        cursor: pointer;
        padding: var(--ks-spacing-1) var(--ks-spacing-2);
        border-radius: var(--ks-radius-sm);
        transition: background-color 0.12s;
    }

    .block-editor-undo-btn:hover {
        background: var(--ks-bg-hover);
    }

    .block-editor-undo-btn:focus-visible {
        outline: 2px solid var(--ks-border-focus);
        outline-offset: 1px;
    }

    .block-editor-undo-enter-active,
    .block-editor-undo-leave-active {
        transition: opacity 0.18s ease, transform 0.18s ease;
    }

    .block-editor-undo-enter-from,
    .block-editor-undo-leave-to {
        opacity: 0;
        transform: translate(-50%, 8px);
    }

    @media (prefers-reduced-motion: reduce) {
        .block-editor-undo-enter-active,
        .block-editor-undo-leave-active {
            transition: none;
        }
    }
</style>
