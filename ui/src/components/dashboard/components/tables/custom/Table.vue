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
                            v-if="scope.row.namespace && scope.row.flowId"
                            :to="{
                                name: 'executions/update',
                                params: {
                                    namespace: scope.row.namespace,
                                    flowId: scope.row.flowId,
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
            data.value = await store.dispatch("dashboard/chartPreview", {
                chart: props.chart.content,
                globalFilter: {filter: decodedParams},
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
