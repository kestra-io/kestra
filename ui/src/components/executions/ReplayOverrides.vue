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

        <div v-for="value in values" :key="value.key" class="override-row" data-test="replay-override">
            <div class="override-label">
                <code>{{ value.key }}</code>
                <KsText size="small" type="info">
                    {{ kindLabel(value) }}
                </KsText>
            </div>

            <div v-if="isOverridable(value)" class="override-field">
                <KsInput
                    v-model="texts[value.key!]"
                    :aria-label="value.key"
                    data-test="replay-override-input"
                />
                <KsText v-if="parsed[value.key!]?.error" size="small" type="danger" data-test="replay-override-error">
                    {{ $t(`replayOverrides.error.${parsed[value.key!].error}`) }}
                </KsText>
                <KsText v-else-if="value.key! in overrides" size="small" type="info" data-test="replay-override-original">
                    {{ $t("replayOverrides.original", {value: formatValue(value.value)}) }}
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
    import {useI18n} from "vue-i18n"
    import {isDeepEqual} from "@kestra-io/design-system"
    import {useExecutionsStore} from "../../stores/executions"
    import {editableText, formatValue, isOverridable, parseOverride, type ParsedOverride, type ReplayValue} from "./replayOverrides"

    const props = defineProps<{
        executionId: string
        taskRunId: string
        taskId: string
    }>()

    const overridesModel = defineModel<Record<string, unknown>>("overrides", {required: true})
    const invalid = defineModel<boolean>("invalid", {default: false})

    const {t} = useI18n()
    const executionsStore = useExecutionsStore()

    const loading = ref(true)
    const loadFailed = ref(false)
    const values = ref<ReplayValue[]>([])
    const texts = reactive<Record<string, string>>({})

    const parsed = computed(() => Object.fromEntries(
        values.value.filter(isOverridable).map(value => [value.key!, parseOverride(value, texts[value.key!] ?? "")]),
    ) as Record<string, ParsedOverride>)

    const overrides = computed(() => Object.fromEntries(
        values.value
            .filter(value => isOverridable(value) && !parsed.value[value.key!].error && !isDeepEqual(parsed.value[value.key!].value, value.value ?? null))
            .map(value => [value.key!, parsed.value[value.key!].value]),
    ))

    watch(overrides, value => overridesModel.value = value, {immediate: true})
    watch(parsed, value => invalid.value = Object.values(value).some(it => it.error), {immediate: true})

    function kindLabel(value: ReplayValue) {
        return t(`replayOverrides.kind.${value.kind}`, {taskId: value.key?.split(".")[1]})
    }

    onMounted(async () => {
        try {
            values.value = await executionsStore.loadReplayValues({executionId: props.executionId, taskRunId: props.taskRunId})
            values.value.filter(isOverridable).forEach(value => texts[value.key!] = editableText(value.value))
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
