<template>
    <template v-if="data !== undefined">
        <el-table
            :id="containerID"
            :data="data.results"
            :height="240"
            size="small"
        >
            <el-table-column
                v-for="key in Object.keys(props.chart.data.columns)"
                :label="key"
                :key
            >
                <template #default="scope">
                    <template v-if="key === 'id'">
                        <RouterLink
                            v-if="showLink(scope.row)"
                            :to="{
                                name: 'executions/update',
                                params: {
                                    namespace: showLink(scope.row)?.NAMESPACE,
                                    flowId: showLink(scope.row)?.FLOW_ID,
                                    id: scope.row.id,
                                },
                            }"
                        >
                            <code>{{ scope.row.id.slice(0, 8) }}</code>
                        </RouterLink>
                        <code v-else>{{ scope.row.id }}</code>
                    </template>
                    <Status v-else-if="key === 'state'" size="small" :status="scope.row[key]" />
                    <span v-else-if="key === 'duration'">{{ Utils.humanDuration(scope.row[key]) }}</span>
                    <span v-else>{{ scope.row[key] }}</span>
                </template>
            </el-table-column>
        </el-table>
        <Pagination
            v-if="props.chart.chartOptions?.pagination?.enabled"
            :total="data.total"
            :size="pageSize"
            :page="currentPage"
            @page-changed="handlePageChange"
        />
    </template>

    <NoData v-else :text="t('custom_dashboard_empty')" />
</template>

<script lang="ts" setup>
    import {onMounted, ref, watch} from "vue";

    import {useI18n} from "vue-i18n";
    import Status from "../../../../Status.vue";
    import NoData from "../../../../layout/NoData.vue";
    import Pagination from "../../../../layout/Pagination.vue";

    import {useStore} from "vuex";

    import {useRoute} from "vue-router";
    import {Utils} from "@kestra-io/ui-libs";
    import {decodeSearchParams} from "../../../../filter/utils/helpers.ts";

    const {t} = useI18n({useScope: "global"});

    const store = useStore();

    const route = useRoute();

    defineOptions({inheritAttrs: false});
    const props = defineProps({
        chart: {type: Object, required: true},
        showDefault: {type: Boolean, default: false},
    });

    const containerID = `${props.chart.id}__${Math.random()}`;

    const showLink = (row: Record<string, any>) => {
        const fields: Record<string, { field: string; displayName: string }> = props.chart.data.columns;

        function getField(args: Record<string, any>) {
            const result: Partial<Record<"FLOW_ID" | "NAMESPACE", any>> = {};

            for (const key in args) {
                const config = fields[key];
                if (config && (config.field === "FLOW_ID" || config.field === "NAMESPACE")) {
                    result[config.field] = args[key];
                }
            }

            return Object.keys(result).length > 0 ? result : undefined;
        }

        return getField(row);
    };

    const currentPage = ref(1);
    const pageSize = ref(10);

    const handlePageChange = (options) => {
        currentPage.value = options.page;
        pageSize.value = options.size;
        generate(route.params.id);
    };

    const data = ref();
    const generate = async (id) => {
        let decodedParams = decodeSearchParams(route.query, undefined, []);
        if (!props.showDefault) {
            let params = {
                id,
                chartId: props.chart.id,
            };

            if (props.chart.chartOptions?.pagination?.enabled) {
                params.pageNumber = currentPage.value;
                params.pageSize = pageSize.value;
            }
            if (decodedParams) {
                params = {...params, filters: decodedParams};
            }
            data.value = await store.dispatch("dashboard/generate", params);
        } else {
            let filter = {...decodedParams}

            if (props.chart.chartOptions?.pagination?.enabled) {
                filter.pageNumber = currentPage.value;
                filter.pageSize = pageSize.value;
            }

            data.value = await store.dispatch("dashboard/chartPreview", {
                chart: props.chart.content,
                globalFilter: {filter},
            });
        }
    };

    watch(route, async (route) => await generate(route.params?.id));
    onMounted(() => generate(route.params.id));
</script>

<style lang="scss" scoped>
code {
    color: var(--ks-content-id);
}
</style>
