<template>
    <doc-id-display />
    <el-config-provider>
        <error-toast v-if="coreStore.message" :no-auto-hide="true" :message="coreStore.message" />
        <component :is="$route.meta.layout ?? DefaultLayout" v-if="loaded && shouldRenderApp">
            <router-view />
        </component>
        <VueTour />
    </el-config-provider>
</template>

<script>
    import ErrorToast from "./components/ErrorToast.vue";
    import {mapGetters, mapState} from "vuex";
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
            ...mapState("auth", ["user"]),
            ...mapState("flow", ["overallTotal"]),
            ...mapGetters("misc", ["configs"]),
            ...mapStores(useApiStore, usePluginsStore, useLayoutStore, useCoreStore, useDocStore),
            envName() {
                return this.layoutStore.envName || this.configs?.environment?.name;
            },
            isOSS(){
                return true;
            },
            shouldRenderApp() {
                return !this.configs || this.isSetupRoute() || this.configs.isBasicAuthEnabled || localStorage.getItem("basicAuthSetupCompleted") === "true";
            }
        },
        async created() {
            const {name: currentRoute} = this.$route;
            const isAuthRoute = currentRoute === "login" || currentRoute === "setup";
            const hasCredentials = localStorage.getItem("basicAuthCredentials") !== null;

            this.setTitleEnvSuffix();

            if (!this.created && !isAuthRoute) {
                try {
                    const config = await this.loadGeneralResources();
                    if (config === null) {
                        this.displayApp();
                        return;
                    }

                    // Basic auth enabled: redirect to login if no credentials
                    if (this.configs && this.configs.isBasicAuthEnabled && !hasCredentials) {
                        this.$router.push({name: "login"});
                        this.displayApp();
                        return;
                    }

                    // Basic auth disabled: redirect to setup if incomplete
                    if (this.configs && !this.configs.isBasicAuthEnabled && !this.isSetupRoute() && localStorage.getItem("basicAuthSetupCompleted") !== "true") {
                        this.$router.push({name: "setup"});
                        this.displayApp();
                        return;
                    }
                } catch (error) {
                    if (error?.response?.status === 401) {
                        localStorage.removeItem("basicAuthCredentials");
                        this.$router.push({name: "login"});
                        this.displayApp();
                        return;
                    }
                }
            } else if (!isAuthRoute && !hasCredentials) {
                // Fallback: redirect to login when configs unavailable
                this.$router.push({name: "login"});
                this.displayApp();
                return;
            }

            this.displayApp();
        },
        methods: {
            displayApp() {
                Utils.switchTheme(this.$store);

                document.getElementById("loader-wrapper").style.display = "none";
                document.getElementById("app-container").style.display = "block";
                this.loaded = true;
            },
            setTitleEnvSuffix() {
                const envSuffix = this.envName ? ` - ${this.envName}` : "";

                document.title = document.title.replace(/( - .+)?$/, envSuffix);
            },
            async loadGeneralResources() {
                const uid = localStorage.getItem("uid") || (() => {
                    const newUid = Utils.uid();
                    localStorage.setItem("uid", newUid);
                    return newUid;
                })();
                
                if (!localStorage.getItem("basicAuthCredentials")) {
                    return null;
                }
                
                this.pluginsStore.fetchIcons()
                const config = await this.$store.dispatch("misc/loadConfigs");

                await this.docStore.initResourceUrlTemplate(config.version);

                this.apiStore.loadFeeds({
                    version: config.version,
                    iid: config.uuid,
                    uid: uid,
                });

                this.apiStore.loadConfig()
                    .then(apiConfig => {
                        this.initStats(apiConfig, config, uid);
                    })
                
                return config;
            },
            initStats(apiConfig, config, uid) {
                if (!this.configs || this.configs["isAnonymousUsageEnabled"] === false) {
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


                // close survey on page change
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
            isLoginRoute() {
                return this.$route?.name?.startsWith("login");
            },
            isSetupRoute() {
                return this.$route?.name === "setup";
            },
        },
        watch: {
            $route: {
                async handler(route) {
                    if(route.name === "home" && this.isOSS) {
                        await this.$store.dispatch("flow/findFlows", {size: 10, sort: "id:asc"})
                        await this.$store.dispatch("execution/findExecutions", {size: 10}).then(response => {
                            this.executions = response?.total ?? 0;
                        })

                        if (!this.executions && !this.overallTotal) {
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
