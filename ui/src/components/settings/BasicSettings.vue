<template>
    <TopNavBar :title="routeInfo.title">
        <template #additional-right>
            <el-button @click="saveAllSettings()" type="primary" :disabled="!hasUnsavedChanges">
                {{ $t("settings.blocks.save.label") }}
            </el-button>
        </template>
    </TopNavBar>

    <Wrapper>
        <Block :heading="$t('settings.blocks.configuration.label')">
            <template #actions>
                <el-tooltip 
                    :content="$t('settings.blocks.reset_section_to_defaults')" 
                    placement="top"
                >
                    <el-button
                        v-if="!hasDefaultMainConfig"
                        :icon="Reload"
                        circle
                        @click="restoreDefaultConfigurations"
                    />
                </el-tooltip>
            </template>
            <template #content>
                <Row>
                    <Column v-if="allowDefaultNamespace" :label="$t('settings.blocks.configuration.fields.default_namespace')">
                        <NamespaceSelect :value="pendingSettings.defaultNamespace" @update:model-value="onNamespaceSelect" />
                    </Column>

                    <Column :label="$t('settings.blocks.configuration.fields.log_level')">
                        <LogLevelSelector clearable :value="pendingSettings.defaultLogLevel" @update:model-value="onLevelChange" />
                    </Column>

                    <Column :label="$t('settings.blocks.configuration.fields.log_display')">
                        <el-select :modelValue="pendingSettings.logDisplay" @update:model-value="onLogDisplayChange">
                            <el-option
                                v-for="item in logDisplayOptions"
                                :key="item.value"
                                :label="item.text"
                                :value="item.value"
                            />
                        </el-select>
                    </Column>

                    <Column :label="$t('settings.blocks.configuration.fields.editor_type')">
                        <el-select :modelValue="pendingSettings.editorType" @update:model-value="onEditorTypeChange">
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
                        <el-select :modelValue="pendingSettings.executeFlowBehaviour" @update:model-value="onExecuteFlowBehaviourChange">
                            <el-option
                                v-for="item in Object.values(executeFlowBehaviours)"
                                :key="item"
                                :label="$t(`open in ${item}`)"
                                :value="item"
                            />
                        </el-select>
                    </Column>

                    <Column :label="$t('settings.blocks.configuration.fields.execute_default_tab')">
                        <el-select :modelValue="pendingSettings.executeDefaultTab" @update:model-value="onExecuteDefaultTabChange">
                            <el-option
                                v-for="item in executeDefaultTabOptions"
                                :key="item.value"
                                :label="item.label"
                                :value="item.value"
                            />
                        </el-select>
                    </Column>

                    <Column :label="$t('settings.blocks.configuration.fields.flow_default_tab')">
                        <el-select :modelValue="pendingSettings.flowDefaultTab" @update:model-value="onFlowDefaultTabChange">
                            <el-option
                                v-for="item in flowDefaultTabOptions"
                                :key="item.value"
                                :label="item.label"
                                :value="item.value"
                            />
                        </el-select>
                    </Column>
                    <Column :label="$t('settings.blocks.configuration.fields.playground')">
                        <el-switch :modelValue="pendingSettings.editorPlayground" @update:model-value="onEditorPlaygroundChange" />
                    </Column>
                </Row>
                <Row>
                    <Column :label="$t('settings.blocks.configuration.fields.auto_refresh_interval')">
                        <el-input-number
                            :modelValue="pendingSettings.autoRefreshInterval"
                            @update:model-value="onAutoRefreshInterval"
                            controlsPosition="right"
                            :min="2"
                            :max="120"
                        >
                            <template #suffix>
                                <small class="dimmed">{{ $t('seconds').toLowerCase() }}</small>
                            </template>
                        </el-input-number>
                    </Column>
                </Row>
            </template>
        </Block>

        <Block :heading="$t('settings.blocks.theme.label')">
            <template #actions>
                <el-tooltip 
                    :content="$t('settings.blocks.reset_section_to_defaults')" 
                    placement="top"
                >
                    <el-button
                        v-if="!hasDefaultPreferences"
                        :icon="Reload"
                        circle
                        @click="restoreDefaultPreferences"
                    />
                </el-tooltip>
            </template>
            <template #content>
                <Row>
                    <Column :label="$t('settings.blocks.theme.fields.theme')">
                        <el-select :modelValue="pendingSettings.theme" @update:model-value="onTheme">
                            <el-option
                                v-for="item in themesOptions"
                                :key="item.value"
                                :label="item.text"
                                :value="item.value"
                            />
                        </el-select>
                    </Column>

                    <Column :label="$t('settings.blocks.theme.fields.logs_font_size')">
                        <el-input-number
                            :modelValue="pendingSettings.logsFontSize"
                            @update:model-value="onLogsFontSize"
                            controlsPosition="right"
                            :min="1"
                            :max="50"
                        />
                    </Column>

                    <Column :label="$t('settings.blocks.theme.fields.editor_font_family')">
                        <el-select :modelValue="pendingSettings.editorFontFamily" @update:model-value="onFontFamily">
                            <el-option
                                v-for="item in fontFamilyOptions"
                                :key="item.value"
                                :label="item.text"
                                :value="item.value"
                            />
                        </el-select>
                    </Column>

                    <Column :label="$t('settings.blocks.theme.fields.editor_font_size')">
                        <el-input-number
                            :modelValue="pendingSettings.editorFontSize"
                            @update:model-value="onFontSize"
                            controlsPosition="right"
                            :min="1"
                            :max="50"
                        />
                    </Column>
                </Row>

                <Row>
                    <Column :label="$t('settings.blocks.theme.fields.editor_folding_stratgy')">
                        <el-switch :aria-label="$t('Fold auto')" :modelValue="pendingSettings.autofoldTextEditor" @update:model-value="onAutofoldTextEditor" />
                    </Column>
                    <Column :label="$t('settings.blocks.theme.fields.editor_hover_description')">
                        <el-switch :aria-label="$t('Hover description')" :modelValue="pendingSettings.hoverTextEditor" @update:model-value="onHoverTextEditor" />
                    </Column>
                </Row>

                <Row>
                    <Column :label="$t('settings.blocks.theme.fields.environment_name')">
                        <el-tooltip
                            v-if="isEnvNameFromConfig"
                            :content="$t('settings.blocks.theme.fields.environment_name_tooltip')"
                            placement="bottom"
                        >
                            <el-input
                                v-model="pendingSettings.envName"
                                @change="onEnvNameChange"
                                :placeholder="$t('name')"
                                clearable
                            />
                        </el-tooltip>

                        <el-input
                            v-else
                            v-model="pendingSettings.envName"
                            @change="onEnvNameChange"
                            :placeholder="$t('name')"
                            clearable
                        />
                    </Column>

                    <Column :label="$t('settings.blocks.theme.fields.environment_color')">
                        <el-color-picker
                            v-model="pendingSettings.envColor"
                            @change="onEnvColorChange"
                            showAlpha
                        />
                    </Column>
                </Row>
            </template>
        </Block>

        <Block :heading="$t('settings.blocks.localization.label')" :note="$t('settings.blocks.localization.note')">
            <template #actions>
                <el-tooltip 
                    :content="$t('settings.blocks.reset_section_to_defaults')" 
                    placement="top"
                >
                    <el-button
                        v-if="!hasDefaultLocalization"
                        :icon="Reload"
                        circle
                        @click="restoreDefaultLocalization"
                    />
                </el-tooltip>
            </template>
            <template #content>
                <Row>
                    <Column :label="$t('settings.blocks.configuration.fields.language')">
                        <el-select :modelValue="pendingSettings.lang" @update:model-value="onLang">
                            <el-option
                                v-for="item in langOptions"
                                :key="item.value"
                                :label="item.text"
                                :value="item.value"
                            />
                        </el-select>
                    </Column>

                    <Column :label="$t('settings.blocks.localization.fields.time_zone')">
                        <el-select :modelValue="pendingSettings.timezone" @update:model-value="onTimezone" filterable>
                            <el-option
                                v-for="item in zonesWithOffset"
                                :key="item.zone"
                                :label="`${item.zone} (UTC${item.offset === 0 ? '' : item.formattedOffset})`"
                                :value="item.zone"
                            />
                        </el-select>
                    </Column>

                    <Column :label="$t('settings.blocks.localization.fields.date_format')">
                        <el-select :modelValue="pendingSettings.dateFormat" @update:model-value="onDateFormat" :key="localeKey">
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
                        <el-button v-if="canReadTemplates" :icon="Download" @click="exportTemplates()" :hidden="!miscStore?.configs?.isTemplateEnabled" class="w-100">
                            {{ $t("settings.blocks.export.fields.templates") }}
                        </el-button>
                    </Column>
                </Row>
            </template>
        </Block>
    </Wrapper>
