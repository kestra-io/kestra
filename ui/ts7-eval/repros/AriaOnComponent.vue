<script setup lang="ts">
import Wrapper from "./Wrapper.vue"
import Child from "./Child.vue"
</script>
<template>
  <Child label="a" aria-label="ok on an element root" />
  <Wrapper aria-label="rejected on a component root" role="group" />
</template>
