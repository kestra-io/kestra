<template>
    <KsSideBar id="side-menu" v-bind="$attrs" :class="{'is-collapsed': collapsed}" @contextmenu.prevent="onContextMenu">
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
                <template v-if="getSectionCollapsed(section) && sectionHasNewChild(section)" #suffix>
                    <KsNewBadge>{{ t("new") }}</KsNewBadge>
                </template>
                <MenuLink
                    v-for="item in getDisplayedItems(section)"
                    :key="item.id"
                    :item="item"
                    :active="isItemActive(item)"
                    :isNew="isItemNew(item)"
                />
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
        </template>
    </KsSideBar>

    <SidebarCustomizeModal v-model="showCustomizeModal" :menu="menu" />

    <Teleport to="body">
        <div
            v-if="contextMenu.visible"
            class="sidebar-context-menu"
            role="menu"
            :style="{left: `${contextMenu.x}px`, top: `${contextMenu.y}px`}"
        >
            <button ref="contextMenuItem" type="button" role="menuitem" class="sidebar-context-menu__item" @click="openCustomizeFromContextMenu">
                <SquareEditOutline :size="16" />
                {{ $t("customize sidebar") }}
            </button>
        </div>
    </Teleport>
</template>

<script setup lang="ts">
    import {computed, h, ref, defineComponent, onUnmounted, nextTick, watch} from "vue"

    defineOptions({inheritAttrs: false})
    import type {PropType} from "vue"
    import {useRoute, RouterLink} from "vue-router"
    import {useI18n} from "vue-i18n"
    import {KsSideBar, KsSideBarSection, KsSideBarItem, KsIconButton, KsNewBadge} from "@kestra-io/design-system"
    import DockLeft from "vue-material-design-icons/DockLeft.vue"
    import SquareEditOutline from "vue-material-design-icons/SquareEditOutline.vue"

    import BookmarkLinkList from "./BookmarkLinkList.vue"
    import SidebarCustomizeModal from "./SidebarCustomizeModal.vue"
    import {useBookmarksStore} from "../../stores/bookmarks"
    import {useLayoutStore} from "../../stores/layout"
    import {useFeatureSpotlightStore} from "../../stores/featureSpotlight"
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
    const {t} = useI18n({useScope: "global"})
    const layoutStore = useLayoutStore()
    const bookmarksStore = useBookmarksStore()
    const featureSpotlightStore = useFeatureSpotlightStore()
    const showCustomizeModal = ref(false)
    const contextMenu = ref<{visible: boolean; x: number; y: number}>({visible: false, x: 0, y: 0})
    const contextMenuItem = ref<HTMLButtonElement | null>(null)

    const CONTEXT_MENU_WIDTH = 200
    const CONTEXT_MENU_HEIGHT = 60

    function onContextMenu(event: MouseEvent) {
        const x = Math.max(0, Math.min(event.clientX, window.innerWidth - CONTEXT_MENU_WIDTH))
        const y = Math.max(0, Math.min(event.clientY, window.innerHeight - CONTEXT_MENU_HEIGHT))
        contextMenu.value = {visible: true, x, y}
        document.addEventListener("click", hideContextMenu)
        document.addEventListener("keydown", onContextMenuKeydown)
        nextTick(() => contextMenuItem.value?.focus())
    }

    function hideContextMenu() {
        contextMenu.value.visible = false
        document.removeEventListener("click", hideContextMenu)
        document.removeEventListener("keydown", onContextMenuKeydown)
    }

    function onContextMenuKeydown(event: KeyboardEvent) {
        if (event.key === "Escape") hideContextMenu()
    }

    function openCustomizeFromContextMenu() {
        hideContextMenu()
        showCustomizeModal.value = true
    }

    onUnmounted(hideContextMenu)

    function onCollapse(folded: boolean) {
        layoutStore.setSideMenuCollapsed(folded)
        emit("menu-collapse", folded)
    }

    function isItemActive(item: MenuItem): boolean {
        if (item.routes) return item.routes.includes($route.name)
        if (typeof item.href !== "string" || item.href === "/") return false
        return $route.path.startsWith(item.href)
    }

    function sectionHasActiveChild(section: MenuItem): boolean {
        return Boolean(section.child?.some((child) => !child.hidden && isItemActive(child)))
    }

    watch(() => $route.name, () => {
        for (const item of props.menu.flatMap((section) => section.child ?? [section])) {
            if (item.id && isItemActive(item)) featureSpotlightStore.markSeenById(item.id)
        }
    }, {immediate: true})

    function isItemNew(item: MenuItem): boolean {
        return featureSpotlightStore.hasUnseenForId(item.id)
    }

    function sectionHasNewChild(section: MenuItem): boolean {
        return getDisplayedItems(section).some((item) => isItemNew(item))
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
            isNew: {type: Boolean, default: false},
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
                }, itemProps.isNew ? {
                    suffix: () => h(KsNewBadge, null, {default: () => t("new")}),
                } : undefined)

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
    transition: width 0.32s cubic-bezier(0.22, 1, 0.36, 1), border-right-width 0.32s ease;

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

.sidebar-context-menu {
    position: fixed;
    z-index: 9999;
    min-width: 12rem;
    padding: var(--ks-spacing-1);
    background: var(--ks-bg-elevated);
    border: var(--ks-border-width-thin) solid var(--ks-border-strong);
    border-radius: var(--ks-radius-base);
    box-shadow: 0 8px 24px 0 var(--ks-shadow-elevated);

    &__item {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        width: 100%;
        padding: var(--ks-spacing-2);
        border: 0;
        border-radius: var(--ks-radius-xs);
        background: transparent;
        color: var(--ks-text-primary);
        font: inherit;
        font-size: var(--ks-font-size-xs);
        text-align: left;
        cursor: pointer;

        &:hover,
        &:focus-visible {
            background: var(--ks-bg-hover-elevated);
            outline: none;
        }
    }
}
</style>
