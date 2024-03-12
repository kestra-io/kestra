<template>
    <el-config-provider>
        <left-menu v-if="configs" @menu-collapse="onMenuCollapse" />
        <error-toast v-if="message" :no-auto-hide="true" :message="message" />
        <main v-if="loaded">
            <router-view v-if="!error" />
            <template v-else>
                <errors :code="error" />
            </template>
        </main>
        <VueTour />
    </el-config-provider>
</template>

<script setup>
    import Errors from "./components/errors/Errors.vue";
</script>

<script>
    import {ElMessageBox, ElSwitch} from "element-plus";
    import {h, ref} from "vue";
    import LeftMenu from "override/components/LeftMenu.vue";
    import ErrorToast from "./components/ErrorToast.vue";
    import {mapGetters, mapState} from "vuex";
    import Utils from "./utils/utils";
    import VueTour from "./components/onboarding/VueTour.vue";
    import posthog from "posthog-js";

    export default {
        name: "App",
        components: {
            LeftMenu,
            ErrorToast,
            VueTour,
        },
        data() {
            return {
                menuCollapsed: "",
                fullPage: false,
                created: false,
                loaded: false,
            };
        },
        computed: {
            ...mapState("auth", ["user"]),
            ...mapState("core", ["message", "error"]),
            ...mapGetters("core", ["guidedProperties"]),
            ...mapState("flow", ["overallTotal"]),
            ...mapGetters("misc", ["configs"]),
            envName() {
                return this.$store.getters["layout/envName"] || this.configs?.environment?.name;
            },
        },
        async created() {
            if (this.created === false) {
                await this.loadGeneralResources();
                this.displayApp();
                this.initGuidedTour();
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
                        title: "Your data is not protected",
                        message: () => {
                            return h("div", null, [
                                h("p", null, "Enable basic authentication to protect your instance."),
                                h(ElSwitch, {
                                    modelValue: checked.value,
                                    "onUpdate:modelValue": (val) => {
                                        checked.value = val
                                        localStorage.setItem("security.advice.show", `${!val}`)
                                    },
                                    activeText: "Don't show again"
                                }),
                            ])
                        },
                        showCancelButton: true,
                        confirmButtonText: "Enable authentication",
                        cancelButtonText: "Dismiss",
                        center: false,
                        showClose: false,
                    }).then(() => {
                        this.$router.push({path: "admin/stats"});
                    });
                }
            }
        },
        methods: {
            onMenuCollapse(collapse) {
                document.getElementsByTagName("html")[0].classList.add(!collapse ? "menu-not-collapsed" : "menu-collapsed");
                document.getElementsByTagName("html")[0].classList.remove(collapse ? "menu-not-collapsed" : "menu-collapsed");
            }
            ,
            displayApp() {
                this.onMenuCollapse(localStorage.getItem("menuCollapsed") === "true");
                Utils.switchTheme();

                document.getElementById("loader-wrapper").style.display = "none";
                document.getElementById("app-container").style.display = "block";
                this.loaded = true;
            }
            ,
            setTitleEnvSuffix() {
                const envSuffix = this.envName ? ` - ${this.envName}` : "";

                document.title = document.title.replace(/( - .+)?$/, envSuffix);
            }
            ,
            async loadGeneralResources() {
                let uid = localStorage.getItem("uid");
                if (uid === null) {
                    uid = Utils.uid();
                    localStorage.setItem("uid", uid);
                }

                this.$store.dispatch("plugin/icons")
                const config = await this.$store.dispatch("misc/loadConfigs");

                this.$store.dispatch("api/loadFeeds", {
                    version: config.version,
                    iid: config.uuid,
                    uid: uid,
                });

                this.$store.dispatch("api/loadConfig")
                    .then(apiConfig => {
                        this.initStats(apiConfig, config, uid);
                    })
            }
            ,
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
                        autocapture: false,
                    }
                )

                posthog.register_once({
                    from: "APP",
                    iid: config.uuid,
                    uid: uid,
                    app: {
                        version: config.version
                    }
                })

                if (!posthog.get_property("__alias")) {
                    posthog.alias(apiConfig.id);
                }
            }
            ,
            initGuidedTour() {
                this.$store.dispatch("flow/findFlows", {size: 1})
                    .then(flows => {
                        if (flows.total === 0 && this.$route.name === "home") {
                            this.$router.push({
                                name: "welcome",
                                params: {
                                    tenant: this.$route.params.tenant
                                }
                            });
                        }
                    });
            }
            ,
        }
        ,
        watch: {
            $route(to) {
                if (this.user && to.name === "home" && this.overallTotal === 0) {
                    this.$router.push({
                        name: "welcome",
                        params: {
                            tenant: this.$route.params.tenant
                        }
                    });
                }
            }
            ,
            envName() {
                this.setTitleEnvSuffix();
            }
        }
    };
</script>

<style lang="scss">
@use "styles/vendor";
@use "styles/app";
</style>

