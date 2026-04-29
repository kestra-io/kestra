<template>
    <ElTooltip
        :persistent="false"
        :hideAfter="0"
        transition=""
        v-bind="$attrs"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
        <template v-if="$slots.content" #content>
            <slot name="content" />
        </template>
    </ElTooltip>
</template>

<script setup lang="ts">
    import {ElTooltip} from "element-plus";
    defineOptions({inheritAttrs: false});
</script>
