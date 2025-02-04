<template>
    <template v-if="data !== undefined">
        <el-table :id="containerID" :data="data.results" :height="240">
            <el-table-column
                v-for="(column, index) in Object.entries(props.chart.data.columns)"
                :key="index"
                :label="column[0]"
            >
                <template #default="scope">
                    {{
                        column[1].field === "DURATION" ? Utils.humanDuration(scope.row[column[0]]) : scope.row[column[0]]
                    }}
                </template>
            </el-table-column>
        </el-table>
        <el-pagination
            v-if="props.chart.chartOptions?.pagination?.enabled"
            :current-page="currentPage"
            :page-size="pageSize"
            :total="data.total"
            @current-change="handlePageChange"
            @size-change="handlePageSizeChange"
            layout="prev, pager, next, sizes"
            :page-sizes="[5, 10, 20, 50]"
            :pager-count="5"
            class="mt-3"
        />
    </template>

    <NoData v-else :text="t('custom_dashboard_empty')" />
</template>

<script lang="ts" setup>
    import {computed, onMounted, ref, watch} from "vue";

    import {useI18n} from "vue-i18n";
    import NoData from "../../../../layout/NoData.vue";

    import {useStore} from "vuex";
    import moment from "moment";

    import {useRoute} from "vue-router";
    import {Utils} from "@kestra-io/ui-libs";

    const {t} = useI18n({useScope: "global"});

    const store = useStore();

    const route = useRoute();

    defineOptions({inheritAttrs: false});
    const props = defineProps({
        identifier: {type: Number, required: true},
        chart: {type: Object, required: true},
        isPreview: {type: Boolean, required: false, default: false}
    });

    const containerID = `${props.chart.id}__${Math.random()}`;

    const dashboard = computed(() => store.state.dashboard.dashboard);

    const currentPage = ref(1);
    const pageSize = ref(5);

    const handlePageChange = (page) => {
        currentPage.value = page;
        generate();
    };

    const handlePageSizeChange = (size) => {
        currentPage.value = 1;
        pageSize.value = size;
        generate();
    };

    const data = ref();
    const generate = async () => {
        if (!props.isPreview) {
            const params = {
                id: dashboard.value.id,
                chartId: props.chart.id,
                startDate: route.query.timeRange
                    ? moment()
                        .subtract(
                            moment.duration(route.query.timeRange).as("milliseconds"),
                        )
                        .toISOString(true)
                    : route.query.startDate ||
                        moment()
                            .subtract(moment.duration("PT720H").as("milliseconds"))
                            .toISOString(true),
                endDate: route.query.timeRange
                    ? moment().toISOString(true)
                    : route.query.endDate || moment().toISOString(true),
            };
            if (route.query.namespace) {
                params.namespace = route.query.namespace;
            }
            if (route.query.labels) {
                params.labels = Object.fromEntries(route.query.labels.map(l => l.split(":")));
            }

            if (props.chart.chartOptions?.pagination?.enabled) {
                params.pageNumber = currentPage.value;
                params.pageSize = pageSize.value;
            }

            data.value = await store.dispatch("dashboard/generate", params);
        } else {
            data.value = await store.dispatch("dashboard/chartPreview", props.chart.content)
        }
    };

    watch(route, async () => await generate());
    watch(
        () => props.identifier,
        () => generate(),
    );
    onMounted(() => generate());
</script>
