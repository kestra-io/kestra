<template>
    <div class="ks-password w-100">
        <KsInput
            v-model="model"
            :class="hidden || disabled ? 'ks-password--masked' : ''"
            v-bind="({...filteredProps(), ...$attrs} as any)"
            @change="emit('change', $event)"
            autosize
            type="textarea"
        >
            <template v-if="$slots.prepend" #prepend>
                <slot name="prepend" />
            </template>
            <template v-if="$slots.suffix" #suffix>
                <slot name="suffix" />
            </template>
            <template v-if="$slots.default" #default>
                <slot />
            </template>
        </KsInput>
        <KsButton class="hide" link v-if="!disabled && model" :icon="hidden ? EyeOffOutline : EyeOutline" @click="toggle" />
    </div>
</template>

<script setup lang="ts">
    import {ref, watch} from "vue"
    import {useFilteredProps} from "../../utils/filteredProps"
    import EyeOutline from "vue-material-design-icons/EyeOutline.vue"
    import EyeOffOutline from "vue-material-design-icons/EyeOffOutline.vue"

    defineOptions({inheritAttrs: false})

    const model = defineModel<string | number>()

    const props = defineProps<{
        placeholder?: string
        disabled?: boolean
    }>()

    const emit = defineEmits<{
        change: [value: string | number]
    }>()

    defineSlots<{
        prepend?(): unknown
        suffix?(): unknown
        default?(): unknown
    }>()

    const hidden = ref(true)

    const filteredProps = useFilteredProps(props)

    watch(() => props.disabled, newVal => {
        if (newVal) {
            hidden.value = true
        }
    })

    const toggle = () => {
        hidden.value = !hidden.value
        emit("change", model.value ?? "")
    }
</script>

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
