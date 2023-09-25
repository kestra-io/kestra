<template>
    <el-config-provider>
        <left-menu v-if="configs" @menu-collapse="onMenuCollapse" />
        <error-toast v-if="message" :no-auto-hide="true" :message="message" />
        <main :class="menuCollapsed" v-if="loaded">
            <top-nav-bar :menu-collapsed="menuCollapsed" v-if="displayNavBar" />
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
    import LeftMenu from "override/components/LeftMenu.vue";
    import TopNavBar from "./components/layout/TopNavBar.vue";
    import ErrorToast from "./components/ErrorToast.vue";
    import {mapGetters, mapState} from "vuex";
    import Utils from "./utils/utils";
    import {pageFromRoute} from "./utils/eventsRouter";
    import VueTour from "./components/onboarding/VueTour.vue";

    export default {
        name: "App",
        components: {
            LeftMenu,
            TopNavBar,
            ErrorToast,
            VueTour
        },
        data() {
            return {
                menuCollapsed: "",
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
            displayNavBar() {
                if (this.$router) {
                    return this.$route.name !== "welcome";
                }

                return true;
            },
            envName() {
                return this.$store.getters["layout/envName"] || this.configs?.environment?.name;
            }
        },
        async created() {
            if (this.created === false) {
                await this.loadGeneralResources();
                this.displayApp();
                this.initGuidedTour();
            }
            this.setTitleEnvSuffix();
        },
        methods: {
            onMenuCollapse(collapse) {
                this.menuCollapsed = collapse ? "menu-collapsed" : "menu-not-collapsed";
            },
            displayApp() {
                this.onMenuCollapse(localStorage.getItem("menuCollapsed") === "true");
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
                this.$store.dispatch("api/events", {
                    type: "PAGE",
                    page: pageFromRoute(this.$router.currentRoute.value)
                });

                this.$store.dispatch("api/loadFeeds", {
                    version: config.version,
                    iid: config.uuid,
                    uid: uid,
                });
            },
            initGuidedTour() {
                this.$store.dispatch("flow/findFlows", {size: 1})
                    .then(flows => {
                        if (flows.total === 0 && this.$route.name === "home") {
                            this.$router.push({name: "welcome"});
                        }
                    });
            },
        },
        watch: {
            $route(to) {
                if (this.user && to.name === "home" && this.overallTotal === 0) {
                    this.$router.push({name: "welcome"});
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
</style>

