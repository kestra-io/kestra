<template>
    <SideBar
        v-if="menu"
        :menu
        :showLink
        @menu-collapse="onCollapse"
        :class="{overlay: verticalLayout}"
    >
        <template #footer>
            <Auth />    
        </template>
    </SideBar>
</template>

<script setup lang="ts">
    import {useLeftMenu} from "override/components/useLeftMenu";
    import SideBar from "../../components/layout/SideBar.vue";
    import Auth from "../../override/components/auth/Auth.vue";

    import {useBreakpoints, breakpointsElement} from "@vueuse/core";
    const verticalLayout = useBreakpoints(breakpointsElement).smallerOrEqual("sm");

    withDefaults(defineProps<{
        showLink?: boolean
    }>(), {
        showLink: true
    });

    const emit = defineEmits<{
        (e: "menu-collapse", folded: boolean): void
    }>();

    function onCollapse(folded: boolean) {
        emit("menu-collapse", folded);
    }

    const {menu} = useLeftMenu();
</script>

<style scoped lang="scss">
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
