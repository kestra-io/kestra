<template>
    <div :class="{absolute, center}" class="buttons">
        <slot />
    </div>
</template>

<script setup lang="ts">
    defineProps({
        absolute: {type: Boolean, required: false},
        center: {type: Boolean, required: false},
    });
</script>

<style scoped lang="scss">
$width: 100%;
$max-width: 300px;

.buttons {
    display: inline-flex;
    justify-content: end;
    width: $width;
    max-width: $max-width;

    &.center {
        justify-content: center;
    }
}

.absolute {
    z-index: 9999;
    margin: 2rem;
    position: absolute;
    bottom: 0;
    right: 0;
}
</style>
