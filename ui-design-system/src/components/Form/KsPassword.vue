<script setup lang="ts">
    import {ref, watch} from "vue"
    import {useFilteredProps} from "../../utils/filteredProps"
    import EyeOutline from "vue-material-design-icons/EyeOutline.vue"
    import EyeOffOutline from "vue-material-design-icons/EyeOffOutline.vue"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        modelValue?: string | number
        type?: string
        placeholder?: string
        disabled?: boolean
        showPassword?: boolean
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        suffixIcon?: any
        clearable?: boolean
        size?: "large" | "default" | "small"
        name?: string
        id?: string
        required?: boolean
        rows?: number
    }>()

    const filteredProps = useFilteredProps(props, ['autosize', 'type'])

    const hidden = ref(true)

    const emit = defineEmits<{
        "update:modelValue": [value: string | number]
        change: [value: string | number]
    }>()

    defineSlots<{
        prepend?(): unknown
        suffix?(): unknown
        default?(): unknown
    }>()

    watch(() => props.disabled, newVal => {
        if (newVal) {
            hidden.value = true
        }
    })
    
    const toggle = () => {
        hidden.value = !hidden.value;
        emit('change', props.modelValue);
    }
</script>

<template>
    <div class="ks-password w-100">
        <ks-input
            :class="hidden || disabled ? 'ks-password--masked' : ''"
            v-bind="({...filteredProps(), ...$attrs} as any)"
            @update:model-value="emit('update:modelValue', $event)"
            @change="emit('change', $event)"
            autosize
            type="textarea"
        >
            <template v-if="$slots.prepend" #prepend><slot name="prepend" /></template>
            <template v-if="$slots.suffix" #suffix><slot name="suffix" /></template>
            <template v-if="$slots.default" #default><slot /></template>
        </ks-input>
        <ks-button class="hide" link v-if="!disabled && modelValue" :icon="hidden ? EyeOffOutline : EyeOutline" @click="toggle" />
    </div>
</template>

<style scoped lang="scss">
    @font-face {
        font-family: 'DiscFont';
        src: url('../../assets/fonts/obscure-disc.woff2') format('woff2');
    }

    .ks-password {
        position: relative; 

        .hide {
            top: 5px;
            right: 5px;
            position: absolute;
        }
    }

    .ks-password--masked:deep(textarea:not(:placeholder-shown)) {
        font-family: 'DiscFont', serif;
    }
</style>
