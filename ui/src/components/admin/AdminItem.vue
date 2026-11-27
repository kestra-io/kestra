<template>
    <KsSideBarItem
        :title="title ?? $t('admin')"
        :icon="CogOutline"
        :active="active"
        class="admin-item"
        :href="adminHref?.href"
        @click="open"
    />
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

    const landing = computed(() => props.landingRoute ?? props.tabs.find(t => !t.header && !t.disabled && t.route)?.route)
    const adminHref = computed(() => landing.value ? router.resolve(landing.value) : undefined)

    function open() {
        store.setTabs({ownerId: OWNER, tabs: props.tabs})
        if (landing.value) router.push(landing.value)
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

<style scoped>
    .admin-item {
        margin: 0;
        --ks-sidebar-item-title-color: currentColor;
    }
</style>
