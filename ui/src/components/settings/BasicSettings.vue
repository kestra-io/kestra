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
                <LogLevelSelector fit :value="settings.defaultLogLevel" @update:model-value="onLogLevel" />
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

            <SettingRow
                :label="$t('settings.blocks.configuration.fields.playground')"
                :description="$t('settings.blocks.configuration.descriptions.playground')"
            >
                <KsSwitch
                    :aria-label="$t('settings.blocks.configuration.fields.playground')"
                    :modelValue="settings.editorPlayground"
                    @change="onEditorPlayground"
                />
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

            <SettingRow
                :label="$t('settings.blocks.theme.fields.environment_name')"
                :description="$t('settings.blocks.theme.descriptions.environment_name')"
            >
                <KsTooltip
                    v-if="isEnvNameFromConfig"
                    :content="$t('settings.blocks.theme.fields.environment_name_tooltip')"
                    placement="top"
                >
                    <KsInput
                        :modelValue="settings.envName"
                        @change="onEnvName"
                        :placeholder="$t('name')"
                        clearable
                    />
                </KsTooltip>
                <KsInput
                    v-else
                    :modelValue="settings.envName"
                    @change="onEnvName"
                    :placeholder="$t('name')"
                    clearable
                />
            </SettingRow>

            <SettingRow
                :label="$t('settings.blocks.theme.fields.environment_color')"
                :description="$t('settings.blocks.theme.descriptions.environment_color')"
            >
                <KsColorPicker
                    :modelValue="settings.envColor"
                    @change="onEnvColor"
                    showAlpha
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
                        :label="formatDate(item.value)"
                        :value="item.value"
                    />
                </KsSelect>
            </SettingRow>
        </Block>
    </Wrapper>
</template>

