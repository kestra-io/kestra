<template>
    <div class="d-flex align-items-center cursor-pointer">
        <div :style="circle" />
        <span v-if="label">{{ title || $filters.cap(status) }}</span>
    </div>
</template>

<script setup>
    import {computed, defineProps} from "vue";

    const StatusRemap = {
        "failed": "error",
        "warn": "warning"
    };

    const props = defineProps({
        status: {
            type: String,
            required: true,
            default: undefined
        },
        label: {
            type: Boolean,
            default: true
        },
    });

    const circle = computed(() => {
        const statusVarname = (StatusRemap[props.status.toLowerCase()] ?? props.status)?.toLowerCase();
        return {
            backgroundColor: `var(--ks-content-${statusVarname})`,
            width: "6px",
            height: "6px",
            borderRadius: "50%",
            marginRight: "8px",
        };
    });
</script>