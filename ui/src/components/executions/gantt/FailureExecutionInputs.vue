<template>
    <div v-if="loading" v-ks-loading="true" class="execution-inputs__status" />
    <KsAlert v-else-if="error" type="error" :closable="false">
        {{ $t("failureDebugPanel.executionInputs.error") }}
    </KsAlert>
    <KsEmpty v-else-if="props.inputIds.length === 0" :description="$t('failureDebugPanel.executionInputs.empty')" />
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
        taskRunId: string
    }>()

    const loading = ref(false)
    const error = ref(false)
    const values = ref<Record<string, string>>({})

    async function load() {
        if (props.inputIds.length === 0) {
            values.value = {}
            return
        }

        loading.value = true
        error.value = false
        try {
            const expressions = props.inputIds.map((id) => `{{ inputs.${id} }}`)
            const {rendered} = await renderExpressions({
                expressions,
                executionId: props.executionId,
                taskRunId: props.taskRunId,
            })
            values.value = Object.fromEntries(
                props.inputIds.map((id, index) => [id, rendered?.[expressions[index]] ?? ""]),
            )
        } catch {
            error.value = true
        } finally {
            loading.value = false
        }
    }

    watch(() => [props.executionId, props.inputIds.join(",")], load, {immediate: true})
</script>

<style scoped lang="scss">
    .execution-inputs__status {
        position: relative;
        min-height: 4rem;
    }
</style>
