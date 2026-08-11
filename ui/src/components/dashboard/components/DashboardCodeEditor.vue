<template>
    <KsEditor
        v-bind="editorBindings"
        v-model="editorContent"
        schemaType="dashboard"
        lang="yaml"
        :navbar="false"
        @cursor="cursor"
        :options="{diffOverviewBar: false, diffSideBySide: false, editor: {padding: {top: 16}}}"
    />
</template>

<script lang="ts" setup>
    import {onMounted, ref, computed} from "vue"
    import {useDashboardStore} from "../../../stores/dashboard"
    import {KsEditor} from "@kestra-io/design-system"
    import {flowYamlUtils as YAML_UTILS} from "@kestra-io/topology"
    import {useEditorBindings} from "../../../composables/useEditorBindings"
    import {usePluginsStore} from "../../../stores/plugins"

    const dashboardStore = useDashboardStore()

    const editorBindings = useEditorBindings()

    const pluginsStore = usePluginsStore()
    async function updatePluginDocumentation(event: any) {
        const type = YAML_UTILS.getTypeAtPosition(event.model.getValue(), event.position, plugins.value)
        if (type) {
            const plugin = await pluginsStore.load({cls: type})
            pluginsStore.editorPlugin = {cls: type, ...plugin}
        } else {
            pluginsStore.editorPlugin = undefined
        }
    }

    async function updateChartPreview(event: any) {
        const chart = YAML_UTILS.getChartAtPosition(event.model.getValue(), event.position)
        if (chart) {
            const result = await dashboardStore.loadChart(chart)
            dashboardStore.selectedChart = typeof result.data === "object"
                ? {
                    ...result.data,
                    chartOptions: {
                        ...result.data?.chartOptions,
                        width: 12,
                    },
                } as any
                : undefined
            dashboardStore.chartErrors = [result.error].filter(e => e !== null)
        }
    }

    function cursor(event: any) {
        updatePluginDocumentation(event)
        updateChartPreview(event)
    }

    const plugins = ref<string[]>([])
    async function loadPlugins() {
        const data = await pluginsStore.list()
        plugins.value = data.map((plugin: any) => {
            const charts = plugin.charts || []
            const dataFilters = plugin.dataFilters || []
            return charts.concat(dataFilters)
        }).flat()
            .filter(({deprecated}: any) => !deprecated)
            .map(({cls}: any) => cls)
    }

    onMounted(() => {
        loadPlugins()
    })

    const editorContent = computed<string>({
        get: () => dashboardStore.sourceCode as unknown as string,
        set: (value: string) => {
            dashboardStore.sourceCode = value
        },
    })
</script>
