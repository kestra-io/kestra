import {canSaveFlowTemplate, saveFlowTemplate} from "../utils/flowTemplate";
import {mapGetters, mapState} from "vuex";

import BottomLine from "../components/layout/BottomLine";
import ContentSave from "vue-material-design-icons/ContentSave";
import Delete from "vue-material-design-icons/Delete";
import Editor from "../components/inputs/Editor";
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
            readOnlyEditFields: {},
            permission: permission,
            action: action
        };
    },
    computed: {
        ...mapState("auth", ["user"]),
        ...mapGetters("flow", ["flow"]),
        isEdit() {
            return (
                this.$route.name === `${this.dataType}Edit` &&
                (this.dataType === "template" || this.$route.query.tab === "data-source")
            );
        },
        canSave() {
            return canSaveFlowTemplate(true, this.user, this.item, this.dataType);
        },
        canExecute() {
            return this.dataType === "flow" && this.user.isAllowed(permission.EXECUTION, action.CREATE, this.item.namespace)
        },
        routeInfo() {
            return {
                title: this.$t(`${this.dataType} creation`),
                breadcrumb: [
                    {
                        label: this.$t(this.dataType)
                    },
                    {
                        label: this.$t("creation")
                    }
                ]
            };
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
            this.content = YamlUtils.stringify(this.item);
            if (this.isEdit) {
                this.readOnlyEditFields = {
                    id: this.item.id,
                };
            }
        },
        deleteFile() {
            if (this.item) {
                const item = this.item;
                this.$toast()
                    .confirm(this.$t("delete confirm", {name: item.id}), () => {
                        return this.$store
                            .dispatch(`${this.dataType}/delete${this.dataType.capitalize()}`, item)
                            .then(() => {
                                return this.$router.push({
                                    name: this.dataType + "List"
                                });
                            })
                            .then(() => {
                                this.$toast().deleted(item.id);
                            })
                    });
            }
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

                saveFlowTemplate(this, item, this.dataType)
                    .then(() => {
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

                this.$store
                    .dispatch(`${this.dataType}/create${this.dataType.capitalize()}`, {[this.dataType]: item})
                    .then(() => {
                        this.$router.push({
                            name: `${this.dataType}Edit`,
                            params: item,
                            query: {tab: "data-source"}
                        });
                    })
                    .then(() => {
                        this.$toast().saved(item.id)
                    })
            }
        }
    }
};
