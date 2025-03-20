<template>
    <div class="plugin-doc-wrapper" :class="{editorPlugin: plugin}">
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
.plugin-doc-wrapper {
    padding: 1px 1rem;
    background-color: var(--ks-background-body);
    padding-bottom: 5rem;
}

.editorPlugin{
    padding: 1rem ;
    min-height: 100%;
}
</style>