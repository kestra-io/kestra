<template>
    <TopNavBar :title="routeInfo.title" />

    <Wrapper>
        <Block :heading="$t('settings.blocks.configuration.label')">
            <SettingRow
                v-if="allowDefaultNamespace"
                :label="$t('settings.blocks.configuration.fields.default_namespace')"
                :description="$t('settings.blocks.configuration.descriptions.default_namespace')"
            >
                <NamespaceSelect fit :value="settings.defaultNamespace" @update:model-value="onNamespace" />
            </SettingRow>

            <SettingRow
                :label="$t('settings.blocks.configuration.fields.log_level')"
                :description="$t('settings.blocks.configuration.descriptions.log_level')"
            >
                <LogLevelSelector fit clearable :value="settings.defaultLogLevel" @update:model-value="onLogLevel" />
            </SettingRow>

            <SettingRow
                :label="$t('settings.blocks.configuration.fields.log_display')"
                :description="$t('settings.blocks.configuration.descriptions.log_display')"
            >
                <KsSelect fit :modelValue="settings.logDisplay" @update:model-value="onLogDisplay">
                    <KsOption v-for="item in logDisplayOptions" :key="item.value" :label="item.text" :value="item.value" />
                </KsSelect>
            </SettingRow>

            <SettingRow
                :label="$t('settings.blocks.configuration.fields.editor_type')"
                :description="$t('settings.blocks.configuration.descriptions.editor_type')"
            >
                <KsSelect fit :modelValue="settings.editorType" @update:model-value="onEditorType">
                    <KsOption v-for="item in editorTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
                </KsSelect>
            </SettingRow>

            <SettingRow
                :label="$t('settings.blocks.configuration.fields.execute_flow')"
                :description="$t('settings.blocks.configuration.descriptions.execute_flow')"
            >
                <KsSelect fit :modelValue="settings.executeFlowBehaviour" @update:model-value="onExecuteFlowBehaviour">
                    <KsOption v-for="item in executeFlowOptions" :key="item.value" :label="item.label" :value="item.value" />
                </KsSelect>
            </SettingRow>

            <SettingRow
                :label="$t('settings.blocks.configuration.fields.execute_default_tab')"
                :description="$t('settings.blocks.configuration.descriptions.execute_default_tab')"
            >
                <KsSelect fit :modelValue="settings.executeDefaultTab" @update:model-value="onExecuteDefaultTab">
                    <KsOption v-for="item in executeDefaultTabOptions" :key="item.value" :label="item.label" :value="item.value" />
                </KsSelect>
            </SettingRow>

            <SettingRow
                :label="$t('settings.blocks.configuration.fields.flow_default_tab')"
                :description="$t('settings.blocks.configuration.descriptions.flow_default_tab')"
            >
                <KsSelect fit :modelValue="settings.flowDefaultTab" @update:model-value="onFlowDefaultTab">
                    <KsOption v-for="item in flowDefaultTabOptions" :key="item.value" :label="item.label" :value="item.value" />
                </KsSelect>
            </SettingRow>

            <SettingRow
                :label="$t('settings.blocks.configuration.fields.auto_refresh_interval')"
                :description="$t('settings.blocks.configuration.descriptions.auto_refresh_interval')"
            >
                <KsSelect fit :modelValue="settings.autoRefreshInterval" @update:model-value="onAutoRefreshInterval">
                    <KsOption v-for="item in autoRefreshOptions" :key="item.value" :label="item.label" :value="item.value" />
                </KsSelect>
            </SettingRow>
        </Block>

        <Block :heading="$t('settings.blocks.theme.label')">
            <SettingRow
                stacked
                :label="$t('settings.blocks.theme.fields.color_mode')"
                :description="$t('settings.blocks.theme.descriptions.color_mode')"
            >
                <ThemePicker :modelValue="settings.theme" :options="themeOptions" @update:model-value="onTheme" />
            </SettingRow>

            <SettingRow
                :label="$t('settings.blocks.theme.fields.logs_font_size')"
                :description="$t('settings.blocks.theme.descriptions.logs_font_size')"
            >
                <KsSelect fit :modelValue="settings.logsFontSize" @update:model-value="onLogsFontSize">
                    <KsOption v-for="item in fontSizeOptions" :key="item.value" :label="item.label" :value="item.value" />
                </KsSelect>
            </SettingRow>

            <SettingRow
                :label="$t('settings.blocks.theme.fields.editor_font_family')"
                :description="$t('settings.blocks.theme.descriptions.editor_font_family')"
            >
                <KsSelect fit :modelValue="settings.editorFontFamily" @update:model-value="onFontFamily">
                    <KsOption v-for="item in fontFamilyOptions" :key="item.value" :label="item.text" :value="item.value" />
                </KsSelect>
            </SettingRow>

            <SettingRow
                :label="$t('settings.blocks.theme.fields.editor_font_size')"
                :description="$t('settings.blocks.theme.descriptions.editor_font_size')"
            >
                <KsSelect fit :modelValue="settings.editorFontSize" @update:model-value="onFontSize">
                    <KsOption v-for="item in fontSizeOptions" :key="item.value" :label="item.label" :value="item.value" />
                </KsSelect>
            </SettingRow>

            <SettingRow
                :label="$t('settings.blocks.theme.fields.editor_folding_stratgy')"
                :description="$t('settings.blocks.theme.descriptions.editor_folding_stratgy')"
            >
                <KsSwitch
                    :aria-label="$t('settings.blocks.theme.fields.editor_folding_stratgy')"
                    :modelValue="settings.autofoldTextEditor"
                    @change="onAutofold"
                />
            </SettingRow>

            <SettingRow
                :label="$t('settings.blocks.theme.fields.editor_hover_description')"
                :description="$t('settings.blocks.theme.descriptions.editor_hover_description')"
            >
                <KsSwitch
                    :aria-label="$t('settings.blocks.theme.fields.editor_hover_description')"
                    :modelValue="settings.hoverTextEditor"
                    @change="onHover"
                />
            </SettingRow>
        </Block>

        <Block :heading="$t('settings.blocks.localization.label')">
            <SettingRow
                :label="$t('settings.blocks.configuration.fields.language')"
                :description="$t('settings.blocks.localization.descriptions.language')"
            >
                <KsSelect fit :modelValue="settings.lang" @update:model-value="onLang">
                    <KsOption v-for="item in langOptions" :key="item.value" :label="item.text" :value="item.value" />
                </KsSelect>
            </SettingRow>

            <SettingRow
                :label="$t('settings.blocks.localization.fields.time_zone')"
                :description="$t('settings.blocks.localization.descriptions.time_zone')"
            >
                <KsSelect fit :modelValue="settings.timezone" @update:model-value="onTimezone" filterable>
                    <KsOption
                        v-for="item in zonesWithOffset"
                        :key="item.zone"
                        :label="`${item.zone} (UTC${item.offset === 0 ? '' : item.formattedOffset})`"
                        :value="item.zone"
                    />
                </KsSelect>
            </SettingRow>

            <SettingRow
                :label="$t('settings.blocks.localization.fields.date_format')"
                :description="$t('settings.blocks.localization.descriptions.date_format')"
            >
                <KsSelect fit :modelValue="settings.dateFormat" @update:model-value="onDateFormat" :key="localeKey">
                    <KsOption
                        v-for="item in dateFormats"
                        :key="settings.timezone + item.value"
                        :label="$filters.date(now, item.value)"
                        :value="item.value"
                    />
                </KsSelect>
            </SettingRow>
        </Block>
    </Wrapper>
