<template>
    <Navbar :title="details.title" :breadcrumb="details.breadcrumb">
        <template #additional-right>
            <router-link
                v-if="tab === 'flows'"
                :to="{name: 'flows/create', query: {namespace}}"
            >
                <Create :label="$t('create_flow')" />
            </router-link>

            <Create
                v-if="tab === 'kv'"
                :label="$t('kv.add')"
                @click="store.commit('namespace/changeKVModalVisibility', true)"
            />
        </template>
    </Navbar>
    <Tabs :tabs :route-name="namespace ? 'namespaces/update' : ''" :namespace />
</template>

<script setup lang="ts">
    import {ref, computed, Ref, onMounted} from "vue";

    import {useTabs} from "override/components/namespaces/useTabs";
    import {useRoute} from "vue-router";
    import useRouteContext from "../../mixins/useRouteContext";
    import {useStore} from "vuex";

    import Navbar from "../layout/TopNavBar.vue";
    import Create from "./buttons/Create.vue";
    import Tabs from "../Tabs.vue";

    const {tabs, details} = useTabs();

    const route = useRoute();

    const context = ref({title: details.title});
    useRouteContext(context);

    const namespace = computed(() => route.params?.id) as Ref<string>;
    const tab = computed(() => route.params?.tab);

    const store = useStore();
    onMounted(() => {
        if (namespace.value) {
            store.dispatch("namespace/load", namespace.value);
        }
    });
</script>
