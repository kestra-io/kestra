<template>
    <div class="settings-panel">
        <div class="header">
            <h6>{{ $t("filter.options") }}</h6>
            <KsButton link :icon="Close" size="small" class="close-icon" @click="$emit('close')" />
        </div>

        <div class="list">
            <div v-if="chartShown" class="row">
                <span class="label">{{ $t("filter.show chart") }}</span>
                <KsSwitch v-model="localChartVisible" />
            </div>
            <div class="row">
                <KsTooltip :content="refreshTooltip" placement="top">
                    <span class="label">{{ $t("filter.periodic refresh") }}</span>
                </KsTooltip>
                <KsSwitch v-model="periodicRefreshEnabled" />
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, inject, ref, watch} from "vue"
    import {Close} from "../utils/icons"
    import {usePeriodicRefresh} from "../composables/usePeriodicRefresh"
    import {FILTER_CONTEXT_INJECTION_KEY} from "../utils/filterInjectionKeys"

    defineEmits<{close: []}>()

    const filter = inject(FILTER_CONTEXT_INJECTION_KEY)!

    const {isEnabled: periodicRefreshEnabled, tooltip: refreshTooltip, toggleRefresh} = usePeriodicRefresh()

    const chartShown = computed(() => filter.tableOptions.value?.chart?.shown !== false)
    const localChartVisible = ref(filter.chartVisible.value)

    const refreshCallback = () => {
        if (filter.tableOptions.value?.refresh?.callback) {
            filter.tableOptions.value.refresh.callback()
        }
        filter.refreshData()
    }

    watch(() => filter.chartVisible.value, (value) => {
        localChartVisible.value = value ?? true
    })

    watch(localChartVisible, (value) => {
        filter.updateChart(value)
    })

    watch(periodicRefreshEnabled, (value) => {
        toggleRefresh(value, refreshCallback)
    }, {immediate: true})
</script>

<style lang="scss" scoped>
.settings-panel {
    display: flex;
    flex-direction: column;
    border-radius: var(--ks-radius-lg);

    .header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: var(--ks-spacing-3) var(--ks-spacing-4) var(--ks-spacing-2);
        background-color: var(--ks-bg-active);
        border-bottom: 1px solid var(--ks-border-default);

        h6 {
            margin: 0;
            font-size: var(--ks-font-size-sm);
            font-weight: 700;
        }

        :deep(.close-icon) {
            color: var(--ks-text-dim);

            &:hover {
                color: var(--ks-content-link, var(--ks-text-link));
            }
        }
    }

    .list {
        display: flex;
        flex-direction: column;
    }

    .row {
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: var(--ks-spacing-3);
        padding: var(--ks-spacing-2) var(--ks-spacing-4);
        border-bottom: 1px solid var(--ks-border-default);

        &:last-child {
            border-bottom: none;
        }

        .label {
            font-size: var(--ks-font-size-sm);
            color: var(--ks-text-primary);
        }
    }
}
</style>
