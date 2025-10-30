<template>
    <span v-if="required" class="me-1 text-danger">*</span>
    <label v-if="label" class="label" :for="uid">{{ label }}</label>
    <div class="wrapper" :class="[props.margin, props.class]">
        <el-input
            v-model="input"
            :id="uid"
            :placeholder
            :disabled
            :type="disabled ? '' : 'textarea'"
            :autosize="{minRows: 1}"
            :inputStyle="haveError ? {boxShadow: '0 0 6px #ab0009'} : {}"
            :suffixIcon="disabled ? Lock : undefined"
        />
    </div>
</template>

<script setup lang="ts">
    import {useId, computed} from "vue";
    import Lock from "vue-material-design-icons/Lock.vue";

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
        haveError: {type: Boolean, default: false}
    });

    const input = computed({
        get: () => props.modelValue,
        set: (value) => {
            emits("update:modelValue", value);
        }
    });
</script>

<style scoped lang="scss">
@import "../../styles/code.scss";

:deep(.el-input__icon) {
    .lock-icon {
        color: var(--ks-content-inactive);
    }
}
</style>
