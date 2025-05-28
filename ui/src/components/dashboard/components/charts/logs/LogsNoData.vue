<template>
    <div class="p-3">
        <div>
            <p class="m-0 fs-6">
                <span class="fw-bold">{{ t("logs") }}</span>
            </p>
        </div>
        <div class="text-center">
            <h5 class="text mb-2 fw-bold">
                {{ t("no_logs_data") }}
            </h5>
            <p class="text mb-6">
                {{ t("no_logs_data_description") }}
            </p>
            <Bar
                :data="chartData"
                :options="options"
                class="tall"
            />
        </div>
    </div>
</template>

<script setup>
    import {computed} from "vue";
    import {useI18n} from "vue-i18n";
    import {Bar} from "vue-chartjs";
    import {defaultConfig} from "../../../../../utils/charts.js";

    const {t} = useI18n({useScope: "global"});

    const placeholderData = () => {
        const data = [];
        for (let i = 0; i < 30; i++) {
            data.push(Math.floor(Math.random() * 40) + 10);
        }
        return data;
    };

    const chartData = computed(() => ({
        labels: Array(30).fill().map((_, i) => i + 1),
        datasets: [{
            data: placeholderData(),
            backgroundColor: "rgba(128, 128, 128, 0.15)",
            borderRadius: 2,
            barThickness: 12
        }]
    }));

    const options = computed(() => 
        defaultConfig({
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    display: false
                }
            }
        })
    );
</script>

<style lang="scss" scoped>
$height: 200px;

.tall {
    height: $height;
    max-height: $height;
}

.text {
    color: var(--ks-content-secondary);
}
</style> 