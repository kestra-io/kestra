<template>
    <div class="filter-number" :class="{inline}">
        <template v-if="range">
            <KsInputNumber
                :modelValue="toNumber(bounds[0])"
                :placeholder="$t('filter.number_min')"
                controlsPosition="right"
                data-test="filter-number-min"
                @update:model-value="(value: number | undefined) => updateBound(0, value)"
            />
            <span class="separator">-</span>
            <KsInputNumber
                :modelValue="toNumber(bounds[1])"
                :placeholder="$t('filter.number_max')"
                controlsPosition="right"
                data-test="filter-number-max"
                @update:model-value="(value: number | undefined) => updateBound(1, value)"
            />
        </template>
        <KsInputNumber
            v-else
            :modelValue="toNumber(typeof modelValue === 'string' ? modelValue : '')"
            :placeholder="label"
            controlsPosition="right"
            data-test="filter-number-value"
            @update:model-value="(value: number | undefined) => emits('update:modelValue', toText(value))"
        />
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"

    const props = defineProps<{
        label?: string;
        /** A single value, or `[min, max]` when {@link range} is set. */
        modelValue: string | string[];
        range?: boolean;
        inline?: boolean;
    }>()

    const emits = defineEmits<{
        "update:modelValue": [value: string | string[]];
    }>()

    const bounds = computed(() => {
        const [min = "", max = ""] = Array.isArray(props.modelValue) ? props.modelValue : []
        return [min, max]
    })

    const toNumber = (value: string) => (value === "" ? undefined : Number(value))
    const toText = (value: number | undefined | null) => (value == null ? "" : String(value))

    const updateBound = (index: 0 | 1, value: number | undefined) => {
        const next = [...bounds.value]
        next[index] = toText(value)
        emits("update:modelValue", next)
    }
</script>

<style scoped>
.filter-number {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-2);
    padding: var(--ks-spacing-4);

    &.inline {
        padding: 0;
        flex: 1;
        min-width: 0;
    }

    & .separator {
        color: var(--ks-text-dim);
    }
}
</style>
