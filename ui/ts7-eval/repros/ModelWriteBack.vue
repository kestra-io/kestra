<script setup lang="ts">
import {ref} from "vue"
import Toggle from "./Toggle.vue"
const open = ref(false)
</script>
<template><Toggle v-model="open" /></template>
