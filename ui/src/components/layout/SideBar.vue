<template>
    <!-- Mobile opener moved to TopNavBar -->

    <!-- Desktop / large screens: regular fixed sidebar -->
    <SidebarMenu
        v-if="!isSmallScreen"
        ref="sideBarRef"
        id="side-menu"
        :menu
        @update:collapsed="onToggleCollapse"
        width="268px"
        :collapsed="collapsed"
        linkComponentName="LeftMenuLink"
        hideToggle
        role="navigation"
        :aria-hidden="false"
    >
        <template #header>
            <SidebarToggleButton
                @toggle="collapsed = onToggleCollapse(!collapsed)"
            />
            <div class="logo">
                <component :is="props.showLink ? 'router-link' : 'div'" :to="{name: 'home'}">
                    <span class="img" />
                </component>
            </div>
            <Environment />
        </template>

        <template #footer>
            <slot name="footer" />
        </template>
    </SidebarMenu>

    <!-- Mobile slide-in sidebar + overlay -->
    <div
        v-if="isSmallScreen"
        class="sidebar-overlay"
        :class="{open: isMobileSidebarOpen}"
        @click="isMobileSidebarOpen = false"
        aria-hidden="true"
    />
    <nav
        v-if="isSmallScreen"
        class="mobile-sidebar"
        :class="{open: isMobileSidebarOpen}"
        role="navigation"
        :aria-hidden="!isMobileSidebarOpen"
    >
        <div class="mobile-sidebar__header">
            <button class="icon-btn close-left" aria-label="Close menu" @click="isMobileSidebarOpen = false">
                <CloseIcon />
            </button>
            <div class="logo">
                <component :is="props.showLink ? 'router-link' : 'div'" :to="{name: 'home'}">
                    <span class="img" />
                </component>
            </div>
        </div>
        <div class="mobile-sidebar__body">
            <SidebarMenu
                ref="sideBarRef"
                id="side-menu"
                :menu
                width="268px"
                :collapsed="false"
                linkComponentName="LeftMenuLink"
                hideToggle
            >
                <template #footer>
                    <slot name="footer" />
                </template>
            </SidebarMenu>
        </div>
    </nav>
</template>

<script setup lang="ts">
    import {
        onUpdated,
        onMounted,
        onBeforeUnmount,
        ref,
        computed, h, watch
    } from "vue";
    import {useI18n} from "vue-i18n";
    import {useRoute} from "vue-router";

    import {SidebarMenu} from "vue-sidebar-menu";

    import StarOutline from "vue-material-design-icons/StarOutline.vue";

    import Environment from "./Environment.vue";
    import BookmarkLinkList from "./BookmarkLinkList.vue";
    import {useBookmarksStore} from "../../stores/bookmarks";
    import type {MenuItem} from "override/components/useLeftMenu";
    import {useLayoutStore} from "../../stores/layout";
    import SidebarToggleButton from "./SidebarToggleButton.vue";
    import CloseIcon from "vue-material-design-icons/Close.vue";


    const props = withDefaults(defineProps<{
        menu: MenuItem[],
        showLink: boolean
    }>(), {
        showLink: true
    })

    const $emit = defineEmits(["menu-collapse"])

    const $route = useRoute()
    const {t} = useI18n({useScope: "global"});

    const layoutStore = useLayoutStore();

    const BREAKPOINT = 992; // px
    const isSmallScreen = ref(false);
    const isMobileSidebarOpen = ref(false);

    function onToggleCollapse(folded: boolean) {
        collapsed.value = folded;
        layoutStore.setSideMenuCollapsed(folded);
        $emit("menu-collapse", folded);

        return folded;
    }

    function evaluateScreenSize() {
        const small = window.innerWidth < BREAKPOINT;
        isSmallScreen.value = small;
        if (!small) {
            isMobileSidebarOpen.value = false;
        }
    }

    onMounted(() => {
        evaluateScreenSize();
        window.addEventListener("resize", evaluateScreenSize);
    });

    onBeforeUnmount(() => {
        window.removeEventListener("resize", evaluateScreenSize);
    });

    // Keep collapsed state in sync with mobile sidebar visibility
    watch(isMobileSidebarOpen, (open) => {
        if (isSmallScreen.value) onToggleCollapse(!open);
    });

    // React to external layoutStore changes
    watch(() => layoutStore.sideMenuCollapsed, (val) => {
        if (isSmallScreen.value) isMobileSidebarOpen.value = !val;
    });

    function disabledCurrentRoute(items: MenuItem[]) {
        return items
            .map(r => {
                if (r.href?.path === $route.path) {
                    r.disabled = true;
                }

                // route hack is still needed for blueprints
                if (r.href !== "/" && ($route.path.startsWith(r.href) || r.routes?.includes($route.name))) {
                    r.class = "vsm--link_active";
                }

                if (r.child && r.child.some(c => $route.path.startsWith(c.href) || c.routes?.includes($route.name))) {
                    r.class = "vsm--link_active";
                    r.child = disabledCurrentRoute(r.child);
                }

                return r;
            })
    }


    function expandParentIfNeeded() {
        document.querySelectorAll(".vsm--link.vsm--link_level-1.vsm--link_active:not(.vsm--link_open)[aria-haspopup]").forEach(e => {
            (e as HTMLElement).click()
        });
    }

    onUpdated(() => {
        // Required here because in mounted() the menu is not yet rendered
        expandParentIfNeeded();
    })

    const bookmarksStore = useBookmarksStore();

    const menu = computed(() => {
        return [
            ...(bookmarksStore.pages?.length ? [{
                title: t("bookmark"),
                icon: {
                    element: StarOutline,
                    class: "menu-icon",
                },
                child: [{
                    // here we use only one component for all bookmarks
                    // so when one edits the bookmark, it will be updated without closing the section
                    component: () => h(BookmarkLinkList, {pages: bookmarksStore.pages}),
                }]
            }] : []),
            ...(props.menu ? disabledCurrentRoute(props.menu) : [])
        ];
    });

    const collapsed = ref(localStorage.getItem("menuCollapsed") === "true")
