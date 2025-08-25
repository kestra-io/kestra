<template>
    <doc-id-display />
    <el-config-provider>
        <error-toast v-if="coreStore.message" :no-auto-hide="true" :message="coreStore.message" />
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
    import posthog from "posthog-js";
    import "@kestra-io/ui-libs/style.css";

    import {useApiStore} from "./stores/api";
    import {usePluginsStore} from "./stores/plugins";
    import {useLayoutStore} from "./stores/layout";
    import {useCoreStore} from "./stores/core";
    import {useDocStore} from "./stores/doc";
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
            isOSS(){
                return true;
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

                const apiConfig = await this.apiStore.loadConfig();
                this.initStats(apiConfig, config, uid);

                return config;
            },
            initStats(apiConfig, config, uid) {
                if (this.miscStore.configs["isAnonymousUsageEnabled"] === false) {
                    return;
                }

                // only run posthog in production
                if (import.meta.env.MODE === "production") {
                    posthog.init(
                        apiConfig.posthog.token,
                        {
                            api_host: apiConfig.posthog.apiHost,
                            ui_host: "https://eu.posthog.com",
                            capture_pageview: false,
                            capture_pageleave: true,
                            autocapture: false,
                        }
                    )

                    posthog.register_once(this.statsGlobalData(config, uid));

                    if (!posthog.get_property("__alias")) {
                        posthog.alias(apiConfig.id);
                    }
                }

                let surveyVisible = false;
                window.addEventListener("PHSurveyShown", () => {
                    surveyVisible = true;
                });

                window.addEventListener("PHSurveyClosed", () => {
                    surveyVisible = false;
                })

                window.addEventListener("KestraRouterAfterEach", () => {
                    if (surveyVisible) {
                        window.dispatchEvent(new Event("PHSurveyClosed"))
                        surveyVisible = false;
                    }
                })
            },
            statsGlobalData(config, uid) {
                return {
                    from: "APP",
                    iid: config.uuid,
                    uid: uid,
                    app: {
                        version: config.version,
                        type: "OSS"
                    }
                }
            },
        },
        watch: {
            $route: {
                async handler(route) {
                    if(route.name === "home" && this.isOSS) {
                        await this.flowStore.findFlows({size: 10, sort: "id:asc"})
                        await this.executionsStore.findExecutions({size: 10}).then(response => {
                            this.executions = response?.total ?? 0;
                        })

                        if (!this.executions && !this.flowStore.overallTotal) {
                            this.$router.push({name: "welcome", params: {tenant: this.$route.params.tenant}});
                        }
                    }
                }
            },
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
