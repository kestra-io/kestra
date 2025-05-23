<template>
    <el-switch
        :model-value="modelValue"
        @update:model-value="onInput"
        :active-action-icon="Check"
        :inactive-action-icon="Close"
    />
</template>

<script setup lang="ts">
    import Check from "vue-material-design-icons/Check.vue"
    import Close from "vue-material-design-icons/Close.vue"


    defineProps<{modelValue?: boolean}>()

    const emit = defineEmits<{(e: "update:modelValue", value: boolean): void}>()

    const onInput = (value: boolean) => emit("update:modelValue", value)
</script>
