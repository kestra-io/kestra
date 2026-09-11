<template>
    <KsSideBarItem
        :title="title ?? $t('admin')"
        :icon="CogOutline"
        :active="active"
        class="admin-item"
        @click="open"
    >
    </KsSideBarItem>
</template>

<script setup lang="ts">
    import {computed, onUnmounted, watch} from "vue"
    import {useRoute, useRouter, type RouteLocationRaw} from "vue-router"
    import CogOutline from "vue-material-design-icons/CogOutline.vue"
    import {KsSideBarItem} from "@kestra-io/design-system"
    import {useRouteTabsStore, activeScopeTab, type RouteTab} from "../../stores/routeTabs"

    const props = defineProps<{
        tabs: RouteTab[]
        landingRoute?: RouteLocationRaw
        /** Overrides the entry label — EE narrows this panel down to settings. */
        title?: string
    }>()

    const OWNER = Symbol("admin-tabs")

    const route = useRoute()
    const router = useRouter()
    const store = useRouteTabsStore()

    const active = computed(() => Boolean(activeScopeTab(route, props.tabs, router)))

    function open() {
        store.setTabs({ownerId: OWNER, tabs: props.tabs})
        const landing = props.landingRoute ?? props.tabs.find(t => !t.header && !t.disabled && t.route)?.route
        if (landing) router.push(landing)
    }

    watch(
        () => [route.path, props.tabs] as const,
        () => (active.value
            ? store.setTabs({ownerId: OWNER, tabs: props.tabs})
            : store.clearTabsIfOwner(OWNER)),
        {immediate: true, deep: true},
    )

    onUnmounted(() => store.clearTabsIfOwner(OWNER))
</script>

<style scoped lang="scss">
    .admin-item {
        margin: 0;
        --ks-sidebar-item-title-color: currentColor;
    }
</style>
