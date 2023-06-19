<template>
    <div>
        <editor-view
            :flow-id="defaultFlowTemplate.id"
            :namespace="defaultFlowTemplate.namespace"
            :is-creating="true"
            :flow-graph="flowGraph"
            :is-read-only="false"
            :total="total"
            :guided-properties="guidedProperties"
            :flow-error="flowError"
            :flow="flowWithSource"
        />
    </div>
    <div id="guided-right" />
</template>

<script>
    import EditorView from "../inputs/EditorView.vue";
    import {mapGetters, mapState} from "vuex";
    import RouteContext from "../../mixins/routeContext";
    import YamlUtils from "../../utils/yamlUtils";

    export default {
        mixins: [RouteContext],
        components: {
            EditorView
        },
        data() {
            return {
                defaultFlowTemplate: {
                    id: "hello-world",
                    namespace: "dev",
                    tasks: [{
                        id: "hello",
                        type: "io.kestra.core.tasks.log.Log",
                        message: "Kestra team wishes you a great day! 👋"
                    }]
                }
            }
        },
        beforeUnmount() {
            this.$store.commit("flow/setFlowError", undefined);
        },
        computed: {
            flowWithSource() {
                return {source: YamlUtils.stringify(this.defaultFlowTemplate)};
            },
            ...mapState("flow", ["flowGraph", "total"]),
            ...mapState("auth", ["user"]),
            ...mapState("plugin", ["pluginSingleList", "pluginsDocumentation"]),
            ...mapGetters("core", ["guidedProperties"]),
            ...mapGetters("flow", ["flowError"]),
            routeInfo() {
                return {
                    title: this.$t("flows")
                };
            },
        },
        beforeRouteLeave(to, from, next) {
            this.$store.commit("flow/setFlow", null);
            next();
        }
    };
</script>
