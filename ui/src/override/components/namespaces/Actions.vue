<template>
    <Dashboards
        v-if="tab === 'overview' && ALLOWED_CREATION_ROUTES.includes(routeFamily(route.name))"
        @dashboard="onSelectDashboard"
    />

    <Action
        v-if="tab === 'flows'"
        :label="$t('create_flow')"
        :to="createFlowTarget"
    />

    <Action
        v-if="tab === 'kv'"
        :label="$t('kv.inherited')"
        :icon="FamilyTree"
        type="default"
        @click="namespacesStore.inheritedKVModalVisible = true"
    />

    <Action
        v-if="tab === 'kv'"
        :label="$t('kv.add')"
        @click="namespacesStore.addKvModalVisible = true"
    />
</template>

<script setup lang="ts">
    import {computed, Ref} from "vue"
    import {useRoute, useRouter} from "vue-router"
    import {useNamespacesStore} from "override/stores/namespaces"
    import {useMiscStore} from "override/stores/misc"
    import Action from "../../../components/namespaces/components/buttons/Action.vue"
    import Dashboards from "override/components/dashboard/Selector.vue"
    import {ALLOWED_CREATION_ROUTES} from "../../../components/dashboard/composables/useDashboards"
    import {useActiveTab} from "../../../composables/useActiveTab"
    import {routeFamily} from "../../../utils/routeFamily"
    import {NAMESPACE_PARENT_ROUTE} from "../../../utils/namespaceTabRoutes"
    import FamilyTree from "vue-material-design-icons/FamilyTree.vue"

    const route = useRoute()
    const router = useRouter()
    const namespacesStore = useNamespacesStore()
    const miscStore = useMiscStore()

    const onSelectDashboard = (value: any) => {
        router.replace({
            params: {...route.params, dashboard: value},
        })
    }

    const tab = useActiveTab()
    const namespace = computed(() => route.params?.id) as Ref<string>

    const systemNamespace = computed(() => miscStore.configs?.systemNamespace ?? "system")

    /*
     * The system namespace has a guided recipe builder of its own, so sending its users
     * to the raw YAML editor would skip it. The tab is a child route resolved from
     * `params.tab`, so it has to be targeted by name rather than a `tab` query param.
     */
    const createFlowTarget = computed(() => namespace.value === systemNamespace.value
        ? {name: `${NAMESPACE_PARENT_ROUTE}/blueprints`, params: {tenant: route.params.tenant, id: namespace.value}}
        : {name: "flows/create", query: {namespace: namespace.value}})
</script>
