<template>
    <KsSearch
        :class="{'full-width': fullWidth}"
        v-model="internalValue"
        :placeholder="placeholder"
        clearable
        @update:modelValue="(v) => emits('update:model-value', v ?? '')"
    />
</template>

<script setup lang="ts">
    import {ref, watch} from "vue"
    import KsSearch from "../../../../Form/KsSearch.vue"

    const props = defineProps<{
        modelValue: string;
        fullWidth?: boolean;
        placeholder?: string;
    }>()

    const emits = defineEmits<{
        "update:model-value": [value: string];
    }>()

    const internalValue = ref(props.modelValue)

    watch(
        () => props.modelValue,
        (newVal) => {
            internalValue.value = newVal
        },
    )
</script>

<style lang="scss" scoped>
    .full-width {
        margin-right: 0.5rem;
    }
</style>
