<template>
    <el-cascader-panel :options="options" />
</template>

<script setup lang="ts">
    import type {PropType} from "vue";

    interface Options {
        label: string;
        value: [string, number, boolean];
        children?: Options[];
    }

    defineProps({
        options: {
            type: Object as PropType<Options>,
            required: true,
        },
    });
</script>
