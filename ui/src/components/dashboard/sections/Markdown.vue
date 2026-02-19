<template>
    <section v-if="data" id="markdown">
        <Markdown :source="data" />
    </section>

    <NoData v-else :text="EMPTY_TEXT" />
</template>

<script setup lang="ts">
    import {PropType, watch, ref} from "vue";

    import type {Chart} from "../composables/useDashboards";
    import {getPropertyValue, useChartGenerator} from "../composables/useDashboards";

    import Markdown from "../../layout/Markdown.vue";
    import NoData from "../../layout/NoData.vue";
    import {FilterObject} from "../../../utils/filters";

    const props = defineProps({
        chart: {type: Object as PropType<Chart>, required: true},
        filters: {type: Array as PropType<FilterObject[]>, default: () => []},
        showDefault: {type: Boolean, default: false},
    });

    const data = ref();

    import {useRoute} from "vue-router";
    import {useDashboardStore} from "../../../stores/dashboard.ts";

    const route = useRoute();
    const dashboardStore = useDashboardStore();
    const dashboardID = dashboardStore.getDashboardRelatedToThisRoute(route);
    const {EMPTY_TEXT, generate} = useChartGenerator(dashboardID, props, false);

    const getData = async () => {
        if (props.chart.source?.type === "FlowDescription") data.value = getPropertyValue(await generate(), "description") ?? EMPTY_TEXT;
        else data.value = props.chart.content ?? props.chart.source?.content;
    };


    function refresh() {
        return getData();
    }

    defineExpose({
        refresh
    });

    watch(() => route.params.filters, () => {
        refresh();
    }, {deep: true, immediate: true});
</script>
