<template>
    <div class="d-flex align-items-center cursor-pointer">
        <div :style class="circle" />
        <span>{{ $filters.cap(status) }}</span>
    </div>
</template>

<script setup>
    import {computed} from "vue";

    const props = defineProps({status: {type: String, required: true}});

    const style = computed(() => ({
        backgroundColor: `var(--ks-chart-${props.status.toLowerCase()})`,
    }));
</script>

<style scoped lang="scss">
.circle {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    margin-right: 8px;
}
</style>
