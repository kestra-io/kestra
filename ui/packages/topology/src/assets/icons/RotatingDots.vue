<template>
    <DotsCircleIcon class="loading-dots-circle" :alt="alt" />
</template>

<script lang="ts" setup>
    import DotsCircleIcon from "vue-material-design-icons/DotsCircle.vue"
    defineProps<{
        alt?: string;
    }>()
</script>

<style lang="scss" scoped>
.loading-dots-circle {
    animation: rotate 2s linear infinite;
}

@keyframes rotate {
    from { transform: rotate(0deg); }
    to { transform: rotate(360deg); }
}
</style>
