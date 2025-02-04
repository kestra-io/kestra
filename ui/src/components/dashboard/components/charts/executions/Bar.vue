<template>
    <div class="p-4 responsive-container">
        <div
            class="d-flex flex-wrap justify-content-between pb-4 info-container"
        >
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
                <div
                    class="d-flex justify-content-end align-items-center switch-content"
                >
                    <span class="pe-2 fw-light small">{{ t("duration") }}</span>
                    <el-switch
                        v-model="duration"
                        :active-icon="CheckIcon"
                        inline-prompt
                    />
                </div>
                <div id="executions" />
            </div>
        </div>

        <BarChart
            v-if="total > 0"
            :data="data"
            :total="total"
            :duration="duration"
            :plugins="[barLegend]"
            :small="isSmallScreen"
            class="tall"
        />

        <NoData v-else />
    </div>
</template>

<script setup>
    import {ref} from "vue";
    import {useI18n} from "vue-i18n";
    import CheckIcon from "vue-material-design-icons/Check.vue";

    import {useMediaQuery} from "@vueuse/core";

    import {barLegend} from "../legend.js";

    import NoData from "../../../../layout/NoData.vue";

    import BarChart from "./BarChart.vue";

    const {t} = useI18n({useScope: "global"});
    const duration = ref(true);

    const isSmallScreen = useMediaQuery("(max-width: 610px)");

    defineProps({
        data: {
            type: Object,
            required: true,
        },
        total: {
            type: Number,
            required: true,
        },
    });
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

.responsive-container {
    min-height: 100%;
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
