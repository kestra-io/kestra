<template>
    <div class="button-top">
        <el-button-group>
            <el-tooltip :content="$t('source only')">
                <el-button
                    :icon="FileDocumentEditOutline"
                    @click="setView(views.NONE)"
                />
            </el-tooltip>
            <el-tooltip :content="$t('documentation.documentation')">
                <el-button
                    :icon="BookOpenVariant"
                    @click="setView(views.DOC)"
                />
            </el-tooltip>
            <el-tooltip :content="$t('chart preview')">
                <el-button
                    :icon="ChartBar"
                    @click="setView(views.CHART)"
                />
            </el-tooltip>
            <el-tooltip :content="$t('dashboard.preview')">
                <el-button
                    :icon="ViewDashboard"
                    @click="setView(views.DASHBOARD)"
                />
            </el-tooltip>
        </el-button-group>

        <ValidationErrors
            class="mx-3"
            tooltip-placement="bottom-start"
            :errors="errors"
        />

        <el-button
            :icon="ContentSave"
            @click="$emit('save', source)"
            :type="buttonType"
            :disabled="!allowSaveUnchanged && source === initialSource"
        >
            {{ $t("save") }}
        </el-button>
    </div>
    <div class="w-100" v-if="currentView === views.DASHBOARD">
        <el-row class="custom">
            <el-col
                v-for="chart in charts"
                :key="JSON.stringify(chart)"
                :xs="24"
                :sm="12"
            >
                <div
                    v-if="chart.data"
                    class="p-4 d-flex flex-column"
                >
                    <p class="m-0 fs-6 fw-bold">
                        {{ chart.data.chartOptions?.displayName ?? chart.id }}
                    </p>
                    <p
                        v-if="chart.chartOptions?.description"
                        class="m-0 fw-light"
                    >
                        <small>{{ chart.data.chartOptions.description }}</small>
                    </p>

                    <div class="mt-4 flex-grow-1">
                        <component
                            :is="types[chart.data.type]"
                            :source="chart.data.content"
                            :chart="chart.data"
                            :identifier="chart.data.id"
                            is-preview
                        />
                    </div>
                </div>
                <div v-else class="d-flex justify-content-center align-items-center text-container">
                    <el-tooltip :content="chart.error">
                        {{ chart.error }}
                    </el-tooltip>
                </div>
            </el-col>
        </el-row>
    </div>
    <div class="main-editor" v-else>
        <div
            id="editorWrapper"
            class="editor-combined"
            style="flex: 1;"
        >
            <editor
                @save="(allowSaveUnchanged || source !== initialSource) ? $emit('save', $event) : undefined"
                v-model="source"
                schema-type="dashboard"
                lang="yaml"
                @update:model-value="source = $event"
                @cursor="updatePluginDocumentation"
                :creating="true"
                :read-only="false"
                :navbar="false"
            />
        </div>
        <div v-if="displaySide" class="slider" @mousedown.prevent.stop="dragEditor" />
        <div
            v-if="displaySide"
            :class="{'d-flex': displaySide}"
            :style="displaySide ? `flex: 0 0 calc(${100 - editorWidth}% - 11px)` : 'flex: 1 0 0%'"
        >
            <PluginDocumentation
                v-if="currentView === views.DOC"
                class="plugin-doc combined-right-view enhance-readability"
                :override-intro="intro"
            />
            <div
                class="d-flex justify-content-center align-items-center w-100 p-5"
                v-else-if="currentView === views.CHART"
            >
                <div v-if="selectedChart" class="w-100">
                    <p class="fs-6 fw-bold">
                        {{ selectedChart.chartOptions?.displayName ?? selectedChart.id }}
                    </p>
                    <p
                        v-if="selectedChart.chartOptions?.description"
                    >
                        <small>{{ selectedChart.chartOptions.description }}</small>
                    </p>

                    <div class="w-100">
                        <component
                            :key="selectedChart.id"
                            :is="types[selectedChart.type]"
                            :source="selectedChart.content"
                            :chart="selectedChart"
                            :identifier="selectedChart.id"
                            is-preview
                        />
                    </div>
                </div>
                <div v-else-if="chartError" class="text-container">
                    <span>{{ chartError }}</span>
                </div>
                <div v-else>
                    <el-empty :image="EmptyVisualDashboard" :image-size="200">
                        <template #description>
                            <h5>
                                {{ $t("dashboard.click_chart_preview") }}
                            </h5>
                        </template>
                    </el-empty>
                </div>
            </div>
        </div>
    </div>
</template>
<script setup>
    import PluginDocumentation from "../../plugins/PluginDocumentation.vue";
    import ValidationErrors from "../../flows/ValidationError.vue"
    import BookOpenVariant from "vue-material-design-icons/BookOpenVariant.vue";
    import ChartBar from "vue-material-design-icons/ChartBar.vue";
    import FileDocumentEditOutline from "vue-material-design-icons/FileDocumentEditOutline.vue";
    import ViewDashboard from "vue-material-design-icons/ViewDashboard.vue";
    import EmptyVisualDashboard from "../../../assets/empty_visuals/Visuals_empty_dashboard.svg"

    // avoid an eslint warning about missing declaration
    defineEmits(["save"])
