<template>
    <DocIdDisplay />
    <el-config-provider>
        <ErrorToast v-if="coreStore.message" :noAutoHide="true" :message="coreStore.message" />
        <component :is="route.meta.layout ?? DefaultLayout" v-if="loaded && shouldRenderApp">
            <router-view />
        </component>
        <VueTour v-if="shouldRenderApp && route?.name && !route.meta?.anonymous" />
        <UnsavedChangesDialog />
    </el-config-provider>
</template>

<script lang="ts" setup>
    import {ref, computed, watch, onMounted} from "vue";
    import {useRoute} from "vue-router";
    import {useApiStore} from "./stores/api";
    import {useLayoutStore} from "./stores/layout";
    import {useCoreStore} from "./stores/core";
    import {useDocStore} from "./stores/doc";
    import {useMiscStore} from "override/stores/misc";
    import Utils from "./utils/utils";
    import * as BasicAuth from "./utils/basicAuth";
    import {initPostHogForSetup} from "./composables/usePosthog";
    import ErrorToast from "./components/ErrorToast.vue";
    import VueTour from "./components/onboarding/VueTour.vue";
    import DefaultLayout from "override/components/layout/DefaultLayout.vue";
    import DocIdDisplay from "./components/DocIdDisplay.vue";
    import UnsavedChangesDialog from "./components/UnsavedChangesDialog.vue";
    import "./styles/vendor.scss"
    import "@kestra-io/ui-libs/style.css";
    import "./styles/app.scss"
    import {usePluginsStore} from "./stores/plugins";

    const loaded = ref(false);

    const apiStore = useApiStore();
    const layoutStore = useLayoutStore();
    const coreStore = useCoreStore();
    const docStore = useDocStore();
    const miscStore = useMiscStore();

    const route = useRoute();

    const envName = computed(() => layoutStore.envName || miscStore.configs?.environment?.name);

    const shouldRenderApp = computed(() => loaded.value);

    function setTitleEnvSuffix() {
        const envSuffix = envName.value ? ` - ${envName.value}` : "";
        document.title = document.title.replace(/( - .+)?$/, envSuffix);
    }

    const pluginsStore = usePluginsStore();

    async function loadGeneralResources() {
        const config = await miscStore.loadConfigs();
        const uid = localStorage.getItem("uid") || (() => {
            const newUid = Utils.uid();
            localStorage.setItem("uid", newUid);
            return newUid;
        })();

        if (!config.isBasicAuthInitialized || !BasicAuth.isLoggedIn()) {
            return null;
        }

        pluginsStore.fetchIcons();

        await docStore.initResourceUrlTemplate(config.version);

        apiStore.loadFeeds({
            version: config.version,
            iid: config.uuid,
            uid: uid,
        });

        await initPostHogForSetup(config);

        return config;
    }

    function displayApp() {
        Utils.switchTheme(miscStore);

        const loader = document.getElementById("loader-wrapper");
        if (loader) loader.style.display = "none";
        const appContainer = document.getElementById("app-container");
        if (appContainer) appContainer.style.display = "block";
        loaded.value = true;
    }
    watch(() => route?.meta?.anonymous, async (anonymous) => {
        if (!anonymous && BasicAuth.isLoggedIn()) {
            try {
                await loadGeneralResources();
            } catch (error) {
                console.warn("Failed to load general resources:", error);
            }
        }
    }, {immediate: true});

    onMounted(async () => {
        setTitleEnvSuffix();
        displayApp();
    });

    watch(envName, () => {
        setTitleEnvSuffix();
    });
</script>