<template>
    <DataTable
        @page-changed="onPageChanged"
        ref="dataTable"
        :total="metricsTotal"
        :embed="true"
    >
        <template #navbar>
            <slot name="navbar" />
        </template>
        <template #table>
            <el-table
                :data="metrics"
                :defaultSort="{prop: 'name', order: 'ascending'}"
                tableLayout="auto"
                fixed
                @sort-change="onSort"
            >
                <template v-for="col in displayColumns" :key="col">
                    <el-table-column v-if="col === 'taskId' && showTask" prop="taskId" sortable :label="$t('task')">
                        <template #default="scope">
                            <p>{{ scope.row.taskId }}</p>
                        </template>
                    </el-table-column>

                    <el-table-column v-else-if="col === 'name'" prop="name" sortable :label="$t('name')">
                        <template #default="scope">
                            <template v-if="scope.row.type === 'timer'">
                                <Kicon><Timer /></Kicon>
                            </template>
                            <template v-else>
                                <Kicon><Counter /></Kicon>
                            </template>
                            &nbsp;<code>{{ scope.row.name }}</code>
                        </template>
                    </el-table-column>

                    <el-table-column v-else-if="col === 'value'" prop="value" sortable :label="$t('value')">
                        <template #default="scope">
                            <span v-if="scope.row.type === 'timer'">
                                {{ humanizeDuration((scope.row.value / 1000).toString()) }}
                            </span>
                            <span v-else>
                                {{ humanizeNumber(scope.row.value) }}
                            </span>
                        </template>
                    </el-table-column>

                    <el-table-column v-else-if="col === 'tags'" prop="tags" :label="$t('tags')">
                        <template #default="scope">
                            <el-tag
                                v-for="(value, key) in scope.row.tags"
                                :key="key"
                                class="tag"
                                type="info"
                                size="small"
                                disableTransitions
                            >
                                {{ key }}: <strong>{{ value }}</strong>
                            </el-tag>
                        </template>
                    </el-table-column>
                </template>
            </el-table>
        </template>
    </DataTable>
</template>

<script setup lang="ts">
    import {ref, watch} from "vue";
    import {useI18n} from "vue-i18n";

    import Timer from "vue-material-design-icons/Timer.vue";
    import Counter from "vue-material-design-icons/Numeric.vue";

    import Kicon from "../Kicon.vue";
    import DataTable from "../layout/DataTable.vue";

    import type {Execution} from "../../stores/executions";
    import {humanizeDuration, humanizeNumber} from "../../utils/filters";

    import {useExecutionsStore} from "../../stores/executions";
    
    import {useTableColumns} from "../../composables/useTableColumns";
    import {useDataTableActions} from "../../composables/useDataTableActions";

    const {t} = useI18n();

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
        optionalColumns: () => []
    });

    const localOptionalColumns = ref([
        {label: t("task"), prop: "taskId", default: true},
        {label: t("name"), prop: "name", default: true},
        {label: t("value"), prop: "value", default: true},
        {label: t("tags"), prop: "tags", default: true},
    ]);

    const {visibleColumns: displayColumns, updateVisibleColumns: updateDisplayColumns} = useTableColumns({
        columns: localOptionalColumns.value,
        storageKey: "execution-metrics"
    });

    const dataTable = ref();

    const executionsStore = useExecutionsStore();

    const metrics = ref<any[] | undefined>(undefined);
    const metricsTotal = ref<number>(0);

    const loadData = (callback?: () => void) => {
        let params: Record<string, any> = {};

        if (props.taskRunId) {
            params.taskRunId = props.taskRunId;
        }

        if (internalPageNumber.value) {
            params.page = internalPageNumber.value;
        }

        if (internalPageSize.value) {
            params.size = internalPageSize.value;
        }

        if (internalSort.value) {
            params.sort = internalSort.value;
        } else {
            params.sort = "name:asc";
        }

        executionsStore.loadMetrics({
            executionId: props.execution?.id ?? "",
            params: params,
            store: false
        }).then((response: any) => {
            metrics.value = response.results;
            metricsTotal.value = response.total;
            if (callback) {
                callback();
            }
        });
    };

    const {
        internalPageNumber,
        internalPageSize,
        internalSort,
        onPageChanged,
        onSort,
        onDataLoaded
    } = useDataTableActions({
        embed: props.embed,
        dataTableRef: dataTable,
        loadData: loadData
    });

    watch(() => props.taskRunId, () => {
        loadData(onDataLoaded);
    });

    defineExpose({
        loadData,
        onDataLoaded,
        updateDisplayColumns
    });
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
        font-size: 0.75rem;
    }
</style>