<template>
    <el-config-provider>
        <left-menu @menu-collapse="onMenuCollapse" />
        <error-toast v-if="message" :no-auto-hide="true" :message="message" />
        <main :class="menuCollapsed" v-if="loaded">
            <top-nav-bar :menu-collapsed="menuCollapsed" />
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
    import "./styles/layout/vue-tour-overload.scss"
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
            ...mapState("core", ["message", "error"]),
            ...mapGetters("core", ["guidedProperties"]),
        },
        created() {
            if (this.created === false) {
                this.displayApp()
                this.loadGeneralRessources()
            }
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
            loadGeneralRessources() {
                let uid = localStorage.getItem("uid");
                if (uid === null) {
                    uid = Utils.uid();
                    localStorage.setItem("uid", uid);
                }

                this.$store.dispatch("plugin/icons")
                this.$store.dispatch("misc/loadConfigs")
                    .then(value => {
                        this.$store.dispatch("api/events", {
                            type: "PAGE",
                            page: pageFromRoute(this.$router.currentRoute.value)
                        });

                        this.$store.dispatch("api/loadFeeds", {
                            version: value.version,
                            iid: value.uuid,
                            uid: uid,
                        });
                    })
            }
        }
    };
</script>

<style lang="scss">
    @use "styles/vendor";
    @use "styles/app";
</style>

