<template>
    <el-switch v-model="playgroundStore.enabled" :active-text="t('playground.toggle')" class="toggle" />
</template>

<script setup lang="ts">
    import {useI18n} from "vue-i18n";
    import {usePlaygroundStore} from "../../stores/playground";

    const {t} = useI18n();
    const playgroundStore = usePlaygroundStore();

</script>

<style lang="scss" scoped>
.toggle{
    margin-right: 1rem;
}
</style>
