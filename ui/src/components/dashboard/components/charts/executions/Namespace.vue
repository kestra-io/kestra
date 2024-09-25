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

    import {defaultConfig} from "../../../../../utils/charts.js";
    import {getScheme} from "../../../../../utils/scheme.js";

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
        const labels = Object.entries(props.data)
            .sort(([, a], [, b]) => b.total - a.total)
            .map(([namespace]) => namespace);

        const executionData = {};

        labels.forEach((namespace) => {
            const counts = props.data[namespace].counts;

            for (const [state, count] of Object.entries(counts)) {
                if (!executionData[state]) {
                    executionData[state] = {
                        label: state,
                        data: [],
                        backgroundColor: getScheme(state),
                        stack: state,
                    };
                }
                executionData[state].data.push(count);
            }
        });

        const datasets = Object.values(executionData).filter((dataset) =>
            dataset.data.some((count) => count > 0),
        );

        return {
            labels,
            datasets,
        };
    });

    const options = computed(() =>
        defaultConfig({
            barThickness: 25,
            skipNull: true,
            borderSkipped: false,
            borderColor: "transparent",
            borderWidth: 2,
            plugins: {
                barLegend: {
                    containerID: "pernamespace",
                },
                tooltip: {
                    enabled: true,
                    filter: (value) => value.raw,
                    callbacks: {
                        label: (value) => {
                            const {label} = value.dataset;
                            return `${label.toLowerCase().capitalize()}: ${value.raw}`;
                        },
                    },
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
