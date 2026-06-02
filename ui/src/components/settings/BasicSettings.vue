<template>
    <TopNavBar :title="routeInfo.title">
        <template #actions>
            <KsButton @click="saveAllSettings()" type="primary" :disabled="!hasUnsavedChanges">
                {{ $t("settings.blocks.save.label") }}
            </KsButton>
        </template>
    </TopNavBar>

    <Wrapper>
        <Block :heading="$t('settings.blocks.configuration.label')">
            <template #actions>
                <KsTooltip
                    :content="$t('settings.blocks.reset_section_to_defaults')"
                    placement="top"
                >
                    <KsButton
                        v-if="!hasDefaultMainConfig"
                        :icon="Reload"
                        circle
                        @click="restoreDefaultConfigurations"
                    />
                </KsTooltip>
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
                        <KsSelect :modelValue="pendingSettings.logDisplay" @update:model-value="onLogDisplayChange">
                            <KsOption
                                v-for="item in logDisplayOptions"
                                :key="item.value"
                                :label="item.text"
                                :value="item.value"
                            />
                        </KsSelect>
                    </Column>

                    <Column :label="$t('settings.blocks.configuration.fields.editor_type')">
                        <KsSelect :modelValue="pendingSettings.editorType" @update:model-value="onEditorTypeChange">
                            <KsOption
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
                        </KsSelect>
                    </Column>

                    <Column :label="$t('settings.blocks.configuration.fields.execute_flow')">
                        <KsSelect :modelValue="pendingSettings.executeFlowBehaviour" @update:model-value="onExecuteFlowBehaviourChange">
                            <KsOption
                                v-for="item in Object.values(executeFlowBehaviours)"
                                :key="item"
                                :label="$t(`open in ${item}`)"
                                :value="item"
                            />
                        </KsSelect>
                    </Column>

                    <Column :label="$t('settings.blocks.configuration.fields.execute_default_tab')">
                        <KsSelect :modelValue="pendingSettings.executeDefaultTab" @update:model-value="onExecuteDefaultTabChange">
                            <KsOption
                                v-for="item in executeDefaultTabOptions"
                                :key="item.value"
                                :label="item.label"
                                :value="item.value"
                            />
                        </KsSelect>
                    </Column>

                    <Column :label="$t('settings.blocks.configuration.fields.flow_default_tab')">
                        <KsSelect :modelValue="pendingSettings.flowDefaultTab" @update:model-value="onFlowDefaultTabChange">
                            <KsOption
                                v-for="item in flowDefaultTabOptions"
                                :key="item.value"
                                :label="item.label"
                                :value="item.value"
                            />
                        </KsSelect>
                    </Column>
                    <Column :label="$t('settings.blocks.configuration.fields.playground')">
                        <KsSwitch :modelValue="pendingSettings.editorPlayground" @update:model-value="onEditorPlaygroundChange" />
                    </Column>
                </Row>
                <Row>
                    <Column :label="$t('settings.blocks.configuration.fields.auto_refresh_interval')">
                        <KsInputNumber
                            :modelValue="pendingSettings.autoRefreshInterval"
                            @update:model-value="onAutoRefreshInterval"
                            controlsPosition="right"
                            :min="2"
                            :max="120"
                        >
                            <template #suffix>
                                <small class="dimmed">{{ $t('seconds').toLowerCase() }}</small>
                            </template>
                        </KsInputNumber>
                    </Column>
                </Row>
            </template>
        </Block>

        <Block :heading="$t('settings.blocks.theme.label')">
            <template #actions>
                <KsTooltip
                    :content="$t('settings.blocks.reset_section_to_defaults')"
                    placement="top"
                >
                    <KsButton
                        v-if="!hasDefaultPreferences"
                        :icon="Reload"
                        circle
                        @click="restoreDefaultPreferences"
                    />
                </KsTooltip>
            </template>
            <template #content>
                <Row>
                    <Column :label="$t('settings.blocks.theme.fields.theme')">
                        <KsSelect :modelValue="pendingSettings.theme" @update:model-value="onTheme">
                            <KsOption
                                v-for="item in themesOptions"
                                :key="item.value"
                                :label="item.text"
                                :value="item.value"
                            />
                        </KsSelect>
                    </Column>

                    <Column :label="$t('settings.blocks.theme.fields.logs_font_size')">
                        <KsInputNumber
                            :modelValue="pendingSettings.logsFontSize"
                            @update:model-value="onLogsFontSize"
                            controlsPosition="right"
                            :min="1"
                            :max="50"
                        />
                    </Column>

                    <Column :label="$t('settings.blocks.theme.fields.editor_font_family')">
                        <KsSelect :modelValue="pendingSettings.editorFontFamily" @update:model-value="onFontFamily">
                            <KsOption
                                v-for="item in fontFamilyOptions"
                                :key="item.value"
                                :label="item.text"
                                :value="item.value"
                            />
                        </KsSelect>
                    </Column>

                    <Column :label="$t('settings.blocks.theme.fields.editor_font_size')">
                        <KsInputNumber
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
                        <KsSwitch :aria-label="$t('Fold auto')" :modelValue="pendingSettings.autofoldTextEditor" @update:model-value="onAutofoldTextEditor" />
                    </Column>
                    <Column :label="$t('settings.blocks.theme.fields.editor_hover_description')">
                        <KsSwitch :aria-label="$t('Hover description')" :modelValue="pendingSettings.hoverTextEditor" @update:model-value="onHoverTextEditor" />
                    </Column>
                </Row>

                <Row>
                    <Column :label="$t('settings.blocks.theme.fields.environment_name')">
                        <KsTooltip
                            v-if="isEnvNameFromConfig"
                            :content="$t('settings.blocks.theme.fields.environment_name_tooltip')"
                            placement="bottom"
                        >
                            <KsInput
                                v-model="pendingSettings.envName"
                                @change="onEnvNameChange"
                                :placeholder="$t('name')"
                                clearable
                            />
                        </KsTooltip>

                        <KsInput
                            v-else
                            v-model="pendingSettings.envName"
                            @change="onEnvNameChange"
                            :placeholder="$t('name')"
                            clearable
                        />
                    </Column>

                    <Column :label="$t('settings.blocks.theme.fields.environment_color')">
                        <KsColorPicker
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
                <KsTooltip
                    :content="$t('settings.blocks.reset_section_to_defaults')"
                    placement="top"
                >
                    <KsButton
                        v-if="!hasDefaultLocalization"
                        :icon="Reload"
                        circle
                        @click="restoreDefaultLocalization"
                    />
                </KsTooltip>
            </template>
            <template #content>
                <Row>
                    <Column :label="$t('settings.blocks.configuration.fields.language')">
                        <KsSelect :modelValue="pendingSettings.lang" @update:model-value="onLang">
                            <KsOption
                                v-for="item in langOptions"
                                :key="item.value"
                                :label="item.text"
                                :value="item.value"
                            />
                        </KsSelect>
                    </Column>

                    <Column :label="$t('settings.blocks.localization.fields.time_zone')">
                        <KsSelect :modelValue="pendingSettings.timezone" @update:model-value="onTimezone" filterable>
                            <KsOption
                                v-for="item in zonesWithOffset"
                                :key="item.zone"
                                :label="`${item.zone} (UTC${item.offset === 0 ? '' : item.formattedOffset})`"
                                :value="item.zone"
                            />
                        </KsSelect>
                    </Column>

                    <Column :label="$t('settings.blocks.localization.fields.date_format')">
                        <KsSelect :modelValue="pendingSettings.dateFormat" @update:model-value="onDateFormat" :key="localeKey">
                            <KsOption
                                v-for="item in dateFormats"
                                :key="pendingSettings.timezone + item.value"
                                :label="$filters.date(now, item.value)"
                                :value="item.value"
                            />
                        </KsSelect>
                    </Column>
                </Row>
            </template>
        </Block>

        <Block :heading="$t('settings.blocks.export.label')" v-if="canReadFlows" last>
            <template #content>
                <Row>
                    <Column>
                        <KsButton v-if="canReadFlows" :icon="Download" @click="exportFlows()" class="w-100">
                            {{ $t("settings.blocks.export.fields.flows") }}
                        </KsButton>
                    </Column>
                </Row>
            </template>
        </Block>
    </Wrapper>
