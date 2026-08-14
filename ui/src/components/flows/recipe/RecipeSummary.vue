<template>
    <KsCard class="summary-card" data-test="recipe-summary-card">
        <div class="summary-header">
            <span class="summary-title">{{ $t("recipe.summary.preview_title") }}</span>
            <span class="summary-live">{{ $t("recipe.summary.live") }}</span>
        </div>

        <KsAlert
            v-if="!isValid && hasInteracted"
            type="warning"
            class="summary-alert"
            :closable="false"
        >
            {{ invalidHint }}
        </KsAlert>

        <div class="yaml-preview">
            <KsEditor
                v-bind="editorBindings"
                :modelValue="yamlContent"
                lang="yaml"
                :readOnly="true"
                :navbar="false"
                :options="{fullHeight: false}"
                class="yaml-editor"
                data-test="recipe-yaml-preview"
            />
        </div>

        <div class="summary-actions">
            <KsButton
                type="primary"
                class="create-btn"
                :disabled="!isValid"
                data-test="recipe-create-btn"
                @click="$emit('create')"
            >
                {{ $t("recipe.create_flow") }}
            </KsButton>
        </div>
    </KsCard>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import {KsEditor} from "@kestra-io/design-system"
    import {useEditorBindings} from "../../../composables/useEditorBindings"

    const props = defineProps<{
        yamlContent: string
        isValid: boolean
        hasChannel: boolean
        triggerValid: boolean
        hasInteracted: boolean
    }>()

    defineEmits<{
        create: []
    }>()

    const {t} = useI18n()
    const editorBindings = useEditorBindings()

    const invalidHint = computed(() => {
        if (!props.hasChannel && !props.triggerValid) {
            return t("recipe.summary.invalid_hint")
        }
        if (!props.hasChannel) {
            return t("recipe.then.no_channel_warning")
        }
        return t("recipe.summary.invalid_hint_trigger")
    })
</script>

<style scoped lang="scss">
    .summary-card {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-3);
        height: fit-content;
        position: sticky;
        top: var(--ks-spacing-4);
    }

    .summary-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding-bottom: var(--ks-spacing-2);
        border-bottom: var(--ks-border-width-thin) solid var(--ks-border-default);
    }

    .summary-title {
        display: block;
        font-size: var(--ks-font-size-md);
        font-weight: var(--ks-font-weight-semibold);
    }

    .summary-live {
        font-size: var(--ks-font-size-2xs);
        font-weight: var(--ks-font-weight-semibold);
        letter-spacing: 0.06em;
        color: var(--ks-text-muted);
    }

    .summary-alert {
        margin: 0;
    }

    .yaml-editor {
        height: 26rem;
        border: var(--ks-border-width-thin) solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        overflow: hidden;
    }

    .summary-actions {
        display: flex;
        padding-top: var(--ks-spacing-2);
        border-top: var(--ks-border-width-thin) solid var(--ks-border-default);
    }

    .create-btn {
        width: 100%;
    }
</style>
