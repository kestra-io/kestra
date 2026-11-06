<script setup lang="ts">
import Wrapper from "./Wrapper.vue"
const types: ("flows" | "files")[] = ["flows", "files"]
const items = ["a"]
</script>
<template>
  <section v-for="type in types" :key="type">
    <div v-if="type === 'flows'">flows</div>
    <template v-else-if="type === 'files'">
      <Wrapper v-for="item in items" :key="item">
        <template #title><span @click.stop="console.log(item)">{{ item }}</span></template>
      </Wrapper>
    </template>
  </section>
</template>
