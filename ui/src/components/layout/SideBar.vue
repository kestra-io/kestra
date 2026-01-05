<template>
    <SidebarMenu
        ref="sideBarRef"
        id="side-menu"
        :menu
        @update:collapsed="onToggleCollapse"
        width="280px"
        :collapsed="collapsed"
        linkComponentName="LeftMenuLink"
        hideToggle
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
</template>

<script setup lang="ts">
    import {onUpdated, computed, h, watch} from "vue";
    import {useI18n} from "vue-i18n";
    import {useRoute} from "vue-router";
    import {useMediaQuery} from "@vueuse/core";
    import {SidebarMenu} from "vue-sidebar-menu";
    import StarOutline from "vue-material-design-icons/StarOutline.vue";

    import Environment from "./Environment.vue";
    import BookmarkLinkList from "./BookmarkLinkList.vue";
    import {useBookmarksStore} from "../../stores/bookmarks";
    import type {MenuItem} from "override/components/useLeftMenu";
    import {useLayoutStore} from "../../stores/layout";
    import SidebarToggleButton from "./SidebarToggleButton.vue";


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

    function onToggleCollapse(folded: boolean) {
        collapsed.value = folded;
        layoutStore.setSideMenuCollapsed(folded);
        $emit("menu-collapse", folded);

        return folded;
    }

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
</style>