</script>

<style scoped lang="scss">
.collapseButton {
    position: absolute;
    top: .5rem;
    right: 0;
    z-index: 1;

    #side-menu & {
        border: none;
        background: none;

        &:hover {
            background: none !important;
            color: var(--ks-content-link) !important;
        }
    }
}

#side-menu {
    position: static;
    z-index: 1039;
    border-right: 1px solid var(--ks-border-primary);
    background-color: var(--ks-background-left-menu);

    .logo {
        overflow: hidden;
        padding: 35px 0;
        height: 112px;
        position: relative;

        > * {
            transition: 0.2s all;
            position: absolute;
            left: 37px;
            display: block;
            height: 55px;
            width: 100%;
            overflow: hidden;

            span.img {
                height: 100%;
                background: url(../../assets/logo.svg) 0 0 no-repeat;
                background-size: 179px 55px;
                display: block;
                transition: 0.2s all;

                html.dark & {
                    background-image: url(../../assets/logo-white.svg);
                }
            }
        }
    }
}

/* Mobile slide-in */
.mobile-sidebar {
  position: fixed;
  top: 0;
  left: 0;
  height: 100%;
  width: 268px;
  background-color: var(--ks-background-left-menu);
  box-shadow: 2px 0 10px rgba(0,0,0,0.3);
  transform: translateX(-100%);
  transition: transform 0.3s ease-in-out;
  z-index: 2000;

  &.open { transform: translateX(0); }
}

.mobile-sidebar__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid var(--ks-border-primary);
}

.mobile-sidebar__body {
  height: calc(100% - 56px);
  overflow: auto;
}

.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 6px;
  border-radius: 8px;
  background: rgba(0,0,0,0.08);
  border: 1px solid var(--ks-border-primary);
  color: var(--ks-text-secondary);
}

.close-left { margin-right: 8px; }

.sidebar-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.5);
  z-index: 1999;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.3s ease-in-out;

  &.open {
    opacity: 1;
    pointer-events: all;
  }
}

/* Sidebar menu item styling */
#side-menu :deep(.vsm--item) {
    margin: 4px 8px;
}

#side-menu :deep(.vsm--link) {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 8px 10px;
    border-radius: 10px;
}

#side-menu :deep(.vsm--link:hover) {
    background: var(--ks-background-card);
}

/* Remove gray icon background and size icons similar to text */
#side-menu :deep(.vsm--icon) {
    background: transparent !important;
    width: auto;
    height: auto;
    display: inline-flex;
    align-items: center;
    justify-content: center;
}

#side-menu :deep(.vsm--icon > svg),
#side-menu :deep(.vsm--icon > span > svg) {
    width: 18px;
    height: 18px;
}


/* opener moved to TopNavBar */
</style>