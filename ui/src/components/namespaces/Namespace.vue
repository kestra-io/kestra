<template>
    <Navbar :title="details.title" :breadcrumb="details.breadcrumb">
        <template #additional-right>
            <Actions />
        </template>
    </Navbar>
    <Tabs :tabs :route-name="namespace ? 'namespaces/update' : ''" :namespace />
</template>

<script setup lang="ts">
    import {ref, computed, Ref, onMounted} from "vue";

    import {useTabs} from "override/components/namespaces/useTabs";
    import {useHelpers} from "./utils/useHelpers";
    import {useRoute} from "vue-router";
    import useRouteContext from "../../mixins/useRouteContext";
    import {useStore} from "vuex";

    import Navbar from "../layout/TopNavBar.vue";
    import Actions from "override/components/namespaces/Actions.vue";
    import Tabs from "../Tabs.vue";

    const {tabs} = useTabs();
    const {details} = useHelpers();

    const route = useRoute();

    const context = ref({title: details.title});
    useRouteContext(context);

    const namespace = computed(() => route.params?.id) as Ref<string>;

    const store = useStore();
    onMounted(() => {
        if (namespace.value) {
            store.dispatch("namespace/load", namespace.value);
        }
    });
</script>
