<template>
    <KsSwitch
        :modelValue="modelValue"
        @update:model-value="onInput"
        :activeActionIcon="Check"
    />
</template>

<script setup lang="ts">
    import Check from "vue-material-design-icons/Check.vue"


    defineProps<{modelValue?: boolean}>()

    const emit = defineEmits<{(e: "update:modelValue", value: boolean): void}>()

    const onInput = (value: string | number | boolean | undefined) => emit("update:modelValue", Boolean(value))
</script>
