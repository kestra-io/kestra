<template>
    <span v-if="required" class="me-1 text-danger">*</span>
    <span v-if="label" class="label">{{ label }}</span>
    <div class="mt-1 mb-2 wrapper" :class="props.class">
        <el-input
            v-model="input"
            @input="handleInput"
            :placeholder
            :disabled
            type="textarea"
            :autosize="{minRows: 1}"
        />
    </div>
</template>

<script setup lang="ts">
    import {ref, watch} from "vue";

    const emits = defineEmits(["update:modelValue"]);
    const props = defineProps({
        modelValue: {type: [String, Number, Boolean], default: undefined},
        label: {type: String, default: undefined},
        placeholder: {type: String, default: ""},
        required: {type: Boolean, default: false},
        disabled: {type: Boolean, default: false},
        class: {type: String, default: undefined},
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
