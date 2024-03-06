<template>
    <top-nav-bar :title="routeInfo.title" />
    <section class="container">
        <el-form class="ks-horizontal max-size">
            <el-form-item :label="$t('Language')">
                <el-select :model-value="lang" @update:model-value="onLang">
                    <el-option
                        v-for="item in langOptions"
                        :key="item.value"
                        :label="item.text"
                        :value="item.value"
                    />
                </el-select>
            </el-form-item>

            <el-form-item :label="$t('theme')">
                <el-select :model-value="theme" @update:model-value="onTheme">
                    <el-option
                        v-for="item in themesOptions"
                        :key="item.value"
                        :label="item.text"
                        :value="item.value"
                    />
                </el-select>
            </el-form-item>

            <el-form-item :label="$t('date format')">
                <el-select :model-value="dateFormat" @update:model-value="onDateFormat">
                    <el-option
                        v-for="item in dateFormats"
                        :key="timezone + item.value"
                        :label="$filters.date(now, item.value)"
                        :value="item.value"
                    />
                </el-select>
            </el-form-item>

            <el-form-item :label="$t('timezone')">
                <el-select :model-value="timezone" @update:model-value="onTimezone" filterable>
                    <el-option
                        v-for="item in zonesWithOffset"
                        :key="item.zone"
                        :label="`${item.zone} (UTC${item.offset === 0 ? '' : item.formattedOffset})`"
                        :value="item.zone"
                    />
                </el-select>
            </el-form-item>

            <el-form-item :label="$t('Editor theme')">
                <el-select :model-value="editorTheme" @update:model-value="onEditorTheme">
                    <el-option
                        v-for="item in editorThemesOptions"
                        :key="item.value"
                        :label="item.text"
                        :value="item.value"
                    />
                </el-select>
            </el-form-item>

            <el-form-item :label="$t('Editor fontsize')">
                <el-input-number
                    :model-value="editorFontSize"
                    @update:model-value="onFontSize"
                    controls-position="right"
                    :min="1"
                    :max="50"
                />
            </el-form-item>

            <el-form-item :label="$t('Editor fontfamily')">
                <el-select :model-value="editorFontFamily" @update:model-value="onFontFamily">
                    <el-option
                        v-for="item in fontFamilyOptions"
                        :key="item.value"
                        :label="item.text"
                        :value="item.value"
                    />
                </el-select>
            </el-form-item>

            <el-form-item label="&nbsp;">
                <el-checkbox :label="$t('Fold auto')" :model-value="autofoldTextEditor" @update:model-value="onAutofoldTextEditor" />
            </el-form-item>

            <el-form-item :label="$t('Default namespace')" v-if="allowDefaultNamespace">
                <namespace-select data-type="flow" :value="defaultNamespace" @update:model-value="onNamespaceSelect" />
            </el-form-item>

            <el-form-item :label="$t('Default log level')">
                <log-level-selector clearable :value="defaultLogLevel" @update:model-value="onLevelChange" />
            </el-form-item>

            <el-form-item v-if="canReadFlows || canReadTemplates" :label="$t('exports')">
                <el-button v-if="canReadFlows" :icon="Download" size="large" @click="exportFlows()">
                    {{ $t('export all flows') }}
                </el-button>
                <el-button v-if="canReadTemplates" :icon="Download" size="large" @click="exportTemplates()" :hidden="!configs.isTemplateEnabled">
                    {{ $t('export all templates') }}
                </el-button>
            </el-form-item>

            <el-form-item :label="$t('log expand setting')">
                <el-select :model-value="logDisplay" @update:model-value="onLogDisplayChange">
                    <el-option
                        v-for="item in logDisplayOptions"
                        :key="item.value"
                        :label="item.text"
                        :value="item.value"
                    />
                </el-select>
            </el-form-item>

            <el-form-item :label="$t('environment name setting')">
                <el-input
                    v-model="envName"
                    @change="onEnvNameChange"
                    :placeholder="$t('name')"
                    clearable
                />
            </el-form-item>

            <el-form-item :label="$t('environment color setting')">
                <el-color-picker
                    v-model="envColor"
                    @change="onEnvColorChange"
                    show-alpha
                />
            </el-form-item>
            <el-form-item :label="$t('execute flow behaviour')">
                <el-select :model-value="executeFlowBehaviour" @update:model-value="onExecuteFlowBehaviourChange">
                    <el-option
                        v-for="item in Object.values(executeFlowBehaviours)"
                        :key="item"
                        :label="$t(`open in ${item}`)"
                        :value="item"
                    />
                </el-select>
            </el-form-item>
        </el-form>
        <slot name="form-items" />
    </section>
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

    export const DATE_FORMAT_STORAGE_KEY = "dateFormat";
    export const TIMEZONE_STORAGE_KEY = "timezone";
    export default {
        mixins: [RouteContext],
        components: {
            NamespaceSelect,
            LogLevelSelector,
            TopNavBar
        },
        props: {
            allowDefaultNamespace: {
                type: Boolean,
                default: true
            }
        },
        data() {
            return {
                defaultNamespace: undefined,
                defaultLogLevel: undefined,
                lang: undefined,
                theme: undefined,
                editorTheme: undefined,
                dateFormat: undefined,
                timezone: undefined,
                zonesWithOffset: this.$moment.tz.names().map((zone) => {
                    const timezoneMoment = this.$moment.tz(zone);
                    return {
                        zone,
                        offset: timezoneMoment.utcOffset(),
                        formattedOffset: timezoneMoment.format("Z")
                    };
                }).sort((a, b) => a.offset - b.offset),
                autofoldTextEditor: undefined,
                guidedTour: undefined,
                logDisplay: undefined,
                editorFontSize: undefined,
                editorFontFamily: undefined,
                executeFlowBehaviour: undefined,
                now: this.$moment(),
                envName: undefined,
                envColor: undefined
            };
        },
        created() {
            const darkTheme = document.getElementsByTagName("html")[0].className.indexOf("dark") >= 0;
            const store = useStore();

            this.defaultNamespace = localStorage.getItem("defaultNamespace") || "";
            this.defaultLogLevel = localStorage.getItem("defaultLogLevel") || "INFO";
            this.lang = localStorage.getItem("lang") || "en";
            this.theme = localStorage.getItem("theme") || "light";
            this.editorTheme = localStorage.getItem("editorTheme") || (darkTheme ? "dark" : "vs");
            this.dateFormat = localStorage.getItem(DATE_FORMAT_STORAGE_KEY) || "llll";
            this.timezone = localStorage.getItem(TIMEZONE_STORAGE_KEY) || this.$moment.tz.guess();
            this.autofoldTextEditor = localStorage.getItem("autofoldTextEditor") === "true";
            this.guidedTour = localStorage.getItem("tourDoneOrSkip") === "true";
            this.logDisplay = localStorage.getItem("logDisplay") || logDisplayTypes.DEFAULT;
            this.editorFontSize = parseInt(localStorage.getItem("editorFontSize")) || 12;
            this.editorFontFamily = localStorage.getItem("editorFontFamily") || "'Source Code Pro', monospace";
            this.executeFlowBehaviour = localStorage.getItem("executeFlowBehaviour") || "same tab";
            this.envName = store.getters["layout/envName"] || this.configs?.environment?.name;
            this.envColor = store.getters["layout/envColor"] || this.configs?.environment?.color;
        },
        methods: {
            onNamespaceSelect(value) {
                this.defaultNamespace = value;

                if (value) {
                    localStorage.setItem("defaultNamespace", value)
                } else {
                    localStorage.removeItem("defaultNamespace")
                }
                this.$toast().saved(this.$t("settings"), undefined, {multiple: true});
            },
            onLevelChange(value) {
                this.defaultLogLevel = value;

                if (value) {
                    localStorage.setItem("defaultLogLevel", value)
                } else {
                    localStorage.removeItem("defaultLogLevel")
                }
                this.$toast().saved(this.$t("settings"), undefined, {multiple: true});
            },
            onLang(value) {
                localStorage.setItem("lang", value);
                this.$moment.locale(value);
                this.$i18n.locale = value;
                this.lang = value;
                this.$toast().saved(this.$t("settings"), undefined, {multiple: true});
            },
            onTheme(value) {
                Utils.switchTheme(value)
                this.theme = value;
                this.$toast().saved(this.$t("settings"), undefined, {multiple: true});
            },
            onDateFormat(value) {
                localStorage.setItem(DATE_FORMAT_STORAGE_KEY, value);
                this.dateFormat = value;
                this.$toast().saved(this.$t("settings"), undefined, {multiple: true});
            },
            onTimezone(value) {
                localStorage.setItem(TIMEZONE_STORAGE_KEY, value);
                this.timezone = value;
                this.$toast().saved(this.$t("settings"), undefined, {multiple: true});
            },
            onEditorTheme(value) {
                localStorage.setItem("editorTheme", value);
                this.editorTheme = value;
                this.$toast().saved(this.$t("settings"), undefined, {multiple: true});
            },
            onAutofoldTextEditor(value) {
                localStorage.setItem("autofoldTextEditor", value);
                this.autofoldTextEditor = value;
                this.$toast().saved(this.$t("settings"), undefined, {multiple: true});
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
                localStorage.setItem("logDisplay", value);
                this.logDisplay = value;
                this.$toast().saved(this.$t("settings"), undefined, {multiple: true});
            },
            onFontSize(value) {
                localStorage.setItem("editorFontSize", value);
                this.editorFontSize = value;
                this.$toast().saved(this.$t("settings"), undefined, {multiple: true});
            },
            onFontFamily(value) {
                localStorage.setItem("editorFontFamily", value);
                this.editorFontFamily = value;
                this.$toast().saved(this.$t("settings"), undefined, {multiple: true});
            },
            onEnvNameChange(value) {
                if (value !== this.configs?.environment?.name) {
                    this.$store.commit("layout/setEnvName", value);
                }

                this.$toast().saved(this.$t("settings"), undefined, {multiple: true});
            },
            onEnvColorChange(value) {
                if (value !== this.configs?.environment?.color) {
                    this.$store.commit("layout/setEnvColor", value);
                }

                this.$toast().saved(this.$t("settings"), undefined, {multiple: true});
            },
            onExecuteFlowBehaviourChange(value) {
                this.executeFlowBehaviour = value;

                localStorage.setItem(storageKeys.EXECUTE_FLOW_BEHAVIOUR, value);

                this.$toast().saved(this.$t("settings"), undefined, {multiple: true});
            }
        },
        computed: {
            ...mapGetters("core", ["guidedProperties"]),
            ...mapState("auth", ["user"]),
            ...mapGetters("misc", ["configs"]),
            routeInfo() {
                return {
                    title: this.$t("settings")
                };
            },
            langOptions() {
                return [
                    {value: "en", text: "English"},
                    {value: "fr", text: "Français"}
                ];
            },
            themesOptions() {
                return [
                    {value: "light", text: "Light"},
                    {value: "dark", text: "Dark"}
                ]
            },
            editorThemesOptions() {
                return  [
                    {value: "vs", text: "Light"},
                    {value: "dark", text: "Dark"}
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
            }
        }
    };
</script>
<style>
    .el-input-number {
        max-width: 20vw;
    }
</style>
