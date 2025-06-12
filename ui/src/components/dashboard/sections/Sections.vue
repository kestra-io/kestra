<template>
    <section id="charts" :class="{padding}">
        <el-row :gutter="16">
            <el-col
                v-for="chart in props.charts"
                :key="`chart__${chart.id}`"
                :xs="24"
                :sm="(chart.chartOptions?.width || 6) * 4"
                :md="(chart.chartOptions?.width || 6) * 2"
            >
                <div class="d-flex flex-column">
                    <p v-if="!isKPIChart(chart.type)">
                        <span class="fs-6 fw-bold">{{
                            labels(chart).title
                        }}</span>
                        <template v-if="labels(chart)?.description">
                            <br>
                            <small class="fw-light">
                                {{ labels(chart).description }}
                            </small>
                        </template>
                    </p>

                    <div class="flex-grow-1">
                        <component
                            :is="TYPES[chart.type as keyof typeof TYPES]"
                            :chart
                            :filters
                            :show-default="props.showDefault"
                        />
                    </div>
                </div>
            </el-col>
        </el-row>
    </section>
</template>

<script setup lang="ts">
    import {onMounted, ref} from "vue";

    import type {Chart} from "../composables/useDashboards";
    import {TYPES, isKPIChart, getChartTitle} from "../composables/useDashboards";

    import {useRoute, useRouter} from "vue-router";
    const route = useRoute();
    const router = useRouter();

    const props = defineProps<{
        charts?: Chart[];
        showDefault?: boolean;
        padding?: boolean;
    }>();

    const labels = (chart: Chart) => ({
        title: getChartTitle(chart),
        description: chart?.chartOptions?.description,
    });

    const filters = ref<{ field: string; operation: string; value: string | string[] }[]>([]);
    onMounted(() => {
        const dateTimeKeys = ["startDate", "endDate", "timeRange"];

        // Default to the last 7 days if no time range is set
        if (!Object.keys(route.query).some((key) => dateTimeKeys.some((dateTimeKey) => key.includes(dateTimeKey)))) {
            router.push({query: {...route.query, "filters[timeRange][EQUALS]": "PT168H"}});
        }

        if (route.name === "flows/update") {
            filters.value.push({field: "namespace", operation: "EQUALS", value: route.params.namespace});
            filters.value.push({field: "flowId", operation: "EQUALS", value: route.params.id})
        }

        if (route.name === "namespaces/update") {
            filters.value.push({field: "namespace", operation: "EQUALS", value: route.params.id});
        }
    });
</script>

<style scoped lang="scss">
@import "@kestra-io/ui-libs/src/scss/variables";

section#charts {
    &.padding {
        padding: 0 2rem 1rem;
    }

    & .el-row .el-col {
        margin-bottom: 1rem;

        & > div {
            height: 100%;
            padding: 1.5rem;
            background: var(--ks-background-card);
            border: 1px solid var(--ks-border-primary);
            border-radius: $border-radius;
            box-shadow: 0px 2px 4px 0px var(--ks-card-shadow);
        }
    }
}
</style>
