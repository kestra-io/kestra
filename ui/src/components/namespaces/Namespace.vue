<template>
    <TopNavBar :title="details.title" :breadcrumb="details.breadcrumb">
        <template #additional-right>
            <Actions />
        </template>
    </TopNavBar>
    <Tabs :tabs :routeName="namespace ? 'namespaces/update' : ''" :namespace />
</template>

<script setup lang="ts">
    import {computed, Ref, watch, onMounted} from "vue";
    import {useRoute, useRouter} from "vue-router";
    import {useTabs} from "override/components/namespaces/useTabs";
    import {useHelpers} from "./utils/useHelpers";
    import useRouteContext from "../../composables/useRouteContext";
    import {useNamespacesStore} from "override/stores/namespaces";
    import TopNavBar from "../layout/TopNavBar.vue";
    import Actions from "override/components/namespaces/Actions.vue";
    import {useMiscStore} from "override/stores/misc";
    import Tabs from "../Tabs.vue";

    const {tabs} = useTabs();
    const {details} = useHelpers();

    const route = useRoute();
    const router = useRouter();

    const context = computed(() => ({title:details.value.title}));
    useRouteContext(context);

    const namespace = computed(() => route.params?.id) as Ref<string>;

    const miscStore = useMiscStore();
    const namespacesStore = useNamespacesStore();

    watch(namespace, (newID) => {
        if (newID) {
            namespacesStore.load(newID);
        }
    });

    watch(() => route.params.tab, (newTab) => {
        if (newTab === "overview" || newTab === "executions") {
            const dateTimeKeys = ["startDate", "endDate", "timeRange"];

            if (!Object.keys(route.query).some((key) => dateTimeKeys.some((dateTimeKey) => key.includes(dateTimeKey)))) {
                const DEFAULT_DURATION = miscStore.configs?.chartDefaultDuration ?? "P30D";
                const newQuery = {...route.query, "filters[timeRange][EQUALS]": DEFAULT_DURATION};
                router.replace({name: route.name, params: route.params, query: newQuery});
            }
        }
    }, {immediate: true});

    onMounted(() => {
        const main = document.querySelector("main");
        if(main) main.scrollTop = 0;

        if (namespace.value) {
            namespacesStore.load(namespace.value);
        }
    });
</script>
