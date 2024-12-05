<template>
    <div v-if="showAppId" class="app-id-display-box">
        appId: {{ store.state.doc.appId }}
    </div>
</template>

<script lang="ts" setup>
    import {computed} from "vue";
    import {useStore} from "vuex";
    import {useRoute} from "vue-router";

    const store = useStore();
    const route = useRoute();

    const showAppId = computed(() => route.query["showAppId"] !== undefined);
</script>

<style lang="scss" scoped>
.app-id-display-box {
    position: fixed;
    top: 0;
    left: 0;
    padding: .5rem 1rem;
    background-color: hotpink;
    color: black;
    z-index: 2000;
}
</style>
