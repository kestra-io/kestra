<template>
    <el-option
        v-for="(option, index) in options"
        :key="`${option.label}__${index}`"
        :label="option.label"
        :value="option.value"
        @click="callback(option)"
    />
</template>

<script setup lang="ts">
    import {OptionType} from "../../filters";

    defineProps({
        options: {type: Array as () => OptionType[], required: true},
        callback: {type: Function, required: true},
    });
</script>
