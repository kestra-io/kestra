<template>
    <div>
        <nprogress-container />
        <top-nav-bar :menu-collapsed="menuCollapsed" />
        <Menu @onMenuCollapse="onMenuCollapse" />
        <custom-toast v-if="message" :no-auto-hide="true" :message="message" />
        <div id="app" class="container-fluid">
            <div class="content-wrapper" :class="menuCollapsed">
                <router-view v-if="!error" />
                <template v-else>
                    <errors :code="error" />
                </template>
            </div>
            <b-modal hide-header hide-footer scrollable id="log-fullscreen-modal" modal-class="modal-fullscreen">
                <!--prevent load dom in memory most of the time with v-if-->
                <logs v-if="fullscreen" />
            </b-modal>
        </div>
        <div id="theme-loaded" />
    </div>
</template>

<script>
    import Menu from "override/components/Menu.vue";
    import TopNavBar from "./components/layout/TopNavBar";
    import CustomToast from "./components/customToast";
    import NprogressContainer from "vue-nprogress/src/NprogressContainer";
    import Errors from "./components/errors/Errors";
    import {mapState} from "vuex";
    import Logs from "./components/executions/Logs";

    export default {
        name: "App",
        components: {
            Menu,
            TopNavBar,
            CustomToast,
            NprogressContainer,
            Errors,
            Logs
        },
        data() {
            return {
                menuCollapsed: "",
                created: false,
            };
        },
        computed: {
            ...mapState("core", ["message", "error", "themes", "theme"]),
            ...mapState("log", ["fullscreen"])
        },
        created() {
            if (this.created === false) {
                if (this.$route.path === "/") {
                    this.$router.push({name: "flows/list"});
                }

                this.displayApp()
                this.loadGeneralRessources()
            }
        },
        methods: {
            onMenuCollapse(collapse) {
                this.menuCollapsed = collapse ? "menu-collapsed" : "menu-not-collapsed";
            },
            displayApp() {
                this.grabThemeResources();

                this.onMenuCollapse(localStorage.getItem("menuCollapsed") === "true");

                this.$root.$on("setTheme", (theme) => {
                    this.switchTheme(theme);
                })

                this.switchTheme(undefined, () => {
                    document.getElementById("loader-wrapper").style.display = "none";
                    document.getElementById("app-container").style.display = "block";
                });
            },
            loadGeneralRessources() {
                this.$store.dispatch("plugin/icons")
            },
            grabThemeResources() {
                // eslint-disable-next-line no-undef
                const assets = JSON.parse(KESTRA_ASSETS);
                // eslint-disable-next-line no-undef
                const basePath = KESTRA_UI_PATH;

                const themes = {};

                Object.entries(assets)
                    .filter(r => r[0].startsWith("theme-"))
                    .forEach(r => {
                        let theme = r[0];
                        let files = typeof r[1] === "string" ? [r[1]] : r[1] ;

                        if (themes[theme] === undefined) {
                            themes[theme] = [];
                        }

                        files
                            .forEach(r => {
                                let elem;
                                if (r.endsWith(".js")) {
                                    elem = document.createElement("script");
                                    elem.setAttribute("type", "text/javascript");
                                    elem.setAttribute("src", basePath + "/" + r);
                                } else {
                                    elem = document.createElement("link");
                                    elem.setAttribute("rel", "stylesheet");
                                    elem.setAttribute("href", basePath + "/" + r);
                                }

                                elem.setAttribute("data-theme", theme);

                                themes[theme].push(elem);
                            })
                    })

                this.$store.commit("core/setThemes", themes);
            },
            switchTheme(theme, callback) {
                // default theme
                if (theme === undefined) {
                    if (localStorage.getItem("theme")) {
                        theme =  localStorage.getItem("theme");
                    } else if (window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches) {
                        theme = "theme-dark";
                    } else {
                        theme = "theme-light";
                    }
                }

                // remove old one
                [...document.querySelectorAll("*[data-theme]")]
                    .forEach(elem => {
                        elem.parentNode.removeChild(elem);
                    })

                // class name
                let htmlClass = document.getElementsByTagName("html")[0].classList;

                htmlClass.forEach((cls) => {
                    if (cls.startsWith("theme")) {
                        htmlClass.remove(cls);
                    }
                })

                htmlClass.add(theme);

                // add current one
                this.themes[theme]
                    .forEach(r => {
                        document.getElementsByTagName("head")[0].appendChild(r);
                    })

                // check loaded
                let intervalID = setInterval(
                    () => {
                        let loaderCheck = document.getElementById("theme-loaded");

                        if (loaderCheck && getComputedStyle(loaderCheck).content === "\"" + theme + "\"") {
                            clearInterval(intervalID);
                            if (this.theme !== theme) {
                                localStorage.setItem("theme", theme);
                                this.$store.commit("core/setTheme", theme);
                            }
                            callback && callback(theme)
                        }
                    },
                    1000
                );
            }
        }
    };
</script>


<style lang="scss">
.modal-fullscreen .modal {
    padding: 0 !important;
}
.modal-fullscreen .modal-dialog {
    max-width: 100%;
    height: 100%;
    margin: 0;
}
.modal-fullscreen .modal-content {
    border: 0;
    border-radius: 0;
    min-height: 100%;
    height: auto;
}
.modal-fullscreen .modal-dialog {
    max-height: 100%;
}
</style>
