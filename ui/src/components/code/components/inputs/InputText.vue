<template>
    <span v-if="required" class="me-1 text-danger">*</span>
    <label v-if="label" class="label" :for="uid">{{ label }}</label>
    <div class="wrapper" :class="[props.margin, props.class]">
        <el-input
            v-model="input"
            :id="uid"
            :placeholder
            :disabled
            type="textarea"
            :autosize="{minRows: 1}"
        />
    </div>
</template>

<script setup lang="ts">
    import {useId, computed} from "vue";

    defineOptions({inheritAttrs: false});

    const uid = useId();

    const emits = defineEmits(["update:modelValue"]);
    const props = defineProps({
        modelValue: {type: [String, Number, Boolean], default: undefined},
        label: {type: String, default: undefined},
        placeholder: {type: String, default: ""},
        required: {type: Boolean, default: false},
        disabled: {type: Boolean, default: false},
        margin: {type: String, default: "mt-1 mb-2"},
        class: {type: String, default: undefined},
    });

    const input = computed({
        get: () => props.modelValue,
        set: (value) => {
            emits("update:modelValue", value);
        },
    });
</script>

<style scoped lang="scss">
@import "../../styles/code.scss";
</style>
