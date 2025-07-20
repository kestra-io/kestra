<template>
    <div class="d-flex gap-2 w-100">
        <el-input
            class="flex-grow-1"
            :class="hidden || disabled ? 'secret-value' : ''"
            v-model="modelValue"
            :placeholder="placeholder"
            autosize
            type="textarea"
            required
            :disabled="disabled"
        />
        <el-button v-if="!disabled && modelValue" :icon="hidden ? EyeOffOutline : EyeOutline" @click="hidden = !hidden" />
    </div>
</template>

<script setup lang="ts">
    import EyeOutline from "vue-material-design-icons/EyeOutline.vue";
    import EyeOffOutline from "vue-material-design-icons/EyeOffOutline.vue";
    import {ref, watch} from "vue";

    const props = withDefaults(defineProps<{
        placeholder: string,
        disabled?: boolean,
    }>(), {disabled: false});

    const modelValue = defineModel<string>({
        required: true
    })

    const hidden = ref(true);

    watch(() => props.disabled, newVal => {
        if (newVal) {
            hidden.value = true;
        }
    })
</script>

<style scoped lang="scss">
    @font-face {
        font-family: 'DiscFont';
        src:  url('../../assets/fonts/obscure-disc.woff2') format('woff2');
    }

    .secret-value:deep(textarea:not(:placeholder-shown)) {
        font-family: 'DiscFont', serif;
    }
</style>