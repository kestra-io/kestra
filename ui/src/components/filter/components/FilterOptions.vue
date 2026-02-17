<template>
    <div class="expand-panel">
        <div class="options-row">
            <div class="options-left">
                <div v-if="filter.tableOptions.value?.chart?.shown !== false" class="option-item">
                    <span class="option-label">{{ $t("filter.show chart") }}</span>
                    <el-switch 
                        v-model="localChartVisible"
                    />
                </div>
            </div>

            <div class="options-right">
                <div class="option-item">
                    <el-switch 
                        v-model="periodicRefreshEnabled"
                    />
                    <Kicon :tooltip="refreshTooltip" placement="top">
                        <span class="option-label periodic">{{ $t("filter.periodic refresh") }}</span>
                    </Kicon>
                </div>

                <el-popover
                    v-if="filter.tableOptions.value?.columns?.shown !== false"
                    v-model:visible="isColumnsPanelVisible"
                    placement="bottom-end"
                    trigger="click"
                    :width="300"
                    :popperClass="'p-0'"
                    :showArrow="false"
                    @hide="isColumnsPanelVisible = false"
                >
                    <template #reference>
                        <el-button size="default" class="columns-button" :icon="CogOutline">
                            <el-tooltip :content="$t('filter.customize columns')" placement="top" effect="light">
                                <span>{{ $t("filter.columns") }}</span>
                            </el-tooltip>
                        </el-button>
                    </template>

                    <CustomColumns
                        :columns="filter.properties.value?.columns ?? []"
                        :visibleColumns="filter.properties.value?.displayColumns ?? []"
                        :storageKey="filter.properties.value?.storageKey ?? ''"
                        @update-columns="filter.updateProperties"
                        @close="isColumnsPanelVisible = false"
                    />
                </el-popover>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {ref, inject, watch} from "vue";

    import Kicon from "../../Kicon.vue";
    import CustomColumns from "../segments/CustomColumns.vue";

    import {CogOutline} from "../utils/icons";

    import {usePeriodicRefresh} from "../composables/usePeriodicRefresh";
    import {FILTER_CONTEXT_INJECTION_KEY} from "../utils/filterInjectionKeys";

    const filter = inject(FILTER_CONTEXT_INJECTION_KEY)!;

    const {isEnabled: periodicRefreshEnabled, tooltip: refreshTooltip, toggleRefresh} = usePeriodicRefresh();

    const isColumnsPanelVisible = ref(false);
    const localChartVisible = ref(filter.chartVisible.value);

    const refreshCallback = () => {
        if (filter.tableOptions.value?.refresh?.callback) {
            filter.tableOptions.value.refresh.callback();
        }
        filter.refreshData();
    };

    watch(
        () => filter.chartVisible.value,
        (newVal) => {
            localChartVisible.value = newVal ?? true;
        }
    );

    watch(
        localChartVisible,
        (newVal) => {
            filter.updateChart(newVal);
        }
    );

    watch(
        periodicRefreshEnabled,
        (newVal) => {
            toggleRefresh(newVal, refreshCallback);
        }, {immediate: true}
    );
</script>

<style lang="scss" scoped>
.expand-panel {
    animation: slideDown 0.2s ease-out;
    border-top: 1px solid var(--ks-border-secondary);
    padding-top: 0.5rem;

    .options-row {
        display: flex;
        justify-content: space-between;
        align-items: center;

        .options-left,
        .options-right {
            display: flex;
            align-items: center;

            .option-item {
                display: flex;
                align-items: center;

                .option-label {
                    font-weight: 500;
                    font-size: 0.875rem;
                    margin: 0 6px;
                }

                .periodic {
                    margin-right: 0;
                }
            }
        }

        .options-right {
            gap: 0.5rem;
        }
    }

    .columns-button {
        background-color: transparent;
        border: none;
        box-shadow: none;
        margin: 0;
        padding: 0.25rem 0.5rem;
        font-size: 14px;

        :deep(svg) {
            color: var(--ks-content-tertiary);
        }

        &:hover {
            background-color: var(--ks-tag-background);
        }
    }
}

@keyframes slideDown {
    from {
        opacity: 0;
        transform: translateY(-10px);
    }
    to {
        opacity: 1;
        transform: translateY(0);
    }
}
</style>
