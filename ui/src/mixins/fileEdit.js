import { canSaveFile, saveFile } from "../utils/file";
import { mapGetters, mapState } from "vuex";

import BottomLine from "../components/layout/BottomLine";
import ContentSave from "vue-material-design-icons/ContentSave";
import Delete from "vue-material-design-icons/Delete";
import Editor from "../components/inputs/Editor";
import RouteContext from "./routeContext";
import Yaml from "yaml";
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
    created() {
        this.loadFile();
    },
    computed: {
        ...mapState("auth", ["user"]),
        ...mapGetters("flow", ["flow"]),
        isEdit() {
            return (
                this.$route.name === `${this.dataType}Edit` &&
                this.$route.query.tab === "data-source"
            );
        },
        canSave() {
            return canSaveFile(true, this.user, this.content, this.dataType);
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
        file() {
            return this[this.dataType]
        },
        canDelete() {
            return (
                this.isEdit &&
                this.user &&
                this.user.isAllowed(
                    permission[this.dataType.toUpperCase()],
                    action.DELETE,
                    this.content.namespace
                )
            );
        },
    },
    methods: {
        loadFile() {
            this.content = Yaml.stringify(this.file);
            if (this.isEdit) {
                this.readOnlyEditFields = {
                    id: this.file.id,
                };
            }
        },
        deleteFile() {
            if (this.file) {
                const file = this.file;
                this.$toast()
                    .confirm(this.$t("delete confirm", {name: file.id}), () => {
                        return this.$store
                            .dispatch("file/delete" + this.dataType.capitalize(), file)
                            .then(() => {
                                return this.$router.push({
                                    name: this.dataType + "List"
                                });
                            })
                            .then(() => {
                                this.$toast().deleted(file.id);
                            })
                    });
            }
        },
        save() {
            if (this.file) {
                let file;
                try {
                    file = Yaml.parse(this.content);
                } catch (err) {
                    this.$toast().warning(
                        this.$t("check your the yaml is valid"),
                        this.$t("invalid template")
                    );

                    return;
                }
                if (this.isEdit) {
                    for (const key in this.readOnlyEditFields) {
                        if (file[key] !== this.readOnlyEditFields[key]) {
                            this.$toast().warning(this.$t("read only fields have changed (id, namespace...)"))

                            return;
                        }
                    }
                }

                saveFile(this, file, this.dataType)
                    .then(() => {
                        this.loadFile();
                    });
            } else {
                const file = Yaml.parse(this.content);
                this.$store
                    .dispatch("template/createFile", {
                        file: file
                    })
                    .then(() => {
                        this.$router.push({
                            name: "templateEdit",
                            params: file,
                            query: { tab: "data-source" }
                        });
                    })
                    .then(() => {
                        this.$toast().success({
                            name: file.id
                        })
                    })
            }
        }
    }
};