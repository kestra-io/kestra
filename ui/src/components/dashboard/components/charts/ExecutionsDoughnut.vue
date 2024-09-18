<template>
    <div class="h-100 p-4">
        <span class="fs-6 fw-bold">
            {{ t("dashboard.total_executions") }}
        </span>

        <div class="d-flex align-items-center h-100">
            <Doughnut
                :data="{
                    labels: ['VueJs', 'EmberJs', 'ReactJs', 'AngularJs'],
                    datasets: [
                        {
                            backgroundColor: [
                                '#41B883',
                                '#E46651',
                                '#00D8FF',
                                '#DD1B16',
                            ],
                            data: [40, 20, 80, 10],
                        },
                    ],
                }"
                :options="options"
                class="tall"
            />
        </div>
    </div>
</template>

<script setup>
    import {useI18n} from "vue-i18n";

    import {Doughnut} from "vue-chartjs";

    const {t} = useI18n({useScope: "global"});

    defineProps({
        data: {
            type: Object,
            required: true,
        },
    });

    const options = {
        responsive: true,
        maintainAspectRatio: false,
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
