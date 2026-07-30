<template>
    <KsForm class="webhook-panel" labelPosition="top" @submit.prevent>
        <KsFormItem :label="$t('recipe.webhook.key_label')">
            <KsInput
                v-model="recipe.webhookKey"
                class="key-input"
                :placeholder="$t('recipe.webhook.key_placeholder')"
                data-test="recipe-webhook-key"
            />
        </KsFormItem>

        <KsFormItem :label="$t('recipe.webhook.endpoint_url')">
            <div class="endpoint-row">
                <KsInput
                    :modelValue="endpointUrl"
                    readonly
                    class="endpoint-input"
                    data-test="recipe-webhook-url"
                />
                <KsIconButton :tooltip="$t('copy')" placement="top" @click="copyUrl">
                    <ContentCopy />
                </KsIconButton>
            </div>
            <span class="hint">{{ $t("recipe.webhook.endpoint_hint") }}</span>
        </KsFormItem>
    </KsForm>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"
    import type {RecipeState} from "../../../../composables/useFlowRecipe"
    import * as Utils from "../../../../utils/utils"
    import {webhookUrl} from "../../../../utils/webhook"
    import {DEFAULT_WEBHOOK_KEY} from "../../../../utils/recipeToYaml"

    const props = defineProps<{
        recipe: RecipeState
        systemNamespace: string
        flowId: string
    }>()

    const endpointUrl = computed(() => webhookUrl({
        namespace: props.systemNamespace,
        id: props.flowId,
        key: props.recipe.webhookKey || DEFAULT_WEBHOOK_KEY,
    }))

    const copyUrl = () => {
        Utils.copy(endpointUrl.value)
    }
</script>

<style scoped lang="scss">
    .webhook-panel {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-4);
    }

    .key-input {
        font-family: var(--ks-font-family-mono, monospace);
    }

    .endpoint-row {
        display: flex;
        gap: var(--ks-spacing-2);
        align-items: center;
    }

    .endpoint-input {
        flex: 1;
        font-family: var(--ks-font-family-mono, monospace);
        font-size: var(--ks-font-size-xs);
    }

    .hint {
        display: block;
        margin-top: var(--ks-spacing-1);
        font-size: var(--ks-font-size-sm);
        color: var(--ks-text-secondary);
    }
</style>
