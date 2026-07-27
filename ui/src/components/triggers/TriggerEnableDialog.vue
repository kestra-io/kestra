<template>
    <KsDialog
        :modelValue="modelValue"
        destroyOnClose
        :appendToBody="true"
        @update:modelValue="emit('update:modelValue', !!$event)"
    >
        <template #header>
            <span>{{ count && count > 1 ? t("trigger enable dialog.bulk title", {count}) : t("trigger enable dialog.title") }}</span>
        </template>

        <KsAlert type="info" :closable="false" class="description">
            {{ count && count > 1 ? t("trigger enable dialog.bulk description") : t("trigger enable dialog.description") }}
        </KsAlert>

        <slot />

        <p id="trigger-enable-strategy-label" class="strategy-label">
            {{ t("trigger enable dialog.strategy") }}
        </p>

        <KsRadioGroup v-model="choice" class="radio-vertical" aria-labelledby="trigger-enable-strategy-label">
            <KsRadio value="SKIP" class="radio-item">
                {{ t("trigger enable dialog.options.skip") }}
            </KsRadio>
            <KsRadio value="FOLLOW_CONFIGURATION" class="radio-item">
                <i18n-t keypath="trigger enable dialog.options.follow" scope="global">
                    <template #property>
                        <code>recoverMissedSchedules</code>
                    </template>
                </i18n-t>
            </KsRadio>
        </KsRadioGroup>

        <template #footer>
            <KsButton @click="emit('update:modelValue', false)">
                {{ t("cancel") }}
            </KsButton>
            <KsButton type="primary" @click="confirm">
                {{ t("enable") }}
            </KsButton>
        </template>
    </KsDialog>
</template>

<script setup lang="ts">
    import {ref, watch} from "vue"
    import {useI18n} from "vue-i18n"

    const {t} = useI18n()

    const props = defineProps<{
        modelValue: boolean;
        count?: number;
    }>()

    const emit = defineEmits<{
        (e: "update:modelValue", value: boolean): void;
        (e: "confirm", recoverMissedSchedules: boolean | undefined): void;
    }>()

    const choice = ref<"SKIP" | "FOLLOW_CONFIGURATION">("SKIP")

    watch(() => props.modelValue, (open) => {
        if (open) choice.value = "SKIP"
    })

    const confirm = () => {
        emit("confirm", choice.value === "FOLLOW_CONFIGURATION" ? true : undefined)
        emit("update:modelValue", false)
    }
</script>

<style lang="scss" scoped>
.description {
    margin-bottom: var(--ks-spacing-3);
}

.strategy-label {
    margin-bottom: var(--ks-spacing-2);
}

.radio-vertical {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
}

.radio-item {
    margin-bottom: var(--ks-spacing-1);
}
</style>
