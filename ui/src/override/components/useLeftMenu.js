import {
    watch,
    onUpdated,
    onMounted,
    ref,
    computed,
    shallowRef, h
} from "vue";
import {useRoute} from "vue-router";
import {useI18n} from "vue-i18n";
import {useStore} from "vuex";
import StarOutline from "vue-material-design-icons/StarOutline.vue";


export function useLeftMenu($emit, generatedMenu) {
    const $route = useRoute()
    const {locale, t} = useI18n()
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

    watch(locale, () => {
        localMenu.value = disabledCurrentRoute(generatedMenu.value);
    }, {deep: true});

    const menu = computed(() => {
        return [
            ...(store.state.starred.pages?.length ? [{
                title: t("starred"),
                icon: {
                    element: shallowRef(StarOutline),
                    class: "menu-icon",
                },
                child: store.state.starred.pages.map(p => {
                    return {
                        component: () => h("a", {href: p.path, style: "padding: 0 4px;"}, p.label),
                    }
                })
            }] : []),
            ...disabledCurrentRoute(generatedMenu.value)
        ];
    });

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

    return {
        collapsed,
        localMenu,
        onToggleCollapse,
        sideBarRef,
        menu
    }
}