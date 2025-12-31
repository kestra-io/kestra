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

    import CustomColumns from "../segments/CustomColumns.vue";

    import {CogOutline} from "../utils/icons";

    import {FILTER_CONTEXT_INJECTION_KEY} from "../utils/filterInjectionKeys";

    const filter = inject(FILTER_CONTEXT_INJECTION_KEY)!;

    const isColumnsPanelVisible = ref(false);
    const localChartVisible = ref(filter.chartVisible.value);

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
