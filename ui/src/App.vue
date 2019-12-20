<template>
    <div>
        <Menu />
        <top-nav-bar />

        <div id="app" class="container-fluid">
            <div class="content-wrapper">
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
    created () {
        const defaultPage = localStorage.getItem('defaultPage') || 'flows'
        if (this.$route.path === '/') {
            this.$router.push({name: defaultPage})
        }
    }
};
</script>

<style lang="scss">
body,
html,
.container {
    height: 100% !important;
}
.content-wrapper {
    padding-top: 15px;
    padding-bottom: 60px !important;
    padding-left: 50px;
}
</style>
