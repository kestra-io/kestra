<template>
    <div class="button-top">
        <el-button-group class="view-buttons">
            <el-tooltip :content="$t('source only')">
                <el-button
                    :type="buttonType(views.NONE)"
                    :icon="FileDocumentEditOutline"
                    @click="setView(views.NONE)"
                />
            </el-tooltip>
            <el-tooltip :content="$t('documentation.documentation')">
                <el-button
                    :type="buttonType(views.DOC)"
                    :icon="BookOpenVariant"
                    @click="setView(views.DOC)"
                />
            </el-tooltip>
            <el-tooltip :content="$t('chart preview')">
                <el-button
                    :type="buttonType(views.CHART)"
                    :icon="ChartBar"
                    @click="setView(views.CHART)"
                />
            </el-tooltip>
            <el-tooltip :content="$t('dashboards.preview')">
                <el-button
                    :type="buttonType(views.DASHBOARD)"
                    :icon="ViewDashboard"
                    @click="setView(views.DASHBOARD)"
                />
            </el-tooltip>
        </el-button-group>

        <ValidationErrors
            class="mx-3"
            tooltipPlacement="bottom-start"
            :errors="errors"
        />

        <el-button
            :icon="ContentSave"
            @click="emit('save', source)"
            :type="saveButtonType"
            :disabled="!allowSaveUnchanged && source === initialSource"
        >
            {{ $t("save") }}
        </el-button>
    </div>
    <div class="w-100 p-4" v-if="currentView === views.DASHBOARD">
        <Sections :dashboard="{id: 'default', charts: []}" :charts="charts.map(chart => chart.data)" showDefault />
    </div>
    <div class="main-editor" v-else>
        <el-splitter v-if="displaySide" class="dashboard-edit" @resize="onSplitterResize">
            <el-splitter-panel :size="editorWidth" min="25%" max="75%">
                <Editor
                    @save="(allowSaveUnchanged || source !== initialSource) ? $emit('save', $event) : undefined"
                    v-model="source"
                    schemaType="dashboard"
                    lang="yaml"
                    @update:model-value="source = $event"
                    @cursor="updatePluginDocumentation"
                    :creating="true"
                    :readOnly="false"
                    :navbar="false"
                />
            </el-splitter-panel>
            <el-splitter-panel :size="100 - editorWidth">
                <PluginDocumentation
                    v-if="currentView === views.DOC"
                    class="combined-right-view enhance-readability"
                    :overrideIntro="intro"
                    absolute
                />
                <div
                    class="chart-view"
                    v-else-if="currentView === views.CHART"
                >
                    <div v-if="selectedChart.length" class="w-100">
                        <Sections :dashboard="{id: 'default', charts: []}" :charts="selectedChart" showDefault />
                    </div>
                    <div v-else-if="chartError" class="text-container">
                        <span>{{ chartError }}</span>
                    </div>
                    <div v-else>
                        <el-empty :image="EmptyVisualDashboard" :imageSize="200">
                            <template #description>
                                <h5>
                                    {{ $t("dashboards.chart_preview") }}
                                </h5>
                            </template>
                        </el-empty>
                    </div>
                </div>
            </el-splitter-panel>
        </el-splitter>
        <div v-else class="editor-only">
            <Editor
                @save="(allowSaveUnchanged || source !== initialSource) ? $emit('save', $event) : undefined"
                v-model="source"
                schemaType="dashboard"
                lang="yaml"
                @update:model-value="source = $event"
                @cursor="updatePluginDocumentation"
                :creating="true"
                :readOnly="false"
                :navbar="false"
            />
        </div>
    </div>
