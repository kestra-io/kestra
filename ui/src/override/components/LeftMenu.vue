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
            <Auth />
        </template>
    </SideBar>
</template>

<script setup lang="ts">
    import {useLeftMenu} from "override/components/useLeftMenu"
    import SideBar from "../../components/layout/SideBar.vue"
    import Auth from "override/components/auth/Auth.vue"

    import {useBreakpoints, breakpointsElement} from "@vueuse/core"
    const verticalLayout = useBreakpoints(breakpointsElement).smallerOrEqual("sm")

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

    function onCollapse(folded: boolean) {
        emit("menu-collapse", folded)
    }

    const {menu} = useLeftMenu()
</script>

<style scoped lang="scss">
#side-menu {
    .kel-select {
        padding: 0 18px;
        padding-bottom: 15px;
        transition: all 0.2s ease;
        background-color: transparent;
    }
}
</style>
