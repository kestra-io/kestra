<script lang="ts" setup>
defineProps<{
    label: string
}>()
</script>

<template>
 <button
    style="
      border: 0 solid #e2e8f0;
      margin-top: 10px;
      background-color: rgb(100, 149, 237);
      border-radius: 0.25rem;
      font-weight: 700;
      padding: 0.5rem 1rem 0.5rem 1rem;
      color: rgb(24, 24, 24);
    "
  >
    <slot /> {{ label }}
  </button>
</template>