</template>

<script lang="ts">
    import RouteContext from "../../mixins/routeContext"
    import TopNavBar from "../../components/layout/TopNavBar.vue"
    import NamespaceSelect from "../../components/namespaces/components/NamespaceSelect.vue"
    import LogLevelSelector from "../../components/logs/LogLevelSelector.vue"
    import * as Utils from "../../utils/utils"
    import {mapStores} from "pinia"
    import {useMiscStore} from "override/stores/misc"
    import {logDisplayTypes, storageKeys, executeFlowBehaviours} from "../../utils/constants"

    import Wrapper from "./components/Wrapper.vue"
    import Block from "./components/block/Block.vue"
    import SettingRow from "./components/block/SettingRow.vue"
    import ThemePicker from "./components/block/ThemePicker.vue"
    import {defaultNamespace} from "../../composables/useNamespaces"

    const FONT_SIZES = [10, 11, 12, 13, 14, 15, 16, 18, 20]
    const AUTO_REFRESH_INTERVALS = [5, 10, 15, 30, 60, 120]

    const CONFIG = "settings.blocks.configuration"
    const THEME = "settings.blocks.theme"
    const LOCALE = "settings.blocks.localization"

    const SETTING_TOASTS = {
        defaultNamespace: [`${CONFIG}.fields.default_namespace`, `${CONFIG}.descriptions.default_namespace`],
        defaultLogLevel: [`${CONFIG}.fields.log_level`, `${CONFIG}.descriptions.log_level`],
        logDisplay: [`${CONFIG}.fields.log_display`, `${CONFIG}.descriptions.log_display`],
        [storageKeys.EDITOR_VIEW_TYPE]: [`${CONFIG}.fields.editor_type`, `${CONFIG}.descriptions.editor_type`],
        [storageKeys.EXECUTE_FLOW_BEHAVIOUR]: [`${CONFIG}.fields.execute_flow`, `${CONFIG}.descriptions.execute_flow`],
        executeDefaultTab: [`${CONFIG}.fields.execute_default_tab`, `${CONFIG}.descriptions.execute_default_tab`],
        flowDefaultTab: [`${CONFIG}.fields.flow_default_tab`, `${CONFIG}.descriptions.flow_default_tab`],
        [storageKeys.AUTO_REFRESH_INTERVAL]: [`${CONFIG}.fields.auto_refresh_interval`, `${CONFIG}.descriptions.auto_refresh_interval`],
        logsFontSize: [`${THEME}.fields.logs_font_size`, `${THEME}.descriptions.logs_font_size`],
        editorFontFamily: [`${THEME}.fields.editor_font_family`, `${THEME}.descriptions.editor_font_family`],
        editorFontSize: [`${THEME}.fields.editor_font_size`, `${THEME}.descriptions.editor_font_size`],
        autofoldTextEditor: [`${THEME}.fields.editor_folding_stratgy`, `${THEME}.descriptions.editor_folding_stratgy`],
        hoverTextEditor: [`${THEME}.fields.editor_hover_description`, `${THEME}.descriptions.editor_hover_description`],
        [storageKeys.TIMEZONE_STORAGE_KEY]: [`${LOCALE}.fields.time_zone`, `${LOCALE}.descriptions.time_zone`],
        [storageKeys.DATE_FORMAT_STORAGE_KEY]: [`${LOCALE}.fields.date_format`, `${LOCALE}.descriptions.date_format`],
    }

    export default {
        mixins: [RouteContext],
        components: {
            TopNavBar,
            Wrapper,
            Block,
            SettingRow,
            ThemePicker,
            NamespaceSelect,
            LogLevelSelector,
        },
        props: {
            allowDefaultNamespace: {
                type: Boolean,
                default: true,
            },
        },
        data() {
            return {
                settings: {
                    defaultNamespace: defaultNamespace(),
                    defaultLogLevel: localStorage.getItem("defaultLogLevel") || "INFO",
                    logDisplay: localStorage.getItem("logDisplay") || logDisplayTypes.DEFAULT,
                    editorType: localStorage.getItem(storageKeys.EDITOR_VIEW_TYPE) || "YAML",
                    executeFlowBehaviour: localStorage.getItem(storageKeys.EXECUTE_FLOW_BEHAVIOUR) || executeFlowBehaviours.SAME_TAB,
                    executeDefaultTab: localStorage.getItem("executeDefaultTab") || "gantt",
                    flowDefaultTab: localStorage.getItem("flowDefaultTab") || "overview",
                    autoRefreshInterval: parseInt(localStorage.getItem(storageKeys.AUTO_REFRESH_INTERVAL)) || 10,
                    theme: Utils.getSelectedTheme(),
                    logsFontSize: parseInt(localStorage.getItem("logsFontSize")) || 12,
                    editorFontFamily: localStorage.getItem("editorFontFamily") || "'JetBrains Mono', monospace",
                    editorFontSize: parseInt(localStorage.getItem("editorFontSize")) || 12,
                    autofoldTextEditor: localStorage.getItem("autofoldTextEditor") === "true",
                    hoverTextEditor: localStorage.getItem("hoverTextEditor") === "true",
                    lang: Utils.getLang(),
                    timezone: localStorage.getItem(storageKeys.TIMEZONE_STORAGE_KEY) || this.$moment.tz.guess(),
                    dateFormat: localStorage.getItem(storageKeys.DATE_FORMAT_STORAGE_KEY) || "llll",
                },
                zonesWithOffset: this.$moment.tz.names().map((zone) => {
                    const timezoneMoment = this.$moment.tz(zone)
                    return {
                        zone,
                        offset: timezoneMoment.utcOffset(),
                        formattedOffset: timezoneMoment.format("Z"),
                    }
                }).sort((a, b) => a.offset - b.offset),
                now: this.$moment(),
                localeKey: this.$moment.locale(),
            }
        },
        computed: {
            ...mapStores(useMiscStore),
            mappedTheme() {
                return this.miscStore.theme
            },
            routeInfo() {
                return {
                    title: this.$t("settings.label"),
                }
            },
            editorTypeOptions() {
                return [
                    {label: this.$t("no_code.labels.yaml"), value: "YAML"},
                    {label: this.$t("no_code.labels.no_code"), value: "NO_CODE"},
                ]
            },
            executeFlowOptions() {
                return Object.values(executeFlowBehaviours).map((item) => ({
                    value: item,
                    label: this.$t(`open in ${item}`),
                }))
            },
            logDisplayOptions() {
                return [
                    {value: logDisplayTypes.ERROR, text: this.$t("expand error")},
                    {value: logDisplayTypes.ALL, text: this.$t("expand all")},
                    {value: logDisplayTypes.HIDDEN, text: this.$t("collapse all")},
                ]
            },
            fontSizeOptions() {
                return FONT_SIZES.map((size) => ({value: size, label: `${size}px`}))
            },
            autoRefreshOptions() {
                return AUTO_REFRESH_INTERVALS.map((seconds) => ({value: seconds, label: `${seconds}`}))
            },
            fontFamilyOptions() {
                return [
                    {value: "'JetBrains Mono', monospace", text: "JetBrains Mono"},
                    {value: "'Source Code Pro', monospace", text: "Source Code Pro"},
                    {value: "'Courier New', monospace", text: "Courier"},
                    {value: "'Times New Roman', serif", text: "Times New Roman"},
                    {value: "'Book Antiqua', serif", text: "Book Antiqua"},
                    {value: "'Times New Roman Arabic', serif", text: "Times New Roman Arabic"},
                    {value: "'SimSun', sans-serif", text: "SimSun"},
                ]
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
                    {value: "hi", text: "Hindi"},
                ]
            },
            themeOptions() {
                return [
                    {value: "dark-2", label: this.$t("settings.blocks.theme.color_modes.dark_2"), preview: "dark-2"},
                    {value: "dark", label: this.$t("settings.blocks.theme.color_modes.dark_1"), preview: "dark"},
                    {value: "light", label: this.$t("settings.blocks.theme.color_modes.light"), preview: "light"},
                    {value: "syncWithSystem", label: this.$t("settings.blocks.theme.color_modes.sync"), preview: "sync"},
                ]
            },
            executeDefaultTabOptions() {
                return [
                    {value: "overview", label: this.$t("overview")},
                    {value: "gantt", label: this.$t("gantt")},
                    {value: "logs", label: this.$t("logs")},
                    {value: "topology", label: this.$t("topology")},
                    {value: "outputs", label: this.$t("outputs")},
                    {value: "metrics", label: this.$t("metrics")},
                ]
            },
            flowDefaultTabOptions() {
                return [
                    {value: "overview", label: this.$t("overview")},
                    {value: "topology", label: this.$t("topology")},
                    {value: "executions", label: this.$t("executions")},
                    {value: "edit", label: this.$t("edit")},
                    {value: "revisions", label: this.$t("revisions")},
                    {value: "triggers", label: this.$t("triggers")},
                    {value: "logs", label: this.$t("logs")},
                    {value: "metrics", label: this.$t("metrics")},
                    {value: "dependencies", label: this.$t("dependencies")},
                    {value: "concurrency", label: this.$t("concurrency")},
                    {value: "auditlogs", label: this.$t("auditlogs")},
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
                    {value: "LLLL"},
                ]
            },
        },
        methods: {
            persist(key, value) {
                if (value === undefined || value === null || value === "") {
                    localStorage.removeItem(key)
                } else {
                    localStorage.setItem(key, String(value))
                }
                const meta = SETTING_TOASTS[key]
                this.notifySaved(meta?.[0], meta?.[1])
            },
            notifySaved(labelKey, descriptionKey) {
                const title = labelKey
                    ? this.$t("settings.updated", {name: this.$t(labelKey)})
                    : this.$t("saved")
                const body = descriptionKey ? this.$t(descriptionKey) : this.$t("settings.label")
                this.$toast().success(body, title)
            },
            onNamespace(value) {
                const previous = localStorage.getItem("defaultNamespace") || ""
                this.settings.defaultNamespace = value
                this.persist("defaultNamespace", value)

                if (previous !== (value || "")) {
                    this.clearNamespaceFilters()
                }
            },
            onLogLevel(value) {
                this.settings.defaultLogLevel = value
                this.persist("defaultLogLevel", value)
            },
            onLogDisplay(value) {
                this.settings.logDisplay = value
                this.persist("logDisplay", value)
            },
            onEditorType(value) {
                this.settings.editorType = value
                this.persist(storageKeys.EDITOR_VIEW_TYPE, value)
            },
            onExecuteFlowBehaviour(value) {
                this.settings.executeFlowBehaviour = value
                this.persist(storageKeys.EXECUTE_FLOW_BEHAVIOUR, value)
            },
            onExecuteDefaultTab(value) {
                this.settings.executeDefaultTab = value
                this.persist("executeDefaultTab", value)
            },
            onFlowDefaultTab(value) {
                this.settings.flowDefaultTab = value
                this.persist("flowDefaultTab", value)
            },
            onAutoRefreshInterval(value) {
                this.settings.autoRefreshInterval = value
                this.persist(storageKeys.AUTO_REFRESH_INTERVAL, value)
            },
            onTheme(value) {
                this.settings.theme = value
                Utils.switchTheme(this.miscStore, value)
                this.notifySaved(`${THEME}.fields.color_mode`, `${THEME}.descriptions.color_mode`)
            },
            onLogsFontSize(value) {
                this.settings.logsFontSize = value
                this.persist("logsFontSize", value)
            },
            onFontFamily(value) {
                this.settings.editorFontFamily = value
                this.persist("editorFontFamily", value)
            },
            onFontSize(value) {
                this.settings.editorFontSize = value
                this.persist("editorFontSize", value)
            },
            onAutofold(value) {
                this.settings.autofoldTextEditor = value
                this.persist("autofoldTextEditor", value)
            },
            onHover(value) {
                this.settings.hoverTextEditor = value
                this.persist("hoverTextEditor", value)
            },
            onLang(value) {
                this.settings.lang = value
                this.persist("lang", value)

                document.location.assign(document.location.href)
            },
            onTimezone(value) {
                this.settings.timezone = value
                this.persist(storageKeys.TIMEZONE_STORAGE_KEY, value)
            },
            onDateFormat(value) {
                this.settings.dateFormat = value
                this.persist(storageKeys.DATE_FORMAT_STORAGE_KEY, value)
            },
            clearNamespaceFilters() {
                Object.keys(sessionStorage)
                    .filter((key) => key.includes("_restore_url"))
                    .forEach((key) => {
                        const value = sessionStorage.getItem(key)
                        if (!value) return

                        const filters = JSON.parse(value)
                        const updated = Object.fromEntries(
                            Object.entries(filters).filter(([k]) => k !== "namespace" && !k.startsWith("filters[namespace]")),
                        )

                        if (Object.keys(updated).length) {
                            sessionStorage.setItem(key, JSON.stringify(updated))
                        } else {
                            sessionStorage.removeItem(key)
                        }
                    })
            },
            updateThemeBasedOnSystem() {
                if (this.settings.theme === "syncWithSystem") {
                    Utils.switchTheme(this.miscStore, "syncWithSystem")
                }
            },
        },
        mounted() {
            this.mediaQuery = window.matchMedia("(prefers-color-scheme: dark)")
            this.mediaQuery.addEventListener("change", this.updateThemeBasedOnSystem)
        },
        beforeUnmount() {
            this.mediaQuery?.removeEventListener("change", this.updateThemeBasedOnSystem)
        },
        watch: {
            mappedTheme: {
                handler() {
                    this.settings.theme = Utils.getSelectedTheme()
                },
                immediate: true,
            },
        },
    }
</script>
