<template>
    <FlowPlaygroundToggle
        v-if="isEditTab && isPlaygroundAllowed && editorIsAllowedEdit && !deleted"
    />
    <template v-if="showDashboards">
        <Dashboards @dashboard="onSelectDashboard" />
        <KsDivider direction="vertical" />
    </template>
    <NavBarActions>
        <NavBarAction
            v-if="isEditTab && canEdit && !deleted && !flowStore.isCreating"
            :icon="ContentCopy"
            :label="$t('copy')"
            @click="editorCopyFlow"
        />
        <NavBarAction
            v-if="isEditTab && editorIsAllowedEdit && !deleted"
            :icon="Download"
            :label="$t('flow_export')"
            @click="editorExportYaml"
        />
        <NavBarAction
            v-if="isEditTab && canDelete && !deleted && !flowStore.isCreating"
            :icon="Delete"
            :label="$t('delete')"
            @click="editorDeleteFlow"
        />
        <NavBarAction
            v-if="tab === 'logs' && hasLogs"
            :icon="Delete"
            :label="$t('delete logs')"
            @click="deleteLogs"
        />

        <template #secondary>
            <NavBarAction
                v-if="(isEditTab && editorIsAllowedEdit && !deleted) && flowStore.isCreating"
                :icon="ContentSave"
                :label="$t('save_as_draft')"
                :disabled="editorIsReadOnly"
                data-test="flow-create"
                @click="editorCreateDraft"
            />
            <NavBarAction
                v-else-if="isEditTab && editorIsAllowedEdit && !deleted"
                :icon="Publish"
                :label="$t('publish')"
                :disabled="editorIsReadOnly || editorHasErrors || !editorCanPublish"
                :type="isEditTab && editorExecuteBlockedReason ? 'primary' : undefined"
                :highlight="editorPublishHighlighted"
                data-test="flow-publish"
                @click="editorPublish"
            />
            <NavBarAction
                v-else-if="flow && canEdit && !deleted && !flowStore.isCreating"
                :icon="Pencil"
                :label="$t('edit flow')"
                @click="editFlow"
            />
        </template>

        <template #primary>
            <NavBarAction
                v-if="deleted"
                type="primary"
                :icon="Restore"
                :label="$t('restore')"
                @click="restoreFlow"
            />
            <TriggerFlow
                v-else-if="shouldShowExecute"
                type="primary"
                compact
                :flowId="flow?.id"
                :namespace="flow?.namespace"
                :flowSource="flow?.source"
                :blockedReason="isEditTab ? editorExecuteBlockedReason : undefined"
                @blocked="editorPointToPublish"
            />
        </template>
    </NavBarActions>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute, useRouter} from "vue-router"
    import {useFlowStore} from "../../../stores/flow"
    import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"
    import Pencil from "vue-material-design-icons/Pencil.vue"
    import Restore from "vue-material-design-icons/Restore.vue"
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"
    import Download from "vue-material-design-icons/Download.vue"
    import Delete from "vue-material-design-icons/Delete.vue"
    import Publish from "vue-material-design-icons/Publish.vue"
    import ContentSave from "vue-material-design-icons/ContentSave.vue"
    import NavBarActions from "../../../components/layout/NavBarActions.vue"
    import NavBarAction from "../../../components/layout/NavBarAction.vue"
    import FlowPlaygroundToggle from "../../../components/inputs/FlowPlaygroundToggle.vue"
    import TriggerFlow from "../../../components/flows/TriggerFlow.vue"
    import Dashboards from "override/components/dashboard/Selector.vue"
    import {ALLOWED_CREATION_ROUTES} from "../../../components/dashboard/composables/useDashboards"
    import {useDashboardSelection} from "../../../components/dashboard/composables/useDashboardSelection"
    import {useIsMobile} from "../../../composables/useIsMobile"
    import {routeFamily} from "../../../utils/routeFamily"
    import {useActiveTab} from "../../../composables/useActiveTab"
    import resource from "../../../models/resource"
    import action from "../../../models/action"
    import {useAuthStore} from "override/stores/auth"
    import {useUnsavedChangesStore} from "../../../stores/unsavedChanges"
    import {useLogsStore} from "../../../stores/logs"
    import {usePlaygroundStore} from "../../../stores/playground"
    import {useToast} from "../../../utils/toast"
    import {useFlowEditorActions} from "../../../components/flows/useFlowEditorActions"

    const {t} = useI18n({useScope: "global"})

    const unsavedChangesStore = useUnsavedChangesStore()
    const flowStore = useFlowStore()
    const logsStore = useLogsStore()
    const playgroundStore = usePlaygroundStore()
    const router = useRouter()
    const route = useRoute()
    const toast = useToast()

    const isMobile = useIsMobile()

    const flow = computed(() => flowStore.flow)
    const deleted = computed(() => flow.value?.deleted || false)
    const tab = useActiveTab()
    const isEditTab = computed(() => tab.value === "edit" || flowStore.isCreating)

    const authStore = useAuthStore()

    const {
        canPublish: editorCanPublish,
        executeBlockedReason: editorExecuteBlockedReason,
        publishHighlighted: editorPublishHighlighted,
        pointToPublish: editorPointToPublish,
        createDraft: editorCreateDraft,
        hasErrors: editorHasErrors,
        isReadOnly: editorIsReadOnly,
        isAllowedEdit: editorIsAllowedEdit,
        isPlaygroundAllowed,
        publish: editorPublish,
        exportYaml: editorExportYaml,
        copyFlow: editorCopyFlow,
        deleteFlow: editorDeleteFlow,
    } = useFlowEditorActions()

    const {selectDashboard: onSelectDashboard} = useDashboardSelection()

    const showDashboards = computed(() =>
        !isMobile.value && tab.value === "overview" && ALLOWED_CREATION_ROUTES.includes(routeFamily(route.name)),
    )

    const canExecute = computed(() =>
        flow.value && authStore.user?.isAllowed(resource.FLOW, action.EXECUTE, flow.value.namespace),
    )

    const shouldShowExecute = computed(() => {
        if (!flow.value || deleted.value) return false
        if (flowStore.isCreating) return false
        // on a draft-only flow, only the Playground's Run all tasks can run it
        if (flowStore.isDraftOnly && !(isEditTab.value && playgroundStore.enabled)) return false
        return !!canExecute.value
    })

    const canEdit = computed(() =>
        authStore.user?.isAllowed(resource.FLOW, action.UPDATE, flow.value?.namespace),
    )

    const canDelete = computed(() =>
        authStore.user?.isAllowed(resource.FLOW, action.DELETE, flow.value?.namespace),
    )

    const editFlow = () => {
        router.push({
            name: "flows/update/edit",
            params: {
                namespace: flow.value?.namespace,
                id: flow.value?.id,
                tenant: route.params.tenant,
            },
        })
    }

    const hasLogs = computed(() =>
        logsStore.logs !== undefined && logsStore.logs.length > 0,
    )

    const deleteLogs = () => {
        toast.confirm(
            t("delete_all_logs"),
            async () => {
                if (!flow.value) return
                return logsStore.deleteLogs({
                    namespace: flow.value.namespace,
                    flowId: flow.value.id,
                })
            },
        )
    }

    const restoreFlow = () => {
        if (!flow.value?.source) return
        flowStore.createFlow({
            flow: YAML_UTILS.deleteMetadata(flow.value.source, "deleted"),
            restore: true,
        }).then(() => {
            unsavedChangesStore.unsavedChange = false
            router.go(0)
        })
    }
</script>
