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
  <el-button @click="emit('click')" type="success">
    <slot /> {{ label }} <em>remote</em>
    <img :src="image" alt="Kestra Logo" style="width: 16px; height: 16px; margin-left: 5px;" />
  </el-button>
</template>
