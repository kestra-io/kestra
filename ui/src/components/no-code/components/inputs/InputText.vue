<template>
    <span v-if="required" class="me-1 text-danger">*</span>
    <label v-if="label" class="label" :for="uid">{{ label }}</label>
    <div class="wrapper" :class="[props.margin, props.class]">
        <KsInput
            ref="elInputRef"
            :modelValue="(input as string | number | undefined)"
            @update:model-value="input = $event"
            :id="uid"
            :placeholder
            :disabled
            :type="disabled ? '' : 'textarea'"
            :autosize="{minRows: 1}"
            :inputStyle="haveError ? {boxShadow: '0 0 6px #ab0009'} : {}"
            :suffixIcon="SuffixIcon"
        />
    </div>
</template>

<script setup lang="ts">
    import {useId, computed, useTemplateRef} from "vue"
    import Lock from "vue-material-design-icons/Lock.vue"

    const SuffixIcon = computed(() => {
        if (props.disabled) {
            return Lock
        }

        return undefined
    })

    defineOptions({inheritAttrs: false})

    const uid = useId()
    const elInputRef = useTemplateRef("elInputRef")

    const emits = defineEmits(["update:modelValue"])
    const props = defineProps({
        modelValue: {type: [String, Number, Boolean], default: undefined},
        label: {type: String, default: undefined},
        placeholder: {type: String, default: ""},
        required: {type: Boolean, default: false},
        disabled: {type: Boolean, default: false},
        margin: {type: String, default: "mt-1 mb-2"},
        class: {type: String, default: undefined},
        haveError: {type: Boolean, default: false},
    })

    const input = computed({
        get: () => props.modelValue,
        set: (value) => {
            emits("update:modelValue", value)
        },
    })

    defineExpose({
        focus: () => {
            (elInputRef.value as any)?.focus?.()
        },
    })
</script>

<style scoped lang="scss">
@import "../../styles/code.scss";

:deep(.kel-input__icon) {
    .lock-icon {
        color: var(--ks-text-inactive);
    }
}
</style>
