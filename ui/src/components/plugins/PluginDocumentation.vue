<template>
    <div class="plugin-documentation-div">
        <markdown v-if="editorPlugin" :source="editorPlugin.markdown" />
        <markdown v-else :source="introContent" />
    </div>
</template>

<script>
    import Markdown from "../layout/Markdown.vue";
    import {mapState} from "vuex";
    import intro from "../../assets/documentations/basic.md?raw"

    export default {
        name: "PluginDocumentation",
        components: {Markdown},
        computed: {
            ...mapState("plugin", ["editorPlugin"]),
            introContent () {
                return intro
            }
        },
        created() {
            this.$store.dispatch("plugin/list");
        }
    }
</script>

<style scoped lang="scss">
    .plugin-documentation-div {
        width: 0;
    }
</style>