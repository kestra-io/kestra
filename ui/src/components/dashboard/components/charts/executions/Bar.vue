<template>
    <div class="p-4 responsive-container">
        <div class="d-flex flex-wrap justify-content-between pb-4 info-container">
            <div class="info-block">
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

            <div class="switch-container">
                <div class="d-flex justify-content-end align-items-center switch-content">
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
            v-if="total > 0"
            :data="parsedData"
            :options="options"
            :plugins="[barLegend]"
            class="tall"
        />
        
        <el-empty v-else :description="$t('no data')" />
    </div>
</template>
<script setup>
    import {computed, ref, onMounted, onUnmounted} from "vue";
    import {useI18n} from "vue-i18n";

    import moment from "moment";
    import {Bar} from "vue-chartjs";

    import {barLegend} from "../legend.js";

    import Utils from "../../../../../utils/utils.js";
    import {defaultConfig, getFormat} from "../../../../../utils/charts.js";
    import {getScheme} from "../../../../../utils/scheme.js";

    import Check from "vue-material-design-icons/Check.vue";

    const {t} = useI18n({useScope: "global"});
    const isSmallScreen = ref(window.innerWidth < 610);

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
                        backgroundColor: getScheme(state),
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

    onMounted(() => {
        const handleResize = () => {
            isSmallScreen.value = window.innerWidth < 610;
        };
        window.addEventListener("resize", handleResize);

        onUnmounted(() => {
            window.removeEventListener("resize", handleResize);
        });
    });

    const options = computed(() =>
        defaultConfig({
            barThickness: isSmallScreen.value ? 8 : 12,
            skipNull: true,
            borderSkipped: false,
            borderColor: "transparent",
            borderWidth: 2,
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
                        maxTicksLimit: isSmallScreen.value ? 5 : 8,
                        callback: function (value) {
                            const label = this.getLabelForValue(value);
                            const date = moment(new Date(label));

                            const isCurrentYear = date.year() === moment().year();

                            return date.format(
                                isCurrentYear ? "MM/DD" : "MM/DD/YY",
                            );
                        },
                    },
                },
                y: {
                    title: {
                        display: !isSmallScreen.value,
                        text: t("executions"),
                    },
                    grid: {
                        display: false,
                    },
                    display: true,
                    position: "left",
                    stacked: true,
                    ticks: {
                        maxTicksLimit: isSmallScreen.value ? 5 : 8,
                    },
                },
                yB: {
                    title: {
                        display: duration.value && !isSmallScreen.value,
                        text: t("duration"),
                    },
                    grid: {
                        display: false,
                    },
                    display: duration.value,
                    position: "right",
                    ticks: {
                        maxTicksLimit: isSmallScreen.value ? 5 : 8,
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


Copy code
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

@media (max-width: 610px) {
  .responsive-container {
    padding: 2px;
  }

  .info-container {
    flex-direction: column;
    text-align: center;
  }

  .info-block {
    margin-bottom: 15px;
  }

  .switch-container {
    display: flex;
    justify-content: center;
    width: 100%;
  }

  .switch-content {
    justify-content: center;
  }

  .fs-2 {
    font-size: 1.5rem;
  }

  .fs-6 {
    font-size: 0.875rem;
  }

  .small {
    font-size: 0.75rem;
  }

  .pe-2 {
    padding-right: 0.5rem;
  }
}
</style>
