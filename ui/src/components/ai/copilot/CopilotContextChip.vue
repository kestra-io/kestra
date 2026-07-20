<template>
    <!-- Shows the page the copilot is focused on (sent as `additionalContext`). Dismissible to drop it. -->
    <div class="copilot-context" data-test="copilot-context-chip">
        <KsTag closable size="small" :icon="icon" @close="emit('clear')">
            {{ label }}
        </KsTag>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import FileDocumentOutline from "vue-material-design-icons/FileDocumentOutline.vue"
    import PlayCircleOutline from "vue-material-design-icons/PlayCircleOutline.vue"
    import FolderOutline from "vue-material-design-icons/FolderOutline.vue"
    import type {ScopeBinding} from "./types"

    const props = defineProps<{scope: ScopeBinding}>()
    const emit = defineEmits<{clear: []}>()

    const {t} = useI18n()

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
    const label = computed(() => {
        switch (props.scope.kind) {
        case "EXECUTION":
            return t("ai.copilot.context.execution", {id: props.scope.executionId ?? ""})
        case "NAMESPACE":
            return t("ai.copilot.context.namespace", {namespace: props.scope.namespace ?? ""})
        default:
            return t("ai.copilot.context.flow", {flow: props.scope.flowId ?? ""})
        }
    })
</script>

<style scoped>
    .copilot-context {
        display: flex;
        margin-bottom: var(--ks-spacing-2);
    }
</style>
