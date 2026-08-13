<template>
    <KsForm class="execution-panel" labelPosition="top" @submit.prevent>
        <KsFormItem :label="$t('recipe.execution.watch_namespace')">
            <KsSelect
                v-model="recipe.watchNamespace"
                filterable
                clearable
                :loading="namespacesLoading"
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
                    <component :is="STATES[stateName].icon" class="state-icon" :class="`state-icon-${stateName.toLowerCase()}`" />
                    {{ stateName }}
                </KsCheckTag>
            </div>
            <span v-if="recipe.states.length === 0" class="hint hint-error">
                {{ $t("recipe.execution.states_required") }}
            </span>
            <span v-else-if="recipe.states.includes('FAILED')" class="hint hint-reco">
                <Check class="hint-icon" />
                {{ $t("recipe.execution.states_recommended") }}
            </span>
        </KsFormItem>
    </KsForm>
</template>

<script setup lang="ts">
    import {STATES} from "@kestra-io/design-system"
    import Check from "vue-material-design-icons/Check.vue"
    import type {RecipeState} from "../../../../composables/useFlowRecipe"

    withDefaults(defineProps<{
        recipe: RecipeState
        namespaceOptions: string[]
        namespacesLoading?: boolean
        toggleState: (stateName: string) => void
    }>(), {
        namespacesLoading: false,
    })

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
        width: var(--ks-spacing-4);
        height: var(--ks-spacing-4);
        vertical-align: middle;
        margin-right: var(--ks-spacing-1);
    }

    .state-icon-failed {
        color: var(--ks-status-error);
    }

    .state-icon-warning {
        color: var(--ks-status-warning);
    }

    .state-icon-success {
        color: var(--ks-status-success);
    }

    .state-icon-killed {
        color: var(--ks-status-neutral);
    }

    .state-icon-paused {
        color: var(--ks-status-pending);
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

    .hint-reco {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        color: var(--ks-text-secondary);
    }

    .hint-icon {
        display: inline-flex;
        font-size: var(--ks-font-size-md);
        color: var(--ks-text-success);
    }
</style>
