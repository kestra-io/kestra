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

    const QUICK_FILTER_TABS = [
        {
            key: "all",
            token: "--ks-text-link",
            states: [] as string[],
        },
        {
            key: "running",
            token: "--ks-status-running",
            states: ["SUBMITTED", "CREATED", "RESTARTED", "QUEUED", "RUNNING", "RETRYING", "KILLING"],
        },
        {
            key: "paused",
            token: "--ks-status-paused",
            states: ["PAUSED", "BREAKPOINT"],
        },
        {
            key: "success",
            token: "--ks-status-success",
            states: ["SUCCESS"],
        },
        {
            key: "warning",
            token: "--ks-status-warning",
            states: ["WARNING"],
        },
        {
            key: "failed",
            token: "--ks-status-error",
            states: ["FAILED", "KILLED", "CANCELLED", "SKIPPED", "RETRIED"],
        },
    ] as const

    type TabKey = (typeof QUICK_FILTER_TABS)[number]["key"]

    const props = defineProps<{chart: Chart}>()

    const emit = defineEmits<{change: [filter: FilterObject | null, tab: TabKey]}>()

    const {t} = useI18n({useScope: "global"})

    const EXECUTIONS_DATA_TYPE = "io.kestra.plugin.core.dashboard.data.Executions"

    const hasQuickFilters = computed(() => {
        if (props.chart.data?.type !== EXECUTIONS_DATA_TYPE) return false
        const columns = props.chart.data?.columns ?? {}
        return Object.values(columns).some((col: Record<string, any>) => col.field === "STATE")
    })

    const activeTab = ref<TabKey>("all")

    const stateFilter = (key: TabKey): FilterObject | null => {
        const tab = QUICK_FILTER_TABS.find((t) => t.key === key)
        if (!tab?.states.length) return null
        return {field: "state", operation: "IN", value: [...tab.states]}
    }

    const selectTab = (key: TabKey) => {
        if (activeTab.value === key) return
        activeTab.value = key
        emit("change", stateFilter(key), key)
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
