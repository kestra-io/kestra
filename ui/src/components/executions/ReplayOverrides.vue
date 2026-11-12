<template>
    <section v-ks-loading="loading" class="replay-overrides" data-test="replay-overrides">
        <h4 class="section-title">
            {{ $t("replayOverrides.title") }}
        </h4>
        <KsText tag="p" size="small" class="description">
            {{ $t("replayOverrides.description", {taskId}) }}
        </KsText>

        <KsAlert
            v-if="loadFailed"
            type="error"
            :closable="false"
            showIcon
            data-test="replay-overrides-error"
            :title="$t('replayOverrides.load_error', {taskId})"
        />
        <KsText v-else-if="!loading && !outputs.length" tag="p" size="small" type="info" data-test="replay-overrides-empty">
            {{ $t("replayOverrides.empty", {taskId}) }}
        </KsText>

        <div v-if="outputs.length" class="override-rows">
            <div v-for="output in outputs" :key="output.path" class="override-row" data-test="replay-override">
                <div class="override-label">
                    <code>{{ output.path }}</code>
                    <KsText size="small" type="info" data-test="replay-override-current">
                        {{ $t("replayOverrides.current", {value: formatValue(output.value)}) }}
                    </KsText>
                </div>

                <div class="override-field">
                    <KsInput
                        v-model="texts[output.path]"
                        :aria-label="output.path"
                        data-test="replay-override-input"
                    />
                    <KsText v-if="parsed[output.path]?.error" size="small" type="danger" data-test="replay-override-error">
                        {{ $t(`replayOverrides.error.${parsed[output.path].error}`) }}
                    </KsText>
                </div>
            </div>
        </div>
    </section>
</template>

<script setup lang="ts">
    import {computed, onMounted, reactive, ref, watch} from "vue"
    import {isDeepEqual} from "@kestra-io/design-system"
    import type {ReplayOutputServiceReplayOutput} from "@kestra-io/kestra-sdk"
    import {useExecutionsStore} from "../../stores/executions"
    import {editableText, formatValue, parseOverride, type ParsedOverride} from "./replayOverrides"

    const props = defineProps<{
        executionId: string
        taskRunId: string
        taskId: string
    }>()

    const overridesModel = defineModel<Record<string, unknown>>("overrides", {required: true})
    const invalid = defineModel<boolean>("invalid", {default: false})

    const executionsStore = useExecutionsStore()

    const loading = ref(true)
    const loadFailed = ref(false)
    const outputs = ref<ReplayOutputServiceReplayOutput[]>([])
    const texts = reactive<Record<string, string>>({})

    const parsed = computed(() => Object.fromEntries(
        outputs.value.map(output => [output.path, parseOverride(output.value, texts[output.path] ?? "")]),
    ) as Record<string, ParsedOverride>)

    const overrides = computed(() => Object.fromEntries(
        outputs.value
            .filter(output => !parsed.value[output.path].error && !isDeepEqual(parsed.value[output.path].value, output.value ?? null))
            .map(output => [output.path, parsed.value[output.path].value]),
    ))

    watch(overrides, value => overridesModel.value = value, {immediate: true})
    watch(parsed, value => invalid.value = Object.values(value).some(it => it.error), {immediate: true})

    onMounted(async () => {
        try {
            outputs.value = await executionsStore.loadReplayOutputs({executionId: props.executionId, taskRunId: props.taskRunId})
            outputs.value.forEach(output => texts[output.path] = editableText(output.value))
        } catch {
            loadFailed.value = true
        } finally {
            loading.value = false
        }
    })
</script>

<style scoped>
.replay-overrides {
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-3);
}

.section-title {
    font-size: var(--ks-font-size-sm);
    font-weight: 600;
    margin: var(--ks-spacing-5) 0 0;
    color: var(--ks-text-primary);
}

.description {
    margin: 0;
}

.override-rows {
    display: grid;
    grid-template-columns: auto minmax(calc(var(--ks-spacing-16) * 2), 1fr);
    gap: var(--ks-spacing-3);
    align-items: start;
}

.override-row {
    display: contents;
}

.override-label,
.override-field {
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-1);
    min-width: 0;
}

.override-label code {
    overflow-wrap: anywhere;
}
</style>
