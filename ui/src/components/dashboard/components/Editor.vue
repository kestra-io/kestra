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
            tooltip-placement="bottom-start"
            :errors="errors"
        />

        <el-button
            :icon="ContentSave"
            @click="$emit('save', source)"
            :type="saveButtonType"
            :disabled="!allowSaveUnchanged && source === initialSource"
        >
            {{ $t("save") }}
        </el-button>
    </div>
    <div class="w-100 p-4" v-if="currentView === views.DASHBOARD">
        <Sections :charts="charts.map(chart => chart.data)" show-default />
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
                class="combined-right-view enhance-readability"
                :override-intro="intro"
                absolute
            />
            <div
                class="d-flex justify-content-center align-items-center w-100 p-3"
                v-else-if="currentView === views.CHART"
            >
                <div v-if="selectedChart" class="w-100">
                    <p class="fs-6 fw-bold">
                        {{ getChartTitle(selectedChart) }}
                    </p>
                    <p
                        v-if="selectedChart.chartOptions?.description"
                    >
                        <small>{{ selectedChart.chartOptions.description }}</small>
                    </p>

                    <div :style="`position: relative; width:calc(${100}% - 10px)`">
                        <component
                            :key="selectedChart.id"
                            :is="TYPES[selectedChart.type]"
                            :source="selectedChart.content"
                            :chart="selectedChart"
                            :identifier="selectedChart.id"
                            show-default
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
                                {{ $t("dashboards.chart_preview") }}
                            </h5>
                        </template>
                    </el-empty>
                </div>
            </div>
        </div>
    </div>
</template>
<script setup>
    import {YamlUtils as YAML_UTILS} from "@kestra-io/ui-libs";

    import {TYPES, getChartTitle} from "../composables/useDashboards";

    import PluginDocumentation from "../../plugins/PluginDocumentation.vue";
    import Sections from "../sections/Sections.vue";
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
    import {mapStores} from "pinia";

    import Editor from "../../inputs/Editor.vue";
    import {usePluginsStore} from "../../../stores/plugins";
    import {useDashboardStore} from "../../../stores/dashboard";
    import yaml from "yaml";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
    import intro from "../../../assets/docs/dashboard_home.md?raw";

    export default {
        computed: {
            ...mapStores(usePluginsStore, useDashboardStore),
            ContentSave() {
                return ContentSave
            },
            saveButtonType() {
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
                    const type = YAML_UTILS.getTaskType(event.model.getValue(), event.position, this.plugins)
                    if (type) {

                        this.pluginsStore.load({cls: type})
                            .then(plugin => {
                                this.pluginsStore.editorPlugin = {cls: type, ...plugin};
                            })
                    } else {
                        this.pluginsStore.editorPlugin = undefined;
                    }
                } else if (this.currentView === this.views.CHART) {
                    const chart = YAML_UTILS.getChartAtPosition(event.model.getValue(), event.position)
                    if (chart) {
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
                this.pluginsStore.list({...this.$route.params})
                    .then(data => {
                        this.plugins = data.map(plugin => {
                            const charts = plugin.charts || [];
                            const dataFilters = plugin.dataFilters || [];
                            return charts.concat(dataFilters);
                        }).flat();
                    })
            },
            buttonType(view) {
                return view === this.currentView ? "primary" : "default";
            },
            setView(view) {
                this.currentView = view;

                if (view === this.views.DASHBOARD) {
                    this.validateAndLoadAllCharts();
                }
            },
            async validateAndLoadAllCharts() {
                this.charts = [];
                const allCharts = YAML_UTILS.getAllCharts(this.source);
                for (const chart of allCharts) {
                    const loadedChart = await this.loadChart(chart);
                    this.charts.push(loadedChart);
                }
            },
            async loadChart(chart) {
                const yamlChart = yaml.stringify(chart);
                const result = {error: null, data: null, raw: {}};
                await this.dashboardStore.validateChart(yamlChart)
                    .then(errors => {
                        if (errors.constraints) {
                            result.error = errors.constraints;
                        } else {
                            result.data = {...chart, content: yamlChart, raw: chart};
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
                chartError: null
            }
        },
        watch: {
            source() {
                this.dashboardStore.validateDashboard(this.source)
                    .then(errors => {
                        if (errors.constraints) {
                            this.errors = [errors.constraints];
                        } else {
                            this.errors = undefined;
                        }
                    });
            }
        },
        beforeUnmount() {
            this.pluginsStore.editorPlugin = undefined;
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
