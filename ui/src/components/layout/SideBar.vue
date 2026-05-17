<template>
    <SidebarMenu
        ref="sideBarRef"
        id="side-menu"
        :menu
        @update:collapsed="onToggleCollapse"
        width="220px"
        :collapsed="collapsed"
        linkComponentName="LeftMenuLink"
        hideToggle
    >
        <template #header>
            <SidebarToggleButton
                @toggle="collapsed = onToggleCollapse(!collapsed)"
            />
            <Environment />
        </template>

        <template #footer>
            <slot name="footer" />
        </template>
    </SidebarMenu>
</template>

<script setup lang="ts">
    import {onMounted, onUpdated, nextTick, computed, h, watch} from "vue"
    import {useRoute} from "vue-router"
    import {useMediaQuery} from "@vueuse/core"
    import {SidebarMenu} from "vue-sidebar-menu"

    import Environment from "./Environment.vue"
    import BookmarkLinkList from "./BookmarkLinkList.vue"
    import {useBookmarksStore} from "../../stores/bookmarks"
    import type {MenuItem} from "override/components/useLeftMenu"
    import {useLayoutStore} from "../../stores/layout"
    import SidebarToggleButton from "./SidebarToggleButton.vue"


    const props = withDefaults(defineProps<{
        menu: MenuItem[],
        showLink?: boolean,
        logoTo?: object
    }>(), {
        showLink: true,
        logoTo: () => ({name: "welcome"}),
    })

    const $emit = defineEmits(["menu-collapse"])

    const $route = useRoute()

    const layoutStore = useLayoutStore()

    function onToggleCollapse(folded: boolean) {
        collapsed.value = folded
        layoutStore.setSideMenuCollapsed(folded)
        $emit("menu-collapse", folded)

        return folded
    }

    function disabledCurrentRoute(items: MenuItem[]) {
        return items
            .map(r => {
                if (typeof r.href === "object" && r.href?.path === $route.path) {
                    r.disabled = true
                }

                // When `routes` is defined on the item, treat it as authoritative —
                // otherwise a coarse path prefix can hijack a more specific sibling
                // (e.g. /apps matching /apps/catalog).
                const isLeafActive = (item: MenuItem) => {
                    if (typeof item.href !== "string" || item.href === "/") return false
                    if (item.routes) return item.routes.includes($route.name)
                    return $route.path.startsWith(item.href)
                }

                if (isLeafActive(r)) {
                    r.class = "vsm--link_active"
                }

                if ((!r.href || typeof r.href === "string") && r.child && r.child.some(isLeafActive)) {
                    r.class = "vsm--link_active"
                    r.child = disabledCurrentRoute(r.child)
                }

                return r
            })
    }


    function expandParentIfNeeded() {
        document.querySelectorAll(".vsm--link.vsm--link_level-1.vsm--link_active[aria-expanded=\"false\"]").forEach(e => {
            (e as HTMLElement).click()
        })
    }

    onMounted(() => nextTick(expandParentIfNeeded))

    onUpdated(() => {
        // Required here because in mounted() the menu is not yet rendered
        expandParentIfNeeded()
    })

    const bookmarksStore = useBookmarksStore()

    const menu = computed(() => {
        return [
            ...(props.menu ? disabledCurrentRoute(props.menu) : []),
            ...(bookmarksStore.pages?.length ? [{
                title: "Favourites",
                child: [{
                    // here we use only one component for all bookmarks
                    // so when one edits the bookmark, it will be updated without closing the section
                    component: () => h(BookmarkLinkList, {pages: bookmarksStore.pages}),
                }],
            }] : []),
        ]
    })

    const collapsed = computed({
        get: () => layoutStore.sideMenuCollapsed,
        set: (v: boolean) => layoutStore.setSideMenuCollapsed(v),
    })

    const isSmallScreen = useMediaQuery("(max-width: 768px)")

    watch(() => $route.name, (newRoute, oldRoute) => {
        if (newRoute !== oldRoute && isSmallScreen.value && !collapsed.value) {
            onToggleCollapse(true)
        }
    })
</script>

<style scoped lang="scss">
.collapseButton {
    position: absolute;
    top: -1.55rem;
    right: .5rem;
    z-index: 1;

    #side-menu & {
        border: none;
    }
}

#side-menu {
    position: static;
    z-index: 1039;
    border-right: 1px solid var(--ks-border-default);
    background-color: var(--ks-bg-sidebar);
    padding: 32px 0 16px;
}
</style>
