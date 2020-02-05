<template>
    <div>
        <top-nav-bar :menuCollapsed="menuCollapsed" />
        <Menu @onMenuCollapse="onMenuCollapse" />

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

export default {
    name: "app",
    components: {
        Menu,
        TopNavBar
    },
    data() {
        return {
            menuCollapsed: "",
        };
    },
    created() {
        if (this.$route.path === "/") {
            this.$router.push({ name: "flowsList" });
        }
        this.onMenuCollapse(localStorage.getItem('menuCollapsed') === 'true')
    },
    methods: {
        onMenuCollapse(collapse) {
            this.menuCollapsed = collapse ? "menu-collapsed" : "menu-not-collapsed";
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
