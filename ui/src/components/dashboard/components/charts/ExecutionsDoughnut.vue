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
                    :plugins="[totalsLegend]"
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
</script>

<style lang="scss" scoped>
@import "@kestra-io/ui-libs/src/scss/variables";

$height: 200px;

.tall {
    height: $height;
    max-height: $height;
}
</style>
