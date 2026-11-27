<template>
    <div v-if="visible" class="flow-save-status" data-test="flow-save-status" :data-state="state">
        <KsDropdown v-if="state === 'draft'" trigger="click" @command="onCommand">
            <button type="button" class="flow-save-status-trigger" data-test="flow-save-status-menu">
                <KsIcon :color="ICON_COLORS.draft"><CircleMedium /></KsIcon>
                <span>{{ label }}</span>
                <KsIcon><ChevronDown /></KsIcon>
            </button>
            <template #dropdown>
                <KsDropdownMenu>
                    <KsDropdownItem :icon="FileCompare" command="changes" data-test="flow-save-status-changes">
                        {{ $t("flowSaveStatus.viewChanges") }}
                    </KsDropdownItem>
                    <KsDropdownItem
                        v-if="publishedRevision !== undefined"
                        :icon="Undo"
                        command="discard"
                        danger
                        data-test="flow-save-status-discard"
                    >
                        {{ $t("flowSaveStatus.discard") }}
                    </KsDropdownItem>
                </KsDropdownMenu>
            </template>
        </KsDropdown>
        <template v-else>
            <KsIcon :class="{'is-loading': state === 'saving'}" :color="state ? ICON_COLORS[state] : undefined">
                <component :is="state ? ICONS[state] : Check" />
            </KsIcon>
            <KsText :type="state === 'upToDate' || state === 'saving' ? 'secondary' : undefined" :class="{'flow-save-status-published': state === 'published'}">
                {{ label }}
            </KsText>
        </template>
    </div>
    <KsDialog
        :modelValue="autosaveStore.status === 'conflict'"
        :showClose="false"
        :closeOnClickModal="false"
        :closeOnPressEscape="false"
        :appendToBody="true"
        width="560px"
    >
        <template #header>
            <span>{{ $t("flowAutosave.conflictDialog.title") }}</span>
        </template>
        <KsText tag="p">{{ $t("flowAutosave.conflictDialog.description") }}</KsText>
        <template #footer>
            <KsButton data-test="flow-autosave-take-server" @click="autosaveStore.takeServer()">
                {{ $t("flowAutosave.conflictDialog.takeServer") }}
            </KsButton>
            <KsButton type="primary" data-test="flow-autosave-keep-local" @click="autosaveStore.keepLocal()">
                {{ $t("flowAutosave.conflictDialog.keepLocal") }}
            </KsButton>
        </template>
    </KsDialog>
</template>

<script setup lang="ts">
    import {computed, type Component} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute, useRouter} from "vue-router"
    import Check from "vue-material-design-icons/Check.vue"
    import Loading from "vue-material-design-icons/Loading.vue"
    import CircleMedium from "vue-material-design-icons/CircleMedium.vue"
    import AlertOutline from "vue-material-design-icons/AlertOutline.vue"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import FileCompare from "vue-material-design-icons/FileCompare.vue"
    import Undo from "vue-material-design-icons/Undo.vue"
    import {useFlowStore} from "../../stores/flow"
    import {useFlowAutosaveStore} from "../../stores/flowAutosave"
    import {useActiveTab} from "../../composables/useActiveTab"
    import {useToast} from "../../utils/toast"
    import {FLOW_PARENT_ROUTE} from "./flowTabs"
    import {useFlowSaveStatus, type FlowSaveState} from "./useFlowSaveStatus"
    import {useFlowEditorActions} from "./useFlowEditorActions"

    const ICONS: Record<FlowSaveState, Component> = {
        upToDate: Check,
        saving: Loading,
        draft: CircleMedium,
        invalid: CircleMedium,
        offline: AlertOutline,
        published: Check,
    }

    const ICON_COLORS: Record<FlowSaveState, string | undefined> = {
        upToDate: "var(--ks-icon-muted)",
        saving: "var(--ks-text-link)",
        draft: "var(--ks-text-link)",
        invalid: "var(--ks-text-warning)",
        offline: "var(--ks-text-warning)",
        published: "var(--ks-text-success)",
    }

    const {t} = useI18n()
    const flowStore = useFlowStore()
    const autosaveStore = useFlowAutosaveStore()
    const router = useRouter()
    const route = useRoute()
    const toast = useToast()
    const tab = useActiveTab()
    const {state, publishedRevision, errorCount} = useFlowSaveStatus()
    const {publish} = useFlowEditorActions()

    const visible = computed(() => state.value !== undefined && tab.value === "edit" && flowStore.isAllowedEdit && !flowStore.flow?.deleted)

    const label = computed(() => {
        const revision = publishedRevision.value
        switch (state.value) {
        case "upToDate": return t("flowSaveStatus.upToDate", {revision})
        case "saving": return t("flowSaveStatus.saving")
        case "draft": return revision === undefined ? t("flowSaveStatus.draftUnpublished") : t("flowSaveStatus.draft", {revision})
        case "invalid": return t("flowSaveStatus.invalid", {count: errorCount.value}, errorCount.value)
        case "offline": return t("flowSaveStatus.offline")
        case "published": return t("flowSaveStatus.published", {revision: autosaveStore.justPublished})
        }
        return ""
    })

    function onCommand(command: string) {
        if (command === "changes") openChanges()
        if (command === "discard") toast.confirm(t("flowSaveStatus.discardConfirm", {revision: publishedRevision.value}), discard)
    }

    async function openChanges() {
        const flow = flowStore.flow
        if (!flow) return
        await router.push({
            name: `${FLOW_PARENT_ROUTE}/revisions`,
            params: {namespace: flow.namespace, id: flow.id, tenant: route.params.tenant},
            query: {
                revisionLeft: publishedRevision.value?.toString(),
                revisionRight: flow.revision?.toString(),
            },
        })
    }

    // There is no endpoint to drop a revision, so the published source is published again over the draft.
    async function discard() {
        const flow = flowStore.flow
        if (!flow || publishedRevision.value === undefined) return
        const published = await flowStore.loadFlow({namespace: flow.namespace, id: flow.id, revision: String(publishedRevision.value), store: false})
        if (!published?.source) return
        flowStore.flowYaml = published.source
        await publish()
    }
</script>

<style scoped>
    .flow-save-status {
        display: flex;
        align-items: center;
        justify-content: flex-end;
        gap: var(--ks-spacing-2);
        min-width: 16rem;
        margin-right: var(--ks-spacing-3);
        font-size: var(--ks-font-size-sm);
        white-space: nowrap;
    }

    .flow-save-status-trigger {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        padding: 0;
        border: 0;
        background: none;
        color: var(--ks-text-primary);
        font: inherit;
        cursor: pointer;
    }

    .flow-save-status-published {
        color: var(--ks-text-success);
    }
</style>
