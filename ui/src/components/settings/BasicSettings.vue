<template>
    <top-nav-bar :title="routeInfo.title">
        <template #additional-right>
            <el-button @click="saveAllSettings()" type="primary">
                {{ $t("settings.blocks.save.fields.name") }}
            </el-button>
        </template>
    </top-nav-bar>

    <Wrapper>
        <Block :heading="$t('settings.blocks.configuration.label')">
            <template #content>
                <Row>
                    <Column v-if="allowDefaultNamespace" :label="$t('settings.blocks.configuration.fields.default_namespace')">
                        <namespace-select data-type="flow" :value="pendingSettings.defaultNamespace" @update:model-value="onNamespaceSelect" />
                    </Column>

                    <Column :label="$t('settings.blocks.configuration.fields.log_level')">
                        <log-level-selector clearable :value="pendingSettings.defaultLogLevel" @update:model-value="onLevelChange" />
                    </Column>

                    <Column :label="$t('settings.blocks.configuration.fields.log_display')">
                        <el-select :model-value="pendingSettings.logDisplay" @update:model-value="onLogDisplayChange">
                            <el-option
                                v-for="item in logDisplayOptions"
                                :key="item.value"
                                :label="item.text"
                                :value="item.value"
                            />
                        </el-select>
                    </Column>

                    <Column :label="$t('settings.blocks.configuration.fields.editor_type')">
                        <el-select :model-value="pendingSettings.editorType" @update:model-value="onEditorTypeChange">
                            <el-option
                                v-for="item in [
                                    {
                                        label: $t('no_code.labels.yaml'),
                                        value: 'YAML'

                                    }, 
                                    {
                                        label: $t('no_code.labels.no_code'),
                                        value: 'NO_CODE'
                                    }]"
                                :key="item.value"
                                :label="item.label"
                                :value="item.value"
                            />
                        </el-select>
                    </Column>

                    <Column :label="$t('settings.blocks.configuration.fields.execute_flow')">
                        <el-select :model-value="pendingSettings.executeFlowBehaviour" @update:model-value="onExecuteFlowBehaviourChange">
                            <el-option
                                v-for="item in Object.values(executeFlowBehaviours)"
                                :key="item"
                                :label="$t(`open in ${item}`)"
                                :value="item"
                            />
                        </el-select>
                    </Column>

                    <Column :label="$t('settings.blocks.configuration.fields.execute_default_tab')">
                        <el-select :model-value="pendingSettings.executeDefaultTab" @update:model-value="onExecuteDefaultTabChange">
                            <el-option
                                v-for="item in executeDefaultTabOptions"
                                :key="item.value"
                                :label="item.label"
                                :value="item.value"
                            />
                        </el-select>
                    </Column>
                </Row>
            </template>
        </Block>

        <Block :heading="$t('settings.blocks.theme.label')">
            <template #content>
                <Row>
                    <Column :label="$t('settings.blocks.theme.fields.mode')">
                        <el-select :model-value="pendingSettings.theme" @update:model-value="onTheme">
                            <el-option
                                v-for="item in themesOptions"
                                :key="item.value"
                                :label="item.text"
                                :value="item.value"
                            />
                        </el-select>
                    </Column>

                    <Column :label="$t('settings.blocks.theme.fields.chart_color_scheme.label')">
                        <el-select :model-value="pendingSettings.chartColor" @update:model-value="onChartColor">
                            <el-option
                                v-for="item in [
                                    {value: 'classic', text: $t('settings.blocks.theme.fields.chart_color_scheme.classic')},
                                    {value: 'kestra', text: $t('settings.blocks.theme.fields.chart_color_scheme.kestra')}
                                ]"
                                :key="item.value"
                                :label="item.text"
                                :value="item.value"
                            />
                        </el-select>
                    </Column>
                    <Column :label="$t('settings.blocks.theme.fields.editor_font_family')">
                        <el-select :model-value="pendingSettings.editorFontFamily" @update:model-value="onFontFamily">
                            <el-option
                                v-for="item in fontFamilyOptions"
                                :key="item.value"
                                :label="item.text"
                                :value="item.value"
                            />
                        </el-select>
                    </Column>
                </Row>

                <Row>
                    <Column :label="$t('settings.blocks.theme.fields.editor_theme')">
                        <el-select :model-value="pendingSettings.editorTheme" @update:model-value="onEditorTheme">
                            <el-option
                                v-for="item in editorThemesOptions"
                                :key="item.value"
                                :label="item.text"
                                :value="item.value"
                            />
                        </el-select>
                    </Column>

                    <Column :label="$t('settings.blocks.theme.fields.editor_font_size')">
                        <el-input-number
                            :model-value="pendingSettings.editorFontSize"
                            @update:model-value="onFontSize"
                            controls-position="right"
                            :min="1"
                            :max="50"
                        />
                    </Column>
                    
                    <Column :label="$t('settings.blocks.theme.fields.logs_font_size')"> 
                        <el-input-number
                            :model-value="pendingSettings.logsFontSize"
                            @update:model-value="onLogsFontSize"
                            controls-position="right"
                            :min="1"
                            :max="50"
                        />
                    </Column>
                </Row>

                <Row>
                    <Column :overrides="{sm: 24, md: 24, lg: 24, xl: 24}" :label="$t('settings.blocks.theme.fields.editor_folding_stratgy')">
                        <el-switch :aria-label="$t('Fold auto')" :model-value="pendingSettings.autofoldTextEditor" @update:model-value="onAutofoldTextEditor" />
                    </Column>
                </Row>

                <Row>
                    <Column :label="$t('settings.blocks.theme.fields.environment_name')">
                        <el-input
                            v-model="pendingSettings.envName"
                            @change="onEnvNameChange"
                            :placeholder="$t('name')"
                            clearable
                            show-word-limit
                            maxlength="30"
                        />
                    </Column>

                    <Column :label="$t('settings.blocks.theme.fields.environment_color')">
                        <el-color-picker
                            v-model="pendingSettings.envColor"
                            @change="onEnvColorChange"
                            show-alpha
                        />
                    </Column>
                </Row>
            </template>
        </Block>

        <Block :heading="$t('settings.blocks.localization.label')" :note="$t('settings.blocks.localization.note')">
            <template #content>
                <Row>
                    <Column :label="$t('settings.blocks.configuration.fields.language')">
                        <el-select :model-value="pendingSettings.lang" @update:model-value="onLang">
                            <el-option
                                v-for="item in langOptions"
                                :key="item.value"
                                :label="item.text"
                                :value="item.value"
                            />
                        </el-select>
                    </Column>

                    <Column :label="$t('settings.blocks.localization.fields.time_zone')">
                        <el-select :model-value="pendingSettings.timezone" @update:model-value="onTimezone" filterable>
                            <el-option
                                v-for="item in zonesWithOffset"
                                :key="item.zone"
                                :label="`${item.zone} (UTC${item.offset === 0 ? '' : item.formattedOffset})`"
                                :value="item.zone"
                            />
                        </el-select>
                    </Column>

                    <Column :label="$t('settings.blocks.localization.fields.date_format')">
                        <el-select :model-value="pendingSettings.dateFormat" @update:model-value="onDateFormat" :key="localeKey">
                            <el-option
                                v-for="item in dateFormats"
                                :key="pendingSettings.timezone + item.value"
                                :label="$filters.date(now, item.value)"
                                :value="item.value"
                            />
                        </el-select>
                    </Column>
                </Row>
            </template>
        </Block>

        <Block :heading="$t('settings.blocks.export.label')" v-if="canReadFlows || canReadTemplates" last>
            <template #content>
                <Row>
                    <Column>
                        <el-button v-if="canReadFlows" :icon="Download" @click="exportFlows()" class="w-100">
                            {{ $t("settings.blocks.export.fields.flows") }}
                        </el-button>
                    </Column>
                    <Column>
                        <el-button v-if="canReadTemplates" :icon="Download" @click="exportTemplates()" :hidden="!configs?.isTemplateEnabled" class="w-100">
                            {{ $t("settings.blocks.export.fields.templates") }}
                        </el-button>
                    </Column>
                </Row>
            </template>
        </Block>
    </Wrapper>
