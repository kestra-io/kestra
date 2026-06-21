<template>
    <div class="ks-logo-badge">
        <img :src="glyph" alt="" class="ks-logo-badge__glyph">
        <span class="ks-logo-badge__check">
            <CheckBold />
        </span>
    </div>
</template>

<script setup lang="ts">
    import CheckBold from "vue-material-design-icons/CheckBold.vue"
    import glyph from "../../assets/images/kestra-glyph.svg"
</script>

<style scoped lang="scss">
    .ks-logo-badge {
        position: relative;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        padding: var(--ks-spacing-3);
        border: 1px solid var(--ks-border-success);
        border-radius: var(--ks-radius-lg);
        background: var(--ks-bg-overlay);
        box-shadow: 0 1px 2px var(--ks-shadow-element);

        &__glyph {
            display: block;
            width: 3rem;
            height: 3rem;
        }

        &__check {
            position: absolute;
            right: calc(-1 * var(--ks-spacing-2));
            bottom: calc(-1 * var(--ks-spacing-2));
            display: inline-flex;
            align-items: center;
            justify-content: center;
            padding: var(--ks-spacing-1);
            border: 1px solid var(--ks-border-success);
            border-radius: 50%;
            background: var(--ks-bg-overlay);
            box-shadow: 0 8px 12px var(--ks-shadow-elevated);
            color: var(--ks-icon-success);
        }
    }
</style>
