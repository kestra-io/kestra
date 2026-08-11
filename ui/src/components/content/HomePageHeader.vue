<template>
    <div class="home-page-header">
        <h2>{{ title }}</h2>
        <slot />
    </div>
</template>

<script setup lang="ts">
    interface Props {
        title?: string
    }

    defineProps<Props>()
</script>

<style scoped lang="scss">
.home-page-header {
  h2 {
    padding-top: 0;
    font-size: var(--ks-font-size-xl);
    font-weight: 400;
    margin-top: 2rem;
    margin-bottom: .75rem;
    border: none;
  }

  :deep(p) {
    font-size: var(--ks-font-size-sm);
    line-height: var(--ks-font-size-xl);
    margin: .5rem 0;
  }
}
</style>
