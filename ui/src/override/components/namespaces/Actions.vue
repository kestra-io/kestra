<template>
    <Dashboards
        v-if="tab === 'overview' && ALLOWED_CREATION_ROUTES.includes(String(route.name))"
        @dashboard="onSelectDashboard"
    />

    <Action
        v-if="tab === 'flows'"
        :label="$t('create_flow')"
        :to="{name: 'flows/create', query: {namespace}}"
    />

    <Action
        v-if="tab === 'kv'"
        :label="$t('kv.inherited')"
        :icon="FamilyTree"
        @click="namespacesStore.inheritedKVModalVisible = true"
    />

    <Action
        v-if="tab === 'kv'"
        :label="$t('kv.add')"
        @click="namespacesStore.addKvModalVisible = true"
    />
</template>

<script setup lang="ts">
    import {computed, Ref} from "vue";
    import {useRoute, useRouter} from "vue-router";
    import {useNamespacesStore} from "override/stores/namespaces";
    import Action from "../../../components/namespaces/components/buttons/Action.vue";
    import Dashboards from "../../../components/dashboard/components/selector/Selector.vue";
    import {ALLOWED_CREATION_ROUTES} from "../../../components/dashboard/composables/useDashboards";
    import FamilyTree from "vue-material-design-icons/FamilyTree.vue";

    const route = useRoute();
    const router = useRouter();
    const namespacesStore = useNamespacesStore();

    const onSelectDashboard = (value: any) => {
        router.replace({
            params: {...route.params, dashboard: value}
        });
    };

    const tab = computed(() => route.params?.tab);
    const namespace = computed(() => route.params?.id) as Ref<string>;
</script>
