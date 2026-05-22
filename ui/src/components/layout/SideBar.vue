<template>
    <KsSideBar id="side-menu" :class="{'is-collapsed': collapsed}">
        <template #header>
            <div class="header-toolbar">
                <SidebarToggleButton @toggle="onCollapse(true)" />
            </div>
            <Environment />
        </template>

        <template v-for="(section, sIdx) in menu" :key="section.id ?? `s-${sIdx}`">
            <div v-if="!section.child" class="top-level-link">
                <MenuLink
                    :item="section"
                    :active="isItemActive(section)"
                />
            </div>
            <KsSideBarSection
                v-else
                :title="section.title"
                collapsible
                :defaultCollapsed="!sectionHasActiveChild(section)"
            >
                <template v-for="(item, iIdx) in section.child" :key="item.id ?? `i-${iIdx}`">
                    <MenuLink
                        v-if="!item.hidden"
                        :item="item"
                        :active="isItemActive(item)"
                    />
                </template>
            </KsSideBarSection>
        </template>

        <KsSideBarSection v-if="bookmarksStore.pages?.length" title="Favourites" collapsible>
            <BookmarkLinkList :pages="bookmarksStore.pages" />
        </KsSideBarSection>

        <template #footer>
            <slot name="footer" />
        </template>
    </KsSideBar>
</template>

<script setup lang="ts">
    import {computed, h, defineComponent} from "vue"
    import type {PropType} from "vue"
    import {useRoute, RouterLink} from "vue-router"
    import {KsSideBar, KsSideBarSection, KsSideBarItem} from "@kestra-io/design-system"

    import Environment from "./Environment.vue"
    import SidebarToggleButton from "./SidebarToggleButton.vue"
    import BookmarkLinkList from "./BookmarkLinkList.vue"
    import {useBookmarksStore} from "../../stores/bookmarks"
    import {useLayoutStore} from "../../stores/layout"
    import type {MenuItem} from "override/components/useLeftMenu"

    withDefaults(defineProps<{
        menu: MenuItem[],
        showLink?: boolean,
        logoTo?: object,
        collapsed?: boolean,
    }>(), {
        showLink: true,
        logoTo: () => ({name: "welcome"}),
        collapsed: false,
    })

    const emit = defineEmits<{
        (e: "menu-collapse", folded: boolean): void
    }>()

    const $route = useRoute()
    const layoutStore = useLayoutStore()
    const bookmarksStore = useBookmarksStore()

    function onCollapse(folded: boolean) {
        layoutStore.setSideMenuCollapsed(folded)
        emit("menu-collapse", folded)
    }

    function isItemActive(item: MenuItem): boolean {
        if (typeof item.href !== "string" || item.href === "/") return false
        if (item.routes) return item.routes.includes($route.name)
        return $route.path.startsWith(item.href)
    }

    function sectionHasActiveChild(section: MenuItem): boolean {
        return Boolean(section.child?.some((child) => !child.hidden && isItemActive(child)))
    }

    // Inline adapter: maps a MenuItem to <KsSideBarItem>, wiring vue-router navigation
    // via <RouterLink custom> when the item has a resolved href.
    const MenuLink = defineComponent({
        name: "SideBarMenuLink",
        props: {
            item: {type: Object as PropType<MenuItem>, required: true},
            active: {type: Boolean, default: false},
        },
        setup(props) {
            const hrefString = computed(() => (typeof props.item.href === "string" ? props.item.href : ""))
            const locked = computed(() => Boolean(props.item.attributes?.locked))

            return () => {
                const itemNode = (extraProps: Record<string, unknown> = {}) => h(KsSideBarItem, {
                    title: props.item.title,
                    icon: props.item.icon?.element,
                    active: props.active,
                    locked: locked.value,
                    ...extraProps,
                })

                if (!hrefString.value) return itemNode()

                return h(RouterLink, {to: hrefString.value, custom: true}, {
                    default: ({href, navigate}: {href: string; navigate: (e: MouseEvent) => void}) =>
                        itemNode({href, onClick: navigate}),
                })
            }
        },
    })
</script>

<style scoped lang="scss">
#side-menu {
    width: 215px;
    flex-shrink: 0;
    box-sizing: border-box;
    overflow: hidden;
    transition: width 0.25s ease, border-right-width 0.25s ease;

    &.is-collapsed {
        width: 0;
        border-right-width: 0;
    }
}

.top-level-link {
    padding: 0 var(--ks-spacing-2);
}

.header-toolbar {
    display: flex;
    justify-content: flex-end;
    margin-top: calc(-1 * var(--ks-spacing-4));
    margin-bottom: var(--ks-spacing-2);
}
</style>
