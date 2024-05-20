<template>
    <section>
        <h1 class="heading" v-text="heading" />
        <slot name="content" />
        <el-divider v-if="!last" />
    </section>
</template>

<script setup lang="ts">
    defineProps({
        heading: {type: String, required: true},
        last: {type: Boolean, default: false},
    });
</script>

<style scoped lang="scss">
@import "@kestra-io/ui-libs/src/scss/variables.scss";

section {
    margin: calc($spacer * 2);

    & > h1.heading {
        margin-bottom: calc($spacer * 2);
        font-size: calc($font-size-base * 1.5);
        font-weight: 600;
    }
}
</style>
