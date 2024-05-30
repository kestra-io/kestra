<template>
    <div :class="{left, center, right}" class="buttons">
        <slot />
    </div>
</template>

<script setup lang="ts">
    defineProps({
        left: {type: Boolean, required: false},
        center: {type: Boolean, required: false},
        right: {type: Boolean, required: false},
    });
</script>

<style lang="scss">
@import url("./buttons.scss");
</style>

<style scoped lang="scss">
$width: 100%;
$max-width: 300px;

.buttons {
    display: inline-flex;
    width: $width;
    max-width: $max-width;

    &.left {
        justify-content: start;
    }

    &.center {
        justify-content: center;
    }

    &.right {
        justify-content: end;
    }
}

.left,
.right {
    z-index: 9999;
    margin: 2rem;
    position: fixed;
    bottom: 0;
}

.left {
    left: 0;
}

.right {
    right: 0;
}
</style>
