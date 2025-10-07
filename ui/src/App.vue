<template>
    <DocIdDisplay />
    <el-config-provider>
        <ErrorToast v-if="coreStore.message" :noAutoHide="true" :message="coreStore.message" />
        <component :is="$route.meta.layout ?? DefaultLayout" v-if="loaded && shouldRenderApp">
            <router-view />
        </component>
        <VueTour v-if="shouldRenderApp && $route?.name && !isAnonymousRoute" />
    </el-config-provider>
</template>

<script>
    import ErrorToast from "./components/ErrorToast.vue";
    import {mapStores} from "pinia";
    import Utils from "./utils/utils";
    import {shallowRef} from "vue";
    import VueTour from "./components/onboarding/VueTour.vue";
    import DefaultLayout from "override/components/layout/DefaultLayout.vue";
    import DocIdDisplay from "./components/DocIdDisplay.vue";
    import "@kestra-io/ui-libs/style.css";

    import {useApiStore} from "./stores/api";
    import {usePluginsStore} from "./stores/plugins";
    import {useLayoutStore} from "./stores/layout";
    import {useCoreStore} from "./stores/core";
    import {useDocStore} from "./stores/doc";
    import {initPostHogForSetup} from "./composables/usePosthog";
    import {useMiscStore} from "override/stores/misc";
    import {useExecutionsStore} from "./stores/executions";
    import * as BasicAuth from "./utils/basicAuth";
    import {useFlowStore} from "./stores/flow";

    // Main App
    export default {
        name: "App",
        components: {
            ErrorToast,
            VueTour,
            DocIdDisplay
        },
        data() {
            return {
                DefaultLayout: shallowRef(DefaultLayout),
                fullPage: false,
                created: false,
                loaded: false,
                executions: 0,
            };
        },
        computed: {
            ...mapStores(useApiStore, usePluginsStore, useLayoutStore, useCoreStore, useDocStore, useMiscStore, useExecutionsStore, useFlowStore),
            envName() {
                return this.layoutStore.envName || this.miscStore.configs?.environment?.name;
            },
            shouldRenderApp() {
                return this.loaded
            },
            isAnonymousRoute() {
                return (this.isLoginRoute || this.isSetupRoute);
            },
            isLoginRoute() {
                return this.$route?.name?.startsWith("login")
            },
            isSetupRoute() {
                return this.$route?.name === "setup"
            },
        },
        async created() {
            this.setTitleEnvSuffix()

            if (!this.isAnonymousRoute && BasicAuth.isLoggedIn()) {
                try {
                    await this.loadGeneralResources()
                } catch (error) {
                    console.warn("Failed to load general resources:", error)
                }
            }

            this.displayApp();
        },
        methods: {
            displayApp() {
                Utils.switchTheme(this.miscStore);

                document.getElementById("loader-wrapper").style.display = "none";
                document.getElementById("app-container").style.display = "block";
                this.loaded = true;
            },
            setTitleEnvSuffix() {
                const envSuffix = this.envName ? ` - ${this.envName}` : "";

                document.title = document.title.replace(/( - .+)?$/, envSuffix);
            },
            async loadGeneralResources() {
                const config = await this.miscStore.loadConfigs();
                const uid = localStorage.getItem("uid") || (() => {
                    const newUid = Utils.uid();
                    localStorage.setItem("uid", newUid);
                    return newUid;
                })();

                if (!config.isBasicAuthInitialized || !BasicAuth.isLoggedIn()) {
                    return null;
                }

                this.pluginsStore.fetchIcons()

                await this.docStore.initResourceUrlTemplate(config.version);

                this.apiStore.loadFeeds({
                    version: config.version,
                    iid: config.uuid,
                    uid: uid,
                });

                await initPostHogForSetup(config);

                return config;
            },
        },
        watch: {
            envName() {
                this.setTitleEnvSuffix();
            }
        }
    };
</script>

<style lang="scss">
@use "styles/vendor";
@use "styles/app";
#app {
    display: flex;
    height: 100vh;
    overflow: hidden;
}
#app main {
    flex: 1;
    overflow: auto;
}
</style>
