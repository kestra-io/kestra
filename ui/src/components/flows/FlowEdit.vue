<template>
    <div>
        <b-row>
            <b-col>
                <h1>
                    <router-link to="/flows">Flow</router-link>
                    <chevron-right />
                    {{flowName}}
                </h1>
            </b-col>
        </b-row>
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
                        <span class="text-capitalize">{{$t('save')}} </span><content-save title />
                    </b-button>
                </li>
            </ul>
        </bottom-line>
    </div>
</template>

<script>
import { mapState } from "vuex";
import ChevronRight from "vue-material-design-icons/ChevronRight";
import ContentSave from "vue-material-design-icons/ContentSave";
import Yaml from "yaml";
import BottomLine from "../layout/BottomLine";

export default {
    components: {
        ChevronRight,
        editor: require("vue2-ace-editor"),
        ContentSave,
        BottomLine
    },
    data() {
        return {
            content: ""
        };
    },
    created() {
        this.loadFlow();
    },
    computed: {
        ...mapState("flow", ["flow"]),
        flowName() {
            return (this.flow && this.flow.id) || this.$t("new");
        }
    },
    methods: {
        loadFlow() {
            if (this.$route.name === "flowsEdit") {
                this.$store
                    .dispatch("flow/loadFlow", this.$route.params)
                    .then(() => {
                        this.content = Yaml.stringify(this.flow);
                    });
            }
        },
        editorInit: function() {
            require("brace/mode/yaml");
            require("brace/theme/chrome");
            const editor = this.$refs.aceEditor.editor;
            editor.setFontSize("18px");
        },
        save() {
            if (this.flow) {
                this.$store
                    .dispatch("flow/saveFlow", {
                        flow: Yaml.parse(this.content)
                    })
                    .finally(this.loadFlow);
            } else {
                const flow = Yaml.parse(this.content);
                this.$store
                    .dispatch("flow/createFlow", {
                        flow: flow
                    })
                    .then(() => {
                        this.$router.push({ name: "flowsEdit", params: flow });
                        this.$bvToast.toast("Created.", {
                            title: "Flow editor",
                            autoHideDelay: 5000,
                            toaster: "b-toaster-top-right",
                            variant: "success"
                        });
                    })
                    .catch(() => {
                        this.$bvToast.toast("Failed to save.", {
                            title: "Flow save error",
                            autoHideDelay: 5000,
                            toaster: "b-toaster-top-right",
                            variant: "danger"
                        });
                    });
            }
        }
    },
    destroyed() {
        this.$store.commit("flow/setFlow", undefined);
    }
};
</script>

<style scoped lang="scss">
.editor-wrapper {
    height: calc(100vh - 133px);
    >div {
        padding: 0px;
    }
}
</style>
