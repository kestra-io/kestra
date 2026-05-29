<template>
    <div class="raw-filter">
        <div v-if="filter.hasUnrenderableFilters?.value" class="unrenderable-banner">
            <KsAlert type="warning" :closable="false" :title="$t('filter.unrenderable_title')">
                {{ $t("filter.unrenderable_body") }}
            </KsAlert>
        </div>

        <slot
            name="rawEditor"
            :modelValue="draft"
            :onUpdate="(v: string) => (draft = v)"
        >
            <!-- Default editor: monospace textarea. Consumers can swap in Monaco via the rawEditor slot. -->
            <textarea
                v-model="draft"
                class="raw-textarea"
                spellcheck="false"
                autocomplete="off"
                autocorrect="off"
                autocapitalize="off"
                :rows="rows"
                :placeholder="$t('filter.raw_placeholder')"
                :readonly="filter.readOnly?.value"
            />
        </slot>

        <div class="raw-actions">
            <KsButton
                size="default"
                :disabled="!isDirty || filter.readOnly?.value"
                @click="handleApply"
            >
                {{ $t("filter.raw_apply") }}
            </KsButton>
            <KsButton
                link
                :disabled="!isDirty"
                @click="handleRevert"
            >
                {{ $t("filter.raw_revert") }}
            </KsButton>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, inject, ref, watch} from "vue"
    import {FILTER_CONTEXT_INJECTION_KEY} from "./utils/filterInjectionKeys"

    const filter = inject(FILTER_CONTEXT_INJECTION_KEY)!
    const draft = ref(filter.rawQuery?.value ?? "")

    // only update query filter text area if the user has not changed the input
    watch(() => filter.rawQuery?.value, (latest, previous) => {
        if (latest === undefined) return
        if (draft.value === (previous ?? "")) {
            draft.value = latest
        }
    })

    const isDirty = computed(() => draft.value !== (filter.rawQuery?.value ?? ""))

    /** Rough auto-sizing: at least 4 rows, grow with content up to 16. */
    const rows = computed(() => {
        const n = (draft.value.match(/\n/g)?.length ?? 0) + 1
        return Math.min(Math.max(n, 4), 16)
    })

    const handleApply = () => filter.applyRawQuery(draft.value)
    const handleRevert = () => {
        draft.value = filter.rawQuery?.value ?? ""
    }
</script>

<style lang="scss" scoped>
.raw-filter {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
    flex: 1;
    min-width: 0;
}

.unrenderable-banner {
    margin-bottom: 0.25rem;
}

.raw-textarea {
    width: 100%;
    box-sizing: border-box;
    font-family: var(--ks-font-family-mono);
    font-size: var(--ks-font-size-xs);
    line-height: 1.5;
    padding: 0.5rem 0.75rem;
    background-color: var(--ks-bg-elevated);
    color: var(--ks-text-primary);
    border: 1px solid var(--ks-border-default);
    border-radius: var(--ks-radius-sm);
    resize: vertical;

    &:focus {
        outline: none;
        border-color: var(--ks-text-link);
    }

    &[readonly] {
        opacity: 0.6;
        cursor: not-allowed;
    }
}

.raw-actions {
    display: flex;
    align-items: center;
    gap: 0.5rem;
}
</style>
