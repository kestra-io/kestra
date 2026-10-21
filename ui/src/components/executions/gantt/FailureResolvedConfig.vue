<template>
    <div v-if="flowLoading || loading" v-ks-loading="true" class="resolved-config__status" />
    <KsAlert v-else-if="flowError || error" type="error" :closable="false">
        {{ $t("gantt_panel.configuration_error") }}
    </KsAlert>
    <KsEmpty v-else-if="!resolvedYaml" :description="$t('gantt_panel.configuration_empty')" />
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

    let latestLoad = 0
    async function load() {
        const current = ++latestLoad
        if (props.flowLoading) return
        if (!props.rawBlock) {
            resolvedYaml.value = undefined
            loading.value = false
            return
        }

        loading.value = true
        error.value = false
        resolvedYaml.value = undefined
        try {
            const rawBlock = props.rawBlock
            const {rendered} = await renderExpressions({
                expressions: [rawBlock],
                executionId: props.executionId,
                taskRunId: props.taskRunId,
            })
            if (current !== latestLoad) return
            resolvedYaml.value = rendered?.[rawBlock]
        } catch {
            if (current === latestLoad) error.value = true
        } finally {
            if (current === latestLoad) loading.value = false
        }
    }

    watch(() => [props.executionId, props.taskRunId, props.rawBlock, props.flowLoading], load, {immediate: true})

    const markdownContent = computed(() => "```yaml\n" + (resolvedYaml.value ?? "") + "\n```")
</script>

<style scoped>
    .resolved-config__status {
        position: relative;
        min-height: 4rem;
    }
</style>
