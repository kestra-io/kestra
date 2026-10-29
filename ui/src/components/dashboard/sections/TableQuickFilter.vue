<template>
    <div class="quick-filters">
        <template v-if="hasQuickFilters">
            <button
                v-for="tab in QUICK_FILTER_TABS"
                :key="tab.key"
                class="quick-filter-tab"
                :class="{active: activeTab === tab.key}"
                :disabled="isTabDisabled(tab.key)"
                :style="{'--tab-color': `var(${tab.token})`}"
                @click="selectTab(tab.key)"
            >
                {{ $t(`dashboards.quick_filters.${tab.key}`) }}
                <KsBadge
                    v-if="counts?.[tab.key]"
                    :value="counts[tab.key]"
                    :max="MAX_BADGE_COUNT"
                    type="neutral"
                    inline
                    class="tab-count"
                />
                <Motion
                    v-if="activeTab === tab.key"
                    as="span"
                    class="tab-indicator"
                    layoutId="tab-indicator"
                    :transition="{type: 'spring', stiffness: 400, damping: 30}"
                />
            </button>
        </template>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue"

    import {Motion} from "motion-v"
    import {QueryFilter} from "@kestra-io/kestra-sdk"

    import type {Chart} from "../types.ts"
    import {
        QUICK_FILTER_TABS,
        QuickFilterTabKey,
        hasQuickFilters as chartHasQuickFilters,
        stateFilterForTab,
    } from "./quickFilters"

    const MAX_BADGE_COUNT = 999

    const props = defineProps<{
        chart: Chart;
        /** Number of executions behind each tab. A tab with no entry is still loading and stays selectable. */
        counts?: Partial<Record<QuickFilterTabKey, number>>;
    }>()

    const emit = defineEmits<{change: [filter: QueryFilter | null, tab: QuickFilterTabKey]}>()


    const hasQuickFilters = computed(() => chartHasQuickFilters(props.chart))

    const activeTab = ref<QuickFilterTabKey>("all")

    // The active tab stays enabled even if a refresh empties it, otherwise the selection would look broken.
    const isTabDisabled = (key: QuickFilterTabKey) =>
        activeTab.value !== key && props.counts?.[key] === 0

    const selectTab = (key: QuickFilterTabKey) => {
        if (activeTab.value === key) return
        activeTab.value = key
        emit("change", stateFilterForTab(props.chart, key), key)
    }
</script>

<style scoped>
    .quick-filters {
        display: flex;
        align-items: center;
        min-height: var(--ks-spacing-6);
        overflow-x: auto;
        scrollbar-width: none;

        &::-webkit-scrollbar {
            display: none;
        }
    }

    .quick-filter-tab {
        display: inline-flex;
        align-items: center;
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        font-size: var(--ks-font-size-xs);
        font-weight: var(--ks-font-weight-regular);
        color: var(--ks-text-secondary);
        background: none;
        border: none;
        cursor: pointer;
        white-space: nowrap;
        position: relative;
        transition: color var(--ks-duration-fast);

        gap: var(--ks-spacing-2);

        &:hover:not(:disabled),
        &.active {
            color: var(--tab-color);
        }

        &:disabled {
            cursor: not-allowed;
            opacity: 0.5;
        }
    }

    .tab-indicator {
        position: absolute;
        bottom: 0;
        left: 0;
        right: 0;
        height: var(--ks-border-width-base);
        background: var(--tab-color);
        border-radius: var(--ks-radius-xs) var(--ks-radius-xs) 0 0;
    }
</style>
