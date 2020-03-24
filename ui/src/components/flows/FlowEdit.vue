<template>
    <div>
        <b-row class="row editor-wrapper">
            <b-col md="12">
                <editor
                    ref="aceEditor"
                    v-model="content"
                    @init="editorInit"
                    lang="yaml"
                    theme="chrome"
                    width="100%"
                    height="100%"
                ></editor>
            </b-col>
        </b-row>
        <bottom-line>
            <ul class="navbar-nav ml-auto">
                <li class="nav-item">
                    <b-button @click="save">
                        <span class="text-capitalize">{{$t('save')}}</span>
                        <content-save title />
                    </b-button>
                </li>
            </ul>
        </bottom-line>
    </div>
</template>

<script>
import { mapState } from "vuex";
import ContentSave from "vue-material-design-icons/ContentSave";
import Yaml from "yaml";
import BottomLine from "../layout/BottomLine";
import RouteContext from "../../mixins/routeContext";

export default {
    mixins: [RouteContext],
    components: {
        editor: require("vue2-ace-editor"),
        ContentSave,
        BottomLine
    },
    data() {
        return {
            content: "",
            readOnlyEditFields: {}
        };
    },
    created() {
        this.loadFlow();
    },
    computed: {
        ...mapState("flow", ["flow"]),
        isEdit() {
            return (
                this.$route.name === "flow" &&
                this.$route.query.tab === "data-source"
            );
        },
        flowName() {
            return (this.flow && this.flow.id) || this.$t("new");
        },
        routeInfo() {
            return {
                title: this.$t("flow creation"),
                breadcrumb: [
                    {
                        label: this.$t("flow")
                    },
                    {
                        label: this.$t("creation")
                    }
                ]
            };
        }
    },
    methods: {
        loadFlow() {
            this.content = Yaml.stringify(this.flow);
            if (this.isEdit) {
                this.readOnlyEditFields = {
                    id: this.flow.id,
                    namespace: this.flow.namespace,
                    revision: this.flow.revision
                };
            }
        },
        editorInit: function() {
            require("brace/mode/yaml");
            require("brace/theme/chrome");
            this.$refs.aceEditor.editor.textInput.focus()
        },
        save() {
            if (this.flow) {
                let flow;
                try {
                    flow = Yaml.parse(this.content);
                } catch (err) {
                    this.$bvToast.toast(this.$t("check your the yaml is valid").capitalize(), {
                        title: this.$t("invalid flow").capitalize(),
                        autoHideDelay: 5000,
                        toaster: "b-toaster-top-right",
                        variant: "warning"
                    });
                    return
                }
                if (this.isEdit) {
                    for (const key in this.readOnlyEditFields) {
                        if (flow[key] !== this.readOnlyEditFields[key]) {
                            this.$bvToast.toast(
                                this.$t(
                                    "read only fields have changed (id, namespace...)"
                                ).capitalize(),
                                {
                                    title: this.$t(
                                        "unable to save"
                                    ).capitalize(),
                                    autoHideDelay: 5000,
                                    toaster: "b-toaster-top-right",
                                    variant: "warning"
                                }
                            );
                            return;
                        }
                    }
                }
                this.$store
                    .dispatch("flow/saveFlow", {
                        flow
                    })
                    .then(() => {
                        this.$bvToast.toast(
                            this.$t("flow update ok").capitalize(),
                            {
                                title: this.$t("saved").capitalize(),
                                autoHideDelay: 5000,
                                toaster: "b-toaster-top-right",
                                variant: "success"
                            }
                        );
                    })
                    .finally(() => {
                        this.loadFlow();
                    });
            } else {
                const flow = Yaml.parse(this.content);
                this.$store
                    .dispatch("flow/createFlow", {
                        flow: flow
                    })
                    .then(() => {
                        this.$router.push({
                            name: "flow",
                            params: flow,
                            query: { tab: "data-source" }
                        });
                        this.$bvToast.toast("Created.", {
                            title: "Flow editor",
                            autoHideDelay: 5000,
                            toaster: "b-toaster-top-right",
                            variant: "success"
                        });
                    })
            }
        }
    }
};
</script>

<style scoped lang="scss">
.editor-wrapper {
    height: calc(100vh - 133px);
    > div {
        padding: 0px;
    }
}
</style>
