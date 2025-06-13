<template>
    <section v-if="data" id="kpi">
        <span class="pb-2">{{ getChartTitle(props.chart!) }}</span>
        <p class="m-0 fs-2 fw-bold">
            {{ getPropertyValue(data, "value") }}{{ percentageShown ? "%" : "" }}
        </p>
    </section>

    <NoData v-else :text="EMPTY_TEXT" />
</template>

<script setup lang="ts">
    import {PropType} from "vue";

    import type {Chart} from "../composables/useDashboards";
    import {getChartTitle, getPropertyValue, useChartGenerator} from "../composables/useDashboards";

    import NoData from "../../layout/NoData.vue";

    const props = defineProps({
        chart: {type: Object as PropType<Chart>, required: true},
        filters: {type: Array as PropType<string[]>, default: () => []},
        showDefault: {type: Boolean, default: false},
    });

    const {percentageShown, EMPTY_TEXT, data} = useChartGenerator(props);
</script>

<style scoped lang="scss">
@import "@kestra-io/ui-libs/src/scss/variables";

section#kpi {
    height: 100%;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    text-align: center;
}
</style>
