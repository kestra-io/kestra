<template>
    <DocIdDisplay />
    <ErrorToast v-if="coreStore.message" :noAutoHide="true" :message="coreStore.message" />
    <component :is="SdkDriftBanner" v-if="SdkDriftBanner" />
    <div id="app-shell">
        <AppTopNavBar  v-if="loaded && route?.name && !route.meta?.anonymous"  />
        <div id="app-body">
            <component :is="route.meta.layout ?? DefaultLayout" v-if="loaded">
                <router-view />
            </component>
        </div>
    </div>
    <OnboardingOverlay v-if="loaded && route?.name && !route.meta?.anonymous" />
    <UnsavedChangesDialog />
    <DrillDownDrawer />
    <PwaInstallPrompt v-if="loaded && route?.name && !route.meta?.anonymous" />
</template>

<script lang="ts" setup>
    import "./styles/vendor.scss"
    import "./styles/app.scss"

    import {ref, computed, watch, onMounted, provide, defineAsyncComponent} from "vue"
    import {useRoute} from "vue-router"
    import {useApiStore} from "./stores/api"
    import {useLayoutStore} from "./stores/layout"
    import {useCoreStore} from "./stores/core"
    import {useDocStore} from "./stores/doc"
    import {useMiscStore} from "override/stores/misc"
    import * as Utils from "./utils/utils"
    import * as BasicAuth from "./utils/basicAuth"
    import {applyFontScale, getAppFontSizeMode} from "./utils/appFontSize"
    import {initPosthogIfEnabled} from "./utils/posthog"
    import {SAVED_FILTER_ANALYTICS_INJECTION_KEY, trackSavedFilter} from "./utils/savedFilterTracking"
    import ErrorToast from "./components/ErrorToast.vue"
    import OnboardingOverlay from "./components/onboarding/OnboardingOverlay.vue"
    import DefaultLayout from "override/components/layout/DefaultLayout.vue"
    import AppTopNavBar from "./components/layout/AppTopNavBar.vue"
    import DocIdDisplay from "./components/DocIdDisplay.vue"
    import UnsavedChangesDialog from "./components/UnsavedChangesDialog.vue"
    import DrillDownDrawer from "./components/dashboard/DrillDownDrawer.vue"
    import PwaInstallPrompt from "./components/PwaInstallPrompt.vue"
    import {useThemeCycle} from "./composables/useThemeCycle"
    import {revealApp} from "./utils/loaderReveal"

    // Dev-only, dynamically imported so the component is entirely absent from production bundles:
    // `import.meta.env.DEV` is statically replaced with `false` by Vite in prod builds, so this
    // branch (and the import() it guards) is dead-code eliminated rather than merely hidden by v-if.
    const SdkDriftBanner = import.meta.env.DEV
        ? defineAsyncComponent(() => import("./components/SdkDriftBanner.vue"))
        : null

    const loaded = ref(false)

    const apiStore = useApiStore()
    const layoutStore = useLayoutStore()
    const coreStore = useCoreStore()
    const docStore = useDocStore()

    const miscStore = useMiscStore()
    useThemeCycle(miscStore)

    provide(SAVED_FILTER_ANALYTICS_INJECTION_KEY, trackSavedFilter)

    const route = useRoute()

    const envName = computed(() => layoutStore.envName || miscStore.configs?.environment?.name)

    function setTitleEnvSuffix() {
        const envSuffix = envName.value ? ` - ${envName.value}` : ""
        document.title = document.title.replace(/( - .+)?$/, envSuffix)
    }

    async function loadGeneralResources() {
        const config = await miscStore.loadConfigs()
        const uid = localStorage.getItem("uid") || (() => {
            const newUid = Utils.uid()
            localStorage.setItem("uid", newUid)
            return newUid
        })()

        if (!config.isBasicAuthInitialized || !BasicAuth.isLoggedIn()) {
            return null
        }

        await docStore.initResourceUrlTemplate(config.version)

        apiStore.loadFeeds({
            version: config.version,
            iid: config.uuid,
            uid: uid,
        })

        void initPosthogIfEnabled(config)

        return config
    }

    function displayApp() {
        Utils.switchTheme(miscStore)
        applyFontScale(getAppFontSizeMode())

        revealApp(() => { loaded.value = true })
    }

    watch(() => route?.meta?.anonymous, async (anonymous) => {
        if (!anonymous && BasicAuth.isLoggedIn()) {
            try {
                await loadGeneralResources()
            } catch (error) {
                console.warn("Failed to load general resources:", error)
            }
        }
    }, {immediate: true})

    onMounted(async () => {
        setTitleEnvSuffix()
        displayApp()
    })

    watch(envName, () => {
        setTitleEnvSuffix()
    })
</script>
