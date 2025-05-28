<template>
    <side-bar :generate-menu="generateMenu" @menu-collapse="onCollapse">
        <template #footer>
            <auth />    
        </template>
    </side-bar>
</template>

<script setup>
    import {useLeftMenu} from "override/components/useLeftMenu";
    import SideBar from "../../components/layout/SideBar.vue";
    import Auth from "../../override/components/auth/Auth.vue";

    const $emit = defineEmits(["menu-collapse"])

    function onCollapse(folded) {
        $emit("menu-collapse", folded);
    }

    const {generateMenu} = useLeftMenu();
</script>

<style lang="scss" scoped>
#side-menu {
    .el-select {
        padding: 0 30px;
        padding-bottom: 15px;
        transition: all 0.2s ease;
        background-color: transparent;
    }
    &.vsm_collapsed {
        .el-select {
            padding-left: 5px;
            padding-right: 5px;
        }
    }
}
</style>