</template>

<script setup>
    import Download from "vue-material-design-icons/Download.vue";
    import {executeFlowBehaviours} from "../../utils/constants";
</script>

<script>
    import RouteContext from "../../mixins/routeContext";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import NamespaceSelect from "../../components/namespace/NamespaceSelect.vue";
    import LogLevelSelector from "../../components/logs/LogLevelSelector.vue";
    import Utils from "../../utils/utils";
    import {mapGetters, mapState, useStore} from "vuex";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import {logDisplayTypes, storageKeys} from "../../utils/constants";

    import Wrapper from "./components/Wrapper.vue"
    import Block from "./components/block/Block.vue"
    import Row from "./components/block/Row.vue"
    import Column from "./components/block/Column.vue"

    export const DATE_FORMAT_STORAGE_KEY = "dateFormat";
    export const TIMEZONE_STORAGE_KEY = "timezone";
    export default {
        mixins: [RouteContext],
        components: {
            NamespaceSelect,
            LogLevelSelector,
            TopNavBar,

            Wrapper,
            Block,
            Row,
            Column
        },
        props: {
            allowDefaultNamespace: {
                type: Boolean,
                default: true
            }
        },
        data() {
            return {
                pendingSettings: {
                    defaultNamespace: undefined,
                    defaultLogLevel: undefined,
                    editorType: undefined,
                    lang: undefined,
                    theme: undefined,
                    editorTheme: undefined,
                    chartColor: undefined,
                    dateFormat: undefined,
                    timezone: undefined,
                    autofoldTextEditor: undefined,
                    logDisplay: undefined,
                    editorFontSize: undefined,
                    editorFontFamily: undefined,
                    executeFlowBehaviour: undefined,
                    envName: undefined,
                    envColor: undefined,
                    executeDefaultTab: undefined,
                    logsFontSize: undefined
                },
                settingsKeyMapping: {
                    chartColor: "scheme",
                    dateFormat: DATE_FORMAT_STORAGE_KEY,
                    timezone: TIMEZONE_STORAGE_KEY,
                    executeFlowBehaviour: storageKeys.EXECUTE_FLOW_BEHAVIOUR,
                },
                zonesWithOffset: this.$moment.tz.names().map((zone) => {
                    const timezoneMoment = this.$moment.tz(zone);
                    return {
                        zone,
                        offset: timezoneMoment.utcOffset(),
                        formattedOffset: timezoneMoment.format("Z")
                    };
                }).sort((a, b) => a.offset - b.offset),
                guidedTour: undefined,
                now: this.$moment(), 
                localeKey: this.$moment.locale(),
            };
        },
        created() {
            const store = useStore();

            this.pendingSettings.defaultNamespace = localStorage.getItem("defaultNamespace") || "";
            this.pendingSettings.editorType = localStorage.getItem(storageKeys.EDITOR_VIEW_TYPE) || "YAML";
            this.pendingSettings.defaultLogLevel = localStorage.getItem("defaultLogLevel") || "INFO";
            this.pendingSettings.lang = Utils.getLang();
            
            this.pendingSettings.theme = Utils.getTheme();
            this.pendingSettings.editorTheme = Utils.getTheme("editorTheme")

            let scheme = localStorage.getItem("scheme") || "classic";
            if(scheme === "default") scheme = "classic";
            this.pendingSettings.chartColor =  scheme

            this.pendingSettings.dateFormat = localStorage.getItem(DATE_FORMAT_STORAGE_KEY) || "llll";
            this.pendingSettings.timezone = localStorage.getItem(TIMEZONE_STORAGE_KEY) || this.$moment.tz.guess();
            this.pendingSettings.autofoldTextEditor = localStorage.getItem("autofoldTextEditor") === "true";
            this.guidedTour = localStorage.getItem("tourDoneOrSkip") === "true";
            this.pendingSettings.logDisplay = localStorage.getItem("logDisplay") || logDisplayTypes.DEFAULT;
            this.pendingSettings.editorFontSize = parseInt(localStorage.getItem("editorFontSize")) || 12;
            this.pendingSettings.editorFontFamily = localStorage.getItem("editorFontFamily") || "'Source Code Pro', monospace";
            this.pendingSettings.executeFlowBehaviour = localStorage.getItem("executeFlowBehaviour") || "same tab";
            this.pendingSettings.executeDefaultTab = localStorage.getItem("executeDefaultTab") || "gantt";
            this.pendingSettings.envName = store.getters["layout/envName"] || this.configs?.environment?.name;
            this.pendingSettings.envColor = store.getters["layout/envColor"] || this.configs?.environment?.color;
            this.pendingSettings.logsFontSize = parseInt(localStorage.getItem("logsFontSize")) || 12;
        },
        methods: {
            onNamespaceSelect(value) {
                this.pendingSettings.defaultNamespace = value;
            },
            onEditorTypeChange(value) {
                this.pendingSettings.editorType = value;
                localStorage.setItem(storageKeys.EDITOR_VIEW_TYPE, value);
            },
            onLevelChange(value) {
                this.pendingSettings.defaultLogLevel = value;
            },
            onLang(value) {
                this.pendingSettings.lang = value;
            },
            onTheme(value) {
                this.pendingSettings.theme = value;
            },
            updateThemeBasedOnSystem() {
                if (this.theme === "syncWithSystem") {
                    Utils.switchTheme("syncWithSystem");
                }
            },
            onDateFormat(value) {
                this.pendingSettings.dateFormat = value;
            },
            onTimezone(value) {
                this.pendingSettings.timezone = value;
            },
            onEditorTheme(value) {
                this.pendingSettings.editorTheme = value;
            },
            onChartColor(value) {
                this.pendingSettings.chartColor = value;
            },
            onAutofoldTextEditor(value) {
                this.pendingSettings.autofoldTextEditor = value;
            },
            exportFlows() {
                return this.$store
                    .dispatch("flow/exportFlowByQuery", {})
                    .then(_ => {
                        this.$toast().success(this.$t("flows exported"));
                    })
            },
            exportTemplates() {
                return this.$store
                    .dispatch("template/exportTemplateByQuery", {})
                    .then(_ => {
                        this.$toast().success(this.$t("templates exported"));
                    })
            },
            onLogDisplayChange(value) {
                this.pendingSettings.logDisplay = value;
            },
            onFontSize(value) {
                this.pendingSettings.editorFontSize = value;
            },
            onFontFamily(value) {
                this.pendingSettings.editorFontFamily = value;
            },
            onEnvNameChange(value) {
                this.pendingSettings.envName = value;
            },
            onEnvColorChange(value) {
                this.pendingSettings.envColor = value;
            },
            onExecuteFlowBehaviourChange(value) {
                this.pendingSettings.executeFlowBehaviour = value;
            },
            onExecuteDefaultTabChange(value){
                this.pendingSettings.executeDefaultTab = value;
            },
            onLogsFontSize(value) {
                this.pendingSettings.logsFontSize = value;
            },
            saveAllSettings() {
                Object.keys(this.pendingSettings).forEach((key) => {
                    const storedKey = this.settingsKeyMapping[key]
                    switch(key) {
                    case "defaultNamespace":
                    case "defaultLogLevel":
                        if(this.pendingSettings[key])
                            localStorage.setItem(key, this.pendingSettings[key])
                        else
                            localStorage.removeItem(key)
                        break
                    case "envName":
                        if (this.pendingSettings[key] !== this.configs?.environment?.name) {
                            this.$store.commit("layout/setEnvName", this.pendingSettings[key])
                        }
                        break
                    case "envColor":
                        if (this.pendingSettings[key] !== this.configs?.environment?.color) {
                            this.$store.commit("layout/setEnvColor", this.pendingSettings[key])
                        }
                        break
                    case "autofoldTextEditor":
                        localStorage.setItem(key, this.pendingSettings[key])
                        break
                    case "logsFontSize":
                        localStorage.setItem(key, this.pendingSettings[key])
                        this.$store.commit("layout/setLogsFontSize", this.pendingSettings[key])
                        break   
                    case "theme":
                        Utils.switchTheme(this.pendingSettings[key]);
                        localStorage.setItem(key, Utils.getTheme())
                        break
                    case "lang":
                    {
                        if(this.pendingSettings[key]) {
                            localStorage.setItem(key, this.pendingSettings[key])
                        }

                        let newlang = Utils.getLang();
                        this.$moment.locale(newlang);
                        this.$i18n.locale = newlang;
                        this.localeKey = this.$moment.locale();

                        break;
                    }
                    default:
                        if (storedKey) {
                            if(this.pendingSettings[key])
                                localStorage.setItem(storedKey, this.pendingSettings[key])
                        }
                        else {
                            if(this.pendingSettings[key])
                                localStorage.setItem(key, this.pendingSettings[key])
                        }
                    }
                })
                this.$toast().saved(this.$t("settings.label"), undefined, {multiple: true});
            }
        },
        mounted() {
            const mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
            mediaQuery.addEventListener("change", this.updateThemeBasedOnSystem);
        },
        computed: {
            ...mapState("auth", ["user"]),
            ...mapGetters("misc", ["configs"]),
            routeInfo() {
                return {
                    title: this.$t("settings.label")
                };
            },
            langOptions() {
                return [
                    {value: "en", text: "English"},
                    {value: "fr", text: "French"},
                    {value: "de", text: "German"},
                    {value: "pl", text: "Polish"},
                    {value: "it", text: "Italian"},
                    {value: "es", text: "Spanish"},
                    {value: "pt", text: "Portuguese"},
                    {value: "ru", text: "Russian"},
                    {value: "zh_CN", text: "Chinese"},
                    {value: "ja", text: "Japanese"},
                    {value: "ko", text: "Korean"},
                    {value: "hi", text: "Hindi"}
                ];
            },
            themesOptions() {
                return [
                    {value: "light", text: "Light"},
                    {value: "dark", text: "Dark"},
                    {value: "syncWithSystem", text: "Sync With System"}
                ]
            },
            editorThemesOptions() {
                return  [
                    {value: "light", text: "Light"},
                    {value: "dark", text: "Dark"},
                    {value: "syncWithSystem", text: "Sync With System"}
                ]
            },
            dateFormats() {
                return  [
                    {value: "YYYY-MM-DDTHH:mm:ssZ"},
                    {value: "YYYY-MM-DD hh:mm:ss A"},
                    {value: "DD/MM/YYYY HH:mm:ss"},
                    {value: "lll"},
                    {value: "llll"},
                    {value: "LLL"},
                    {value: "LLLL"}
                ]
            },
            canReadFlows() {
                return this.user && this.user.isAllowed(permission.FLOW, action.READ);
            },
            canReadTemplates() {
                return this.user && this.user.isAllowed(permission.TEMPLATE, action.READ);
            },
            logDisplayOptions() {
                return  [
                    {value: logDisplayTypes.ERROR, text: this.$t("expand error")},
                    {value: logDisplayTypes.ALL, text: this.$t("expand all")},
                    {value: logDisplayTypes.HIDDEN, text: this.$t("collapse all")}
                ]
            },
            fontFamilyOptions() {
                // Array of font family that contains arabic language and japanese, chinese, korean languages compatible font family
                return [
                    {
                        value: "'Source Code Pro', monospace",
                        text: "Source Code Pro"
                    },
                    {
                        value: "'Courier New', monospace",
                        text: "Courier"
                    },
                    {
                        value: "'Times New Roman', serif",
                        text: "Times New Roman"
                    },
                    {
                        value: "'Book Antiqua', serif",
                        text: "Book Antiqua"
                    },
                    {
                        value: "'Times New Roman Arabic', serif",
                        text: "Times New Roman Arabic"
                    },
                    {
                        value: "'SimSun', sans-serif",
                        text: "SimSun"
                    }
                ]
            },
            executeDefaultTabOptions() {
                return [
                    {
                        value : "overview",
                        label: this.$t("overview")
                    },
                    {
                        value : "gantt",
                        label: this.$t("gantt")
                    },
                    {
                        value : "logs",
                        label: this.$t("logs")
                    },
                    {
                        value : "topology",
                        label: this.$t("topology")
                    },
                    {
                        value: "outputs",
                        label: this.$t("outputs")
                    },
                    {
                        value : "metrics",
                        label: this.$t("metrics")
                    }
                ]
            }
        }
    };
</script>
<style>

    .settings-wrapper .el-input-number {
        max-width: 20vw;
    }

    .el-input__count {
        color: var(--bs-white) !important;
        
        .el-input__count-inner {
            background: none !important;
        }
    }
</style>