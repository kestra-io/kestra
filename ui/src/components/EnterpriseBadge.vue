<template>
    <span>
        <slot />
        <LockIcon v-if="enable" class="lock-ee" />
    </span>
</template>

<script setup lang="ts">
    import LockIcon from "vue-material-design-icons/LockOutline.vue";
    defineProps({
        enable: {
            type: Boolean,
            default: false,
        },
    })
</script>

<style scoped lang="scss">
    span {
        display: inline-flex;
        align-items: center;
        justify-content: start;
    }

    .lock-ee {
        margin-left:.5rem;
        opacity:.5;
    }
</style>