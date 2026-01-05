<template>
    <Dashboards
        v-if="tab === 'overview' && ALLOWED_CREATION_ROUTES.includes(String(route.name))"
        @dashboard="onSelectDashboard"
    />
    <Action
        v-if="deleted"
        type="default"
        :icon="BackupRestore"
        :label="$t('restore')"
        @click="restoreFlow"
    />
    <Action
        v-if="canEdit && !deleted && tab !== 'edit'"
        type="default"
        :icon="Pencil"
        :label="$t('edit flow')"
        @click="editFlow"
    />
    <TriggerFlow
        v-if="flow && !deleted && tab !== 'apps' && canExecute"
        type="primary"
        :flowId="flow?.id"
        :namespace="flow?.namespace"
        :flowSource="flow?.source"
    />
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import {useRoute, useRouter} from "vue-router";
    import {useFlowStore} from "../../../stores/flow";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import Pencil from "vue-material-design-icons/Pencil.vue";
    import BackupRestore from "vue-material-design-icons/BackupRestore.vue";
    import Action from "../../../components/namespaces/components/buttons/Action.vue";
    // @ts-expect-error does not have types
    import TriggerFlow from "../../../components/flows/TriggerFlow.vue";
    import Dashboards from "../../../components/dashboard/components/selector/Selector.vue";
    import {ALLOWED_CREATION_ROUTES} from "../../../components/dashboard/composables/useDashboards";
    import permission from "../../../models/permission";
    import action from "../../../models/action";
    import {useAuthStore} from "override/stores/auth";
    import {useUnsavedChangesStore} from "../../../stores/unsavedChanges";

    const onSelectDashboard = (value: any) => {
        router.replace({
            params: {...route.params, dashboard: value}
        });
    };

    const unsavedChangesStore = useUnsavedChangesStore();
    const flowStore = useFlowStore();
    const router = useRouter();
    const route = useRoute();

    const flow = computed(() => flowStore.flow);
    const deleted = computed(() => flow.value?.deleted || false);
    const tab = computed(() => route.params?.tab as string);

    const authStore = useAuthStore();

    const canExecute = computed(() =>
        flow.value && authStore.user?.isAllowed(permission.EXECUTION, action.CREATE, flow.value.namespace)
    );

    const canEdit = computed(() =>
        authStore.user?.isAllowed(permission.FLOW, action.UPDATE, flow.value?.namespace)
    );

    const editFlow = () => {
        router.push({
            name: "flows/update",
            params: {
                namespace: flow.value?.namespace,
                id: flow.value?.id,
                tab: "edit",
                tenant: route.params.tenant,
            },
        });
    };

    const restoreFlow = () => {
        flowStore.createFlow({
            flow: YAML_UTILS.deleteMetadata(flow.value?.source, "deleted"),
        }).then(() => {
            unsavedChangesStore.unsavedChange = false;
            router.go(0);
        });
    };
</script>