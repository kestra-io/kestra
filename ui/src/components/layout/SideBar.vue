<template>
    <SidebarMenu
        ref="sideBarRef"
        id="side-menu"
        :menu
        @update:collapsed="onToggleCollapse"
        width="268px"
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
    import {
        onUpdated,
        ref,
        computed,
        h,
        onMounted,
        nextTick,
        onBeforeUnmount
    } from "vue";
    import {useI18n} from "vue-i18n";
    import {useRoute} from "vue-router";
    import {SidebarMenu} from "vue-sidebar-menu";
    import StarOutline from "vue-material-design-icons/StarOutline.vue";
    import BookmarkLinkList from "./BookmarkLinkList.vue";
    import {useBookmarksStore} from "../../stores/bookmarks";
    import type {MenuItem} from "override/components/useLeftMenu";
    import {useLayoutStore} from "../../stores/layout";
    import SidebarToggleButton from "./SidebarToggleButton.vue";

    const props = withDefaults(
        defineProps<{
            menu: MenuItem[];
            showLink: boolean;
        }>(),
        {
            showLink: true
        }
    );

    const $emit = defineEmits(["menu-collapse"]);
    const $route = useRoute();
    const {t} = useI18n({useScope: "global"});
    const layoutStore = useLayoutStore();

    const collapsed = ref(localStorage.getItem("menuCollapsed") === "true");

    function onToggleCollapse(folded: boolean) {
        collapsed.value = folded;
        layoutStore.setSideMenuCollapsed(folded);
        $emit("menu-collapse", folded);
        localStorage.setItem("menuCollapsed", String(folded));
        return folded;
    }

    function disabledCurrentRoute(items: MenuItem[]) {
        return items.map(r => {
            if (r.href?.path === $route.path) {
                r.disabled = true;
            }

            if (
                r.href !== "/" &&
                ($route.path.startsWith(r.href) || r.routes?.includes($route.name))
            ) {
                r.class = "vsm--link_active";
            }

            if (
                r.child &&
                r.child.some(
                    c => $route.path.startsWith(c.href) || c.routes?.includes($route.name)
                )
            ) {
                r.class = "vsm--link_active";
                r.child = disabledCurrentRoute(r.child);
            }

            return r;
        });
    }

    function expandParentIfNeeded() {
        document
            .querySelectorAll(
                ".vsm--link.vsm--link_level-1.vsm--link_active:not(.vsm--link_open)[aria-haspopup]"
            )
            .forEach(e => (e as HTMLElement).click());
    }

    onUpdated(() => expandParentIfNeeded());

    const bookmarksStore = useBookmarksStore();

    const menu = computed(() => {
        return [
            ...(bookmarksStore.pages?.length
                ? [
                    {
                        title: t("bookmark"),
                        icon: {element: StarOutline, class: "menu-icon"},
                        child: [
                            {
                                component: () =>
                                    h(BookmarkLinkList, {pages: bookmarksStore.pages})
                            }
                        ]
                    }
                ]
                : []),
            ...(props.menu ? disabledCurrentRoute(props.menu) : [])
        ];
    });

    function attachLinkListeners() {
        const sidebar = document.getElementById("side-menu");
        const links = sidebar?.querySelectorAll("a");
        links?.forEach(link => link.addEventListener("click", handleLinkClick));
    }

    function removeLinkListeners() {
        const sidebar = document.getElementById("side-menu");
        const links = sidebar?.querySelectorAll("a");
        links?.forEach(link => link.removeEventListener("click", handleLinkClick));
    }

    function handleLinkClick() {
        if (window.innerWidth < 768) {
            collapsed.value = true;
            layoutStore.setSideMenuCollapsed(true);
            localStorage.setItem("menuCollapsed", "true");
        }
    }

    let observer: MutationObserver | null = null;

    onMounted(async () => {
        await nextTick();
        attachLinkListeners();

        const sidebar = document.getElementById("side-menu");
        if (sidebar) {
            observer = new MutationObserver(() => {
                removeLinkListeners();
                attachLinkListeners();
            });
            observer.observe(sidebar, {childList: true, subtree: true});
        }

        const savedState = localStorage.getItem("menuCollapsed");
        collapsed.value = savedState === "true";
        layoutStore.setSideMenuCollapsed(collapsed.value);
    });

    onBeforeUnmount(() => {
        removeLinkListeners();
        if (observer) observer.disconnect();
    });
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
