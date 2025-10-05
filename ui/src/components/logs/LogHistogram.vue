<template>
    <section class="logs-histogram" v-if="dataAvailable">
        <h3>Log Histogram</h3>
        <p>Log levels over time</p>
        <div class="histogram-chart-container">
            <Bar
                :data="chartData"
                :options="options"
                :class="small ? 'small' : ''"
            />
        </div>
    </section>
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import {Bar} from "vue-chartjs";
    import {useI18n} from "vue-i18n";
    import moment from "moment";
    import {defaultConfig} from "../dashboard/composables/charts";
    import LogUtils from "../../utils/logs";

    const {t} = useI18n();

    interface Props {
        logs: LogEntry[];
        interval?: "minute" | "hour" | "day" | "week" | "month";
        small?: boolean;
    }

    interface LogEntry {
        timestamp: string;
        level: string;
        message?: string;
    }

    const props = withDefaults(defineProps<Props>(), {
        interval: "hour",
        small: false,
    });

    const dataAvailable = computed(() => {
        return props.logs && props.logs.length > 0;
    });

    const groupedLogs = computed(() => {
        return groupLogsByTime(props.logs, props.interval);
    });

    const labels = computed(() => {
        return Object.keys(groupedLogs.value).sort();
    });

    const datasets = computed(() => {
        const levels = LogUtils.levelOrLower();

        return levels.map(level => {
            const data = labels.value.map(timeKey => groupedLogs.value[timeKey]?.[level] || 0);

            return {
                label: level,
                backgroundColor: LogUtils.graphColors(level) || LogUtils.chartColorFromLevel(level, 0.7) || "#666666",
                borderColor: LogUtils.graphColors(level) || LogUtils.chartColorFromLevel(level, 1) || "#333333",
                borderWidth: 1,
                data,
            };
        });
    });

    const chartData = computed(() => {
        if (!dataAvailable.value) return {labels: [], datasets: []};

        return {
            labels: labels.value,
            datasets: datasets.value,
        };
    });

    const options = computed(() =>
        defaultConfig({
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                tooltip: {
                    callbacks: {
                        label: function(context: any) {
                            return `${context.dataset.label}: ${context.raw} logs`;
                        },
                        title: function(context: any) {
                            return `Time: ${context[0]?.label}`;
                        }
                    }
                },
                legend: {
                    display: !props.small,
                    position: "top",
                }
            },
            scales: {
                x: {
                    display: true,
                    title: {
                        display: true,
                        text: t("time"),
                    },
                    ticks: {
                        maxTicksLimit: props.small ? 5 : 10,
                    }
                },
                y: {
                    display: true,
                    title: {
                        display: true,
                        text: t("log count"),
                    },
                    ticks: {
                        beginAtZero: true,
                    }
                }
            },
            barThickness: props.small ? 8 : 12,
            maxBarThickness: props.small ? 10 : 15,
        })
    );

    function groupLogsByTime(logs: LogEntry[], interval: string) {
        const groups: Record<string, Record<string, number>> = {};

        logs.forEach(log => {
            const time = moment(log.timestamp);
            let timeKey: string;

            switch (interval) {
            case "minute":
                timeKey = time.format("YYYY-MM-DD HH:mm");
                break;
            case "hour":
                timeKey = time.format("YYYY-MM-DD HH:00");
                break;
            case "day":
                timeKey = time.format("YYYY-MM-DD");
                break;
            case "week":
                timeKey = time.format("YYYY-ww");
                break;
            case "month":
                timeKey = time.format("YYYY-MM");
                break;
            default:
                timeKey = time.format("YYYY-MM-DD HH:00");
            }

            if (!groups[timeKey]) {
                groups[timeKey] = {};
            }

            if (!groups[timeKey][log.level]) {
                groups[timeKey][log.level] = 0;
            }

            groups[timeKey][log.level]++;
        });

        return groups;
    }
</script>

<style lang="scss" scoped>
.logs-histogram {
    margin-bottom: 1rem;
    border: 1px solid var(--ks-border-primary);
    border-radius: var(--bs-border-radius-lg);
    background-color: var(--ks-background-card);
    padding: 1rem;

    h3 {
        margin-bottom: 0.5rem;
        font-weight: bold;
    }

    // Constrain chart height to prevent infinite growth
    .histogram-chart-container {
        height: 250px;
        max-height: 250px;
        overflow: hidden;
    }
}
</style>
