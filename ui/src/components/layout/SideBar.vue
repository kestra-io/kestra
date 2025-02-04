<template>
    <sidebar-menu
        ref="sideBarRef"
        data-component="FILENAME_PLACEHOLDER"
        id="side-menu"
        :menu="localMenu"
        @update:collapsed="onToggleCollapse"
        width="268px"
        :collapsed="collapsed"
        link-component-name="LeftMenuLink"
        hide-toggle
    >
        <template #header>
            <el-button @click="collapsed = onToggleCollapse(!collapsed)" class="collapseButton" :size="collapsed ? 'small':undefined">
                <chevron-right v-if="collapsed" />
                <chevron-left v-else />
            </el-button>
            <div class="logo">
                <router-link :to="{name: 'home'}">
                    <span class="img" />
                </router-link>
            </div>
            <Environment />
        </template>

        <template #footer>
            <slot name="footer" />
        </template>
    </sidebar-menu>
</template>

<script setup>
    import {
        watch,
        onUpdated,
        onMounted,
        ref,
        computed,
        shallowRef, h
    } from "vue";
    import {useStore} from "vuex";
    import {useI18n} from "vue-i18n";
    import {useRoute} from "vue-router";

    import {SidebarMenu} from "vue-sidebar-menu";

    import ChevronLeft from "vue-material-design-icons/ChevronLeft.vue";
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue";
    import StarOutline from "vue-material-design-icons/StarOutline.vue";

    import Environment from "./Environment.vue";
    import BookmarkLinkList from "./BookmarkLinkList.vue";


    const props = defineProps({
        generateMenu: {
            type: Function,
            required: true
        }
    })

    const $emit = defineEmits(["menu-collapse"])

    const $route = useRoute()
    const {locale, t} = useI18n({useScope: "global"});
    const store = useStore()

    function flattenMenu(menu) {
        return menu.reduce((acc, item) => {
            if (item.child) {
                acc.push(...flattenMenu(item.child));
            }

            acc.push(item);
            return acc;
        }, []);
    }

    function onToggleCollapse(folded) {
        collapsed.value = folded;
        localStorage.setItem("menuCollapsed", folded ? "true" : "false");
        $emit("menu-collapse", folded);

        return folded;
    }

    function disabledCurrentRoute(items) {
        return items
            .map(r => {
                if (r.href === $route.path) {
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
            e.click()
        });
    }

    onUpdated(() => {
        // Required here because in mounted() the menu is not yet rendered
        expandParentIfNeeded();
    })

    const menu = computed(() => {
        return [
            ...(store.state.bookmarks.pages?.length ? [{
                title: t("bookmark"),
                icon: {
                    element: shallowRef(StarOutline),
                    class: "menu-icon",
                },
                child: [{

                    component: () => h(BookmarkLinkList, {pages: store.state.bookmarks.pages}),
                }]
            }] : []),
            ...disabledCurrentRoute(props.generateMenu())
        ];
    });


    watch(locale, () => {
        localMenu.value = menu.value;
    }, {deep: true});

    /**
     * @type {import("vue").Ref<typeof import('vue-sidebar-menu').SidebarMenu>}
     */
    const sideBarRef = ref(null);

    watch(menu, (newVal, oldVal) => {
              // Check if the active menu item has changed, if yes then update the menu
              if (JSON.stringify(flattenMenu(newVal).map(e => e.class?.includes("vsm--link_active") ?? false)) !==
                  JSON.stringify(flattenMenu(oldVal).map(e => e.class?.includes("vsm--link_active") ?? false))) {
                  localMenu.value = newVal;
                  sideBarRef.value?.$el.querySelectorAll(".vsm--item span").forEach(e => {
                      //empty icon name on mouseover
                      e.setAttribute("title", "")
                  });
              }
          },
          {
              flush: "post",
              deep: true
          });

    const collapsed = ref(localStorage.getItem("menuCollapsed") === "true")
    const localMenu = ref([])


    onMounted(() => {
        localMenu.value = menu.value;
    })
</script>

<style lang="scss">
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

        .vsm_collapsed & {
            top: .5rem;
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

            a {
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

        .vsm--icon {
            transition: left 0.2s ease;
            font-size: 1.5em;
            background-color: transparent !important;
            padding-bottom: 15px;
            width: 30px !important;

            svg {
                position: relative;
                margin-top: 13px;
            }
        }

        .vsm--item {
            padding: 0 30px;
            transition: padding 0.2s ease;
        }

        .vsm--child {
            .vsm--item {
                padding: 0;
            }
        }

        .vsm--link {
            padding: 0.3rem 0.5rem;
            margin-bottom: 0.3rem;
            border-radius: .25rem;
            transition: padding 0.2s ease;
            color: var(--ks-content-primary);
            box-shadow: none;

            &_active, body &_active:hover, &.vsm--link_open, &.vsm--link_open:hover {
                background-color: var(--ks-button-background-primary);
                color: var(--ks-button-content-primary);
                font-weight: normal;
            }

            &_disabled {
                pointer-events: auto;
            }

            &:hover, body &_hover {
                background-color: var(--ks-button-background-secondary-hover);
            }

            .el-tooltip__trigger {
                display: flex;
            }
        }

        .vsm--title span:first-child{
            flex-grow: 0;
        }

        .vsm--arrow_default{
            width: 8px;
            &:before{
                border-left-width: 1px;
                border-bottom-width: 1px;
                height: 4px;
                width: 4px;
                top: 3px;
            }
        }



        a.vsm--link_active[href="#"] {
            cursor: initial !important;
        }

        .vsm--dropdown {
            background-color: var(--ks-background-left-menu);
            border-radius: 4px;
            margin-bottom: .5rem;

            .vsm--title {
                top: 3px;
            }
        }

        .vsm--mobile-bg {
            border-radius: 0 var(--bs-border-radius) var(--bs-border-radius) 0;
        }

        &.vsm_collapsed {
            .logo {
                a {
                    left: 8px;

                    span.img {
                        background-size: 207px 55px;
                    }
                }
            }

            .vsm--link {
                padding-left: 13px;
            }

            .vsm--item {
                padding: 0 5px;
            }

            .el-button {
                margin-right: 0;
            }
        }

        .el-tooltip__trigger .lock-icon.material-design-icon > .material-design-icon__svg {
            bottom: 0 !important;
            margin-left: 5px;
        }
    }

</style>
