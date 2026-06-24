<template>
    <section v-if="data" id="markdown">
        <KsMarkdown :content="data" />
    </section>

    <KsNoData
        v-else-if="isFlowDescription"
        :icon="FileDocumentOutline"
        :title="$t('dashboards.no_description')"
        :description="$t('dashboards.no_description_hint')"
    />

    <KsNoData v-else />
</template>

<script setup lang="ts">
    import {PropType, watch, ref, computed} from "vue"

    import type {Chart} from "../composables/useDashboards"
    import {getPropertyValue, useChartGenerator} from "../composables/useDashboards"

    import {KsMarkdown} from "@kestra-io/design-system"
    import FileDocumentOutline from "vue-material-design-icons/FileDocumentOutline.vue"
    import {FilterObject} from "../../../utils/filters"

    const props = defineProps({
        dashboardId: {type: String, required: false, default: undefined},
        chart: {type: Object as PropType<Chart>, required: true},
        filters: {type: Array as PropType<FilterObject[]>, default: () => []},
        showDefault: {type: Boolean, default: false},
    })

    const data = ref()

    import {useRoute} from "vue-router"

    const route = useRoute()
    const {generate} = useChartGenerator(props.dashboardId, props, false)

    const isFlowDescription = computed(() => props.chart.source?.type === "FlowDescription")

    const getData = async () => {
        if (isFlowDescription.value) data.value = getPropertyValue(await generate(), "description") || null
        else data.value = props.chart.content ?? props.chart.source?.content
    }


    function refresh() {
        return getData()
    }

    defineExpose({
        refresh,
    })

    watch(() => route.params.filters, () => {
        refresh()
    }, {deep: true, immediate: true})
</script>
