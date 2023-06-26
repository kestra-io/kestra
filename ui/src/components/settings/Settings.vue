<template>
    <div>
        <el-form class="ks-horizontal">
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

            <el-form-item label="&nbsp;">
                <el-checkbox :label="$t('Fold auto')" :model-value="autofoldTextEditor" @update:model-value="onAutofoldTextEditor" />
            </el-form-item>

            <el-form-item :label="$t('Default namespace')">
                <namespace-select data-type="flow" :value="defaultNamespace" @update:model-value="onNamespaceSelect" />
            </el-form-item>

            <el-form-item :label="$t('Default log level')">
                <log-level-selector clearable :value="defaultLogLevel" @update:model-value="onLevelChange" />
            </el-form-item>

            <el-form-item v-if="canReadFlows || canReadTemplates" :label="$t('exports')">
                <el-button v-if="canReadFlows" :icon="Download" size="large" @click="exportFlows()">
                    {{ $t('export all flows') }}
                </el-button>
                <el-button v-if="canReadTemplates" :icon="Download" size="large" @click="exportTemplates()">
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
        </el-form>
    </div>
</template>

<script setup>
    import Download from "vue-material-design-icons/Download.vue";
</script>

<script>
    import RouteContext from "../../mixins/routeContext";
    import NamespaceSelect from "../../components/namespace/NamespaceSelect.vue";
    import LogLevelSelector from "../../components/logs/LogLevelSelector.vue";
    import Utils from "../../utils/utils";
    import {mapGetters, mapState} from "vuex";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import {logDisplayTypes} from "../../utils/constants";

    export default {
        mixins: [RouteContext],
        components: {
            NamespaceSelect,
            LogLevelSelector,
        },
        data() {
            return {
                defaultNamespace: undefined,
                defaultLogLevel: undefined,
                lang: undefined,
                theme: undefined,
                editorTheme: undefined,
                autofoldTextEditor: undefined,
                guidedTour: undefined,
                logDisplay: undefined
            };
        },
        created() {
            const darkTheme = document.getElementsByTagName("html")[0].className.indexOf("dark") >= 0;

            this.defaultNamespace = localStorage.getItem("defaultNamespace") || "";
            this.defaultLogLevel = localStorage.getItem("defaultLogLevel") || "INFO";
            this.lang = localStorage.getItem("lang") || "en";
            this.theme = localStorage.getItem("theme") || "light";
            this.editorTheme = localStorage.getItem("editorTheme") || (darkTheme ? "dark" : "vs");
            this.autofoldTextEditor = localStorage.getItem("autofoldTextEditor") === "true";
            this.guidedTour = localStorage.getItem("tourDoneOrSkip") === "true";
            this.logDisplay = localStorage.getItem("logDisplay") || logDisplayTypes.DEFAULT;
        },
        methods: {
            onNamespaceSelect(value) {
                this.defaultNamespace = value;

                if (value) {
                    localStorage.setItem("defaultNamespace", value)
                } else {
                    localStorage.removeItem("defaultNamespace")
                }
                this.$toast().saved();
            },
            onLevelChange(value) {
                this.defaultLogLevel = value;

                if (value) {
                    localStorage.setItem("defaultLogLevel", value)
                } else {
                    localStorage.removeItem("defaultLogLevel")
                }
                this.$toast().saved();
            },
            onLang(value) {
                localStorage.setItem("lang", value);
                this.$moment.locale(value);
                this.$i18n.locale = value;
                this.lang = value;
                this.$toast().saved();
            },
            onTheme(value) {
                Utils.switchTheme(value)
                this.theme = value;
                this.$toast().saved();
            },
            onEditorTheme(value) {
                localStorage.setItem("editorTheme", value);
                this.editorTheme = value;
                this.$toast().saved();
            },
            onAutofoldTextEditor(value) {
                localStorage.setItem("autofoldTextEditor", value);
                this.autofoldTextEditor = value;
                this.$toast().saved();
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
                this.$toast().saved();
            }
        },
        computed: {
            ...mapGetters("core", ["guidedProperties"]),
            ...mapState("auth", ["user"]),
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
        }
    };
</script>
