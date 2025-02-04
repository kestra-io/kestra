<template>
    <doc-id-display />
    <el-config-provider>
        <error-toast v-if="message" :no-auto-hide="true" :message="message" />
        <component :is="$route.meta.layout ?? DefaultLayout" v-if="loaded">
            <router-view />
        </component>
        <VueTour />
    </el-config-provider>
</template>

<script>
    import {ElMessageBox, ElSwitch} from "element-plus";
    import {h, ref, shallowRef} from "vue";
    import ErrorToast from "./components/ErrorToast.vue";
    import {mapGetters, mapState} from "vuex";
    import Utils from "./utils/utils";
    import VueTour from "./components/onboarding/VueTour.vue";
    import DefaultLayout from "./components/layout/DefaultLayout.vue";
    import DocIdDisplay from "./components/DocIdDisplay.vue";
    import posthog from "posthog-js";
    import "@kestra-io/ui-libs/style.css";

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
            ...mapState("core", ["message"]),
            ...mapState("flow", ["overallTotal"]),
            ...mapGetters("core", ["guidedProperties"]),
            ...mapGetters("misc", ["configs"]),
            envName() {
                return this.$store.getters["layout/envName"] || this.configs?.environment?.name;
            },
            isOSS(){
                return true;
            },
        },
        async created() {
            if (this.created === false) {
                await this.loadGeneralResources();
                this.displayApp();
            }
            this.setTitleEnvSuffix();

            if (this.configs) {
                // save uptime before showing security advice.
                if (localStorage.getItem("security.advice.uptime") === null) {
                    localStorage.setItem("security.advice.uptime", `${new Date().getTime()}`);
                }
                // use local-storage for ease testing
                if (localStorage.getItem("security.advice.expired") === null) {
                    localStorage.setItem("security.advice.expired", "604800000");  // 7 days.
                }

                // only show security advice after expiration
                const uptime = parseInt(localStorage.getItem("security.advice.uptime"));
                const expired = parseInt(localStorage.getItem("security.advice.expired"));
                const isSecurityAdviceShow = (localStorage.getItem("security.advice.show") || "true") === "true";

                const isSecurityAdviceEnable = new Date().getTime() - uptime >= expired
                if (!this.configs.isBasicAuthEnabled
                    && isSecurityAdviceShow
                    && isSecurityAdviceEnable) {
                    const checked = ref(false);
                    ElMessageBox({
                        title: this.$t("security_advice.title"),
                        message: () => {
                            return h("div", null, [
                                h("p", null, this.$t("security_advice.content")),
                                h(ElSwitch, {
                                    modelValue: checked.value,
                                    "onUpdate:modelValue": (val) => {
                                        checked.value = val
                                        localStorage.setItem("security.advice.show", `${!val}`)
                                    },
                                    activeText: this.$t("security_advice.switch_text")
                                }),
                            ])
                        },
                        showCancelButton: true,
                        confirmButtonText: this.$t("security_advice.enable"),
                        cancelButtonText: this.$t("cancel"),
                        center: false,
                        showClose: false,
                    }).then(() => {
                        this.$router.push({path: "admin/stats"});
                    });
                }
            }
        },
        methods: {
            displayApp() {
                Utils.switchTheme();

                document.getElementById("loader-wrapper").style.display = "none";
                document.getElementById("app-container").style.display = "block";
                this.loaded = true;
            },
            setTitleEnvSuffix() {
                const envSuffix = this.envName ? ` - ${this.envName}` : "";

                document.title = document.title.replace(/( - .+)?$/, envSuffix);
            },
            async loadGeneralResources() {
                let uid = localStorage.getItem("uid");
                if (uid === null) {
                    uid = Utils.uid();
                    localStorage.setItem("uid", uid);
                }

                this.$store.dispatch("plugin/icons")
                const config = await this.$store.dispatch("misc/loadConfigs");
                await this.$store.dispatch("doc/initResourceUrlTemplate", config.version);

                this.$store.dispatch("api/loadFeeds", {
                    version: config.version,
                    iid: config.uuid,
                    uid: uid,
                });

                this.$store.dispatch("api/loadConfig")
                    .then(apiConfig => {
                        this.initStats(apiConfig, config, uid);
                    })
            },
            initStats(apiConfig, config, uid) {
                if (!this.configs || this.configs["isAnonymousUsageEnabled"] === false) {
                    return;
                }

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
