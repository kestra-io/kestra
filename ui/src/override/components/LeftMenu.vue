<template>
    <SideBar v-if="menu" :menu :showLink="showLink" @menu-collapse="onCollapse">
        <template #footer>
            <Auth />    
        </template>
    </SideBar>
</template>

<script setup>
    import {useLeftMenu} from "override/components/useLeftMenu";
    import SideBar from "../../components/layout/SideBar.vue";
    import Auth from "../../override/components/auth/Auth.vue";

    defineProps({showLink: {type: Boolean, default: true}})

    const $emit = defineEmits(["menu-collapse"])

    function onCollapse(folded) {
        $emit("menu-collapse", folded);
    }

    const {menu} = useLeftMenu();
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
