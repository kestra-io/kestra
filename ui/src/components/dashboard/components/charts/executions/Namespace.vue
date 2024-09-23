<template>
    <div class="p-4">
        <div class="d-flex flex justify-content-between pb-4">
            <div>
                <p class="m-0 fs-6">
                    <span class="fw-bold">{{ t("executions") }}</span>
                    <span class="fw-light small">
                        {{ t("dashboard.per_namespace") }}
                    </span>
                </p>
                <p class="m-0 fs-2">
                    {{ total }}
                </p>
            </div>

            <div>
                <div id="pernamespace" />
            </div>
        </div>
        <Bar
            :data="parsedData"
            :options="options"
            :plugins="[barLegend]"
            class="tall"
        />
    </div>
</template>

<script setup>
    import {computed} from "vue";
    import {useI18n} from "vue-i18n";

    import {Bar} from "vue-chartjs";

    import {barLegend} from "../legend.js";

    import {defaultConfig, getStateColor} from "../../../../../utils/charts.js";

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
    });

    const parsedData = computed(() => {
        const labels = Object.keys(props.data);
        const executionData = {};

        // eslint-disable-next-line no-unused-vars
        for (const [namespace, values] of Object.entries(props.data)) {
            let totalCounts = {};

            for (const item of values["*"]) {
                for (const [status, count] of Object.entries(
                    item.executionCounts,
                )) {
                    totalCounts[status] = (totalCounts[status] || 0) + count;

                    if (!executionData[status]) {
                        executionData[status] = [];
                    }
                }
            }

            for (const [status, count] of Object.entries(totalCounts)) {
                executionData[status].push(count);
            }
        }

        const datasets = Object.entries(executionData)
            .map(([label, data]) => ({
                label,
                data: data.map((item) => (item === 0 ? null : item)),
                backgroundColor: getStateColor(label),
                stack: label,
            }))
            .filter((dataset) => dataset.data.some((count) => count > 0));

        return {
            labels: labels,
            datasets: datasets,
        };
    });

    const options = computed(() =>
        defaultConfig({
            barThickness: 20,
            skipNull: true,
            plugins: {
                barLegend: {
                    containerID: "pernamespace",
                },
            },
            scales: {
                x: {
                    title: {
                        display: false,
                        text: t("namespace"),
                    },
                    grid: {
                        display: false,
                    },
                    position: "bottom",
                    display: true,
                    stacked: true,
                },
                y: {
                    title: {
                        display: true,
                        text: t("executions"),
                    },
                    grid: {
                        display: false,
                    },
                    display: true,
                    position: "left",
                    stacked: true,
                    ticks: {
                        maxTicksLimit: 8,
                    },
                },
            },
        }),
    );
</script>

<style lang="scss" scoped>
@import "@kestra-io/ui-libs/src/scss/variables";

$height: 200px;

.tall {
    height: $height;
    max-height: $height;
}

.small {
    font-size: $font-size-xs;
    color: $gray-700;

    html.dark & {
        color: $gray-300;
    }
}
</style>
