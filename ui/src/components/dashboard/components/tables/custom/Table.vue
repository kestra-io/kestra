<template>
    <template v-if="data !== undefined">
        <el-table
            :id="containerID"
            :data="data.results"
            :height="240"
            size="small"
        >
            <el-table-column
                v-for="[key, value] in Object.entries(props.chart.data.columns)"
                :label="value.displayName || key"
                :key
            >
                <template #default="scope">
                    <template v-if="value.field === 'ID'">
                        <RouterLink
                            v-if="linkData(scope.row)"
                            :to="{
                                name: 'executions/update',
                                params: {
                                    namespace: linkData(scope.row)?.NAMESPACE,
                                    flowId: linkData(scope.row)?.FLOW_ID,
                                    id: scope.row[key],
                                },
                            }"
                        >
                            <code>{{ scope.row[key].slice(0, 8) }}</code>
                        </RouterLink>
                        <code v-else>{{ scope.row[key] }}</code>
                    </template>
                    <template v-else-if="value.field === 'FLOW_ID'">
                        <RouterLink
                            v-if="linkData(scope.row)"
                            :to="{
                                name: 'flows/update',
                                params: {
                                    namespace: linkData(scope.row)?.NAMESPACE,
                                    id: linkData(scope.row)?.FLOW_ID,
                                },
                            }"
                        >
                            <code>{{ scope.row[key] }}</code>
                        </RouterLink>
                        <code v-else>{{ scope.row[key] }}</code>
                    </template>
                    <template v-else-if="value.field === 'NAMESPACE'">
                        <RouterLink
                            :to="{
                                name: 'namespaces/update',
                                params: {
                                    id: scope.row[key],
                                },
                            }"
                        >
                            <code>{{ scope.row[key] }}</code>
                        </RouterLink>
                    </template>
                    <Status
                        v-else-if="value.field === 'STATE'"
                        size="small"
                        :status="scope.row[key]"
                    />
                    <span v-else-if="value.field === 'DURATION'">{{
                        Utils.humanDuration(scope.row[key])
                    }}</span>
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
        defaultFilters: {type: Array, default: () => []},
    });

    const containerID = `${props.chart.id}__${Math.random()}`;

    const linkData = (row: Record<string, any>) => {
        const fields: Record<string, { field: string; displayName: string }> = props.chart.data.columns;

        function getField(args: Record<string, any>) {
            const result: Partial<Record<"FLOW_ID" | "NAMESPACE", any>> = {};

            for (const key in args) {
                const config = fields[key];
                if (config && (config.field === "FLOW_ID" || config.field === "NAMESPACE")) {
                    result[config.field] = args[key];
                }
            }

            return result.FLOW_ID && result.NAMESPACE ? result : undefined;
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
                filters: props.defaultFilters.concat(decodedParams?? [])
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
            const params = {filters: {...decodedParams}};

            if (props.chart.chartOptions?.pagination?.enabled) {
                params.pageNumber = currentPage.value;
                params.pageSize = pageSize.value;
            }

            data.value = await store.dispatch("dashboard/chartPreview", {
                chart: props.chart.content,
                globalFilter: {filters: props.defaultFilters.concat(decodedParams?? [])},
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
