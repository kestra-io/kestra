<template>
    <TopNavBar :title="routeInfo.title" class="triggers-top-nav" />
    <Tabs :tabs="tabs" routeName="admin/triggers" class="triggers-tab-bar" />
</template>

<script setup lang="ts">
    import {computed, markRaw} from "vue";
    import {useI18n} from "vue-i18n";

    import TopNavBar from "../layout/TopNavBar.vue";
    import Tabs from "../Tabs.vue";
    import TriggersAdd from "./TriggersAdd.vue";
    import TriggersManage from "./TriggersManage.vue";
    import useRouteContext from "../../composables/useRouteContext";

    const {t} = useI18n({useScope: "global"});

    const routeInfo = computed(() => ({
        title: t("triggers"),
    }));

    useRouteContext(routeInfo);

    const tabs = computed(() => [
        {name: "add", title: t("triggers.tabs.add"), component: markRaw(TriggersAdd)},
        {name: "manage", title: t("triggers.tabs.manage"), component: markRaw(TriggersManage)},
    ]);
</script>

<style scoped lang="scss">
    // The Triggers page has no breadcrumb and no description, so TopNavBar's
    // default 79px height leaves visible empty space below the title before
    // the tab bar (which sticks at --top-navbar-height). Shrink the nav to
    // its content and retarget the tab bar's sticky offset to match, so the
    // Add/Manage tabs sit flush under the title like on other pages
    // (e.g. Namespaces) that do fill the nav vertically.
    .triggers-top-nav {
        :deep(nav) {
            padding-top: 0.75rem;
            padding-bottom: 0.75rem;
        }
        :deep(.description) {
            display: none;
        }
        :deep(h1) {
            line-height: 1.3;
        }
    }

    // Nav is ~50px after the padding trim above; stick the tab bar right
    // below it instead of at the default 79px offset.
    .triggers-tab-bar :deep(.el-tabs.top) {
        top: 50px;
    }
</style>