</template>

<script setup>
    import Reload from "vue-material-design-icons/Reload.vue";
    import Download from "vue-material-design-icons/Download.vue";
    import {executeFlowBehaviours} from "../../utils/constants";
</script>

<script>
    import RouteContext from "../../mixins/routeContext";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import NamespaceSelect from "../../components/namespaces/components/NamespaceSelect.vue";
    import LogLevelSelector from "../../components/logs/LogLevelSelector.vue";
    import Utils from "../../utils/utils";
    import {mapStores} from "pinia";
    import {useLayoutStore} from "../../stores/layout";
    import {useMiscStore} from "override/stores/misc";
    import {useTemplateStore} from "../../stores/template";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import {logDisplayTypes, storageKeys} from "../../utils/constants";

    import Wrapper from "./components/Wrapper.vue"
    import Block from "./components/block/Block.vue"
    import Row from "./components/block/Row.vue"
    import Column from "./components/block/Column.vue"
    import {useAuthStore} from "override/stores/auth"
    import {useFlowStore} from "../../stores/flow"
    import {defaultNamespace} from "../../composables/useNamespaces";


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
                hasUnsavedChanges: false,
                hasDefaultMainConfig: undefined,
                hasDefaultPreferences: undefined,
                hasDefaultLocalization: undefined,
                defaultMainConfig: {
                    defaultNamespace: undefined,
                    defaultLogLevel: "INFO",
                    logDisplay: logDisplayTypes.DEFAULT,
                    editorType: "YAML",
                    executeFlowBehaviour: "same tab",
                    executeDefaultTab: "gantt",
                    flowDefaultTab: "overview",
                    editorPlayground: true,
                    autoRefreshInterval: 10
                },
                defaultPreferences: {
                    theme: "syncWithSystem",
                    logsFontSize: 12,
                    editorFontFamily: "'Source Code Pro', monospace",
                    editorFontSize: 12,
                    autofoldTextEditor: false,
                    hoverTextEditor: false,
                    envName: undefined,
                    envColor: undefined
                },
                defaultLocalization:{
                    lang: "en",
                    timezone: this.$moment.tz.guess(),
                    dateFormat: "llll"
                },
                originalSettings: {},
                pendingSettings: {
                    defaultNamespace: undefined,
                    defaultLogLevel: undefined,
                    editorType: undefined,
                    lang: undefined,
                    theme: undefined,
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
                    autoRefreshInterval: undefined,
                    flowDefaultTab: undefined,
                    editorPlayground: undefined,
                    logsFontSize: undefined
                },
                settingsKeyMapping: {
                    dateFormat: storageKeys.DATE_FORMAT_STORAGE_KEY,
                    timezone: storageKeys.TIMEZONE_STORAGE_KEY,
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
            this.pendingSettings.defaultNamespace = defaultNamespace();
            this.pendingSettings.editorType = localStorage.getItem(storageKeys.EDITOR_VIEW_TYPE) || "YAML";
            this.pendingSettings.defaultLogLevel = localStorage.getItem("defaultLogLevel") || "INFO";
            this.pendingSettings.lang = Utils.getLang();
            this.pendingSettings.theme = Utils.getTheme();

            this.pendingSettings.dateFormat = localStorage.getItem(storageKeys.DATE_FORMAT_STORAGE_KEY) || "llll";
            this.pendingSettings.timezone = localStorage.getItem(storageKeys.TIMEZONE_STORAGE_KEY) || this.$moment.tz.guess();
            this.pendingSettings.autofoldTextEditor = localStorage.getItem("autofoldTextEditor") === "true";
            this.pendingSettings.hoverTextEditor = localStorage.getItem("hoverTextEditor") === "true";
            this.guidedTour = localStorage.getItem("tourDoneOrSkip") === "true";
            this.pendingSettings.logDisplay = localStorage.getItem("logDisplay") || logDisplayTypes.DEFAULT;
            this.pendingSettings.editorFontSize = parseInt(localStorage.getItem("editorFontSize")) || 12;
            this.pendingSettings.editorFontFamily = localStorage.getItem("editorFontFamily") || "'Source Code Pro', monospace";
            this.pendingSettings.executeFlowBehaviour = localStorage.getItem("executeFlowBehaviour") || "same tab";
            this.pendingSettings.executeDefaultTab = localStorage.getItem("executeDefaultTab") || "gantt";
            this.pendingSettings.flowDefaultTab = localStorage.getItem("flowDefaultTab") || "overview";
            this.pendingSettings.editorPlayground = localStorage.getItem("editorPlayground") === "false" ? false : true;
            this.pendingSettings.envName = this.layoutStore.envName || this.miscStore.configs?.environment?.name;
            this.pendingSettings.envColor = this.layoutStore.envColor || this.miscStore.configs?.environment?.color;
            this.pendingSettings.logsFontSize = parseInt(localStorage.getItem("logsFontSize")) || 12;
            this.pendingSettings.autoRefreshInterval = parseInt(localStorage.getItem(storageKeys.AUTO_REFRESH_INTERVAL)) || 10;
            this.originalSettings = JSON.parse(JSON.stringify(this.pendingSettings));

            this.checkDefaultStates();
        },
        methods: {
            checkForChanges() {
                this.hasUnsavedChanges = JSON.stringify(this.pendingSettings) !== JSON.stringify(this.originalSettings);
                this.checkDefaultStates();
            },
            async confirmNavigation() {
                if (!this.hasUnsavedChanges) return true;

                try {
                    await this.$confirm(
                        this.$t("settings.blocks.save.unsaved_warning"),
                        this.$t("settings.blocks.save.unsaved_title"),
                        {
                            confirmButtonText: this.$t("settings.blocks.save.label"),
                            cancelButtonText: this.$t("settings.blocks.save.discard"),
                            type: "warning",
                            showClose: false,
                            closeOnClickModal: false,
                            closeOnPressEscape: false
                        }
                    );
                    await this.saveAllSettings();
                    return true;
                } catch {
                    this.pendingSettings = JSON.parse(JSON.stringify(this.originalSettings));
                    this.hasUnsavedChanges = false;
                    return true;
                }
            },
            isObjectEqual(obj1, obj2, keys) {
                return keys.every(key => {
                    const val1 = obj1[key];
                    const val2 = obj2[key];

                    if (val1 == null && val2 == null) return true;
                    if (val1 == null || val2 == null) return false;

                    return String(val1) === String(val2);
                });
            },
            checkDefaultStates() {
                this.hasDefaultMainConfig = this.isObjectEqual(
                    this.pendingSettings, 
                    this.defaultMainConfig, 
                    Object.keys(this.defaultMainConfig)
                );
                
                this.hasDefaultPreferences = this.isObjectEqual(
                    this.pendingSettings, 
                    this.defaultPreferences, 
                    Object.keys(this.defaultPreferences)
                );

                this.hasDefaultLocalization=this.isObjectEqual(
                    this.pendingSettings,
                    this.defaultLocalization,
                    Object.keys(this.defaultLocalization)
                );
            },
            restoreDefaultLocalization(){
                Object.keys(this.defaultLocalization).forEach(key => {
                    this.pendingSettings[key] = this.defaultLocalization[key];
                });
                
                this.saveAllSettings();
            },
            restoreDefaultConfigurations(){
                Object.keys(this.defaultMainConfig).forEach(key => {
                    this.pendingSettings[key] = this.defaultMainConfig[key];
                });
                
                this.saveAllSettings();
            },
            restoreDefaultPreferences(){
                Object.keys(this.defaultPreferences).forEach(key => {
                    this.pendingSettings[key] = this.defaultPreferences[key];
                });
                
                this.saveAllSettings();
            },
            handleBeforeUnload(e) {
                if (this.hasUnsavedChanges) {
                    e.preventDefault();
                    e.returnValue = "";
                }
            },
            async handleNavigationClick(e) {
                const link = e.target.closest("a");
                if (!link) return;

                if (!window.location.pathname.includes("/settings")) return;

                if (this.hasUnsavedChanges) {
                    e.preventDefault();
                    e.stopPropagation();

                    const shouldNavigate = await this.confirmNavigation();
                    if (shouldNavigate) {
                        const href = link.getAttribute("href");
                        if (link.getAttribute("data-vue-router") === "true") {
                            this.$router.push(href);
                        } else {
                            window.location.href = href;
                        }
                    }
                }
            },
            onNamespaceSelect(value) {
                this.pendingSettings.defaultNamespace = value;
                this.checkForChanges();
            },
            onEditorTypeChange(value) {
                this.pendingSettings.editorType = value;
                localStorage.setItem(storageKeys.EDITOR_VIEW_TYPE, value);
                this.checkForChanges();
            },
            onLevelChange(value) {
                this.pendingSettings.defaultLogLevel = value;
                this.checkForChanges();
            },
            onLang(value) {
                this.pendingSettings.lang = value;
                this.checkForChanges();
            },
            onTheme(value) {
                this.pendingSettings.theme = value;
                this.checkForChanges();
            },
            onDateFormat(value) {
                this.pendingSettings.dateFormat = value;
                this.checkForChanges();
            },
            onTimezone(value) {
                this.pendingSettings.timezone = value;
                this.checkForChanges();
            },
            onAutofoldTextEditor(value) {
                this.pendingSettings.autofoldTextEditor = value;
                this.checkForChanges();
            },
            onHoverTextEditor(value) {
                this.pendingSettings.hoverTextEditor = value;
                this.checkForChanges();
            },
            exportFlows() {
                return this.flowStore.findFlows({size: 1, page: 1})
                    .then((result) => {
                        const flowCount = result.total;

                        return this.flowStore.exportFlowByQuery({})
                            .then(() => {
                                this.$toast().success(
                                    this.$t("flows exported", {
                                        count: flowCount,
                                    })
                                );
                            });
                    });
            },
            exportTemplates() {
                return this.templateStore
                    .exportTemplateByQuery({})
                    .then(_ => {
                        this.$toast().success(this.$t("templates exported"));
                    })
            },
            onLogDisplayChange(value) {
                this.pendingSettings.logDisplay = value;
                this.checkForChanges();
            },
            onFontSize(value) {
                this.pendingSettings.editorFontSize = value;
                this.checkForChanges();
            },
            onFontFamily(value) {
                this.pendingSettings.editorFontFamily = value;
                this.checkForChanges();
            },
            onEnvNameChange(value) {
                this.pendingSettings.envName = value;
                this.checkForChanges();
            },
            onEnvColorChange(value) {
                this.pendingSettings.envColor = value;
                this.checkForChanges();
            },
            onExecuteFlowBehaviourChange(value) {
                this.pendingSettings.executeFlowBehaviour = value;
                this.checkForChanges();
            },
            onExecuteDefaultTabChange(value){
                this.pendingSettings.executeDefaultTab = value;
                this.checkForChanges();
            },
            onAutoRefreshInterval(value) {
                this.pendingSettings.autoRefreshInterval = value;
                this.checkForChanges();
            },
            onFlowDefaultTabChange(value){
                this.pendingSettings.flowDefaultTab = value;
                this.checkForChanges();
            },
            onEditorPlaygroundChange(value) {
                this.pendingSettings.editorPlayground = value;
                this.checkForChanges();
            },
            onLogsFontSize(value) {
                this.pendingSettings.logsFontSize = value;
                this.checkForChanges();
            },
            async saveAllSettings() {
                let refreshWhenSaved = false
                const previousDefaultNamespace = localStorage.getItem("defaultNamespace")
                for (const key in this.pendingSettings){
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
                        if (this.pendingSettings[key] !== this.miscStore.configs?.environment?.name) {
                            this.layoutStore.setEnvName(this.pendingSettings[key]);
                        }
                        break
                    case "envColor":
                        if (this.pendingSettings[key] !== this.miscStore.configs?.environment?.color) {
                            this.layoutStore.setEnvColor(this.pendingSettings[key]);
                        }
                        break
                    case "theme":
                        Utils.switchTheme(this.miscStore, this.pendingSettings[key]);
                        localStorage.setItem(key, Utils.getTheme())
                        break
                    case "lang":
                    {
                        if(this.pendingSettings[key]) {
                            localStorage.setItem(key, this.pendingSettings[key])
                        }

                        // For language change, we have to load a json file into i18n.
                        // To get the new language applied, we refresh the page fully.
                        // This avoids having to rewrite the language loading here
                        // that we already wrote in `i18n.ts`.

                        // NOTE: We cannot call it here directly as we don't have an
                        // instance of VueI18n available.
                        // NOTE2: We have to wait until all values are saved
                        // before refreshing. If we don't, some values will be saved
                        // but the page will refresh before all is saved.
                        refreshWhenSaved = true
                        break;
                    }
                    default:
                        if (storedKey) {
                            if(this.pendingSettings[key])
                                localStorage.setItem(storedKey, this.pendingSettings[key])
                        }
                        else {
                            if(this.pendingSettings[key] !== undefined)
                                localStorage.setItem(key, this.pendingSettings[key])
                        }
                    }
                }

                this.originalSettings = JSON.parse(JSON.stringify(this.pendingSettings));
                this.hasUnsavedChanges = false;
                this.checkDefaultStates();

                // Clear namespace filters from sessionStorage if default namespace changed/cleared
                if (previousDefaultNamespace !== this.pendingSettings.defaultNamespace) {
                    this.clearNamespaceFilters();
                }

                if(refreshWhenSaved){
                    document.location.assign(document.location.href)
                }
                this.$toast().saved(this.$t("settings.label"), undefined, {multiple: true});
            },
            clearNamespaceFilters() {
                Object.keys(sessionStorage)
                    .filter(key => key.includes("_restore_url"))
                    .forEach(key => {
                        const value = sessionStorage.getItem(key);
                        if (!value) return;

                        const filters = JSON.parse(value);
                        const updated = Object.fromEntries(
                            Object.entries(filters).filter(([k]) => k !== "namespace" && !k.startsWith("filters[namespace]"))
                        );

                        if (Object.keys(updated).length) {
                            sessionStorage.setItem(key, JSON.stringify(updated));
                        } else {
                            sessionStorage.removeItem(key);
                        }
                    });
            },
            updateThemeBasedOnSystem() {
                if (this.theme === "syncWithSystem") {
                    Utils.switchTheme(this.miscStore, "syncWithSystem");
                }
            },
        },
        mounted() {
            const mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
            mediaQuery.addEventListener("change", this.updateThemeBasedOnSystem);

            window.addEventListener("beforeunload", this.handleBeforeUnload);
            document.addEventListener("click", this.handleNavigationClick, true); // Use capture phase
        },
        beforeUnmount() {
            window.removeEventListener("beforeunload", this.handleBeforeUnload);
            document.removeEventListener("click", this.handleNavigationClick, true);
        },
        computed: {
            ...mapStores(useLayoutStore, useMiscStore, useTemplateStore, useAuthStore, useFlowStore),
            mappedTheme() {
                return this.miscStore.theme;
            },
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
                    {value: "pt_BR", text: "Portuguese (Brazil)"},
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
            dateFormats() {
                return [
                    {value: "YYYY-MM-DDTHH:mm:ssZ"},
                    {value: "YYYY-MM-DD hh:mm:ss A"},
                    {value: "DD/MM/YYYY HH:mm:ss"},
                    {value: "MM/DD/YYYY HH:mm:ss"},
                    {value: "YYYY.MM.DD HH:mm:ss"},
                    {value: "DD.MM.YYYY HH:mm:ss"},
                    {value: "YYYY-MM-DD HH:mm:ss.SSS"},
                    {value: "HH:mm:ss DD/MM/YYYY"},
                    {value: "HH:mm:ss MM/DD/YYYY"},
                    {value: "ddd, DD MMM YYYY HH:mm:ss"},
                    {value: "dddd, MMMM Do YYYY, h:mm:ss a"},
                    {value: "lll"},
                    {value: "llll"},
                    {value: "LLL"},
                    {value: "LLLL"}
                ]
            },
            canReadFlows() {
                return this.authStore.user?.isAllowed(permission.FLOW, action.READ);
            },
            canReadTemplates() {
                return this.authStore.user?.isAllowed(permission.TEMPLATE, action.READ);
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
            },
            flowDefaultTabOptions() {
                return [
                    {
                        value : "overview",
                        label: this.$t("overview")
                    },
                    {
                        value : "topology",
                        label: this.$t("topology")
                    },
                    {
                        value : "executions",
                        label: this.$t("executions")
                    },
                    {
                        value : "edit",
                        label: this.$t("edit")
                    },
                    {
                        value : "revisions",
                        label: this.$t("revisions")
                    },
                    {
                        value : "triggers",
                        label: this.$t("triggers")
                    },
                    {
                        value : "logs",
                        label: this.$t("logs")
                    },
                    {
                        value : "metrics",
                        label: this.$t("metrics")
                    },
                    {
                        value : "dependencies",
                        label: this.$t("dependencies")
                    },
                    {
                        value : "concurrency",
                        label: this.$t("concurrency")
                    },
                    {
                        value : "auditlogs",
                        label: this.$t("auditlogs")
                    },
                ]
            },
            isEnvNameFromConfig() {
                return !this.layoutStore.envName && !!this.miscStore.configs?.environment?.name;
            }
        },
        watch: {
            mappedTheme: {
                handler() {
                    this.pendingSettings.theme = Utils.getTheme();
                },
                immediate: true,
            },
        },
    };
</script>
<style lang="scss">
    .settings-wrapper .el-input-number {
        max-width: 20vw;

        & .el-input__suffix {
            color: var(--ks-content-secondary);
        }

    }

    .el-input__count {
        color: var(--ks-content-primary) !important;

        .el-input__count-inner {
            background: none !important;
        }
    }
</style>