</script>
<script>
    import Editor from "../../inputs/Editor.vue";
    import YamlUtils from "../../../utils/yamlUtils.js";
    import yaml from "yaml";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
    import intro from "../../../assets/markdown/dashboard_home.md?raw";
    import Markdown from "../../layout/Markdown.vue";
    import TimeSeries from "./charts/custom/TimeSeries.vue";
    import Bar from "./charts/custom/Bar.vue";
    import Pie from "./charts/custom/Pie.vue";
    import Table from "./tables/custom/Table.vue";

    export default {
        computed: {
            ContentSave() {
                return ContentSave
            },
            YamlUtils() {
                return YamlUtils
            },
            buttonType() {
                if (this.errors) {
                    return "danger";
                }

                return this.warnings
                    ? "warning"
                    : "primary";
            },
            displaySide() {
                return this.currentView !== this.views.NONE && this.currentView !== this.views.DASHBOARD;
            }
        },
        props: {
            allowSaveUnchanged: {
                type: Boolean,
                default: false
            },
            initialSource: {
                type: String,
                default: undefined
            }
        },
        mounted() {
            this.loadPlugins();
        },
        components: {
            Editor
        },
        methods: {
            async updatePluginDocumentation(event) {
                if (this.currentView === this.views.DOC) {
                    const type = YamlUtils.getTaskType(event.model.getValue(), event.position, this.plugins)
                    if (type) {
                        this.$store.dispatch("plugin/load", {cls: type})
                            .then(plugin => {
                                this.$store.commit("plugin/setEditorPlugin", {cls: type, ...plugin});
                            });
                    } else {
                        this.$store.commit("plugin/setEditorPlugin", undefined);
                    }
                } else if (this.currentView === this.views.CHART) {
                    const chart = YamlUtils.getChartAtPosition(event.model.getValue(), event.position)
                    if (chart && this.selectedChart?.id !== chart.id) {
                        const result = await this.loadChart(chart);
                        this.selectedChart = result.data;
                        this.chartError = result.error;
                    }
                }
            },
            dragEditor(e) {
                let dragX = e.clientX;

                const {offsetWidth, parentNode} = document.getElementById("editorWrapper");
                let blockWidthPercent = (offsetWidth / parentNode.offsetWidth) * 100;

                const onMouseMove = (e) => {
                    let percent = blockWidthPercent + ((e.clientX - dragX) / parentNode.offsetWidth) * 100;
                    this.editorWidth = percent > 75 ? 75 : percent < 25 ? 25 : percent;

                };
                document.onmousemove = onMouseMove.bind(this);

                document.onmouseup = () => {
                    document.onmousemove = document.onmouseup = null;
                };
            },
            loadPlugins() {
                this.$store.dispatch("plugin/list", {...this.$route.params})
                    .then(data => {
                        this.plugins = data.map(plugin => {
                            const charts = plugin.charts || [];
                            const dataFilters = plugin.dataFilters || [];
                            return charts.concat(dataFilters);
                        }).flat();
                    })
            },
            setView(view) {
                this.currentView = view;

                if (view === this.views.DASHBOARD) {
                    this.validateAndLoadAllCharts();
                }
            },
            async validateAndLoadAllCharts() {
                this.charts = [];
                const allCharts = YamlUtils.getAllCharts(this.source);
                for (const chart of allCharts) {
                    const loadedChart = await this.loadChart(chart);
                    this.charts.push(loadedChart);
                }
            },
            async loadChart(chart) {
                const yamlChart = yaml.stringify(chart);
                const result = {error: null, data: null};
                await this.$store.dispatch("dashboard/validateChart", yamlChart)
                    .then(errors => {
                        if (errors.constraints) {
                            result.error = errors.constraints;
                        } else {
                            result.data = {...chart, content: yamlChart};
                        }
                    });
                return result;
            }
        },
        data() {
            return {
                source: this.initialSource,
                errors: undefined,
                warnings: undefined,
                editorWidth: 50,
                views: {
                    DOC: "documentation",
                    CHART: "chart",
                    NONE: "none",
                    DASHBOARD: "dashboard"
                },
                currentView: "documentation",
                selectedChart: null,
                charts: [],
                chartError: null,
                types: {
                    "io.kestra.plugin.core.dashboard.chart.TimeSeries": TimeSeries,
                    "io.kestra.plugin.core.dashboard.chart.Bar": Bar,
                    "io.kestra.plugin.core.dashboard.chart.Markdown": Markdown,
                    "io.kestra.plugin.core.dashboard.chart.Table": Table,
                    "io.kestra.plugin.core.dashboard.chart.Pie": Pie,
                }
            }
        },
        watch: {
            source() {
                this.$store.dispatch("dashboard/validate", this.source)
                    .then(errors => {
                        if (errors.constraints) {
                            this.errors = [errors.constraints];
                        }
                    });
            }
        },
        beforeUnmount() {
            this.$store.commit("plugin/setEditorPlugin", undefined);
        }
    };
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

        &::-webkit-scrollbar {
            width: 10px;
            height: 2px;
        }

        &::-webkit-scrollbar-track {
            background: var(--card-bg);
        }

        &::-webkit-scrollbar-thumb {
            background: var(--bs-primary);
            border-radius: 20px;
        }
    }

    .slider {
        flex: 0 0 3px;
        border-radius: 0.15rem;
        margin: 0 4px;
        background-color: var(--bs-border-color);
        border: none;
        cursor: col-resize;
        user-select: none; /* disable selection */
        padding: 0;

        &:hover {
            background-color: var(--bs-secondary);
        }
    }

    .text-container {
        width: 100%;
        overflow: hidden;
        text-align: center;
        word-wrap: break-word; /* Ensures long words break and wrap to the next line */
        white-space: normal; /* Allows text to wrap to the next line */
    }
</style>