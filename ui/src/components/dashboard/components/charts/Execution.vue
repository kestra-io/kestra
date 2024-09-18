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
            :plugins="[customLegend]"
            class="tall"
        />
    </div>
</template>

<script setup>
    import {computed, ref} from "vue";
    import {useI18n} from "vue-i18n";

    import moment from "moment";
    import {Bar} from "vue-chartjs";

    import {customLegend} from "./legend.js";

    import Utils from "../../../../utils/utils";
    import {
        defaultConfig,
        backgroundFromState,
        getFormat,
    } from "../../../../utils/charts.js";

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
        const darkTheme = Utils.getTheme() === "dark";

        let datasets = props.data.reduce(function (accumulator, value) {
            Object.keys(value.executionCounts).forEach(function (state) {
                if (accumulator[state] === undefined) {
                    accumulator[state] = {
                        label: state,
                        backgroundColor: backgroundFromState(state),
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
                        label: false,
                        fill: "start",
                        pointRadius: 0,
                        borderWidth: 0.2,
                        borderColor: !darkTheme ? "#7081b9" : "#7989b4",
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
                customLegend: {
                    containerID: "executions",
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
