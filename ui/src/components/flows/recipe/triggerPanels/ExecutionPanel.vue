<template>
    <KsForm class="execution-panel" labelPosition="top" @submit.prevent>
        <KsFormItem :label="$t('recipe.execution.watch_namespace')">
            <KsSelect
                v-model="recipe.watchNamespace"
                filterable
                clearable
                :placeholder="$t('recipe.execution.namespace_placeholder')"
                data-test="recipe-namespace-select"
            >
                <KsOption
                    v-for="ns in namespaceOptions"
                    :key="ns"
                    :label="ns"
                    :value="ns"
                />
            </KsSelect>
        </KsFormItem>

        <KsFormItem>
            <KsCheckbox
                v-model="recipe.includeSub"
                data-test="recipe-include-sub"
            >
                {{ $t("recipe.execution.include_sub") }}
            </KsCheckbox>
            <span class="hint">
                {{ recipe.includeSub ? $t("recipe.execution.include_sub_hint_on") : $t("recipe.execution.include_sub_hint_off") }}
            </span>
        </KsFormItem>

        <KsFormItem :label="$t('recipe.execution.states')">
            <div class="state-pills" data-test="recipe-state-pills">
                <KsCheckTag
                    v-for="stateName in watchableStates"
                    :key="stateName"
                    :checked="recipe.states.includes(stateName)"
                    pill
                    @change="toggleState(stateName)"
                >
                    <component :is="STATES[stateName].icon" class="state-icon" />
                    {{ stateName }}
                </KsCheckTag>
            </div>
            <span v-if="recipe.states.length === 0" class="hint hint-error">
                {{ $t("recipe.execution.states_required") }}
            </span>
        </KsFormItem>
    </KsForm>
</template>

<script setup lang="ts">
    import {STATES} from "@kestra-io/design-system"
    import type {RecipeState} from "../../../../composables/useFlowRecipe"

    defineProps<{
        recipe: RecipeState
        namespaceOptions: string[]
        toggleState: (stateName: string) => void
    }>()

    const watchableStates = ["FAILED", "WARNING", "SUCCESS", "KILLED", "PAUSED"]
</script>

<style scoped lang="scss">
    .execution-panel {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-4);
    }

    .state-pills {
        display: flex;
        flex-wrap: wrap;
        gap: var(--ks-spacing-2);
    }

    .state-icon {
        width: 1rem;
        height: 1rem;
        vertical-align: middle;
        margin-right: var(--ks-spacing-1);
    }

    .hint {
        display: block;
        margin-top: var(--ks-spacing-1);
        font-size: var(--ks-font-size-sm);
        color: var(--ks-text-secondary);
    }

    .hint-error {
        color: var(--ks-text-error);
    }
</style>
