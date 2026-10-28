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
        <KsText v-else-if="!loading && !values.length" tag="p" size="small" type="info" data-test="replay-overrides-empty">
            {{ $t("replayOverrides.empty", {taskId}) }}
        </KsText>

        <div v-for="value in values" :key="value.expression" class="override-row" data-test="replay-override">
            <div class="override-label">
                <code>{{ asExpression(value.expression) }}</code>
                <KsText v-if="value.kind !== 'SECRET' && value.kind !== 'BLOCK'" size="small" type="info" data-test="replay-override-rendered">
                    {{ $t("replayOverrides.rendered", {value: formatValue(value.value)}) }}
                </KsText>
            </div>

            <div v-if="isOverridable(value)" class="override-field">
                <KsInput
                    v-model="texts[value.expression!]"
                    :aria-label="value.expression"
                    data-test="replay-override-input"
                />
                <KsText v-if="parsed[value.expression!]?.error" size="small" type="danger" data-test="replay-override-error">
                    {{ $t(`replayOverrides.error.${parsed[value.expression!].error}`) }}
                </KsText>
            </div>
            <KsText v-else size="small" type="info" data-test="replay-override-locked">
                {{ $t(`replayOverrides.locked.${value.kind}`) }}
            </KsText>
        </div>
    </section>
</template>

<script setup lang="ts">
    import {computed, onMounted, reactive, ref, watch} from "vue"
    import {isDeepEqual} from "@kestra-io/design-system"
    import {useExecutionsStore} from "../../stores/executions"
    import {asExpression, editableText, formatValue, isOverridable, parseOverride, type ParsedOverride, type ReplayValue} from "./replayOverrides"

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
    const values = ref<ReplayValue[]>([])
    const texts = reactive<Record<string, string>>({})

    const parsed = computed(() => Object.fromEntries(
        values.value.filter(isOverridable).map(value => [value.expression!, parseOverride(value, texts[value.expression!] ?? "")]),
    ) as Record<string, ParsedOverride>)

    const overrides = computed(() => Object.fromEntries(
        values.value
            .filter(value => isOverridable(value) && !parsed.value[value.expression!].error && !isDeepEqual(parsed.value[value.expression!].value, value.value ?? null))
            .map(value => [value.expression!, parsed.value[value.expression!].value]),
    ))

    watch(overrides, value => overridesModel.value = value, {immediate: true})
    watch(parsed, value => invalid.value = Object.values(value).some(it => it.error), {immediate: true})

    onMounted(async () => {
        try {
            values.value = await executionsStore.loadReplayValues({executionId: props.executionId, taskRunId: props.taskRunId})
            values.value.filter(isOverridable).forEach(value => texts[value.expression!] = editableText(value.value))
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

.override-row {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    gap: var(--ks-spacing-3);
    align-items: start;
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
