<template>
    <div>
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
import Menu from "./components/Menu.vue";
import TopNavBar from "./components/layout/TopNavBar";
import CustomToast from "./components/customToast";
import { mapState } from "vuex";
export default {
    name: "app",
    components: {
        Menu,
        TopNavBar,
        CustomToast
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
        this.onMenuCollapse(localStorage.getItem("menuCollapsed") === "true");
    },
    methods: {
        onMenuCollapse(collapse) {
            this.menuCollapsed = collapse
                ? "menu-collapsed"
                : "menu-not-collapsed";
        }
    }
};
</script>


<style lang="scss">
@import "styles/variable";

.menu-collapsed {
    transition: all 0.3s ease;
    padding-left: 50px;
}
.menu-not-collapsed {
    transition: all 0.3s;
    padding-left: $menu-width;
}
body {
    min-width: 320px;
}

body,
html,
.container {
    height: 100% !important;
}
.content-wrapper {
    padding-top: 15px;
    padding-bottom: 60px !important;
}
</style>
