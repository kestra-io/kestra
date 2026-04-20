<template>
    <div :style="{'--top-navbar-height': navbarHeight ? `${navbarHeight}px` : undefined}">
        <TopNavBar ref="navEl" :title="routeInfo.title">
            <template v-if="isManageTab" #additional-right>
                <ul>
                    <li>
                        <el-button :icon="Download" @click="exportTriggers()">
                            {{ $t("export_csv") }}
                        </el-button>
                    </li>
                </ul>
            </template>
        </TopNavBar>
        <Tabs :tabs="tabs" routeName="admin/triggers" />
    </div>
</template>

<script setup lang="ts">
    import {computed, markRaw, onBeforeUnmount, onMounted, ref, useTemplateRef, watch} from "vue";
    import {useI18n} from "vue-i18n";
    import {useRoute, useRouter} from "vue-router";
    import Download from "vue-material-design-icons/Download.vue";

    import TopNavBar from "../layout/TopNavBar.vue";
    import Tabs from "../Tabs.vue";
    import TriggersGrid from "./TriggersGrid.vue";
    import TriggersManage from "./TriggersManage.vue";

    import useRouteContext from "../../composables/useRouteContext";
    import {useTriggerStore} from "../../stores/trigger";

    const {t} = useI18n({useScope: "global"});
    const route = useRoute();
    const router = useRouter();
    const triggerStore = useTriggerStore();

    const VALID_TABS = ["add", "manage"] as const;
    const DEFAULT_TAB = "add";

    const routeInfo = computed(() => ({
        title: t("triggers"),
    }));

    useRouteContext(routeInfo);

    const tabs = computed(() => [
        {name: "add", title: t("triggers.tabs.add"), component: markRaw(TriggersGrid)},
        {name: "manage", title: t("triggers.tabs.manage"), component: markRaw(TriggersManage)},
    ]);

    const isManageTab = computed(() => route.params.tab === "manage");

    watch(() => route.params.tab, (tab) => {
        if (tab !== undefined && !VALID_TABS.includes(tab as any)) {
            router.replace({name: "admin/triggers", params: {...route.params, tab: DEFAULT_TAB}});
        }
    }, {immediate: true});

    async function exportTriggers() {
        await triggerStore.exportTriggersAsCSV(route.query);
    }

    const navEl = useTemplateRef<{$el?: HTMLElement} | HTMLElement>("navEl");
    const navbarHeight = ref<number>();
    let observer: ResizeObserver | null = null;

    function measure(el: HTMLElement) {
        const height = Math.round(el.getBoundingClientRect().height);
        if (height > 0) {
            navbarHeight.value = height;
        }
    }

    onMounted(() => {
        const instance = navEl.value;
        if (!instance) return;

        const el = ("$el" in instance ? instance.$el : instance) as HTMLElement;
        if (!el) return;

        measure(el);
        observer = new ResizeObserver(() => measure(el));
        observer.observe(el);
    });

    onBeforeUnmount(() => observer?.disconnect());
</script>