</template>

<script setup lang="ts">
    import {ref, reactive, computed, watch, onMounted, onBeforeUnmount, getCurrentInstance} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRouter} from "vue-router"
    import Reload from "vue-material-design-icons/Reload.vue"
    import Download from "vue-material-design-icons/Download.vue"
    import TopNavBar from "../../components/layout/TopNavBar.vue"
    import NamespaceSelect from "../../components/namespaces/components/NamespaceSelect.vue"
    import LogLevelSelector from "../../components/logs/LogLevelSelector.vue"
    import * as Utils from "../../utils/utils"
    import {useLayoutStore} from "../../stores/layout"
    import {useMiscStore} from "override/stores/misc"
    import resource from "../../models/resource"
    import action from "../../models/action"
    import {logDisplayTypes, storageKeys, executeFlowBehaviours} from "../../utils/constants"
    import Wrapper from "./components/Wrapper.vue"
    import Block from "./components/block/Block.vue"
    import Row from "./components/block/Row.vue"
    import Column from "./components/block/Column.vue"
    import {useAuthStore} from "override/stores/auth"
    import {useFlowStore} from "../../stores/flow"
    import {defaultNamespace} from "../../composables/useNamespaces"
    import {useToast} from "../../utils/toast"

    withDefaults(defineProps<{
        allowDefaultNamespace?: boolean
    }>(), {
        allowDefaultNamespace: true,
    })

    const {t} = useI18n()
    const router = useRouter()
    const layoutStore = useLayoutStore()
    const miscStore = useMiscStore()
    const authStore = useAuthStore()
    const flowStore = useFlowStore()
    const toast = useToast()

    // FIXME: any - $moment is registered as a global property via Vue plugin
    const instance = getCurrentInstance()
    // FIXME: any - $moment is registered as a global property via Vue plugin
    const $moment = instance?.appContext.config.globalProperties.$moment as any // FIXME: any
    // FIXME: any - $confirm is registered as a global property via Vue plugin
    const $confirm = instance?.appContext.config.globalProperties.$confirm as any // FIXME: any
    // FIXME: any - $filters is registered as a global property via Vue plugin
    const $filters = instance?.appContext.config.globalProperties.$filters as any // FIXME: any

    // routeInfo for TopNavBar (replaces routeContext mixin)
    const routeInfo = computed(() => ({
        title: t("settings.label"),
    }))

    // --- Default configs ---
    const defaultMainConfig = {
        defaultNamespace: undefined as string | undefined,
        defaultLogLevel: "INFO",
        logDisplay: logDisplayTypes.DEFAULT,
        editorType: "YAML",
        executeFlowBehaviour: "same tab",
        executeDefaultTab: "gantt",
        flowDefaultTab: "overview",
        editorPlayground: true,
        autoRefreshInterval: 10,
    }
    const defaultPreferences = {
        theme: "syncWithSystem",
        logsFontSize: 12,
        editorFontFamily: "'JetBrains Mono', monospace",
        editorFontSize: 12,
        autofoldTextEditor: false,
        hoverTextEditor: false,
        envName: undefined as string | undefined,
        envColor: undefined as string | undefined,
    }
    const defaultLocalization = {
        lang: "en",
        timezone: $moment?.tz?.guess() as string,
        dateFormat: "llll",
    }

    const settingsKeyMapping: Record<string, string> = {
        dateFormat: storageKeys.DATE_FORMAT_STORAGE_KEY,
        timezone: storageKeys.TIMEZONE_STORAGE_KEY,
        executeFlowBehaviour: storageKeys.EXECUTE_FLOW_BEHAVIOUR,
    }

    // FIXME: any - pendingSettings has mixed value types
    const pendingSettings = reactive<Record<string, any>>({ // FIXME: any
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
        logsFontSize: undefined,
    })

    const hasUnsavedChanges = ref(false)
    const hasDefaultMainConfig = ref<boolean | undefined>(undefined)
    const hasDefaultPreferences = ref<boolean | undefined>(undefined)
    const hasDefaultLocalization = ref<boolean | undefined>(undefined)
    let originalSettings: Record<string, unknown> = {}

    const zonesWithOffset = computed(() => {
        if (!$moment?.tz?.names) return []
        return $moment.tz.names().map((zone: string) => {
            const timezoneMoment = $moment.tz(zone)
            return {
                zone,
                offset: timezoneMoment.utcOffset(),
                formattedOffset: timezoneMoment.format("Z"),
            }
        }).sort((a: {offset: number}, b: {offset: number}) => a.offset - b.offset)
    })

    const now = $moment ? $moment() : new Date()
    const localeKey = ref($moment?.locale() ?? "en")

    // --- Initialization ---
    pendingSettings.defaultNamespace = defaultNamespace()
    pendingSettings.editorType = localStorage.getItem(storageKeys.EDITOR_VIEW_TYPE) || "YAML"
    pendingSettings.defaultLogLevel = localStorage.getItem("defaultLogLevel") || "INFO"
    pendingSettings.lang = Utils.getLang()
    pendingSettings.theme = Utils.getTheme()
    pendingSettings.dateFormat = localStorage.getItem(storageKeys.DATE_FORMAT_STORAGE_KEY) || "llll"
    pendingSettings.timezone = localStorage.getItem(storageKeys.TIMEZONE_STORAGE_KEY) || ($moment?.tz?.guess() ?? "UTC")
    pendingSettings.autofoldTextEditor = localStorage.getItem("autofoldTextEditor") === "true"
    pendingSettings.hoverTextEditor = localStorage.getItem("hoverTextEditor") === "true"
    pendingSettings.logDisplay = localStorage.getItem("logDisplay") || logDisplayTypes.DEFAULT
    pendingSettings.editorFontSize = parseInt(localStorage.getItem("editorFontSize") ?? "") || 12
    pendingSettings.editorFontFamily = localStorage.getItem("editorFontFamily") || "'JetBrains Mono', monospace"
    pendingSettings.executeFlowBehaviour = localStorage.getItem("executeFlowBehaviour") || "same tab"
    pendingSettings.executeDefaultTab = localStorage.getItem("executeDefaultTab") || "gantt"
    pendingSettings.flowDefaultTab = localStorage.getItem("flowDefaultTab") || "overview"
    pendingSettings.editorPlayground = localStorage.getItem("editorPlayground") !== "false"
    pendingSettings.envName = layoutStore.envName || miscStore.configs?.environment?.name
    pendingSettings.envColor = layoutStore.envColor || miscStore.configs?.environment?.color
    pendingSettings.logsFontSize = parseInt(localStorage.getItem("logsFontSize") ?? "") || 12
    pendingSettings.autoRefreshInterval = parseInt(localStorage.getItem(storageKeys.AUTO_REFRESH_INTERVAL) ?? "") || 10
    originalSettings = JSON.parse(JSON.stringify(pendingSettings))

    checkDefaultStates()

    // --- Computed options ---
    const mappedTheme = computed(() => miscStore.theme)

    watch(mappedTheme, () => {
        pendingSettings.theme = Utils.getTheme()
    }, {immediate: true})

    const langOptions = [
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

    const themesOptions = [
        {value: "light", text: "Light"},
        {value: "dark", text: "Dark"},
        {value: "syncWithSystem", text: "Sync With System"},
    ]

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

    const canReadFlows = computed(() => authStore.user?.isAllowed(resource.FLOW, action.VIEW, undefined))

    const logDisplayOptions = computed(() => [
        {value: logDisplayTypes.ERROR, text: t("expand error")},
        {value: logDisplayTypes.ALL, text: t("expand all")},
        {value: logDisplayTypes.HIDDEN, text: t("collapse all")},
    ])

    const fontFamilyOptions = [
        {value: "'JetBrains Mono', monospace", text: "JetBrains Mono"},
        {value: "'Source Code Pro', monospace", text: "Source Code Pro"},
        {value: "'Courier New', monospace", text: "Courier"},
        {value: "'Times New Roman', serif", text: "Times New Roman"},
        {value: "'Book Antiqua', serif", text: "Book Antiqua"},
        {value: "'Times New Roman Arabic', serif", text: "Times New Roman Arabic"},
        {value: "'SimSun', sans-serif", text: "SimSun"},
    ]

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

    const isEnvNameFromConfig = computed(() =>
        !layoutStore.envName && !!miscStore.configs?.environment?.name,
    )

    // --- Methods ---
    function checkForChanges() {
        hasUnsavedChanges.value = JSON.stringify(pendingSettings) !== JSON.stringify(originalSettings)
        checkDefaultStates()
    }

    function isObjectEqual(obj1: Record<string, unknown>, obj2: Record<string, unknown>, keys: string[]): boolean {
        return keys.every(key => {
            const val1 = obj1[key]
            const val2 = obj2[key]

            if (val1 == null && val2 == null) return true
            if (val1 == null || val2 == null) return false

            return String(val1) === String(val2)
        })
    }

    function checkDefaultStates() {
        hasDefaultMainConfig.value = isObjectEqual(
            pendingSettings,
            defaultMainConfig as unknown as Record<string, unknown>,
            Object.keys(defaultMainConfig),
        )

        hasDefaultPreferences.value = isObjectEqual(
            pendingSettings,
            defaultPreferences as unknown as Record<string, unknown>,
            Object.keys(defaultPreferences),
        )

        hasDefaultLocalization.value = isObjectEqual(
            pendingSettings,
            defaultLocalization as unknown as Record<string, unknown>,
            Object.keys(defaultLocalization),
        )
    }

    async function confirmNavigation(): Promise<boolean> {
        if (!hasUnsavedChanges.value) return true

        try {
            await $confirm(
                t("settings.blocks.save.unsaved_warning"),
                t("settings.blocks.save.unsaved_title"),
                {
                    confirmButtonText: t("settings.blocks.save.label"),
                    cancelButtonText: t("settings.blocks.save.discard"),
                    type: "warning",
                    showClose: false,
                    closeOnClickModal: false,
                    closeOnPressEscape: false,
                },
            )
            await saveAllSettings()
            return true
        } catch {
            Object.assign(pendingSettings, JSON.parse(JSON.stringify(originalSettings)))
            hasUnsavedChanges.value = false
            return true
        }
    }

    function restoreDefaultLocalization() {
        Object.keys(defaultLocalization).forEach(key => {
            pendingSettings[key] = (defaultLocalization as Record<string, unknown>)[key]
        })
        saveAllSettings()
    }

    function restoreDefaultConfigurations() {
        Object.keys(defaultMainConfig).forEach(key => {
            pendingSettings[key] = (defaultMainConfig as Record<string, unknown>)[key]
        })
        saveAllSettings()
    }

    function restoreDefaultPreferences() {
        Object.keys(defaultPreferences).forEach(key => {
            pendingSettings[key] = (defaultPreferences as Record<string, unknown>)[key]
        })
        saveAllSettings()
    }

    function handleBeforeUnload(e: BeforeUnloadEvent) {
        if (hasUnsavedChanges.value) {
            e.preventDefault()
            e.returnValue = ""
        }
    }

    async function handleNavigationClick(e: MouseEvent) {
        const link = (e.target as HTMLElement).closest("a")
        if (!link) return

        if (!window.location.pathname.includes("/settings")) return

        if (hasUnsavedChanges.value) {
            e.preventDefault()
            e.stopPropagation()

            const shouldNavigate = await confirmNavigation()
            if (shouldNavigate) {
                const href = link.getAttribute("href")
                if (link.getAttribute("data-vue-router") === "true") {
                    router.push(href!)
                } else {
                    window.location.href = href!
                }
            }
        }
    }

    function onNamespaceSelect(value: string | string[] | undefined) {
        pendingSettings.defaultNamespace = Array.isArray(value) ? value[0] : value
        checkForChanges()
    }

    function onEditorTypeChange(value: string) {
        pendingSettings.editorType = value
        localStorage.setItem(storageKeys.EDITOR_VIEW_TYPE, value)
        checkForChanges()
    }

    function onLevelChange(value: string) {
        pendingSettings.defaultLogLevel = value
        checkForChanges()
    }

    function onLang(value: string) {
        pendingSettings.lang = value
        checkForChanges()
    }

    function onTheme(value: string) {
        pendingSettings.theme = value
        checkForChanges()
    }

    function onDateFormat(value: string) {
        pendingSettings.dateFormat = value
        checkForChanges()
    }

    function onTimezone(value: string) {
        pendingSettings.timezone = value
        checkForChanges()
    }

    function onAutofoldTextEditor(value: string | number | boolean | undefined) {
        pendingSettings.autofoldTextEditor = value
        checkForChanges()
    }

    function onHoverTextEditor(value: string | number | boolean | undefined) {
        pendingSettings.hoverTextEditor = value
        checkForChanges()
    }

    function exportFlows() {
        return (flowStore as any).findFlows({size: 1, page: 1}) // FIXME: any
            .then((result: {total: number}) => {
                const flowCount = result.total

                return (flowStore as any).exportFlowByQuery({namespace: undefined, id: undefined}) // FIXME: any
                    .then(() => {
                        toast.success(
                            t("flows exported", {
                                count: flowCount,
                            }),
                        )
                    })
            })
    }

    function onLogDisplayChange(value: string) {
        pendingSettings.logDisplay = value
        checkForChanges()
    }

    function onFontSize(value: number | undefined) {
        pendingSettings.editorFontSize = value
        checkForChanges()
    }

    function onFontFamily(value: string) {
        pendingSettings.editorFontFamily = value
        checkForChanges()
    }

    function onEnvNameChange(value: string | number) {
        pendingSettings.envName = String(value)
        checkForChanges()
    }

    function onEnvColorChange(value: string | null) {
        pendingSettings.envColor = value ?? undefined
        checkForChanges()
    }

    function onExecuteFlowBehaviourChange(value: string) {
        pendingSettings.executeFlowBehaviour = value
        checkForChanges()
    }

    function onExecuteDefaultTabChange(value: string) {
        pendingSettings.executeDefaultTab = value
        checkForChanges()
    }

    function onAutoRefreshInterval(value: number | undefined) {
        pendingSettings.autoRefreshInterval = value
        checkForChanges()
    }

    function onFlowDefaultTabChange(value: string) {
        pendingSettings.flowDefaultTab = value
        checkForChanges()
    }

    function onEditorPlaygroundChange(value: string | number | boolean | undefined) {
        pendingSettings.editorPlayground = value
        checkForChanges()
    }

    function onLogsFontSize(value: number | undefined) {
        pendingSettings.logsFontSize = value
        checkForChanges()
    }

    async function saveAllSettings() {
        let refreshWhenSaved = false
        const previousDefaultNamespace = localStorage.getItem("defaultNamespace")
        for (const key in pendingSettings) {
            const storedKey = settingsKeyMapping[key]
            switch (key) {
            case "defaultNamespace":
            case "defaultLogLevel":
                if (pendingSettings[key])
                    localStorage.setItem(key, pendingSettings[key])
                else
                    localStorage.removeItem(key)
                break
            case "envName":
                if (pendingSettings[key] !== miscStore.configs?.environment?.name) {
                    layoutStore.setEnvName(pendingSettings[key])
                }
                break
            case "envColor":
                if (pendingSettings[key] !== miscStore.configs?.environment?.color) {
                    layoutStore.setEnvColor(pendingSettings[key])
                }
                break
            case "theme":
                Utils.switchTheme(miscStore, pendingSettings[key])
                localStorage.setItem(key, Utils.getTheme())
                break
            case "lang":
            {
                if (pendingSettings[key]) {
                    localStorage.setItem(key, pendingSettings[key])
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
                break
            }
            default:
                if (storedKey) {
                    if (pendingSettings[key])
                        localStorage.setItem(storedKey, pendingSettings[key])
                } else {
                    if (pendingSettings[key] !== undefined)
                        localStorage.setItem(key, pendingSettings[key])
                }
            }
        }

        originalSettings = JSON.parse(JSON.stringify(pendingSettings))
        hasUnsavedChanges.value = false
        checkDefaultStates()

        // Clear namespace filters from sessionStorage if default namespace changed/cleared
        if (previousDefaultNamespace !== pendingSettings.defaultNamespace) {
            clearNamespaceFilters()
        }

        if (refreshWhenSaved) {
            document.location.assign(document.location.href)
        }
        toast.saved(t("settings.label"), undefined, {multiple: true})
    }

    function clearNamespaceFilters() {
        Object.keys(sessionStorage)
            .filter(key => key.includes("_restore_url"))
            .forEach(key => {
                const value = sessionStorage.getItem(key)
                if (!value) return

                const filters = JSON.parse(value) as Record<string, unknown>
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

    function updateThemeBasedOnSystem() {
        if (pendingSettings.theme === "syncWithSystem") {
            Utils.switchTheme(miscStore, "syncWithSystem")
        }
    }

    onMounted(() => {
        const mediaQuery = window.matchMedia("(prefers-color-scheme: dark)")
        mediaQuery.addEventListener("change", updateThemeBasedOnSystem)

        window.addEventListener("beforeunload", handleBeforeUnload)
        document.addEventListener("click", handleNavigationClick, true) // Use capture phase
    })

    onBeforeUnmount(() => {
        window.removeEventListener("beforeunload", handleBeforeUnload)
        document.removeEventListener("click", handleNavigationClick, true)
    })
</script>
<style scoped lang="scss">
    .settings-wrapper .kel-input-number {
        max-width: 20vw;

        & .kel-input__suffix {
            color: var(--ks-text-secondary);
        }

    }

    .kel-input__count {
        color: var(--ks-text-primary) !important;

        .kel-input__count-inner {
            background: none !important;
        }
    }
</style>
