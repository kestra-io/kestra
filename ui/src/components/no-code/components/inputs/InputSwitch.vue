<template>
    <div class="py-3">
        <span v-if="required" class="me-1 text-danger">*</span>
        <span class="me-3 label">{{ label }}:</span>
        <span class="wrapper">
            <el-switch v-model="input" @input="handleInput" :disabled />
        </span>

        <slot />
    </div>
</template>

<script setup lang="ts">
    import {ref, watch} from "vue";

    const emits = defineEmits(["update:modelValue"]);
    const props = defineProps({
        modelValue: {type: [String, Number, Boolean], default: undefined},
        label: {type: String, required: true},
        required: {type: Boolean, default: false},
        disabled: {type: Boolean, default: false},
    });

    const input = ref(props.modelValue);

    const handleInput = (value: string) => {
        emits("update:modelValue", value);
    };

    watch(
        () => props.modelValue,
        (newValue) => {
            if (newValue !== input.value) {
                input.value = newValue;
            }
        },
    );
</script>

<style scoped lang="scss">
@import "../../styles/code.scss";
</style>
