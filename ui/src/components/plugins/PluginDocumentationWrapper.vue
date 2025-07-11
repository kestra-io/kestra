<template>
    <div class="plugin-doc-wrapper" :class="{editorPlugin: pluginsStore.editorPlugin}">
        <PluginDocumentation :key="miscStore.theme" />
    </div>
</template>
<script lang="ts" setup>
    import PluginDocumentation from "./PluginDocumentation.vue"
    import {usePluginsStore} from "../../stores/plugins";
    import {useMiscStore} from "../../stores/misc";

    const pluginsStore = usePluginsStore()
    const miscStore = useMiscStore();
</script>

<style scoped lang="scss">
.plugin-doc-wrapper {
    padding: 1px 1rem;
    background-color: var(--ks-background-panel);
    padding-bottom: 5rem;
}

.plugin-doc {
    background-color: var(--ks-background-panel) !important;
}

.editorPlugin{
    padding: 1rem ;
    min-height: 100%;
}
</style>