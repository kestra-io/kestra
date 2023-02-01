import {canSaveFlowTemplate, saveFlowTemplate} from "../utils/flowTemplate";
import {mapGetters, mapState} from "vuex";

import BottomLine from "../components/layout/BottomLine.vue";
import ContentSave from "vue-material-design-icons/ContentSave.vue";
import Delete from "vue-material-design-icons/Delete.vue";
import Editor from "../components/inputs/Editor.vue";
import RouteContext from "./routeContext";
import YamlUtils from "../utils/yamlUtils";
import action from "../models/action";
import permission from "../models/permission";

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
            previousContent: "",
            readOnlyEditFields: {},
            permission: permission,
            action: action
        };
    },
    computed: {
        ...mapState("auth", ["user"]),
        ...mapGetters("flow", ["flow"]),
        ...mapGetters("template", ["template"]),
        ...mapGetters("core", ["isUnsaved"]),
        isEdit() {
            return (
                this.$route.name === `${this.dataType}s/update` &&
                (this.dataType === "template" || this.$route.params.tab === "source")
            );
        },
        canSave() {
            return canSaveFlowTemplate(true, this.user, this.item, this.dataType);
        },
        canExecute() {
            return this.dataType === "flow" && this.user.isAllowed(permission.EXECUTION, action.CREATE, this.item.namespace)
        },
        routeInfo() {
            let route = {
                title: this.isEdit ? this.$route.params.id : this.$t(`${this.dataType}`),
                breadcrumb: [
                    {
                        label: this.$t(`${this.dataType}s`),
                        link: {
                            name: `${this.dataType}s/list`,
                        }
                    }
                ]
            };

            if (this.isEdit) {
                route.breadcrumb.push(
                    {
                        label: this.$route.params.namespace,
                        link: {
                            name: `${this.dataType}s/list`,
                            query: {
                                namespace: this.$route.params.namespace
                            }
                        }
                    }
                )
            }

            return route;
        },
        item() {
            return this[this.dataType]
        },
        canDelete() {
            return (
                this.item &&
                this.isEdit &&
                this.user &&
                this.user.isAllowed(
                    permission[this.dataType.toUpperCase()],
                    action.DELETE,
                    this.item.namespace
                )
            );
        },
    },
    methods: {
        loadFile() {
            if (this.$route.query.copy) {
                this.item.id = "";
                this.item.namespace = "";
                delete this.item.revision;
            }

            if (this.dataType === "template") {
                this.content = YamlUtils.stringify(this.template);
                this.previousContent = this.content;
            } else {
                this.content = this.flow.source;
                this.previousContent = this.content;
            }

            if (this.isEdit) {
                this.readOnlyEditFields = {
                    id: this.item.id,
                };
            }
        },
        deleteConfirmMessage() {
            return new Promise((resolve) => {
                resolve(this.$t("delete confirm", {name: this.item.id}));
            });
        }
        ,
        deleteFile() {
            if (this.item) {
                const item = this.item;

                this.deleteConfirmMessage()
                    .then(message => {
                        this.$toast()
                            .confirm(message, () => {
                                return this.$store
                                    .dispatch(`${this.dataType}/delete${this.dataType.capitalize()}`, item)
                                    .then(() => {
                                        this.content = ""
                                        this.previousContent = ""
                                        return this.$router.push({
                                            name: this.dataType + "s/list"
                                        });
                                    })
                                    .then(() => {
                                        this.$toast().deleted(item.id);
                                    })
                            });
                    });
            }
        },
        onChange() {
            this.$store.dispatch("core/isUnsaved", this.previousContent !== this.content);
        },
        save() {
            if (this.item) {
                let item;
                try {
                    item = YamlUtils.parse(this.content);
                } catch (err) {
                    this.$toast().warning(
                        err.message,
                        this.$t("invalid yaml"),
                    );

                    return;
                }
                if (this.isEdit) {
                    for (const key in this.readOnlyEditFields) {
                        if (item[key] !== this.readOnlyEditFields[key]) {
                            this.$toast().warning(this.$t("read only fields have changed (id, namespace...)"))

                            return;
                        }
                    }
                }
                this.previousContent = this.content;
                saveFlowTemplate(this, this.content, this.dataType)
                    .then((flow) => {
                        this.previousContent = YamlUtils.stringify(flow);
                        this.content = YamlUtils.stringify(flow);
                        this.onChange();

                        this.loadFile();
                    });
            } else {
                let item;
                try {
                    item = YamlUtils.parse(this.content);
                } catch (err) {
                    this.$toast().warning(
                        err.message,
                        this.$t("invalid yaml"),
                    );

                    return;
                }
                this.previousContent = YamlUtils.stringify(this.item);
                this.$store
                    .dispatch(`${this.dataType}/create${this.dataType.capitalize()}`, {[this.dataType]: this.content})
                    .then((flow) => {
                        this.previousContent = flow.source;
                        this.content = flow.source;
                        this.onChange();

                        this.$router.push({
                            name: `${this.dataType}s/update`,
                            params: {...item, ...{tab: "source"}}
                        });
                    })
                    .then(() => {
                        this.$toast().saved(item.id)
                    })
            }
        }
    }
};
