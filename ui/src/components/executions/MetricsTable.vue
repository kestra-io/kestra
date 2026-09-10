<template>
    <KsDataTable
        ref="dataTable"
        v-model:currentPage="currentPage"
        v-model:pageSize="pageSize"
        :loadData="loadData"
        :data="hasVisibleColumns ? metrics : []"
        :total="hasVisibleColumns ? metricsTotal : 0"
        :noDataText="hasVisibleColumns ? undefined : $t('no_results.all_columns_hidden')"
        :noDataDescription="hasVisibleColumns ? undefined : $t('no_results.all_columns_hidden_description')"
        :defaultSort="{prop: 'name', order: 'ascending'}"
    >
        <template #navbar>
            <slot name="navbar" />
        </template>

        <template v-if="$slots.empty && hasVisibleColumns" #empty>
            <slot name="empty" />
        </template>

        <template v-for="col in displayColumns" :key="col">
            <KsTableColumn v-if="col === 'taskId' && showTask" prop="taskId" sortable :label="$t('task')">
                <template #default="scope">
                    <p class="m-0">
                        {{ scope.row.taskId }}
                    </p>
                </template>
            </KsTableColumn>

            <KsTableColumn v-else-if="col === 'name'" prop="name" sortable :label="$t('name')">
                <template #default="scope">
                    <template v-if="scope.row.type === 'timer'">
                        <KsIcon><Timer /></KsIcon>
                    </template>
                    <template v-else>
                        <KsIcon><Counter /></KsIcon>
                    </template>
                    &nbsp;<code>{{ scope.row.name }}</code>
                </template>
            </KsTableColumn>

            <KsTableColumn v-else-if="col === 'value'" prop="value" sortable :label="$t('value')">
                <template #default="scope">
                    <span v-if="scope.row.type === 'timer'">
                        {{ humanizeDuration(scope.row.value / 1000) }}
                    </span>
                    <span v-else>
                        {{ humanizeNumber(scope.row.value) }}
                    </span>
                </template>
            </KsTableColumn>

            <KsTableColumn v-else-if="col === 'tags'" prop="tags" :label="$t('tags')">
                <template #default="scope">
                    <KsTag
                        v-for="(value, key) in scope.row.tags"
                        :key="key"
                        class="tag"
                        type="info"
                        size="small"
                        disableTransitions
                    >
                        {{ key }}: <strong>{{ value }}</strong>
                    </KsTag>
                </template>
            </KsTableColumn>
        </template>

        <KsTableColumn className="row-action">
            <template #default="scope">
                <KsIconButton :tooltip="$t('view metrics')" @click="openChart(scope.row)">
                    <ChartAreaspline />
                </KsIconButton>
            </template>
        </KsTableColumn>
    </KsDataTable>

    <KsDrawer v-model="chartOpen" :title="chartMetricName">
        <KsBar
            class="chart"
            :data="chartSeries"
            :categories="chartCategories"
            :loading="chartLoading"
        />
    </KsDrawer>
</template>

<script setup lang="ts">
    import {computed, ref, useTemplateRef, watch} from "vue"
    import {useI18n} from "vue-i18n"

    import Timer from "vue-material-design-icons/Timer.vue"
    import Counter from "vue-material-design-icons/Numeric.vue"
    import ChartAreaspline from "vue-material-design-icons/ChartAreaspline.vue"

    import type {KsChartSeriesItem} from "@kestra-io/design-system"

    import * as MetricsAPI from "@kestra-io/kestra-sdk/metrics"

    import type {Execution} from "../../stores/executions"
    import {date, humanizeDuration, humanizeNumber} from "../../utils/filters"

    import {useTableColumns} from "@kestra-io/design-system"

    const {t} = useI18n()

    const props = withDefaults(defineProps<{
        embed?: boolean;
        taskRunId?: string;
        showTask?: boolean;
        execution?: Execution;
        optionalColumns?: any[];
    }>(), {
        embed: true,
        taskRunId: undefined,
        showTask: false,
        execution: undefined,
        optionalColumns: () => [],
    })

    const localOptionalColumns = ref([
        {label: t("task"), prop: "taskId", default: true},
        {label: t("name"), prop: "name", default: true},
        {label: t("value"), prop: "value", default: true},
        {label: t("tags"), prop: "tags", default: true},
    ])

    const {visibleColumns: displayColumns, updateVisibleColumns: updateDisplayColumns} = useTableColumns({
        columns: localOptionalColumns.value,
        storageKey: "execution-metrics",
    })

    const hasVisibleColumns = computed(() => displayColumns.value.length > 0)

    const metrics = ref<any[] | undefined>(undefined)
    const metricsTotal = ref<number>(0)
    const currentPage = ref(1)
    const pageSize = ref(25)

    const dataTable = useTemplateRef("dataTable")

    const loadData = async ({page, size, sort}: {page?: number; size?: number; sort?: string} = {}) => {
        const response = await MetricsAPI.searchByExecution({
            executionId: props.execution?.id ?? "",
            taskRunId: props.taskRunId,
            page,
            size,
            sort: [sort ?? "name:asc"],
        })
        metrics.value = response.results
        metricsTotal.value = response.total
    }

    watch(() => props.taskRunId, () => {
        dataTable.value?.resetAndReload()
    })

    const chartOpen = ref(false)
    const chartLoading = ref(false)
    const chartMetricName = ref("")
    const chartCategories = ref<string[]>([])
    const chartSeries = ref<KsChartSeriesItem[] | null>(null)

    const openChart = async (row: {name: string; type: string; taskId?: string}) => {
        chartMetricName.value = row.name
        chartOpen.value = true
        chartLoading.value = true
        chartSeries.value = null
        chartCategories.value = []

        try {
            const response = await MetricsAPI.searchByExecution({
                executionId: props.execution?.id ?? "",
                taskRunId: props.taskRunId,
                taskId: props.taskRunId ? undefined : row.taskId,
                size: 1000,
                sort: ["timestamp:asc"],
            })
            const entries = (response.results ?? []).filter(
                (entry: any) => entry.name === row.name && entry.taskId === row.taskId,
            )

            const labelOccurrences = new Map<string, number>()
            chartCategories.value = entries.map((entry: any) => {
                const label = date(entry.timestamp, "HH:mm:ss.SSS")
                const occurrence = (labelOccurrences.get(label) ?? 0) + 1
                labelOccurrences.set(label, occurrence)
                return occurrence === 1 ? label : `${label} (${occurrence})`
            })

            chartSeries.value = [{
                name: row.name,
                data: entries.map((entry: any) => entry.type === "timer" ? entry.value / 1000 : entry.value),
            }]
        } finally {
            chartLoading.value = false
        }
    }

    defineExpose({
        loadData,
        updateDisplayColumns,
        reload: () => dataTable.value?.reload(),
    })
</script>

<style lang="scss" scoped>
    .tag {
        display: inline-flex;
        align-items: center;
        padding: 3px 6px;
        border-radius: 4px;
        border: 1px solid var(--ks-border-info);
        background-color: var(--ks-bg-badge);
        color: var(--ks-text-info);
        font-size: var(--ks-font-size-xs);
    }

    .chart {
        height: 100%;
        min-height: 200px;
    }
</style>
