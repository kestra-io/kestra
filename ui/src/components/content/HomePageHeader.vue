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

<style scoped>
.home-page-header {
  --ks-markdown-paragraph-size: var(--ks-font-size-sm);
  --ks-markdown-paragraph-line-height: var(--ks-font-size-xl);
  --ks-markdown-paragraph-spacing: .5rem;

  h2 {
    padding-top: 0;
    font-size: var(--ks-font-size-xl);
    font-weight: 400;
    margin-top: 2rem;
    margin-bottom: .75rem;
    border: none;
  }
}
</style>
