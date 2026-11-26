<template>
    <KsTooltip v-if="marker" :content="$t(`flowAutosave.${marker.key}.tooltip`)">
        <button type="button" class="flow-change-marker" data-test="flow-change-marker" @click="openChanges">
            <KsTag :type="marker.type" size="small" round :label="$t(`flowAutosave.${marker.key}.label`)" />
        </button>
    </KsTooltip>
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
    import {computed} from "vue"
    import {useRoute, useRouter} from "vue-router"
    import type {KsTagType} from "@kestra-io/design-system"
    import {useFlowStore} from "../../stores/flow"
    import {useFlowAutosaveStore} from "../../stores/flowAutosave"
    import {FLOW_PARENT_ROUTE} from "./flowTabs"

    const flowStore = useFlowStore()
    const autosaveStore = useFlowAutosaveStore()
    const router = useRouter()
    const route = useRoute()

    const marker = computed((): {key: string, type: KsTagType} | undefined => {
        if (flowStore.isCreating) return undefined
        switch (autosaveStore.status) {
        case "conflict": return {key: "conflict", type: "danger"}
        case "offline": return {key: "savedLocally", type: "warning"}
        case "pending":
        case "syncing": return {key: "modified", type: "info"}
        }
        return flowStore.flow?.draft ? {key: "draft", type: "primary"} : undefined
    })

    async function openChanges() {
        const flow = flowStore.flow
        if (!flow) return
        if (autosaveStore.status === "pending") await autosaveStore.sync().catch(() => undefined)

        const revisions = await flowStore.loadRevisions({namespace: flow.namespace, id: flow.id})
        const head = revisions.at(-1)?.revision
        const published = revisions.findLast(revision => !revision.draft && revision.revision !== head)?.revision

        await router.push({
            name: `${FLOW_PARENT_ROUTE}/revisions`,
            params: {namespace: flow.namespace, id: flow.id, tenant: route.params.tenant},
            query: {
                revisionLeft: published?.toString(),
                revisionRight: head?.toString(),
            },
        })
    }
</script>

<style scoped>
    .flow-change-marker {
        margin-left: var(--ks-spacing-2);
        padding: 0;
        border: 0;
        background: none;
        cursor: pointer;
    }
</style>
