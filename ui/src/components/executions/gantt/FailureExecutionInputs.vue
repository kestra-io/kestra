<template>
    <div v-if="loading" v-ks-loading="true" class="execution-inputs__status" />
    <KsAlert v-else-if="error" type="error" :closable="false">
        {{ $t("gantt_panel.inputs_error") }}
    </KsAlert>
    <KsEmpty v-else-if="props.inputIds.length === 0" :imageSize="80" :description="$t('gantt_panel.no_inputs')" />
    <Vars v-else :data="values" />
</template>

<script setup lang="ts">
    import {ref, watch} from "vue"
    import {KsAlert, KsEmpty} from "@kestra-io/design-system"
    import {renderExpressions} from "@kestra-io/kestra-sdk/expressions"
    import Vars from "../Vars.vue"

    const props = defineProps<{
        inputIds: string[]
        executionId: string
    }>()

    const quoteSegment = (segment: string) => `['${segment.replace(/\\/g, "\\\\").replace(/'/g, "\\'")}']`
    const inputExpression = (id: string) => `{{ inputs${id.split(".").map(quoteSegment).join("")} }}`

    const loading = ref(false)
    const error = ref(false)
    const values = ref<Record<string, string>>({})

    let latestLoad = 0
    async function load() {
        const current = ++latestLoad
        if (props.inputIds.length === 0) {
            values.value = {}
            loading.value = false
            return
        }

        loading.value = true
        error.value = false
        try {
            const inputIds = [...props.inputIds]
            const expressions = inputIds.map(inputExpression)
            const {rendered} = await renderExpressions({
                expressions,
                executionId: props.executionId,
            })
            if (current !== latestLoad) return
            values.value = Object.fromEntries(
                inputIds.map((id, index) => [id, rendered?.[expressions[index]] ?? ""]),
            )
        } catch {
            if (current === latestLoad) error.value = true
        } finally {
            if (current === latestLoad) loading.value = false
        }
    }

    watch(() => [props.executionId, props.inputIds.join(",")], load, {immediate: true})
</script>

<style scoped>
    .execution-inputs__status {
        position: relative;
        min-height: 4rem;
    }
</style>
