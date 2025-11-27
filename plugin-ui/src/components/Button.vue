<script lang="ts" setup>
import image from "../remote_assets/logo.svg";
defineProps<{
    label?: string
}>()

const emit = defineEmits<{
    (e: 'click'): void
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
    @click="emit('click')"
  >
    <slot /> {{ label }} 
    <img :src="image" alt="Kestra Logo" style="width: 16px; height: 16px; margin-left: 5px;" />
  </button>
</template>
