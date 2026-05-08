<template>
    <NavBarActions :loading="tab === 'logs' && logsStore.logs === undefined">
        <Dashboards
            v-if="showDashboards"
            @dashboard="onSelectDashboard"
        />
        <NavBarAction
            v-if="deleted"
            :icon="BackupRestore"
            :label="t('restore')"
            @click="restoreFlow"
        />
        <NavBarAction
            v-if="canEdit && !deleted && tab !== 'edit'"
            :icon="Pencil"
            :label="t('edit flow')"
            @click="editFlow"
        />
        <NavBarAction
            v-if="tab === 'logs' && hasLogs"
            :icon="TrashCan"
            :label="t('delete logs')"
            @click="deleteLogs"
        />

        <template #primary>
            <TriggerFlow
                v-if="flow && !deleted && tab !== 'apps' && canExecute"
                type="primary"
                :flowId="flow?.id"
                :namespace="flow?.namespace"
                :flowSource="flow?.source"
            />
        </template>
    </NavBarActions>
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import {useI18n} from "vue-i18n";
    import {useRoute, useRouter} from "vue-router";
    import {useFlowStore} from "../../../stores/flow";
    import {flowYamlUtils as YAML_UTILS} from "@kestra-io/design-system";
    import Pencil from "vue-material-design-icons/Pencil.vue";
    import BackupRestore from "vue-material-design-icons/BackupRestore.vue";
    import TrashCan from "vue-material-design-icons/TrashCan.vue";
    import NavBarActions from "../../../components/layout/NavBarActions.vue";
    import NavBarAction from "../../../components/layout/NavBarAction.vue";
    // @ts-expect-error does not have types
    import TriggerFlow from "../../../components/flows/TriggerFlow.vue";
    import Dashboards from "../../../components/dashboard/components/selector/Selector.vue";
    import {ALLOWED_CREATION_ROUTES} from "../../../components/dashboard/composables/useDashboards";
    import resource from "../../../models/resource";
    import action from "../../../models/action";
    import {useAuthStore} from "override/stores/auth";
    import {useUnsavedChangesStore} from "../../../stores/unsavedChanges";
    import {useDashboardStore} from "../../../stores/dashboard.ts";
    import {useLogsStore} from "../../../stores/logs";
    import {useToast} from "../../../utils/toast";

    const {t} = useI18n({useScope: "global"});

    const unsavedChangesStore = useUnsavedChangesStore();
    const flowStore = useFlowStore();
    const logsStore = useLogsStore();
    const router = useRouter();
    const route = useRoute();
    const toast = useToast();

    const flow = computed(() => flowStore.flow);
    const deleted = computed(() => flow.value?.deleted || false);
    const tab = computed(() => route.params?.tab as string);

    const authStore = useAuthStore();
    const dashboardStore = useDashboardStore();

    const onSelectDashboard = (value: any) => {
        const key = dashboardStore.getUserDashboardStorageKey(route);
        localStorage.setItem(key, value);
        router.replace({
            params: {...route.params, dashboard: value}
        });
    };

    const showDashboards = computed(() =>
        tab.value === "overview" && ALLOWED_CREATION_ROUTES.includes(String(route.name))
    );

    const canExecute = computed(() =>
        flow.value && authStore.user?.isAllowed(resource.EXECUTION, action.CREATE, flow.value.namespace)
    );

    const canEdit = computed(() =>
        authStore.user?.isAllowed(resource.FLOW, action.UPDATE, flow.value?.namespace)
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

    const hasLogs = computed(() =>
        logsStore.logs !== undefined && logsStore.logs.length > 0
    );

    const deleteLogs = () => {
        toast.confirm(
            t("delete_all_logs"),
            async () => {
                if (!flow.value) return;
                return logsStore.deleteLogs({
                    namespace: flow.value.namespace,
                    flowId: flow.value.id,
                });
            },
        );
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
