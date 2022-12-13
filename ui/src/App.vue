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
    </el-config-provider>
</template>

<script>
    import LeftMenu from "override/components/LeftMenu.vue";
    import TopNavBar from "./components/layout/TopNavBar.vue";
    import ErrorToast from "./components/ErrorToast.vue";
    import Errors from "./components/errors/Errors.vue";
    import {mapState} from "vuex";
    import Utils from "./utils/utils";

    export default {
        name: "App",
        components: {
            LeftMenu,
            TopNavBar,
            ErrorToast,
            Errors
        },
        data() {
            return {
                menuCollapsed: "",
                created: false,
                loaded: false,
            };
        },
        computed: {
            ...mapState("core", ["message", "error"])
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
                this.switchTheme();

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
                        this.$store.dispatch("api/loadFeeds", {
                            version: value.version,
                            iid: value.uuid,
                            uid: uid,
                        });
                    })
            },
            switchTheme(theme) {
                // default theme
                if (theme === undefined) {
                    if (localStorage.getItem("theme")) {
                        theme =  localStorage.getItem("theme");
                    } else if (window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches) {
                        theme = "dark";
                    } else {
                        theme = "light";
                    }
                }

                // class name
                let htmlClass = document.getElementsByTagName("html")[0].classList;

                htmlClass.forEach((cls) => {
                    if (cls === "dark" || cls === "light") {
                        htmlClass.remove(cls);
                    }
                })

                htmlClass.add(theme);
                localStorage.setItem("theme", theme);
            }
        }
    };
</script>

<style lang="scss">
    @use "styles/vendor";
    @use "styles/app";
</style>

