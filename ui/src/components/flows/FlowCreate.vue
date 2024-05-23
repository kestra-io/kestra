<template>
    <top-nav-bar :title="routeInfo.title" />
    <section class="full-container">
        <editor-view
            v-if="this.source"
            :flow-id="flowParsed?.id"
            :namespace="flowParsed?.namespace"
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
</template>

<script>
    import EditorView from "../inputs/EditorView.vue";
    import {mapGetters, mapState} from "vuex";
    import RouteContext from "../../mixins/routeContext";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import {apiUrl} from "override/utils/route";
    import {YamlUtils} from "@kestra-io/ui-libs";

    export default {
        mixins: [RouteContext],
        components: {
            EditorView,
            TopNavBar
        },
        data() {
            return {
                source: null
            }
        },
        created() {
            if (this.$route.query.reset) {
                localStorage.setItem("tourDoneOrSkip", undefined);
                this.$store.commit("core/setGuidedProperties", {tourStarted: false});
                this.$tours["guidedTour"].start();
            }
            this.setupFlow()
        },
        beforeUnmount() {
            this.$store.commit("flow/setFlowValidation", undefined);
        },
        methods: {
            async queryBlueprint(blueprintId) {
                return (await this.$http.get(`${this.blueprintUri}/${blueprintId}/flow`)).data;
            },
            async setupFlow() {
                if (this.$route.query.copy && this.flow){
                    this.source = this.flow.source;
                } else if (this.$route.query.blueprintId) {
                    this.source = await this.queryBlueprint(this.$route.query.blueprintId)
                } else {
                    this.source = `id: myflow
namespace: company.myteam
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
    type: io.kestra.plugin.core.http.Request
    uri: https://reqres.in/api/products
    method: POST
    contentType: application/json
    body: "{{ inputs.payload }}"

  - id: print_status
    type: io.kestra.plugin.core.log.Log
    message: hello on {{ outputs.send_data.headers.date | first }}

triggers:
  - id: daily
    type: io.kestra.plugin.core.trigger.Schedule
    cron: "0 9 * * *"`;
                }
            }
        },
        computed: {
            sourceWrapper() {
                return {source: this.source};
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
            blueprintUri() {
                return `${apiUrl(this.$store)}/blueprints/community`
            },
            flowParsed() {
                return YamlUtils.parse(this.source);
            }
        },
        beforeRouteLeave(to, from, next) {
            this.$store.commit("flow/setFlow", null);
            next();
        }
    };
</script>
