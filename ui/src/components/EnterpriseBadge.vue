<template>
    <span>
        <slot />
        <LockIcon v-if="enable" class="lock-ee" />
    </span>
</template>

<script lang="ts" setup>
    import LockIcon from "vue-material-design-icons/LockOutline.vue";
    defineProps({
        enable: {
            type: Boolean,
            default: false,
        },
    })
</script>

<style lang="scss" scoped>
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