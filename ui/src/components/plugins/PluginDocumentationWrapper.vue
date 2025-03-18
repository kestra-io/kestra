<template>
    <div class="plugin-doc" :class="{editorPlugin: plugin}">
        <PluginDocumentation :key="theme" />
    </div>
</template>
<script lang="ts" setup>
    import {computed} from "vue";
    import {useStore} from "vuex";
    import PluginDocumentation from "./PluginDocumentation.vue"

    const store = useStore();
    const plugin = computed(() => store.state.plugin.editorPlugin);
    const theme = computed(() => store.getters["misc/theme"]);
</script>

<style scoped lang="scss">
.plugin-doc {
    padding: 1px .5rem;
    background-color: var(--ks-background-body);
    height: 100%;
}

.editorPlugin{
    padding: 1rem .5rem;
}
</style>