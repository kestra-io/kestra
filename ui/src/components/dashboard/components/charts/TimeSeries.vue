<script setup lang="ts">
    import {Bar} from "vue-chartjs";
    import {defaultConfig} from "../../../../utils/charts.js";

    const groupedDatasets = [
        [
            {
                type: "bar",
                label: "Total Executions (IT, CREATED)",
                data: [40, 20, 12, 45]
            },
            {
                type: "line",
                label: "Execution Duration (IT, CREATED)",
                data: [300, 400, 140, 169]
            }
        ],
        [
            {
                type: "bar",
                label: "Total Executions (IT, SUCCESS)",
                data: [30, 10, 2, 5]
            },
            {
                type: "line",
                label: "Execution Duration (IT, SUCCESS)",
                data: [200, 600, 150, 269]
            }
        ],
        [
            {
                type: "bar",
                label: "Total Executions (FR, CREATED)",
                data: [10, 30, 42, 25]
            },
            {
                type: "line",
                label: "Execution Duration (FR, CREATED)",
                data: [400, 300, 180, 569]
            }
        ],
        [
            {
                type: "bar",
                label: "Total Executions (FR, SUCCESS)",
                data: [43, 25, 32, 15]
            },
            {
                type: "line",
                label: "Execution Duration (FR, SUCCESS)",
                data: [100, 200, 240, 469]
            }
        ]
    ]
    const chartData = {
        labels: ["10/11/2024", "11/11/2024", "12/11/2024", "13/11/2024"],
        datasets: groupedDatasets.flatMap(groupedDataset => {
            const color = `rgb(${Math.floor(Math.random() * 255)}, ${Math.floor(Math.random() * 255)}, ${Math.floor(Math.random() * 255)})`;
            return groupedDataset.map(dataset => {
                return {
                    ...dataset,
                    backgroundColor: color,
                    borderColor: color
                }
            })
        })
    };

    const chartOptions = defaultConfig({
        maintainAspectRatio: true
    });
    chartOptions.scales.x.display = true;
    chartOptions.scales.y.display = true;
    chartOptions.plugins.tooltip.enabled = true;
    chartOptions.plugins.tooltip.intersect = true;
    chartOptions.plugins.tooltip.mode = "point";
    delete chartOptions.elements.point.hoverRadius;
    delete chartOptions.elements.point.radius;
    delete chartOptions.elements.line.fill;
    delete chartOptions.plugins.tooltip.boxPadding;
    delete chartOptions.plugins.tooltip.usePointStyle;
</script>

<template>
    <Bar
        :data="chartData"
        :options="chartOptions"
        class="tall"
    />
</template>

<style scoped lang="scss">

</style>