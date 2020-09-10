<template>
    <div>
        <editor
            v-model="content"
            lang="yaml"
        ></editor>
        <bottom-line v-if="canSave || canDelete">
            <ul class="navbar-nav ml-auto">
                <li class="nav-item">
                    <b-button
                        class="btn-danger"
                        v-if="canDelete"
                        @click="deleteFlow">
                        <delete/>
                        <span>{{$t('delete')}}</span>
                    </b-button>

                    <b-button @click="save" v-if="canSave">
                        <content-save />
                        <span>{{$t('save')}}</span>
                    </b-button>
                </li>
            </ul>
        </bottom-line>
    </div>
</template>

<script>
import { mapState, mapGetters } from "vuex";
import ContentSave from "vue-material-design-icons/ContentSave";
import Delete from "vue-material-design-icons/Delete";
import Yaml from "yaml";
import BottomLine from "../layout/BottomLine";
import Editor from "../inputs/Editor";
import RouteContext from "../../mixins/routeContext";
import permission from "../../models/permission";
import action from "../../models/action";
import { canSaveFlow, saveFlow } from "../../utils/flow";

export default {
    mixins: [RouteContext],
    components: {
        Editor,
        ContentSave,
        Delete,
        BottomLine
    },
    data() {
        return {
            content: "",
            readOnlyEditFields: {},
            permission: permission,
            action: action
        };
    },
    created() {
        this.loadFlow();
    },
    computed: {
        ...mapGetters("flow", ["flow"]),
        ...mapState("auth", ["user"]),
        isEdit() {
            return (
                this.$route.name === "flowEdit" &&
                this.$route.query.tab === "data-source"
            );
        },
        canSave() {
            return canSaveFlow(true, this.user, this.content);
        },
        canDelete() {
            return this.isEdit && this.user &&
                this.user.isAllowed(permission.FLOW, action.DELETE, this.content.namespace)
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
        deleteFlow() {
            if (this.flow) {
                const flow = this.flow;
                this.$toast()
                    .confirm(this.$t("delete confirm", {name: flow.id}), () => {
                        return this.$store
                            .dispatch("flow/deleteFlow", flow)
                            .then(() => {
                                return this.$router.push({
                                    name: "flowsList"
                                });
                            })
                            .then(() => {
                                this.$toast().deleted(flow.id);
                            })
                    });
            }
        },
        save() {
            if (this.flow) {
                let flow;
                try {
                    flow = Yaml.parse(this.content);
                } catch (err) {
                    this.$toast().warning(
                        this.$t("check your the yaml is valid"),
                        this.$t("invalid flow")
                    );

                    return;
                }
                if (this.isEdit) {
                    for (const key in this.readOnlyEditFields) {
                        if (flow[key] !== this.readOnlyEditFields[key]) {
                            this.$toast().warning(this.$t("read only fields have changed (id, namespace...)"))

                            return;
                        }
                    }
                }

                saveFlow(this, flow)
                    .then(() => {
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
                            name: "flowEdit",
                            params: flow,
                            query: { tab: "data-source" }
                        });
                    })
                    .then(() => {
                        this.$toast().success({
                            name: flow.id
                        })
                    })
            }
        }
    }
};
</script>
