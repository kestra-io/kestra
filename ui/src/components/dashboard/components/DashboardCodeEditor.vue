<template>
    <KsEditor
        v-bind="editorBindings"
        v-model="editorContent"
        schemaType="dashboard"
        lang="yaml"
        :navbar="false"
        @cursor="cursor"
        @editorMounted="onEditorMounted"
        :options="{diffOverviewBar: false, diffSideBySide: false, editor: {padding: {top: 16}}}"
    />
</template>

<script lang="ts" setup>
    import {onMounted, ref, computed} from "vue"
    import {useI18n} from "vue-i18n"
    import type * as monaco from "monaco-editor/editor/editor.api"
    import {useDashboardStore} from "../../../stores/dashboard"
    import {KsEditor} from "@kestra-io/design-system"
    import {flowYamlUtils as YAML_UTILS} from "@kestra-io/topology"
    import {useEditorBindings} from "../../../composables/useEditorBindings"
    import {usePluginsStore} from "../../../stores/plugins"
    import {useReadOnlyYamlKeys} from "../../../composables/useReadOnlyYamlKeys"

    const dashboardStore = useDashboardStore()

    const editorBindings = useEditorBindings()
    const {t} = useI18n()

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

    // A dashboard's id is fixed once it exists, so the line is locked in the
    // editor rather than corrected after the fact.
    const monacoEditor = ref<monaco.editor.IStandaloneCodeEditor>()

    function onEditorMounted(editor?: monaco.editor.IStandaloneCodeEditor | monaco.editor.IStandaloneDiffEditor) {
        monacoEditor.value = editor && !("getOriginalEditor" in editor)
            ? editor as monaco.editor.IStandaloneCodeEditor
            : undefined
    }

    useReadOnlyYamlKeys({
        editor: monacoEditor,
        expected: computed(() => ({id: dashboardStore.activeDashboard?.id})),
        enabled: computed(() => !dashboardStore.isCreating),
        hoverMessage: computed(() => t("dashboards.edition.id readonly")),
    })

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
