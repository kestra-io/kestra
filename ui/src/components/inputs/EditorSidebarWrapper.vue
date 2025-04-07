<template>
    <EditorSidebar :current-n-s="namespace" style="width: 100%;height: 100%;" />
</template>

<script lang="ts" setup>
    import {computed} from "vue";
    import {useStore} from "vuex";
    import EditorSidebar from "./EditorSidebar.vue";

    const store = useStore();
    const namespace = computed(() => store.getters["flow/namespace"]);
</script>