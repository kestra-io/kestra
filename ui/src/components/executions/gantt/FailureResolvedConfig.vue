<template>
    <div v-if="flowLoading || loading" v-ks-loading="true" class="resolved-config__status" />
    <KsAlert v-else-if="flowError || error" type="error" :closable="false">
        {{ $t("failureDebugPanel.resolvedConfig.error") }}
    </KsAlert>
    <KsEmpty v-else-if="!resolvedYaml" :description="$t('failureDebugPanel.resolvedConfig.empty')" />
    <KsMarkdown v-else :content="markdownContent" />
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {KsAlert, KsEmpty, KsMarkdown} from "@kestra-io/design-system"
    import {renderExpressions} from "@kestra-io/kestra-sdk/expressions"

    const props = defineProps<{
        rawBlock: string | undefined
        flowLoading: boolean
        flowError: boolean
        executionId: string
        taskRunId: string
    }>()

    const loading = ref(false)
    const error = ref(false)
    const resolvedYaml = ref<string | undefined>(undefined)

    async function load() {
        if (props.flowLoading) return
        if (!props.rawBlock) {
            resolvedYaml.value = undefined
            return
        }

        loading.value = true
        error.value = false
        resolvedYaml.value = undefined
        try {
            const {rendered} = await renderExpressions({
                expressions: [props.rawBlock],
                executionId: props.executionId,
                taskRunId: props.taskRunId,
            })
            resolvedYaml.value = rendered?.[props.rawBlock]
        } catch {
            error.value = true
        } finally {
            loading.value = false
        }
    }

    watch(() => [props.taskRunId, props.rawBlock, props.flowLoading], load, {immediate: true})

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
