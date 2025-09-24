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
    import {useRoute} from "vue-router";
    import {useTabs} from "override/components/namespaces/useTabs";
    import {useHelpers} from "./utils/useHelpers";
    import useRouteContext from "../../composables/useRouteContext";
    import {useNamespacesStore} from "override/stores/namespaces";
    import TopNavBar from "../layout/TopNavBar.vue";
    import Actions from "override/components/namespaces/Actions.vue";
    // @ts-expect-error no types in Tabs yet
    import Tabs from "../Tabs.vue";

    const {tabs} = useTabs();
    const {details} = useHelpers();

    const route = useRoute();

    const context = computed(() => details.value.title);
    useRouteContext(context);

    const namespace = computed(() => route.params?.id) as Ref<string>;

    const namespacesStore = useNamespacesStore();

    watch(namespace, (newID) => {
        if (newID) {
            namespacesStore.load(newID);
        }
    });

    onMounted(() => {
        const main = document.querySelector("main");
        if(main) main.scrollTop = 0;

        if (namespace.value) {
            namespacesStore.load(namespace.value);
        }
    });
</script>
