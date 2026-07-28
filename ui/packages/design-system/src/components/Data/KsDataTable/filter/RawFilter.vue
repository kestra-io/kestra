<template>
    <div class="raw-filter">
        <div v-if="filter.hasUnrenderableFilters?.value" class="unrenderable-banner">
            <KsAlert type="warning" :closable="false" :title="$t('filter.unrenderable_title')">
                {{ $t("filter.unrenderable_body") }}
            </KsAlert>
        </div>

        <div class="raw-editor">
            <slot
                name="rawEditor"
                :modelValue="draft"
                :onUpdate="(v: string) => (draft = v)"
            >
                <input
                    v-model="draft"
                    type="text"
                    class="raw-input"
                    spellcheck="false"
                    autocomplete="off"
                    autocorrect="off"
                    autocapitalize="off"
                    :placeholder="$t('filter.raw_placeholder')"
                    :readonly="filter.readOnly?.value"
                    @keydown.enter.prevent="handleApply"
                >
            </slot>

            <KsButton
                link
                size="small"
                class="raw-action raw-apply"
                :icon="CheckBold"
                :disabled="!isDirty || filter.readOnly?.value"
                :aria-label="$t('filter.raw_apply')"
                :title="$t('filter.raw_apply')"
                @click="handleApply"
            />
            <KsButton
                link
                size="small"
                class="raw-action raw-clear"
                :icon="Close"
                :disabled="filter.readOnly?.value"
                :aria-label="$t('filter.raw_revert')"
                :title="$t('filter.raw_revert')"
                @click="handleRevert"
            />
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, inject, ref, watch} from "vue"
    import {FILTER_CONTEXT_INJECTION_KEY} from "./utils/filterInjectionKeys"
    import {Close} from "./utils/icons"
    import CheckBold from "vue-material-design-icons/CheckBold.vue"

    const filter = inject(FILTER_CONTEXT_INJECTION_KEY)!
    const draft = ref(filter.rawQuery?.value ?? "")

    watch(() => filter.rawQuery?.value, (latest, previous) => {
        if (latest === undefined) return
        if (draft.value === (previous ?? "")) {
            draft.value = latest
        }
    })

    const isDirty = computed(() => draft.value !== (filter.rawQuery?.value ?? ""))

    const handleApply = () => filter.applyRawQuery(draft.value)
    const handleRevert = () => {
        draft.value = filter.rawQuery?.value ?? ""
    }
</script>

<style lang="scss" scoped>
.raw-filter {
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-2);
    flex: 1;
    min-width: 0;
}

.unrenderable-banner {
    margin-bottom: var(--ks-spacing-1);
}

.raw-editor {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-1);
    height: 32px;
    padding: 0 var(--ks-spacing-1) 0 var(--ks-spacing-3);
    background-color: var(--ks-bg-input);
    border: 1px solid var(--ks-border-strong);
    border-radius: var(--ks-radius-base);
    box-shadow: 0 1px 2px var(--ks-shadow-surface);

    &:focus-within {
        border-color: var(--ks-content-link, var(--ks-text-link));
    }
}

.raw-input {
    flex: 1;
    min-width: 0;
    height: 100%;
    border: none;
    background: transparent;
    outline: none;
    font-family: var(--ks-font-family-mono);
    font-size: var(--ks-font-size-xs);
    color: var(--ks-text-primary);

    &::placeholder {
        color: var(--ks-text-secondary);
    }

    &[readonly] {
        opacity: 0.6;
        cursor: not-allowed;
    }
}

.raw-action {
    margin: 0 !important;
    padding: var(--ks-spacing-1) !important;
    flex-shrink: 0;
    color: var(--ks-text-dim);

    :deep(svg) {
        font-size: var(--ks-font-size-base);
    }
}

.raw-apply:not(:disabled):hover {
    color: var(--ks-content-success, var(--ks-status-success));
}

.raw-clear:not(:disabled):hover {
    color: var(--ks-status-error);
}
</style>
