<template>
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
                    draggable="true"
                    @click="emit('addPlugin', entry)"
                    @dragstart="onDragStart($event, entry)"
                    @dragend="onDragEnd"
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
                            draggable="true"
                            @click="emit('addPlugin', entry)"
                            @dragstart="onDragStart($event, entry)"
                            @dragend="onDragEnd"
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
</template>

<script setup lang="ts">
    import {computed, ref} from "vue"
    import {useI18n} from "vue-i18n"
    import {KsTaskIcon} from "@kestra-io/design-system"
    import Magnify from "vue-material-design-icons/Magnify.vue"
    import PlusIcon from "vue-material-design-icons/Plus.vue"
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import ChevronUp from "vue-material-design-icons/ChevronUp.vue"
    import {usePluginsStore} from "../../../stores/plugins"
    import {extractPluginElements} from "../../../utils/pluginUtils"

    export interface PluginEntry {
        cls: string;
        kind: "task" | "trigger";
        group: string;
    }

    /** Key used with dataTransfer to carry plugin data across drag boundaries. */
    export const PLUGIN_DRAG_TYPE = "application/x-kestra-plugin"

    const {t} = useI18n()
    const pluginsStore = usePluginsStore()

    const emit = defineEmits<{
        (e: "addPlugin", entry: PluginEntry): void;
    }>()

    // ── Search & filter state ──

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

    // ── Plugin data ──

    const allPluginEntries = computed<PluginEntry[]>(() => {
        if (!pluginsStore.plugins) return []
        return pluginsStore.plugins.flatMap(plugin => {
            const elements = extractPluginElements(plugin)
            return Object.entries(elements).flatMap(([kind, clsList]) => {
                const normalizedKind: "task" | "trigger" = kind.toLowerCase().includes("trigger") ? "trigger" : "task"
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

    // ── Helpers ──

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

    // ── Drag & drop ──

    function onDragStart(event: DragEvent, entry: PluginEntry) {
        if (!event.dataTransfer) return
        event.dataTransfer.effectAllowed = "copy"
        event.dataTransfer.setData(PLUGIN_DRAG_TYPE, JSON.stringify(entry))
        ;(event.currentTarget as HTMLElement).classList.add("dragging")
    }

    function onDragEnd(event: DragEvent) {
        ;(event.currentTarget as HTMLElement).classList.remove("dragging")
    }
</script>

<style scoped lang="scss">
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
    cursor: grab;
    border-bottom: 1px solid var(--ks-border-secondary);
    transition: background 0.1s;

    &:active {
        cursor: grabbing;
    }

    &.dragging {
        opacity: 0.3;
    }

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
</style>
