<template>
    <Empty
        :type="emptyType"
        demoCta
        :title="$t(`demos.namespace.${props.tab}.title`)"
    >
        <template #description>
            {{ $t(`demos.namespace.${props.tab}.message`) }}
        </template>
    </Empty>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import Empty from "../layout/empty/Empty.vue"

    const props = defineProps<{
        tab: string;
    }>()

    const EMPTY_TYPES: Record<string, string> = {
        edit: "namespace",
        history: "namespace",
        "audit-logs": "auditlogs",
        "reusable-inputs": "reusableInputs",
    }

    const emptyType = computed(() => EMPTY_TYPES[props.tab] ?? props.tab)
</script>
