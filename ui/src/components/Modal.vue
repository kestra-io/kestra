<template>
    <el-dialog
        v-model="modelValue"
        :title="title"
        :width="width"
        destroyOnClose
        :appendToBody="true"
        @before-close="emits('update:modelValue', false)"
    >
        <template v-if="$slots.header" #header>
            <slot name="header" />
        </template>

        <template #default>
            <slot />
        </template>

        <template v-if="$slots.footer" #footer>
            <slot name="footer" />
        </template>
    </el-dialog>
</template>

<script setup lang="ts">
    const emits = defineEmits<{"update:modelValue": [value: boolean]}>();

    defineProps({
        title: {
            type: String,
            default: undefined
        },
        width: {
            type: String,
            default: "560px"
        }
    });

    const modelValue = defineModel({
        type: Boolean,
        required: true
    });
</script>
