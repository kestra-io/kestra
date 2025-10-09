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
    import {PropType, watch} from "vue";

    import {Chart, getDashboard} from "../composables/useDashboards";
    import {getChartTitle, getPropertyValue, useChartGenerator} from "../composables/useDashboards";

    import NoData from "../../layout/NoData.vue";
    import {useRoute} from "vue-router";
    import {FilterObject} from "../../../utils/filters";

    const props = defineProps({
        chart: {type: Object as PropType<Chart>, required: true},
        filters: {type: Array as PropType<FilterObject[]>, default: () => []},
        showDefault: {type: Boolean, default: false},
    });

    const route = useRoute();

    const {percentageShown, EMPTY_TEXT, data, generate} = useChartGenerator(props);

    function refresh() {
        return generate(getDashboard(route, "id")!);
    }

    defineExpose({
        refresh
    });

    watch(() => route.params.filters, () => {
        refresh();
    }, {deep: true});
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
