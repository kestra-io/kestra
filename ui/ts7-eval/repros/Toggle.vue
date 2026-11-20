<script setup lang="ts">
const model = defineModel<boolean>()
</script>
<template><input type="checkbox" :checked="model" @change="model = !model"></template>
