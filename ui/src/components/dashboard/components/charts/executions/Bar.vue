<template>
    <div class="p-4">
        <div class="d-flex flex justify-content-between pb-4">
            <div>
                <p class="m-0 fs-6">
                    <span class="fw-bold">{{ t("executions") }}</span>
                    <span class="fw-light small">
                        {{ t("dashboard.per_day") }}
                    </span>
                </p>
                <p class="m-0 fs-2">
                    {{ total }}
                </p>
            </div>

            <div>
                <div class="d-flex justify-content-end align-items-center">
                    <span class="pe-2 fw-light small">{{ t("duration") }}</span>
                    <el-switch
                        v-model="duration"
                        :active-icon="Check"
                        inline-prompt
                    />
                </div>
                <div id="executions" />
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
    import {computed, ref} from "vue";
    import {useI18n} from "vue-i18n";

    import moment from "moment";
    import {Bar} from "vue-chartjs";

    import {barLegend} from "../legend.js";

    import Utils from "../../../../../utils/utils.js";
    import {
        defaultConfig,
        getStateColor,
        getFormat,
    } from "../../../../../utils/charts.js";

    import Check from "vue-material-design-icons/Check.vue";

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
        let datasets = props.data.reduce(function (accumulator, value) {
            Object.keys(value.executionCounts).forEach(function (state) {
                if (accumulator[state] === undefined) {
                    accumulator[state] = {
                        label: state,
                        backgroundColor: getStateColor(state),
                        borderRadius: 4,
                        yAxisID: "y",
                        data: [],
                    };
                }

                accumulator[state].data.push(value.executionCounts[state]);
            });

            return accumulator;
        }, Object.create(null));

        return {
            labels: props.data.map((r) =>
                moment(r.startDate).format(getFormat(r.groupBy)),
            ),
            datasets: duration.value
                ? [
                    {
                        type: "line",
                        label: t("duration"),
                        fill: false,
                        pointRadius: 0,
                        borderWidth: 0.75,
                        borderColor: "#A2CDFF",
                        yAxisID: "yB",
                        data: props.data.map((value) => {
                            return value.duration.avg === 0
                                ? 0
                                : Utils.duration(value.duration.avg);
                        }),
                    },
                    ...Object.values(datasets),
                ]
                : Object.values(datasets),
        };
    });

    const options = computed(() =>
        defaultConfig({
            plugins: {
                barLegend: {
                    containerID: "executions",
                },
                tooltip: {
                    enabled: true,
                    filter: (value) => value.raw,
                    callbacks: {
                        label: (value) => {
                            const {label, yAxisID} = value.dataset;
                            return `${label.toLowerCase().capitalize()}: ${value.raw}${yAxisID === "yB" ? "s" : ""}`;
                        },
                    },
                },
            },
            scales: {
                x: {
                    title: {
                        display: true,
                        text: t("date"),
                    },
                    grid: {
                        display: false,
                    },
                    position: "bottom",
                    display: true,
                    stacked: true,
                    ticks: {
                        maxTicksLimit: 8,
                        callback: function (value) {
                            return moment(
                                new Date(this.getLabelForValue(value)),
                            ).format("MM/DD");
                        },
                    },
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
                yB: {
                    title: {
                        display: duration.value,
                        text: t("duration"),
                    },
                    grid: {
                        display: false,
                    },
                    display: duration.value,
                    position: "right",
                    ticks: {
                        maxTicksLimit: 8,
                        callback: function (value) {
                            return `${this.getLabelForValue(value)}s`;
                        },
                    },
                },
            },
        }),
    );

    const duration = ref(true);
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
