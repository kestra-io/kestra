<template>
    <div class="plugin-documentation-div">
        <markdown v-if="editorPlugin" :source="editorPlugin.markdown" />
        <div v-else>
            <div class="img get-started" />
            <el-alert type="info" :title="$t('focus task')" show-icon :closable="false" />
        </div>
    </div>
</template>

<script>
    import Markdown from "../layout/Markdown.vue";
    import {mapState} from "vuex";

    export default {
        name: "PluginDocumentation",
        components: {Markdown},
        computed: {
            ...mapState("plugin", ["editorPlugin"]),
        },
        created() {
            this.$store.dispatch("plugin/list");
        },
    }
</script>

<style scoped lang="scss">
    .plugin-documentation-div {
        width: 0;
    }
</style>