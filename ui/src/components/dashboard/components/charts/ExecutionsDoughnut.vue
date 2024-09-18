<template>
    <div class="h-100 p-4">
        <span class="fs-6 fw-bold">
            {{ t("dashboard.total_executions") }}
        </span>

        <div class="d-flex flex-row align-items-center h-100">
            <div class="w-75">
                <Doughnut
                    :data="parsedData"
                    :options="options"
                    :plugins="[totalsLegend, centerPlugin]"
                    class="tall"
                />
            </div>
            <div id="totals" />
        </div>
    </div>
</template>

<script setup>
    import {computed} from "vue";
    import {useI18n} from "vue-i18n";

    import {Doughnut} from "vue-chartjs";

    import {totalsLegend} from "./legend.js";

    import Utils from "../../../../utils/utils.js";
    import {defaultConfig, getStateColor} from "../../../../utils/charts.js";

    const {t} = useI18n({useScope: "global"});

    const props = defineProps({
        data: {
            type: Object,
            required: true,
        },
    });

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
        const backgroundColor = labels.map((state) => getStateColor(state));

        return {labels, datasets: [{data, backgroundColor, borderWidth: 0}]};
    });

    const options = computed(() =>
        defaultConfig({
            plugins: {
                totalsLegend: {
                    containerID: "totals",
                },
            },
        }),
    );

    const centerPlugin = {
        id: "centerPlugin",
        beforeDraw(chart) {
            const darkTheme = Utils.getTheme() === "dark";

            const ctx = chart.ctx;
            const dataset = chart.data.datasets[0];
            const total = dataset.data.reduce((acc, val) => acc + val, 0);
            const centerX = chart.width / 2;
            const centerY = chart.height / 2;

            ctx.save();
            ctx.font = "700 16px Public Sans";
            ctx.textAlign = "center";
            ctx.textBaseline = "middle";
            ctx.fillStyle = darkTheme ? "FFFFFF" : "000000";

            ctx.fillText(total, centerX, centerY);

            ctx.restore();
        },
    };
</script>

<style lang="scss" scoped>
@import "@kestra-io/ui-libs/src/scss/variables";

$height: 200px;

.tall {
    height: $height;
    max-height: $height;
}
</style>
