<template>
    <KsSideBar id="side-menu" :class="{'is-collapsed': collapsed}" @contextmenu.prevent="showCustomizeModal = true">
        <template #header>
            <KsIconButton
                class="header-toggle"
                aria-label="Toggle menu"
                @click="onCollapse(true)"
            >
                <DockLeft />
            </KsIconButton>
        </template>

        <template v-for="(section, sIdx) in menu" :key="section.id ?? `s-${sIdx}`">
            <div v-if="!section.child" class="top-level-link">
                <MenuLink
                    :item="section"
                    :active="isItemActive(section)"
                />
            </div>
            <KsSideBarSection
                v-else-if="getDisplayedItems(section).length > 0"
                :title="section.title"
                collapsible
                :collapsed="getSectionCollapsed(section)"
                @update:collapsed="(value: boolean) => onSectionCollapseChange(section, value)"
            >
                <template v-for="item in getDisplayedItems(section)" :key="item.id">
                    <MenuLink
                        :item="item"
                        :active="isItemActive(item)"
                    />
                </template>
            </KsSideBarSection>
        </template>

        <KsSideBarSection
            v-if="bookmarksStore.pages?.length"
            title="Favourites"
            collapsible
            :collapsed="getCollapsedById(FAVOURITES_SECTION_ID, false)"
            @update:collapsed="(value: boolean) => layoutStore.setMenuSectionCollapsed(FAVOURITES_SECTION_ID, value)"
        >
            <BookmarkLinkList :pages="bookmarksStore.pages" />
        </KsSideBarSection>

        <template #footer>
            <slot name="footer" />
            <div class="sidebar-customize-trigger">
                <KsButton
                    type="text"
                    size="small"
                    class="customize-btn"
                    @click="showCustomizeModal = true"
                >
                    {{ $t("customize sidebar") }}
                </KsButton>
            </div>
        </template>
    </KsSideBar>

    <SidebarCustomizeModal v-model="showCustomizeModal" :menu="menu" />
</template>

<script setup lang="ts">
    import {computed, h, ref, defineComponent} from "vue"
    import type {PropType} from "vue"
    import {useRoute, RouterLink} from "vue-router"
    import {KsSideBar, KsSideBarSection, KsSideBarItem, KsIconButton, KsButton} from "@kestra-io/design-system"
    import DockLeft from "vue-material-design-icons/DockLeft.vue"

    import BookmarkLinkList from "./BookmarkLinkList.vue"
    import SidebarCustomizeModal from "./SidebarCustomizeModal.vue"
    import {useBookmarksStore} from "../../stores/bookmarks"
    import {useLayoutStore} from "../../stores/layout"
    import {
        menuSectionId,
        resolveSectionItemIds,
        pickItemsByIds,
        isMenuItemVisible,
    } from "../../utils/menuCustomization"
    import type {MenuItem} from "override/components/useLeftMenu"

    const props = withDefaults(defineProps<{
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
    const showCustomizeModal = ref(false)

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

    const FAVOURITES_SECTION_ID = "favourites"

    function getCollapsedById(id: string, fallback: boolean): boolean {
        const stored = layoutStore.menuSectionsCollapsed[id]
        return stored !== undefined ? stored : fallback
    }

    function getSectionCollapsed(section: MenuItem): boolean {
        return getCollapsedById(menuSectionId(section), !sectionHasActiveChild(section))
    }

    function onSectionCollapseChange(section: MenuItem, collapsed: boolean) {
        layoutStore.setMenuSectionCollapsed(menuSectionId(section), collapsed)
    }

    function getDisplayedItems(section: MenuItem): MenuItem[] {
        const ids = resolveSectionItemIds(props.menu, layoutStore.menuItemOrder, menuSectionId(section))
        return pickItemsByIds(props.menu, ids)
            .filter((item) => isMenuItemVisible(layoutStore.menuItemVisibility, item))
    }

    // Inline adapter: maps a MenuItem to <KsSideBarItem>, wiring vue-router navigation
    // via <RouterLink custom> when the item has a resolved href.
    const MenuLink = defineComponent({
        name: "SideBarMenuLink",
        props: {
            item: {type: Object as PropType<MenuItem>, required: true},
            active: {type: Boolean, default: false},
        },
        setup(itemProps) {
            const hrefString = computed(() => (typeof itemProps.item.href === "string" ? itemProps.item.href : ""))
            const locked = computed(() => Boolean(itemProps.item.attributes?.locked))

            return () => {
                const itemNode = (extraProps: Record<string, unknown> = {}) => h(KsSideBarItem, {
                    title: itemProps.item.title,
                    icon: itemProps.item.icon?.element,
                    active: itemProps.active,
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
    position: relative;
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

.header-toggle {
    position: absolute;
    top: var(--ks-spacing-4);
    right: var(--ks-spacing-4);
    z-index: 1;
    color: var(--ks-icon-muted);
}

.sidebar-customize-trigger {
    padding: var(--ks-spacing-2) var(--ks-spacing-2) 0;

    .customize-btn {
        width: 100%;
        justify-content: flex-start;
        color: var(--ks-text-dim);
        font-size: var(--ks-font-size-xs);

        &:hover {
            color: var(--ks-text-secondary);
        }
    }
}
</style>