</template>
<script setup lang="ts">
    import {ref, computed, watch, onMounted, onBeforeUnmount, nextTick} from "vue";
    import {useI18n} from "vue-i18n";
    import {useRoute} from "vue-router";
    import Editor from "../../inputs/Editor.vue";
    import PluginDocumentation from "../../plugins/PluginDocumentation.vue";
    import Sections from "../sections/Sections.vue";
    import ValidationErrors from "../../flows/ValidationError.vue";
    import BookOpenVariant from "vue-material-design-icons/BookOpenVariant.vue";
    import ChartBar from "vue-material-design-icons/ChartBar.vue";
    import FileDocumentEditOutline from "vue-material-design-icons/FileDocumentEditOutline.vue";
    import ViewDashboard from "vue-material-design-icons/ViewDashboard.vue";
    import EmptyVisualDashboard from "../../../assets/empty_visuals/Visuals_empty_dashboard.svg";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
    import intro from "../../../assets/docs/dashboard_home.md?raw";
    import yaml from "yaml";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import {usePluginsStore} from "../../../stores/plugins";
    import {useDashboardStore} from "../../../stores/dashboard";
    import {useCoreStore} from "../../../stores/core";

    const {t} = useI18n();

    const props = defineProps<{
        allowSaveUnchanged?: boolean;
        initialSource?: string;
        modelValue?: string;
    }>();

    const emit = defineEmits<{
        (e: "save", source?: string): void;
    }>();

    const route = useRoute();
    const pluginsStore = usePluginsStore();
    const dashboardStore = useDashboardStore();
    const coreStore = useCoreStore();

    const source = ref(props.initialSource);
    const errors = ref<any>(undefined);
    const warnings = ref<any>(undefined);
    const editorWidth = ref(50);
    const views = {
        DOC: "documentation",
        CHART: "chart",
        NONE: "none",
        DASHBOARD: "dashboard"
    };
    const currentView = ref<string>(views.DOC);
    const selectedChart = ref<any[]>([]);
    const charts = ref<any[]>([]);
    const chartError = ref<string | null>(null);

    const dashboardId = computed<string>(() => route.params.dashboard as string);

    const saveButtonType = computed(() => {
        if (errors.value) return "danger";
        return warnings.value ? "warning" : "primary";
    });

    const displaySide = computed(() => {
        return currentView.value !== views.NONE && currentView.value !== views.DASHBOARD;
    });

    function buttonType(view: string) {
        return view === currentView.value ? "primary" : "default";
    }

    function setView(view: string) {
        currentView.value = view;
        if (view === views.DASHBOARD) {
            validateAndLoadAllCharts();
        }
    }

    async function updatePluginDocumentation(event: any) {
        if (currentView.value === views.DOC) {
            const type = YAML_UTILS.getTypeAtPosition(event.model.getValue(), event.position, plugins.value);
            if (type) {
                const plugin = await pluginsStore.load({cls: type});
                pluginsStore.editorPlugin = {cls: type, ...plugin};
            } else {
                pluginsStore.editorPlugin = undefined;
            }
        } else if (currentView.value === views.CHART) {
            const chart = YAML_UTILS.getChartAtPosition(event.model.getValue(), event.position);
            if (chart) {
                const result = await loadChart(chart);
                selectedChart.value = typeof result.data === "object"
                    ? [{
                        ...result.data,
                        chartOptions: {
                            ...result.data?.chartOptions,
                            width: 12
                        }
                    }]
                    : [];
                chartError.value = result.error;
            }
        }
    }

    function onSplitterResize(sizes: number[]) {
        if (sizes && sizes.length >= 1) {
            const percent = sizes[0];
            editorWidth.value = percent > 75 ? 75 : percent < 25 ? 25 : percent;
        }
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

    function validateAndLoadAllCharts() {
        charts.value = [];
        const allCharts = source.value ? YAML_UTILS.getAllCharts(source.value) : [];
        allCharts.forEach(async (chart: any) => {
            const loadedChart = await loadChart(chart);
            charts.value.push(loadedChart);
        });
    }

    async function loadChart(chart: any) {
        const yamlChart = yaml.stringify(chart);
        const result: { error: string | null; data: null | {
            id?: string;
            name?: string;
            type?: string;
            chartOptions?: Record<string, any>;
            dataFilters?: any[];
            charts?: any[];
        }; raw: any } = {
            error: null,
            data: null,
            raw: {}
        };
        const errors = await dashboardStore.validateChart(yamlChart);
        if (errors.constraints) {
            result.error = errors.constraints;
        } else {
            result.data = {...chart, content: yamlChart, raw: chart};
        }
        return result;
    }

    watch(source, async () => {
        const errorsResult = await dashboardStore.validateDashboard(source.value);
        if (errorsResult.constraints) {
            errors.value = [errorsResult.constraints];
        } else {
            errors.value = undefined;
        }

        if (dashboardId.value !== undefined && YAML_UTILS.parse(source.value).id !== dashboardId.value) {
            coreStore.message = {
                variant: "error",
                title: t("readonly property"),
                message: t("dashboards.edition.id readonly"),
            };

            await nextTick();
            if(source.value && dashboardId.value){
                source.value = YAML_UTILS.replaceBlockWithPath({
                    source: source.value,
                    path: "id",
                    newContent: dashboardId.value
                });
            }
        }
    });

    onMounted(() => {
        loadPlugins();
    });

    onBeforeUnmount(() => {
        pluginsStore.editorPlugin = undefined;
    });
</script>
<style scoped lang="scss">
    @import "@kestra-io/ui-libs/src/scss/variables";

    $spacing: 20px;

    .main-editor {
        padding: .5rem 0px;
        background: var(--ks-background-body);
        display: flex;
        height: calc(100% - 49px);
        min-height: 0;
        max-height: 100%;

        > * {
            flex: 1;
        }

        html.dark & {
            background-color: var(--bs-gray-100);
        }
    }

    .el-empty {
        background-color: transparent;

        .el-empty__description {
            font-size: var(--el-font-size-small);
        }
    }

    .custom {
        padding: 24px 32px;

        &.el-row {
            width: 100%;

            & .el-col {
                padding-bottom: $spacing;

                &:nth-of-type(even) > div {
                    margin-left: 1rem;
                }

                & > div {
                    height: 100%;
                    background: var(--ks-background-card);
                    border: 1px solid var(--ks-border-primary);
                    border-radius: $border-radius;
                }
            }
        }
    }

    .editor-combined {
        width: 50%;
        min-width: 0;
    }

    .plugin-doc {
        overflow-x: scroll;
    }

    :deep(.combined-right-view),
    .combined-right-view {
        flex: 1;
        position: relative;
        overflow-y: auto;
        height: 100%;

        &.enhance-readability {
            padding: calc(var(--spacer) * 1.5);
            background-color: var(--bs-gray-100);
        }
    }

    .chart-view {
        width: 100%;
        height: 100%;
        display: flex;
        justify-content: center;
        align-items: center;
        padding: 1rem;
    }

    .editor-only {
        height: 100%;
        display: flex;
        flex-direction: column;
    }

    .text-container {
        width: 100%;
        overflow: hidden;
        text-align: center;
        word-wrap: break-word; /* Ensures long words break and wrap to the next line */
        white-space: normal; /* Allows text to wrap to the next line */
    }

    .view-buttons {
        .el-button {
            &.el-button--primary {
                color: var(--ks-content-link);
                opacity: 1;
            }

            border: 0;
            background: none;
            opacity: 0.5;
            padding-left: 0.5rem;
            padding-right: 0.5rem;
        }
    }
</style>
