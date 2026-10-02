<template>
    <KsFormItem :data-test="`unknown-field-${fieldKey}`">
        <template #label>
            <div class="unknown-head">
                <span class="label">{{ fieldKey }}</span>
                <KsIconButton
                    :tooltip="$t('no_code.remove.default')"
                    :aria-label="$t('no_code.remove.default')"
                    @click="emit('remove')"
                >
                    <TrashCanOutline />
                </KsIconButton>
            </div>
        </template>
        <code class="unknown-value">{{ preview }}</code>
        <FieldValidationErrors :errors />
    </KsFormItem>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import TrashCanOutline from "vue-material-design-icons/TrashCanOutline.vue"
    import FieldValidationErrors from "../FieldValidationErrors.vue"

    const props = defineProps<{fieldKey: string, value: unknown, errors: string[]}>()

    const emit = defineEmits<{(e: "remove"): void}>()

    const MAX_PREVIEW = 160

    const preview = computed(() => {
        const raw = typeof props.value === "object" && props.value !== null
            ? JSON.stringify(props.value)
            : String(props.value)
        return raw.length > MAX_PREVIEW ? `${raw.slice(0, MAX_PREVIEW)}…` : raw
    })
</script>

<style scoped lang="scss">
.unknown-head {
    width: 100%;
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-2);
    min-width: 0;
}

.label {
    color: var(--ks-text-primary);
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: var(--ks-font-size-sm);
    font-weight: 600;
}

.unknown-value {
    display: block;
    margin-top: var(--ks-spacing-1);
    padding: var(--ks-spacing-2) var(--ks-spacing-3);
    border: 1px solid var(--ks-border-subtle);
    border-radius: var(--ks-radius-base);
    background: var(--ks-bg-surface);
    color: var(--ks-text-secondary);
    font-family: var(--ks-font-family-mono);
    font-size: var(--ks-font-size-xs);
    overflow-wrap: anywhere;
}
</style>