<script setup lang="ts">
    import {computed, reactive, watch, onMounted, onBeforeUnmount} from "vue"
    import {useI18n} from "vue-i18n"
    import moment from "moment-timezone"

    import useRouteContext from "../../composables/useRouteContext"
    import {useToast} from "../../utils/toast"
    import {date as dateFilter} from "../../utils/filters"
    import * as Utils from "../../utils/utils"
    import type {SelectedTheme} from "../../utils/utils"
    import {logDisplayTypes, storageKeys, executeFlowBehaviours} from "../../utils/constants"
    import {defaultNamespace} from "../../composables/useNamespaces"
    import {useMiscStore} from "override/stores/misc"
    import {useLayoutStore} from "../../stores/layout"

    import TopNavBar from "../../components/layout/TopNavBar.vue"
    import NamespaceSelect from "../../components/namespaces/components/NamespaceSelect.vue"
    import LogLevelSelector from "../../components/logs/LogLevelSelector.vue"
    import Wrapper from "./components/Wrapper.vue"
    import Block from "./components/block/Block.vue"
    import SettingRow from "./components/block/SettingRow.vue"
    import ThemePicker, {type ThemeOption} from "./components/block/ThemePicker.vue"

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
        editorPlayground: [`${CONFIG}.fields.playground`, `${CONFIG}.descriptions.playground`],
        logsFontSize: [`${THEME}.fields.logs_font_size`, `${THEME}.descriptions.logs_font_size`],
        editorFontFamily: [`${THEME}.fields.editor_font_family`, `${THEME}.descriptions.editor_font_family`],
        editorFontSize: [`${THEME}.fields.editor_font_size`, `${THEME}.descriptions.editor_font_size`],
        autofoldTextEditor: [`${THEME}.fields.editor_folding_stratgy`, `${THEME}.descriptions.editor_folding_stratgy`],
        hoverTextEditor: [`${THEME}.fields.editor_hover_description`, `${THEME}.descriptions.editor_hover_description`],
        [storageKeys.TIMEZONE_STORAGE_KEY]: [`${LOCALE}.fields.time_zone`, `${LOCALE}.descriptions.time_zone`],
        [storageKeys.DATE_FORMAT_STORAGE_KEY]: [`${LOCALE}.fields.date_format`, `${LOCALE}.descriptions.date_format`],
    }

    withDefaults(defineProps<{allowDefaultNamespace?: boolean}>(), {allowDefaultNamespace: true})

    const {t} = useI18n()
    const toast = useToast()
    const miscStore = useMiscStore()
    const layoutStore = useLayoutStore()

    const routeInfo = computed(() => ({title: t("settings.label")}))
    useRouteContext(routeInfo)

    const settings = reactive({
        defaultNamespace: defaultNamespace(),
        defaultLogLevel: localStorage.getItem("defaultLogLevel") || "INFO",
        logDisplay: localStorage.getItem("logDisplay") || logDisplayTypes.DEFAULT,
        editorType: localStorage.getItem(storageKeys.EDITOR_VIEW_TYPE) || "YAML",
        executeFlowBehaviour: localStorage.getItem(storageKeys.EXECUTE_FLOW_BEHAVIOUR) || executeFlowBehaviours.SAME_TAB,
        executeDefaultTab: localStorage.getItem("executeDefaultTab") || "gantt",
        flowDefaultTab: localStorage.getItem("flowDefaultTab") || "overview",
        autoRefreshInterval: parseInt(localStorage.getItem(storageKeys.AUTO_REFRESH_INTERVAL) ?? "") || 10,
        theme: Utils.getSelectedTheme(),
        logsFontSize: parseInt(localStorage.getItem("logsFontSize") ?? "") || 12,
        editorFontFamily: localStorage.getItem("editorFontFamily") || "'JetBrains Mono', monospace",
        editorFontSize: parseInt(localStorage.getItem("editorFontSize") ?? "") || 12,
        autofoldTextEditor: localStorage.getItem("autofoldTextEditor") === "true",
        hoverTextEditor: localStorage.getItem("hoverTextEditor") === "true",
        lang: Utils.getLang(),
        timezone: localStorage.getItem(storageKeys.TIMEZONE_STORAGE_KEY) || moment.tz.guess(),
        dateFormat: localStorage.getItem(storageKeys.DATE_FORMAT_STORAGE_KEY) || "llll",
        editorPlayground: localStorage.getItem("editorPlayground") !== "false",
        envName: layoutStore.envName || miscStore.configs?.environment?.name,
        envColor: layoutStore.envColor || miscStore.configs?.environment?.color,
    })

    const isEnvNameFromConfig = computed(() =>
        !layoutStore.envName && !!miscStore.configs?.environment?.name,
    )

    const zonesWithOffset = moment.tz.names().map((zone) => {
        const timezoneMoment = moment.tz(zone)
        return {
            zone,
            offset: timezoneMoment.utcOffset(),
            formattedOffset: timezoneMoment.format("Z"),
        }
    }).sort((a, b) => a.offset - b.offset)

    const now = moment()
    const localeKey = moment.locale()

    const formatDate = (format: string) => dateFilter(now.toISOString(), format)

    const editorTypeOptions = computed(() => [
        {label: t("no_code.labels.yaml"), value: "YAML"},
        {label: t("no_code.labels.no_code"), value: "NO_CODE"},
    ])

    const executeFlowOptions = computed(() => Object.values(executeFlowBehaviours).map((item) => ({
        value: item,
        label: t(`open in ${item}`),
    })))

    const logDisplayOptions = computed(() => [
        {value: logDisplayTypes.ERROR, text: t("expand error")},
        {value: logDisplayTypes.ALL, text: t("expand all")},
        {value: logDisplayTypes.HIDDEN, text: t("collapse all")},
    ])

    const fontSizeOptions = computed(() => FONT_SIZES.map((size) => ({value: size, label: `${size}px`})))

    const autoRefreshOptions = computed(() => AUTO_REFRESH_INTERVALS.map((seconds) => ({value: seconds, label: `${seconds}`})))

    const fontFamilyOptions = computed(() => [
        {value: "'JetBrains Mono', monospace", text: "JetBrains Mono"},
        {value: "'Source Code Pro', monospace", text: "Source Code Pro"},
        {value: "'Courier New', monospace", text: "Courier"},
        {value: "'Times New Roman', serif", text: "Times New Roman"},
        {value: "'Book Antiqua', serif", text: "Book Antiqua"},
        {value: "'Times New Roman Arabic', serif", text: "Times New Roman Arabic"},
        {value: "'SimSun', sans-serif", text: "SimSun"},
    ])

    const langOptions = computed(() => [
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
    ])

    const themeOptions = computed<ThemeOption[]>(() => [
        {value: "dark-2", label: t("settings.blocks.theme.color_modes.dark_2"), preview: "dark-2"},
        {value: "dark", label: t("settings.blocks.theme.color_modes.dark_1"), preview: "dark"},
        {value: "light", label: t("settings.blocks.theme.color_modes.light"), preview: "light"},
        {value: "syncWithSystem", label: t("settings.blocks.theme.color_modes.sync"), preview: "sync"},
    ])

    const executeDefaultTabOptions = computed(() => [
        {value: "overview", label: t("overview")},
        {value: "gantt", label: t("gantt")},
        {value: "logs", label: t("logs")},
        {value: "topology", label: t("topology")},
        {value: "outputs", label: t("outputs")},
        {value: "metrics", label: t("metrics")},
    ])

    const flowDefaultTabOptions = computed(() => [
        {value: "overview", label: t("overview")},
        {value: "topology", label: t("topology")},
        {value: "executions", label: t("executions")},
        {value: "edit", label: t("edit")},
        {value: "revisions", label: t("revisions")},
        {value: "triggers", label: t("triggers")},
        {value: "logs", label: t("logs")},
        {value: "metrics", label: t("metrics")},
        {value: "dependencies", label: t("dependencies")},
        {value: "concurrency", label: t("concurrency")},
        {value: "auditlogs", label: t("auditlogs")},
    ])

    const dateFormats = [
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

    function persist(key: string, value: string | number | boolean | null | undefined) {
        if (value === undefined || value === null || value === "") {
            localStorage.removeItem(key)
        } else {
            localStorage.setItem(key, String(value))
        }
        const meta = SETTING_TOASTS[key as keyof typeof SETTING_TOASTS]
        notifySaved(meta?.[0], meta?.[1])
    }

    function notifySaved(labelKey?: string, descriptionKey?: string) {
        const title = labelKey
            ? t("settings.updated", {name: t(labelKey)})
            : t("saved")
        const body = descriptionKey ? t(descriptionKey) : t("settings.label")
        toast.success(body, title)
    }

    function clearNamespaceFilters() {
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
    }

    function onNamespace(value: string | string[] | undefined) {
        const namespace = (Array.isArray(value) ? value[0] : value) ?? ""
        const previous = localStorage.getItem("defaultNamespace") || ""
        settings.defaultNamespace = namespace
        persist("defaultNamespace", namespace)

        if (previous !== namespace) {
            clearNamespaceFilters()
        }
    }

    function onLogLevel(value: string) {
        settings.defaultLogLevel = value
        persist("defaultLogLevel", value)
    }

    function onLogDisplay(value: string) {
        settings.logDisplay = value
        persist("logDisplay", value)
    }

    function onEditorType(value: string) {
        settings.editorType = value
        persist(storageKeys.EDITOR_VIEW_TYPE, value)
    }

    function onExecuteFlowBehaviour(value: string) {
        settings.executeFlowBehaviour = value
        persist(storageKeys.EXECUTE_FLOW_BEHAVIOUR, value)
    }

    function onExecuteDefaultTab(value: string) {
        settings.executeDefaultTab = value
        persist("executeDefaultTab", value)
    }

    function onFlowDefaultTab(value: string) {
        settings.flowDefaultTab = value
        persist("flowDefaultTab", value)
    }

    function onAutoRefreshInterval(value: number) {
        settings.autoRefreshInterval = value
        persist(storageKeys.AUTO_REFRESH_INTERVAL, value)
    }

    function onTheme(value: string) {
        settings.theme = value as SelectedTheme
        Utils.switchTheme(miscStore, value)
        notifySaved(`${THEME}.fields.color_mode`, `${THEME}.descriptions.color_mode`)
    }

    function onLogsFontSize(value: number) {
        settings.logsFontSize = value
        persist("logsFontSize", value)
    }

    function onFontFamily(value: string) {
        settings.editorFontFamily = value
        persist("editorFontFamily", value)
    }

    function onFontSize(value: number) {
        settings.editorFontSize = value
        persist("editorFontSize", value)
    }

    function onAutofold(value: boolean | string | number) {
        settings.autofoldTextEditor = Boolean(value)
        persist("autofoldTextEditor", settings.autofoldTextEditor)
    }

    function onHover(value: boolean | string | number) {
        settings.hoverTextEditor = Boolean(value)
        persist("hoverTextEditor", settings.hoverTextEditor)
    }

    function onEditorPlayground(value: boolean | string | number) {
        settings.editorPlayground = Boolean(value)
        persist("editorPlayground", settings.editorPlayground)
    }

    function onEnvName(value: string | number) {
        settings.envName = String(value)
        layoutStore.setEnvName(settings.envName)
        notifySaved(`${THEME}.fields.environment_name`)
    }

    function onEnvColor(value: string | null) {
        settings.envColor = value ?? undefined
        layoutStore.setEnvColor(settings.envColor)
        notifySaved(`${THEME}.fields.environment_color`)
    }

    function onLang(value: string) {
        const previous = settings.lang
        settings.lang = value
        persist("lang", value)

        if (value !== previous) {
            document.location.assign(document.location.href)
        }
    }

    function onTimezone(value: string) {
        settings.timezone = value
        persist(storageKeys.TIMEZONE_STORAGE_KEY, value)
    }

    function onDateFormat(value: string) {
        settings.dateFormat = value
        persist(storageKeys.DATE_FORMAT_STORAGE_KEY, value)
    }

    function updateThemeBasedOnSystem() {
        if (settings.theme === "syncWithSystem") {
            Utils.switchTheme(miscStore, "syncWithSystem")
        }
    }

    let mediaQuery: MediaQueryList | undefined

    onMounted(() => {
        mediaQuery = window.matchMedia("(prefers-color-scheme: dark)")
        mediaQuery.addEventListener("change", updateThemeBasedOnSystem)
    })

    onBeforeUnmount(() => {
        mediaQuery?.removeEventListener("change", updateThemeBasedOnSystem)
    })

    watch(() => miscStore.theme, () => {
        settings.theme = Utils.getSelectedTheme()
    }, {immediate: true})
</script>
