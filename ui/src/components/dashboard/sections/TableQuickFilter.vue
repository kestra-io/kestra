<template>
    <div class="quick-filters">
        <template v-if="hasQuickFilters">
            <button
                v-for="tab in QUICK_FILTER_TABS"
                :key="tab.key"
                class="quick-filter-tab"
                :class="{active: activeTab === tab.key}"
                :style="{'--tab-color': `var(${tab.token})`}"
                @click="selectTab(tab.key)"
            >
                {{ t(`dashboards.quick_filters.${tab.key}`) }}
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
    import {useI18n} from "vue-i18n"

    import {Motion} from "motion-v"

    import type {Chart} from "../types.ts"
    import {FilterObject} from "../../../utils/filters"
    import {
        QUICK_FILTER_TABS,
        QuickFilterTabKey,
        hasQuickFilters as chartHasQuickFilters,
        stateFilterForTab,
    } from "./quickFilters"

    const props = defineProps<{chart: Chart}>()

    const emit = defineEmits<{change: [filter: FilterObject | null, tab: QuickFilterTabKey]}>()

    const {t} = useI18n({useScope: "global"})

    const hasQuickFilters = computed(() => chartHasQuickFilters(props.chart))

    const activeTab = ref<QuickFilterTabKey>("all")

    const selectTab = (key: QuickFilterTabKey) => {
        if (activeTab.value === key) return
        activeTab.value = key
        emit("change", stateFilterForTab(props.chart, key), key)
    }
</script>

<style scoped lang="scss">
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

        &:hover,
        &.active {
            color: var(--tab-color);
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
