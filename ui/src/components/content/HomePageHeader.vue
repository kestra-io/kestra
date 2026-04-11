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
    font-size: var(--kel-font-size-extra-large);
    font-weight: 400;
    margin-top: 2rem;
    margin-bottom: .75rem;
    border: none;
  }

  :deep(p) {
    font-size: var(--kel-font-size-small);
    line-height: var(--kel-font-size-extra-large);
    margin: .5rem 0;
  }
}
</style>
