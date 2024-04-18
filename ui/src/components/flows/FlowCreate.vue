<template>
    <top-nav-bar :title="routeInfo.title" />
    <section class="full-container">
        <editor-view
            :flow-id="defaultFlowTemplate.id"
            :namespace="defaultFlowTemplate.namespace"
            :is-creating="true"
            :flow-graph="flowGraph"
            :is-read-only="false"
            :is-dirty="true"
            :total="total"
            :guided-properties="guidedProperties"
            :flow-validation="flowValidation"
            :flow="sourceWrapper"
            :next-revision="1"
        />
    </section>
    <div id="guided-right" />
</template>

<script>
    import EditorView from "../inputs/EditorView.vue";
    import {mapGetters, mapState} from "vuex";
    import RouteContext from "../../mixins/routeContext";
    import TopNavBar from "../../components/layout/TopNavBar.vue";

    export default {
        mixins: [RouteContext],
        components: {
            EditorView,
            TopNavBar
        },
        created() {
            if (this.$route.query.reset) {
                localStorage.setItem("tourDoneOrSkip", undefined);
                this.$store.commit("core/setGuidedProperties", {
                    tourStarted: false,
                    flowSource: undefined,
                    saveFlow: false,
                    executeFlow: false,
                    validateInputs: false,
                    monacoRange: undefined,
                    monacoDisableRange: undefined
                });
                this.$tours["guidedTour"].start();
            }
        },
        beforeUnmount() {
            this.$store.commit("flow/setFlowValidation", undefined);
        },
        computed: {
            sourceWrapper() {
                return {source: this.defaultFlowTemplate};
            },
            defaultFlowTemplate() {
                if(this.$route.query.copy && this.flow){
                    return this.flow.source;
                }

                return `id: myflow
namespace: myteam
description: Save and Execute the flow

labels:
  env: dev
  project: myproject

inputs:
  - id: payload
    type: JSON
    defaults: |-
      [{"name": "kestra", "rating": "best in class"}]

tasks:
  - id: send_data
    type: io.kestra.plugin.fs.http.Request
    uri: https://reqres.in/api/products
    method: POST
    contentType: application/json
    body: "{{ inputs.payload }}"

  - id: print_status
    type: io.kestra.core.tasks.log.Log
    message: hello on {{ outputs.send_data.headers.date | first }}

triggers:
  - id: daily
    type: io.kestra.core.models.triggers.types.Schedule
    cron: "0 9 * * *"`;
            },
            ...mapState("flow", ["flowGraph", "total"]),
            ...mapState("auth", ["user"]),
            ...mapState("plugin", ["pluginSingleList", "pluginsDocumentation"]),
            ...mapGetters("core", ["guidedProperties"]),
            ...mapGetters("flow", ["flow", "flowValidation"]),
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
