<template>
    <!-- Shows the page the copilot is focused on (sent as `additionalContext`). Dismissible to drop it. -->
    <div class="copilot-context" data-test="copilot-context-chip">
        <KsTag closable size="small" :icon="icon" @close="emit('clear')">
            <!-- Render the id/namespace/flowId via <KsId> so it stands out as a code-styled,
                 link-coloured token — the same treatment execution/flow ids get in tables. -->
            <i18n-t :keypath="context.keypath" scope="global" tag="span">
                <template #[context.slot]>
                    <!-- For a flow, show its namespace alongside the id so the full context is visible. -->
                    <template v-if="context.namespace">
                        <KsId :value="context.namespace" :shrink="false" /><span class="copilot-context-sep">/</span><KsId :value="context.value" :shrink="false" />
                    </template>
                    <KsId v-else :value="context.value" :shrink="false" />
                </template>
            </i18n-t>
        </KsTag>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import FileDocumentOutline from "vue-material-design-icons/FileDocumentOutline.vue"
    import PlayCircleOutline from "vue-material-design-icons/PlayCircleOutline.vue"
    import FolderOutline from "vue-material-design-icons/FolderOutline.vue"
    import type {ScopeBinding} from "./types"

    const props = defineProps<{scope: ScopeBinding}>()
    const emit = defineEmits<{clear: []}>()

    const icon = computed(() => {
        switch (props.scope.kind) {
        case "EXECUTION":
            return PlayCircleOutline
        case "NAMESPACE":
            return FolderOutline
        default:
            return FileDocumentOutline
        }
    })

    // A short, human label for the focused resource. Falls back gracefully if a field is missing.
    // Each context i18n string ("Execution {id}" / "Namespace {namespace}" / "Flow {flow}") has one
    // interpolation slot; the slot name differs per kind, so drive the <i18n-t> slot dynamically.
    const context = computed(() => {
        switch (props.scope.kind) {
        case "EXECUTION":
            return {keypath: "ai.copilot.context.execution", slot: "id", value: props.scope.executionId ?? "", namespace: undefined as string | undefined}
        case "NAMESPACE":
            return {keypath: "ai.copilot.context.namespace", slot: "namespace", value: props.scope.namespace ?? "", namespace: undefined as string | undefined}
        default:
            // FLOW — show the namespace next to the id (a flow is only unique within its namespace).
            return {keypath: "ai.copilot.context.flow", slot: "flow", value: props.scope.flowId ?? "", namespace: props.scope.namespace}
        }
    })
</script>

<style scoped>
    .copilot-context {
        display: flex;
        margin-bottom: var(--ks-spacing-2);
    }

    /* Separator between the namespace and flow id tokens. */
    .copilot-context-sep {
        color: var(--ks-text-secondary);
    }
</style>
