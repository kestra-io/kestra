<template>
    <TopNavBar :title="details.title" :breadcrumb="details.breadcrumb">
        <template #additional-right>
            <Actions />
        </template>
    </TopNavBar>
    <Tabs :tabs :route-name="namespace ? 'namespaces/update' : ''" :namespace />
</template>

<script setup lang="ts">
    import {ref, computed, Ref, watch, onMounted} from "vue";
    import {useRoute} from "vue-router";
    import {useTabs} from "override/components/namespaces/useTabs";
    import {useHelpers} from "./utils/useHelpers";
    import useRouteContext from "../../mixins/useRouteContext";
    import {useNamespacesStore} from "override/stores/namespaces";
    import TopNavBar from "../layout/TopNavBar.vue";
    import Actions from "override/components/namespaces/Actions.vue";
    import Tabs from "../Tabs.vue";

    const {tabs} = useTabs();
    const {details} = useHelpers();

    const route = useRoute();

    const context = ref({title: details.value.title});
    useRouteContext(context);

    const namespace = computed(() => route.params?.id) as Ref<string>;

    const namespacesStore = useNamespacesStore();

    watch(namespace, (newID) => {
        if (newID) {
            namespacesStore.load(newID);
        }
    });

    onMounted(() => {
        if (namespace.value) {
            namespacesStore.load(namespace.value);
        }
    });
</script>
