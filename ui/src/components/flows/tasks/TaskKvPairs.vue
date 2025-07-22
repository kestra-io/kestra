<template>
    <InputPair :model-value="typeof modelValue === 'string' ? undefined : modelValue" />
</template>

<script lang="ts" setup>
    import InputPair from "../../code/components/inputs/InputPair.vue";
    import {PairField} from "../../code/utils/types";

    defineProps<{
        modelValue: PairField["value"] | string,
    }>()
</script>