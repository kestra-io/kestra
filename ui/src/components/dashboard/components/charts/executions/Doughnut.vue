<template>
    <div class="h-100 p-4">
        <span class="fs-6 fw-bold">
            {{ t("dashboard.total_executions") }}
        </span>

        <div
            class="d-flex flex-row align-items-center justify-content-center h-100"
        >
            <div class="w-75">
                <Doughnut
                    v-if="loading"
                    :data="skeletonData"
                    :options="skeletonOptions"
                    class="tall"
                />
                <Doughnut
                    v-else-if="total > 0"
                    :data="parsedData"
                    :options="options"
                    :plugins="[totalsLegend, centerPlugin, thicknessPlugin]"
                    class="tall"
                />
                <NoData v-else />
            </div>
            <div v-if="total > 0" id="totals" />
        </div>
    </div>
</template>

<script setup>
    import {computed} from "vue";
    import {useI18n} from "vue-i18n";
    import {useRouter} from "vue-router";

    import {Doughnut} from "vue-chartjs";

    import {totalsLegend} from "../legend.js";
    import {useTheme} from "../../../../../utils/utils.js";
    import {defaultConfig} from "../../../../../utils/charts.js";
    import {useScheme} from "../../../../../utils/scheme.js";

    const router = useRouter();
    const scheme = useScheme();

    import NoData from "../../../../layout/NoData.vue";

    const {t} = useI18n({useScope: "global"});

    const props = defineProps({
        data: {
            type: Object,
            required: true,
        },
        total: {
            type: Number,
            required: true,
        },
        loading: {
            type: Boolean,
            default: false
        }
    });

    const theme = useTheme();

    const parsedData = computed(() => {
        let stateCounts = Object.create(null);

        props.data.forEach((value) => {
            Object.keys(value.executionCounts).forEach((state) => {
                if (stateCounts[state] === undefined) {
                    stateCounts[state] = 0;
                }

                stateCounts[state] += value.executionCounts[state];
            });
        });

        const labels = Object.keys(stateCounts);
        const data = labels.map((state) => stateCounts[state]);
        const backgroundColor = labels.map((state) => scheme.value[state]);

        const maxDataValue = Math.max(...data);
        const thicknessScale = data.map(
            (value) => 21 + (value / maxDataValue) * 28,
        );

        return {
            labels,
            datasets: [{data, backgroundColor, thicknessScale, borderWidth: 0}],
        };
    });

    const options = computed(() =>
        defaultConfig({
            plugins: {
                totalsLegend: {
                    containerID: "totals",
                },
                tooltip: {
                    enabled: true,
                    intersect: true,
                    filter: (value) => value.raw,
                    callbacks: {
                        title: () => "",
                        label: (value) =>
                            `${value.raw} ${value.label.toLowerCase().capitalize()}`,
                    },
                },
            },
            onClick: (e, elements) => {
                if (elements.length > 0) {
                    const index = elements[0].index;
                    const state = parsedData.value.labels[index];
                    router.push({
                        name: "executions/list",
                        query: {
                            state: state,
                            scope: "USER",
                            size: 100,
                            page: 1,
                        },
                    });
                }
            },
        }, theme.value),
    );

    const centerPlugin = computed(() => ({
        id: "centerPlugin",
        beforeDraw(chart) {
            const darkTheme = theme.value === "dark";

            const ctx = chart.ctx;
            const dataset = chart.data.datasets[0];
            const total = dataset.data.reduce((acc, val) => acc + val, 0);
            const centerX = chart.width / 2;
            const centerY = chart.height / 2;

            ctx.save();
            ctx.font = "700 16px Public Sans";
            ctx.textAlign = "center";
            ctx.textBaseline = "middle";
            ctx.fillStyle = darkTheme ? "#FFFFFF" : "#000000";

            ctx.fillText(total, centerX, centerY);

            ctx.restore();
        },
    }));

    const thicknessPlugin = {
        id: "thicknessPlugin",
        beforeDatasetsDraw(chart) {
            const {ctx} = chart;
            const dataset = chart.data.datasets[0];
            const meta = chart.getDatasetMeta(0);

            const thicknessScale = dataset.thicknessScale;

            meta.data.forEach((arc, index) => {
                const baseRadius = arc.innerRadius;
                const additionalThickness = thicknessScale[index];
                arc.outerRadius = baseRadius + additionalThickness;
                arc.innerRadius = baseRadius;

                arc.draw(ctx);
            });
        },
    };

    const skeletonData = computed(() => {
        const barColor = theme.value === "dark" 
            ? "rgba(255, 255, 255, 0.08)"
            : "rgba(0, 0, 0, 0.06)";

        return {
            labels: ["Loading"],
            datasets: [{
                data: [100],
                backgroundColor: [barColor],
                borderWidth: 0
            }]
        };
    });

    const skeletonOptions = computed(() => ({
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
            legend: {
                display: false
            },
            tooltip: {
                enabled: false
            }
        },
        cutout: "40%"
    }));
</script>

<style lang="scss" scoped>
@import "@kestra-io/ui-libs/src/scss/variables";

$height: 200px;

.tall {
    height: $height;
    max-height: $height;
}
</style>
