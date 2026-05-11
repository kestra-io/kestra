<template>
    <KsDataTable
        ref="dataTable"
        :loadData="loadData"
        :data="metrics"
        :total="metricsTotal"
        :defaultSort="{prop: 'name', order: 'ascending'}"
    >
        <template #navbar>
            <slot name="navbar" />
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
                        {{ humanizeDuration((scope.row.value / 1000).toString()) }}
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


            <KsTableColumn className="row-action">
                <template #default="scope">
                    <router-link
                        :to="{name: 'flows/update',
                              params: {namespace: scope.row.namespace, id: scope.row.flowId, tab: 'metrics', tenant: scope.row.tenant},
                              query: {'filters[q][EQUALS]': scope.row.name}
                        }"
                    >
                        <KsIconButton>
                            <ChartAreaspline />
                        </KsIconButton>
                    </router-link>
                </template>
            </KsTableColumn>
        </template>
    </KsDataTable>
</template>

<script setup lang="ts">
    import {ref, useTemplateRef, watch} from "vue"
    import {useI18n} from "vue-i18n"

    import Timer from "vue-material-design-icons/Timer.vue"
    import Counter from "vue-material-design-icons/Numeric.vue"
    import ChartAreaspline from "vue-material-design-icons/ChartAreaspline.vue"


    import type {Execution} from "../../stores/executions"
    import {humanizeDuration, humanizeNumber} from "../../utils/filters"

    import {useExecutionsStore} from "../../stores/executions"

    import {useTableColumns} from "../../composables/useTableColumns"

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

    const executionsStore = useExecutionsStore()

    const metrics = ref<any[] | undefined>(undefined)
    const metricsTotal = ref<number>(0)

    const dataTable = useTemplateRef("dataTable")

    const loadData = async ({page, size, sort}: {page?: number; size?: number; sort?: string} = {}) => {
        let params: Record<string, any> = {}

        if (props.taskRunId) {
            params.taskRunId = props.taskRunId
        }

        if (page) {
            params.page = page
        }

        if (size) {
            params.size = size
        }

        params.sort = sort ?? "name:asc"

        const response: any = await executionsStore.loadMetrics({
            executionId: props.execution?.id ?? "",
            params: params,
            store: false,
        })
        metrics.value = response.results
        metricsTotal.value = response.total
    }

    watch(() => props.taskRunId, () => {
        dataTable.value?.resetAndReload()
    })

    defineExpose({
        loadData,
        updateDisplayColumns,
    })
</script>

<style lang="scss" scoped>
    .tag {
        display: inline-flex;
        align-items: center;
        padding: 3px 6px;
        border-radius: 4px;
        border: 1px solid var(--ks-badge-border);
        background-color: var(--ks-badge-background);
        color: var(--ks-badge-content);
        font-size: var(--ks-font-size-xs);
    }
</style>
