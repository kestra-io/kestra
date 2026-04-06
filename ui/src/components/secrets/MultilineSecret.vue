<template>
    <div class="d-flex gap-2 w-100">
        <Editor
            v-if="pebble"
            :modelValue="modelValue"
            :class="hidden || disabled ? 'secret-value' : ''"
            :navbar="false"
            :fullHeight="false"
            :shouldFocus="false"
            schemaType="flow"
            lang="plaintext-pebble"
            input
            :largeSuggestions="false"
            @update:model-value="onEditorInput"
        />
        <el-input
            v-else
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
    import Editor from "../inputs/Editor.vue";

    const props = withDefaults(defineProps<{
        placeholder: string,
        disabled?: boolean,
        pebble?: boolean,
    }>(), {disabled: false, pebble: false});

    const modelValue = defineModel<string>({
        required: true
    })

    const hidden = ref(true);

    function onEditorInput(value: string) {
        if (value !== modelValue.value) {
            modelValue.value = value;
        }
    }

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

    .secret-value:deep(.monaco-editor .view-lines),
    .secret-value:deep(.monaco-editor .view-lines span) {
        font-family: 'DiscFont', serif !important;
        color: var(--ks-content-primary) !important;
    }
</style>
