<template>
    <div>
        <nprogress-container></nprogress-container>
        <top-nav-bar :menuCollapsed="menuCollapsed" />
        <Menu @onMenuCollapse="onMenuCollapse" />
        <custom-toast v-if="errorMessage" :noAutoHide="true" toastId="errorToast" :content="errorMessage" :title="$t('error')" />
        <div id="app" class="container-fluid">
            <div class="content-wrapper" :class="menuCollapsed">
                <router-view></router-view>
            </div>
        </div>
    </div>
</template>

<script>
import Menu from "Override/components/Menu.vue";
import TopNavBar from "./components/layout/TopNavBar";
import CustomToast from "./components/customToast";
import NprogressContainer from "vue-nprogress/src/NprogressContainer";
import { mapState } from "vuex";

export default {
    name: "app",
    components: {
        Menu,
        TopNavBar,
        CustomToast,
        NprogressContainer
    },
    data() {
        return {
            menuCollapsed: "",
        };
    },
    computed: {
        ...mapState('core', ['errorMessage'])
    },
    created() {
        if (this.$route.path === "/") {
            this.$router.push({ name: "flowsList" });
        }

        this.displayApp()

        this.onMenuCollapse(localStorage.getItem("menuCollapsed") === "true");
    },
    methods: {
        onMenuCollapse(collapse) {
            this.menuCollapsed = collapse ? "menu-collapsed" : "menu-not-collapsed";
        },
        displayApp() {
            document.getElementById("loader-wrapper").style.display = "none";
            document.getElementById("app-container").style.display = "block";
        }
    }
};
</script>


<style lang="scss">
    @import "app";
</style>
