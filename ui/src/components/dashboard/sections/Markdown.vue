<template>
    <section v-if="data" id="markdown">
        <Markdown :source="data" />
    </section>

    <NoData v-else :text="EMPTY_TEXT" />
</template>

<script setup lang="ts">
    import {PropType, onMounted, watch, ref} from "vue";

    import type {Chart} from "../composables/useDashboards";
    import {getDashboard, getPropertyValue, useChartGenerator} from "../composables/useDashboards";

    import Markdown from "../../layout/Markdown.vue";
    import NoData from "../../layout/NoData.vue";

    const props = defineProps({
        chart: {type: Object as PropType<Chart>, required: true},
        filters: {type: Array as PropType<string[]>, default: () => []},
        showDefault: {type: Boolean, default: false},
    });

    const data = ref();
    const {EMPTY_TEXT, generate} = useChartGenerator(props);

    import {useRoute} from "vue-router";
    const route = useRoute();

    const getData = async (ID: string) => {
        if (props.chart.source?.type === "FlowDescription") data.value = getPropertyValue(await generate(ID), "description") ?? EMPTY_TEXT;
        else data.value = props.chart.content ?? props.chart.source?.content;
    };

    const dashboardID = (route) => getDashboard(route, "id")

    watch(route, async (changed) => await getData(dashboardID(changed)));

    onMounted(async () => await getData(dashboardID(route)));
</script>
