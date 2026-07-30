<template>
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
                    :ref="(el) => (picker.pickerSearchInput.value = el)"
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
</template>

<script setup lang="ts">
    import ChevronLeft from "vue-material-design-icons/ChevronLeft.vue"
    import {KsInput, vKsLoading} from "@kestra-io/design-system"
    import {useI18n} from "vue-i18n"
    import TaskIcon from "../../plugins/TaskIcon.vue"
    import {usePluginsStore} from "../../../stores/plugins"
    import type {TaskPickerApi} from "./useTaskPicker"

    const props = defineProps<{picker: TaskPickerApi}>()

    const {t} = useI18n()
    const pluginsStore = usePluginsStore()

    const {
        taskPickerVisible,
        taskPickerSearch,
        hasSearch,
        pluginsLoading,
        pickerFocusedIndex,
        pickerTab,
        appFilter,
        PICKER_TABS,
        pickerHiddenCount,
        appGroups,
        displayedEntries,
        sectionLabel,
        pickerStyle,
        setPickerTab,
        insertTask,
        onPickerKeydown,
    } = props.picker
</script>

<style scoped lang="scss">
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
</style>
