<template>
    <KsCard class="summary-card" data-test="recipe-summary-card">
        <div class="summary-header">
            <span class="summary-title">{{ $t("recipe.summary.title") }}</span>
        </div>

        <div class="summary-body">
            <span v-if="summary" class="summary-sentence" data-test="recipe-summary-sentence">
                {{ summary }}
            </span>
            <span v-else class="summary-empty">
                {{ $t("recipe.summary.empty") }}
            </span>
        </div>

        <KsAlert
            v-if="!isValid && hasInteracted"
            type="warning"
            class="summary-alert"
            :closable="false"
        >
            {{ invalidHint }}
        </KsAlert>

        <KsCollapse v-model="yamlOpen" class="yaml-preview">
            <KsCollapseItem name="yaml" :title="$t('recipe.summary.yaml_preview')">
                <KsEditor
                    v-if="yamlOpen.length > 0"
                    v-bind="editorBindings"
                    :modelValue="yamlContent"
                    lang="yaml"
                    :readOnly="true"
                    :navbar="false"
                    class="yaml-editor"
                    data-test="recipe-yaml-preview"
                />
            </KsCollapseItem>
        </KsCollapse>

        <div class="summary-actions">
            <KsButton
                type="primary"
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
    import {computed, ref} from "vue"
    import {useI18n} from "vue-i18n"
    import {KsEditor} from "@kestra-io/design-system"
    import {useEditorBindings} from "../../../composables/useEditorBindings"

    const props = defineProps<{
        summary: string
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
    const yamlOpen = ref<string[]>([])

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
        padding-bottom: var(--ks-spacing-2);
        border-bottom: 1px solid var(--ks-border-default);
    }

    .summary-title {
        display: block;
        font-size: var(--ks-font-size-md);
        font-weight: var(--ks-font-weight-semibold);
    }

    .summary-body {
        min-height: 3rem;
    }

    .summary-sentence {
        display: block;
        line-height: 1.6;
    }

    .summary-empty {
        display: block;
        font-style: italic;
        color: var(--ks-text-secondary);
    }

    .summary-alert {
        margin-top: var(--ks-spacing-2);
    }

    .yaml-preview {
        border-top: 1px solid var(--ks-border-default);
        padding-top: var(--ks-spacing-2);
    }

    .yaml-editor {
        height: 12rem;
        border-radius: var(--ks-radius-base);
        overflow: hidden;
    }

    .summary-actions {
        display: flex;
        justify-content: flex-end;
        padding-top: var(--ks-spacing-2);
        border-top: 1px solid var(--ks-border-default);
    }
</style>
