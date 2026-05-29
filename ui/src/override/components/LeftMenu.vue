<template>
    <SideBar
        v-if="menu"
        :menu
        :showLink
        :collapsed
        @menu-collapse="onCollapse"
        :class="{overlay: verticalLayout}"
    >
        <template #footer>
            <AdminItem :tabs="adminTabs" />
            <Auth />
        </template>
    </SideBar>
</template>

<script setup lang="ts">
    import {useBreakpoints, breakpointsElement} from "@vueuse/core"

    import SideBar from "../../components/layout/SideBar.vue"
    import AdminItem from "../../components/admin/AdminItem.vue"
    import Auth from "override/components/auth/Auth.vue"

    import {useLeftMenu} from "override/components/useLeftMenu"
    import {useAdminTabs} from "../../composables/useAdminTabs"

    withDefaults(defineProps<{
        showLink?: boolean
        collapsed?: boolean
    }>(), {
        showLink: true,
        collapsed: false,
    })

    const emit = defineEmits<{
        (e: "menu-collapse", folded: boolean): void
    }>()

    const verticalLayout = useBreakpoints(breakpointsElement).smallerOrEqual("sm")
    const {menu} = useLeftMenu()
    const {adminTabs} = useAdminTabs()

    function onCollapse(folded: boolean) {
        emit("menu-collapse", folded)
    }
</script>

<style scoped lang="scss">
    #side-menu {
        .kel-select {
            padding: 0 var(--ks-spacing-4);
            padding-bottom: 15px;
            transition: all 0.2s ease;
            background-color: transparent;
        }
    }
</style>
