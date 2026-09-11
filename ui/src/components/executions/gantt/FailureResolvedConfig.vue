<template>
    <div v-if="loading" v-ks-loading="true" class="resolved-config__status" />
    <KsAlert v-else-if="error" type="error" :closable="false">
        {{ $t("failureDebugPanel.resolvedConfig.error") }}
    </KsAlert>
    <KsEmpty v-else-if="!resolvedYaml" :description="$t('failureDebugPanel.resolvedConfig.empty')" />
    <KsMarkdown v-else :content="markdownContent" />
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {KsAlert, KsEmpty, KsMarkdown} from "@kestra-io/design-system"
    import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"
    import {flow as fetchFlow} from "@kestra-io/kestra-sdk/flows"
    import {renderExpressions} from "@kestra-io/kestra-sdk/expressions"

    const props = defineProps<{
        namespace: string
        flowId: string
        flowRevision: number
        executionId: string
        taskRunId: string
        taskId: string
    }>()

    const loading = ref(false)
    const error = ref(false)
    const resolvedYaml = ref<string | undefined>(undefined)

    async function load() {
        loading.value = true
        error.value = false
        resolvedYaml.value = undefined
        try {
            // Fetches the exact revision this execution ran on, not the flow's current/latest
            // source — the flow may have been edited since this task run failed.
            const flow = await fetchFlow({
                namespace: props.namespace,
                id: props.flowId,
                revision: props.flowRevision,
                source: true,
            })
            if (!flow.source) return

            const rawBlock = YAML_UTILS.extractBlock({source: flow.source, section: "tasks", key: props.taskId})
            if (!rawBlock) return

            const {rendered} = await renderExpressions({
                expressions: [rawBlock],
                executionId: props.executionId,
                taskRunId: props.taskRunId,
            })
            resolvedYaml.value = rendered?.[rawBlock]
        } catch {
            error.value = true
        } finally {
            loading.value = false
        }
    }

    watch(() => props.taskRunId, load, {immediate: true})

    // A single fenced code block is valid markdown on its own, so KsMarkdown's Shiki
    // highlighter can be reused here instead of pulling in a full read-only Monaco editor
    // for what is usually a handful of lines.
    const markdownContent = computed(() => "```yaml\n" + (resolvedYaml.value ?? "") + "\n```")
</script>

<style scoped lang="scss">
    .resolved-config__status {
        position: relative;
        min-height: 4rem;
    }
</style>
