<template>
    <TopNavBar :title="routeInfo.title" class="triggers-top-nav">
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
</template>

<script setup lang="ts">
    import {computed, markRaw, onBeforeUnmount, onMounted, watch} from "vue";
    import {useI18n} from "vue-i18n";
    import {useRoute} from "vue-router";
    import Download from "vue-material-design-icons/Download.vue";

    import TopNavBar from "../layout/TopNavBar.vue";
    import Tabs from "../Tabs.vue";
    import TriggersAdd from "./TriggersAdd.vue";
    import TriggersManage from "./TriggersManage.vue";
    import useRouteContext from "../../composables/useRouteContext";
    import {useTriggerStore} from "../../stores/trigger";

    const {t} = useI18n({useScope: "global"});
    const route = useRoute();
    const triggerStore = useTriggerStore();

    const routeInfo = computed(() => ({
        title: t("triggers"),
    }));

    useRouteContext(routeInfo);

    const tabs = computed(() => [
        {name: "add", title: t("triggers.tabs.add"), component: markRaw(TriggersAdd)},
        {name: "manage", title: t("triggers.tabs.manage"), component: markRaw(TriggersManage)},
    ]);

    const isManageTab = computed(() => route.params.tab === "manage");

    async function exportTriggers() {
        await triggerStore.exportTriggersAsCSV(route.query);
    }

    // The Tabs component sticks at `top: var(--top-navbar-height)` which is
    // a fixed 79px globally. Pages like Flow detail have a breadcrumb and
    // action buttons that push the nav past that, so the sticky offset lands
    // inside the nav and tabs appear flush. This page has only a title and
    // a conditional export button, so the nav renders shorter than 79px,
    // leaving a visible gap between nav and tabs. Measure the nav on mount
    // (and on size changes / tab changes) and set the variable to match.
    const NAVBAR_HEIGHT_VAR = "--top-navbar-height";
    let previous = "";
    let observer: ResizeObserver | null = null;

    function applyMeasured() {
        const nav = document.querySelector(".triggers-top-nav") as HTMLElement | null;
        if (!nav) return;
        const height = Math.round(nav.getBoundingClientRect().height);
        if (height > 0) {
            document.documentElement.style.setProperty(NAVBAR_HEIGHT_VAR, `${height}px`);
        }
    }

    onMounted(() => {
        previous = document.documentElement.style.getPropertyValue(NAVBAR_HEIGHT_VAR);
        applyMeasured();
        const nav = document.querySelector(".triggers-top-nav");
        if (nav) {
            observer = new ResizeObserver(applyMeasured);
            observer.observe(nav);
        }
    });

    watch(isManageTab, () => applyMeasured());

    onBeforeUnmount(() => {
        observer?.disconnect();
        if (previous) {
            document.documentElement.style.setProperty(NAVBAR_HEIGHT_VAR, previous);
        } else {
            document.documentElement.style.removeProperty(NAVBAR_HEIGHT_VAR);
        }
    });
</script>
