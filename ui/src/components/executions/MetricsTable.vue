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
                <el-table-column v-if="showTask" prop="taskId" sortable :label="$t('task')">
                    <template #default="scope">
                        <p>{{ scope.row.taskId }}</p>
                    </template>
                </el-table-column>

                <el-table-column prop="name" sortable :label="$t('name')">
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

                <el-table-column prop="value" sortable :label="$t('value')">
                    <template #default="scope">
                        <span v-if="scope.row.type === 'timer'">
                            {{ Utils.humanDuration(scope.row.value / 1000) }}
                        </span>
                        <span v-else>
                            {{ humanizeNumber(scope.row.value) }}
                        </span>
                    </template>
                </el-table-column>

                <el-table-column prop="tags" :label="$t('tags')">
                    <template #default="scope">
                        <el-tag
                            v-for="(value, key) in scope.row.tags"
                            :key="key"
                            class="me-1"
                            type="info"
                            size="small"
                            disableTransitions
                        >
                            {{ key }}: <strong>{{ value }}</strong>
                        </el-tag>
                    </template>
                </el-table-column>
            </el-table>
        </template>
    </DataTable>
</template>

<script setup lang="ts">
    import {ref, watch, onMounted} from "vue";
    import Kicon from "../Kicon.vue";
    import Timer from "vue-material-design-icons/Timer.vue";
    import Counter from "vue-material-design-icons/Numeric.vue";
    import DataTable from "../layout/DataTable.vue";
    import {useExecutionsStore} from "../../stores/executions";
    import Utils from "../../utils/utils";
    import {humanizeNumber} from "../../utils/filters";

    const props = defineProps<{
        embed?: boolean;
        taskRunId?: string;
        showTask?: boolean;
        execution: Record<string, any>;
    }>();

    const metrics = ref<any[]>();
    const metricsTotal = ref<number>(0);

    const executionsStore = useExecutionsStore();

    // Pagination/sorting state
    const internalPageNumber = ref<number>(1);
    const internalPageSize = ref<number>(25);
    const internalSort = ref<string>("name:asc");

    function onPageChanged({page, size}: { page: number; size: number }) {
        internalPageNumber.value = page;
        internalPageSize.value = size;
        loadData(() => {});
    }

    function onSort({prop, order}: { prop: string; order: string }) {
        internalSort.value = `${prop}:${order === "descending" ? "desc" : "asc"}`;
        loadData();
    }

    async function loadData(callback?: () => void) {
        const params: Record<string, any> = {};

        if (props.taskRunId) params.taskRunId = props.taskRunId;
        if (internalPageNumber.value) params.page = internalPageNumber.value;
        if (internalPageSize.value) params.size = internalPageSize.value;
        if (internalSort.value) params.sort = internalSort.value;
        else params.sort = "name:asc";

        const metricsResult = await executionsStore.loadMetrics({
            executionId: props.execution.id,
            params,
            store: false
        });
        metrics.value = metricsResult.results;
        metricsTotal.value = metricsResult.total;
        callback?.();
    }

    watch(
        () => props.taskRunId,
        () => {
            loadData();
        }
    );

    onMounted(() => {
        loadData();
    });

    defineExpose({
        loadData,
    });
</script>
