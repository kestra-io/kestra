<script setup lang="ts">
import { storeToRefs } from "pinia";
import { useStore } from "../stores/counter";
import Button from "./Button.vue";
import image from "../remote_assets/logo.svg";
const store = useStore();
const { count } = storeToRefs(store);
</script>

<template>
  <Button
    @click="store.increment"
  >
    <img :src="image" alt="Kestra Logo" style="width: 20px; height: 20px; margin-right: 5px;" />
    Remote counter: {{ count }}
</Button>
</template>

<style scoped />
