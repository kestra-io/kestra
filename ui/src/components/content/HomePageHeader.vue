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
$font-size-md: 1.5rem;
$font-size-xs: .875rem;

.home-page-header {
  h2 {
    padding-top: 0;
    font-size: $font-size-md;
    font-weight: 400;
    margin-top: 2rem;
    margin-bottom: .75rem;
    border: none;
  }

  :deep(p) {
    font-size: $font-size-xs;
    line-height: 1.5em;
    margin: .5rem 0;
  }
}
</style>
