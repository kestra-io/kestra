<template>
    <section v-if="data" id="table">
        <el-table
            :id="containerID"
            :data="data.results"
            :height="240"
            size="small"
        >
            <el-table-column
                v-for="[key, value] in Object.entries(
                    props.chart.data?.columns ?? {},
                )"
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
                            <code class="link">
                                {{ scope.row[key].slice(0, 8) }}
                            </code>
                        </RouterLink>
                        <code v-else class="link"> {{ scope.row[key] }}</code>
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
                            <code class="link">{{ scope.row[key] }}</code>
                        </RouterLink>
                        <code v-else class="link">{{ scope.row[key] }}</code>
                    </template>

                    <Namespace
                        v-else-if="value.field === 'NAMESPACE'"
                        :field="scope.row[key]"
                    />
                    <Status
                        v-else-if="value.field === 'STATE'"
                        size="small"
                        :status="scope.row[key]"
                    />
                    <Duration
                        v-else-if="value.field === 'DURATION'"
                        :field="scope.row[key]"
                    />
                    <Date
                        v-else-if="value.field.toLowerCase().includes('date')"
                        :field="scope.row[key]"
                    />

                    <span v-else>{{ scope.row[key] }}</span>
                </template>
            </el-table-column>
        </el-table>

        <Pagination
            v-if="isPaginationEnabled(props.chart)"
            :total="data.total"
            :page="currentPage"
            :size="pageSize"
            @page-changed="handlePageChange"
        />
    </section>

    <NoData v-else :text="EMPTY_TEXT" />
</template>

<script lang="ts" setup>
    import {PropType, onMounted, watch, ref, computed} from "vue";

    import type {Chart} from "../composables/useDashboards";
    import {
        isPaginationEnabled,
        useChartGenerator,
    } from "../composables/useDashboards";

    import Date from "./table/columns/Date.vue";
    import Duration from "./table/columns/Duration.vue";
    import Namespace from "./table/columns/Namespace.vue";
    import Status from "../../Status.vue";

    import Pagination from "../../layout/Pagination.vue";

    import NoData from "../../layout/NoData.vue";

    const props = defineProps({
        chart: {type: Object as PropType<Chart>, required: true},
        filters: {type: Array as PropType<string[]>, default: () => []},
        showDefault: {type: Boolean, default: false},
    });

    const data = ref();
    const {EMPTY_TEXT, generate} = useChartGenerator(props);

    import {useRoute} from "vue-router";
    const route = useRoute();

    const getData = async (ID: string) => {
        data.value = await generate(ID, pagination.value);
    };

    const currentPage = ref(1);
    const pageSize = ref(10);

    const pagination = computed(() => {
        return isPaginationEnabled(props.chart)
            ? {pageNumber: currentPage.value, pageSize: pageSize.value}
            : undefined;
    });

    const handlePageChange = async (options: { page: number; size: number }) => {
        currentPage.value = options.page;
        pageSize.value = options.size;

        getData(route.params?.id as string);
    };

    watch(route, async (changed) => getData(changed.params?.id as string));

    onMounted(async () => getData(route.params?.id as string));

    const containerID = `${props.chart.id}__${Math.random()}`;

    const linkData = (row: Record<string, any>) => {
        const fields = props.chart.data?.columns as Record<
            string,
            { field: string; displayName: string }
        >;

        function getField(args: Record<string, any>) {
            const result: Partial<Record<"FLOW_ID" | "NAMESPACE", any>> = {};

            for (const key in args) {
                const config = fields[key];
                if (
                    config &&
                    (config.field === "FLOW_ID" || config.field === "NAMESPACE")
                ) {
                    result[config.field] = args[key];
                }
            }

            return result.FLOW_ID && result.NAMESPACE ? result : undefined;
        }

        return getField(row);
    };
</script>

<style scoped lang="scss">
code.link {
    color: var(--ks-content-id);
}
</style>
