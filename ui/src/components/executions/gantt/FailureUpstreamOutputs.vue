<template>
    <div v-if="loading" v-ks-loading="true" class="upstream-outputs__status" />
    <KsAlert v-else-if="error" type="error" :closable="false">
        {{ $t("failureDebugPanel.upstreamOutputs.error") }}
    </KsAlert>
    <KsEmpty v-else-if="Object.keys(values).length === 0" :imageSize="80" :description="$t('failureDebugPanel.upstreamOutputs.empty')" />
    <Vars v-else :data="values" />
</template>

<script setup lang="ts">
    import {ref, watch} from "vue"
    import {KsAlert, KsEmpty} from "@kestra-io/design-system"
    import Vars from "../Vars.vue"
    import {loadTaskRunOutputs} from "../../../composables/useTaskRunOutputs"
    import type {FailureTaskRun} from "./types"

    const props = defineProps<{
        // Task ids the focused task's own resolved config references via `{{ outputs.<id>... }}`.
        referencedTaskIds: string[]
        taskRunList: FailureTaskRun[]
        executionId: string
    }>()

    const loading = ref(false)
    const error = ref(false)
    const values = ref<Record<string, unknown>>({})

    async function load() {
        if (props.referencedTaskIds.length === 0) {
            values.value = {}
            return
        }

        loading.value = true
        error.value = false
        try {
            const merged: Record<string, unknown> = {}
            for (const taskId of props.referencedTaskIds) {
                // A looped task can have several runs; the outputs a Pebble expression like
                // `{{ outputs.extract[0].rows }}` actually resolves against are already visible
                // in "Resolved configuration" — here we just need one representative run to show
                // what that task produced.
                const taskRun = props.taskRunList.find((run) => run.taskId === taskId)
                if (!taskRun) continue

                const outputs = await loadTaskRunOutputs(props.executionId, taskRun.id)
                for (const [key, value] of Object.entries(outputs)) {
                    merged[`${taskId}.${key}`] = value
                }
            }
            values.value = merged
        } catch {
            error.value = true
        } finally {
            loading.value = false
        }
    }

    watch(() => [props.executionId, props.referencedTaskIds.join(",")], load, {immediate: true})
</script>

<style scoped lang="scss">
    .upstream-outputs__status {
        position: relative;
        min-height: 4rem;
    }
</style>
