<template>
    <Editor
        v-model="dashboardStore.sourceCode"
        schemaType="dashboard"
        lang="yaml"
        :navbar="false"
        @cursor="cursor"
    />
</template>

<script lang="ts" setup>
    import {onMounted, ref} from "vue";
    import {useDashboardStore} from "../../../stores/dashboard";
    import Editor from "../../inputs/Editor.vue";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import {usePluginsStore} from "../../../stores/plugins";

    const dashboardStore = useDashboardStore();

    const pluginsStore = usePluginsStore();
    async function updatePluginDocumentation(event: any) {
        const type = YAML_UTILS.getTypeAtPosition(event.model.getValue(), event.position, plugins.value);
        if (type) {
            const plugin = await pluginsStore.load({cls: type});
            pluginsStore.editorPlugin = {cls: type, ...plugin};
        } else {
            pluginsStore.editorPlugin = undefined;
        }
    }

    async function updateChartPreview(event: any) {
        const chart = YAML_UTILS.getChartAtPosition(event.model.getValue(), event.position);
        if (chart) {
            const result = await dashboardStore.loadChart(chart);
            dashboardStore.selectedChart = typeof result.data === "object"
                ? {
                    ...result.data,
                    chartOptions: {
                        ...result.data?.chartOptions,
                        width: 12
                    }
                } as any
                : undefined;
            dashboardStore.chartErrors = [result.error].filter(e => e !== null);
        }
    }

    function cursor(event: any) {
        updatePluginDocumentation(event);
        updateChartPreview(event);
    }

    const plugins = ref<string[]>([]);
    async function loadPlugins() {
        const data = await pluginsStore.list();
        plugins.value = data.map((plugin: any) => {
            const charts = plugin.charts || [];
            const dataFilters = plugin.dataFilters || [];
            return charts.concat(dataFilters);
        }).flat()
            .filter(({deprecated}: any) => !deprecated)
            .map(({cls}: any) => cls);
    }

    onMounted(() => {
        loadPlugins();
    });
</script>
