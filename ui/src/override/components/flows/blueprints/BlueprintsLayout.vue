<template>
    <slot v-if="embed" />
    <div class="blueprints" v-else>
        <slot />
    </div>
</template>

<script setup lang="ts">
    defineProps<{
        embed: boolean;
    }>();
</script>

<style scoped lang="scss">
    .blueprints {
        background: url('../../../../assets/dots-bg.jpg') no-repeat top left;
        background-color: #F6F6FA;
        .dark & {
            background:  url('../../../../assets/dots-bg-dark.jpg') no-repeat top left;
            background-color: #1B1E27;
        }
    }
</style>
