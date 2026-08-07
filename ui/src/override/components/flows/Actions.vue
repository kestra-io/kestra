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
            :label="t('copy')"
            @click="editorCopyFlow"
        />
        <NavBarAction
            v-if="isEditTab && editorIsAllowedEdit && !deleted"
            :icon="Download"
            :label="t('flow_export')"
            @click="editorExportYaml"
        />
        <NavBarAction
            v-if="isEditTab && canDelete && !deleted && !flowStore.isCreating"
            :icon="Delete"
            :label="t('delete')"
            @click="editorDeleteFlow"
        />
        <NavBarAction
            v-if="tab === 'logs' && hasLogs"
            :icon="TrashCan"
            :label="t('delete logs')"
            @click="deleteLogs"
        />

        <template #secondary>
            <KsDropdown
                v-if="isEditTab && editorIsAllowedEdit && !deleted"
                splitButton
                :disabled="editorIsReadOnly || (!isPublishAction && !editorCanSave)"
                :buttonProps="{disabled: editorHasErrors}"
                @click="editorSaveOrPublish"
            >
                {{ isPublishAction ? t('publish') : t('save') }}
                <template #dropdown>
                    <KsDropdownMenu>
                        <KsDropdownItem v-if="isPublishAction" :icon="ContentSave" @click="editorSave">
                            {{ t('save') }}
                        </KsDropdownItem>
                        <KsDropdownItem :icon="FileDocumentEditOutline" @click="editorSaveAsDraft">
                            {{ t('save_as_draft') }}
                        </KsDropdownItem>
                        <KsDropdownItem
                            v-if="editorHaveChange && !flowStore.isCreating"
                            :icon="PlayBoxOutline"
                            :disabled="editorHasErrors || editorIsReadOnly"
                            @click="editorSaveAndExecute"
                        >
                            {{ t('save_and_execute') }}
                        </KsDropdownItem>
                    </KsDropdownMenu>
                </template>
            </KsDropdown>
            <NavBarAction
                v-else-if="canEdit && !deleted"
                :icon="Pencil"
                :label="t('edit flow')"
                @click="editFlow"
            />
        </template>

        <template #primary>
            <NavBarAction
                v-if="deleted"
                type="primary"
                :icon="BackupRestore"
                :label="t('restore')"
                @click="restoreFlow"
            />
            <TriggerFlow
                v-else-if="shouldShowExecute"
                type="primary"
                :flowId="flow?.id"
                :namespace="flow?.namespace"
                :flowSource="flow?.source"
            />
        </template>
    </NavBarActions>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute, useRouter} from "vue-router"
    import {useFlowStore} from "../../../stores/flow"
    import {flowYamlUtils as YAML_UTILS} from "@kestra-io/topology"
    import Pencil from "vue-material-design-icons/Pencil.vue"
    import BackupRestore from "vue-material-design-icons/BackupRestore.vue"
    import TrashCan from "vue-material-design-icons/TrashCan.vue"
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"
    import Download from "vue-material-design-icons/Download.vue"
    import Delete from "vue-material-design-icons/Delete.vue"
    import PlayBoxOutline from "vue-material-design-icons/PlayBoxOutline.vue"
    import ContentSave from "vue-material-design-icons/ContentSave.vue"
    import FileDocumentEditOutline from "vue-material-design-icons/FileDocumentEditOutline.vue"
    import NavBarActions from "../../../components/layout/NavBarActions.vue"
    import NavBarAction from "../../../components/layout/NavBarAction.vue"
    import FlowPlaygroundToggle from "../../../components/inputs/FlowPlaygroundToggle.vue"
    import TriggerFlow from "../../../components/flows/TriggerFlow.vue"
    import Dashboards from "override/components/dashboard/Selector.vue"
    import {ALLOWED_CREATION_ROUTES} from "../../../components/dashboard/composables/useDashboards"
    import {routeFamily} from "../../../utils/routeFamily"
    import {useActiveTab} from "../../../composables/useActiveTab"
    import resource from "../../../models/resource"
    import action from "../../../models/action"
    import {useAuthStore} from "override/stores/auth"
    import {useUnsavedChangesStore} from "../../../stores/unsavedChanges"
    import {useDashboardStore} from "../../../stores/dashboard.ts"
    import {useLogsStore} from "../../../stores/logs"
    import {useToast} from "../../../utils/toast"
    import {useFlowEditorActions} from "../../../components/flows/useFlowEditorActions"

    const {t} = useI18n({useScope: "global"})

    const unsavedChangesStore = useUnsavedChangesStore()
    const flowStore = useFlowStore()
    const logsStore = useLogsStore()
    const router = useRouter()
    const route = useRoute()
    const toast = useToast()

    const flow = computed(() => flowStore.flow)
    const deleted = computed(() => flow.value?.deleted || false)
    const tab = useActiveTab()
    const isEditTab = computed(() => tab.value === "edit" || flowStore.isCreating)

    const authStore = useAuthStore()
    const dashboardStore = useDashboardStore()

    const {
        haveChange: editorHaveChange,
        canSave: editorCanSave,
        hasErrors: editorHasErrors,
        isReadOnly: editorIsReadOnly,
        isAllowedEdit: editorIsAllowedEdit,
        isDraft: editorIsDraft,
        isPlaygroundAllowed,
        save: editorSave,
        saveAsDraft: editorSaveAsDraft,
        publishDraft: editorPublishDraft,
        saveAndExecute: editorSaveAndExecute,
        exportYaml: editorExportYaml,
        copyFlow: editorCopyFlow,
        deleteFlow: editorDeleteFlow,
    } = useFlowEditorActions()

    const onSelectDashboard = (value: any) => {
        const key = dashboardStore.getUserDashboardStorageKey(route)
        localStorage.setItem(key, value)
        router.replace({
            params: {...route.params, dashboard: value},
        })
    }

    const showDashboards = computed(() =>
        tab.value === "overview" && ALLOWED_CREATION_ROUTES.includes(routeFamily(route.name)),
    )

    const canExecute = computed(() =>
        flow.value && authStore.user?.isAllowed(resource.FLOW, action.EXECUTE, flow.value.namespace),
    )

    const shouldShowExecute = computed(() => {
        if (!flow.value || deleted.value) return false
        if (flowStore.isCreating) return false
        return !!canExecute.value
    })

    // The single save-family control: publishing is what a draft is for, saving is what an
    // already-published flow (or a flow being created) needs. The variant not taken stays
    // reachable in the dropdown.
    const isPublishAction = computed(() => editorIsDraft.value && !flowStore.isCreating)

    const editorSaveOrPublish = () => {
        if (isPublishAction.value) {
            editorPublishDraft()
            return
        }
        editorSave()
    }

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
        flowStore.createFlow({
            flow: YAML_UTILS.deleteMetadata(flow.value?.source, "deleted"),
            restore: true,
        }).then(() => {
            unsavedChangesStore.unsavedChange = false
            router.go(0)
        })
    }
</script>
