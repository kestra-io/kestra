import {canSaveFlowTemplate, saveFlowTemplate} from "../utils/flowTemplate";

import ContentSave from "vue-material-design-icons/ContentSave.vue";
import Delete from "vue-material-design-icons/Delete.vue";
import Editor from "../components/inputs/Editor.vue";
import RouteContext from "./routeContext";
import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
import action from "../models/action";
import permission from "../models/permission";
import {pageFromRoute} from "../utils/eventsRouter";
import {apiUrl} from "override/utils/route";
import {mapStores} from "pinia";
import {useApiStore} from "../stores/api";
import {usePluginsStore} from "../stores/plugins";
import {useCoreStore} from "../stores/core";
import {useTemplateStore} from "../stores/template";
import {useAuthStore} from "override/stores/auth";
import {useFlowStore} from "../stores/flow";

export default {
    mixins: [RouteContext],
    components: {
        Editor,
        ContentSave,
        Delete
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
        ...mapStores(useApiStore, usePluginsStore, useCoreStore, useTemplateStore, useFlowStore, useAuthStore),
        guidedProperties() {
            return this.coreStore.guidedProperties;
        },
        isEdit() {
            return (
                this.$route.name === `${this.dataType}s/update` &&
                (this.dataType === "template" || this.$route.params.tab === "source")
            );
        },
        canSave() {
            return canSaveFlowTemplate(true, this.authStore.user, this.item, this.dataType);
        },
        canCreate() {
            return this.dataType === "flow" && this.authStore.user.isAllowed(permission.FLOW, action.CREATE, this.item.namespace)
        },
        canExecute() {
            return this.dataType === "flow" && this.authStore.user.isAllowed(permission.EXECUTION, action.CREATE, this.item.namespace)
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
                this.authStore.user?.isAllowed(
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
                this.content = YAML_UTILS.stringify(this.templateStore.template);
                this.previousContent = this.content;
            } else {
                if (this.flowStore.flow) {
                    this.content = this.flowStore.flow.source;
                    this.previousContent = this.content;
                } else {
                    this.content = "";
                    this.previousContent = "";
                }
            }

            if (this.isEdit) {
                this.readOnlyEditFields = {
                    id: this.item.id,
                };
            }
        },
        deleteConfirmMessage() {
            if (this.dataType === "template") {
                return new Promise((resolve) => {
                    resolve(this.$t("delete confirm", {name: this.item.id}));
                });
            }

            return this.$http
                .get(`${apiUrl()}/flows/${this.flowStore.flow.namespace}/${this.flowStore.flow.id}/dependencies`, {params: {destinationOnly: true}})
                .then(response => {
                    let warning = "";

                    if (response.data && response.data.nodes) {
                        const deps = response.data.nodes
                            .filter(n => !(n.namespace === this.flowStore.flow.namespace && n.id  === this.flowStore.flow.id))
                            .map(n => "<li>" + n.namespace + ".<code>" + n.id  + "</code></li>")
                            .join("\n");

                        warning = "<div class=\"el-alert el-alert--warning is-light mt-3\" role=\"alert\">\n" +
                            "<div class=\"el-alert__content\">\n" +
                            "<p class=\"el-alert__description\">\n" +
                            this.$t("dependencies delete flow") +
                            "<ul>\n" +
                            deps +
                            "</ul>\n" +
                            "</p>\n" +
                            "</div>\n" +
                            "</div>"
                    }

                    return this.$t("delete confirm", {name: this.item.id}) + warning;
                })
        },
        deleteFile() {
            if (this.item) {
                const item = this.item;

                this.deleteConfirmMessage()
                    .then(message => {
                        this.$toast()
                            .confirm(message, () => {
                                const deletePromise = this.dataType === "template"
                                    ? this.templateStore.deleteTemplate(item)
                                    : this.dataType === "flow"
                                        ? this.flowStore.deleteFlow(item)
                                        : undefined;

                                return deletePromise
                                    ?.then(() => {
                                        this.content = ""
                                        this.previousContent = ""
                                        return this.$router.push({
                                            name: this.dataType + "s/list",
                                            params: {
                                                tenant: this.$route.params.tenant
                                            }
                                        });
                                    })
                                    .then(() => {
                                        this.$toast().deleted(item.id);
                                    })
                            });
                    });
            }
        },
        save() {
            if (this.$tours["guidedTour"]?.isRunning?.value && !this.guidedProperties.saveFlow) {
                this.apiStore.events({
                    type: "ONBOARDING",
                    onboarding: {
                        step: this.$tours["guidedTour"]?.currentStep?._value,
                        action: "next",
                        template: this.guidedProperties.template
                    },
                    page: pageFromRoute(this.$router.currentRoute.value)
                });
                this.$tours["guidedTour"]?.nextStep();
                return;
            }

            if (this.item) {
                let item;
                try {
                    item = YAML_UTILS.parse(this.content);
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
                        this.previousContent = YAML_UTILS.stringify(flow);
                        this.content = YAML_UTILS.stringify(flow);
                        this.onChange();

                        this.loadFile();
                    });
            } else {
                let item;
                try {
                    item = YAML_UTILS.parse(this.content);
                } catch (err) {
                    this.$toast().warning(
                        err.message,
                        this.$t("invalid yaml"),
                    );

                    return;
                }
                this.previousContent = YAML_UTILS.stringify(this.item);
                const createPromise = this.dataType === "template"
                    ? this.templateStore.createTemplate({template: this.content})
                    : this.dataType === "flow"
                        ? this.flowStore.createFlow({flow: this.content})
                        : undefined;

                createPromise
                    ?.then((data) => {
                        this.previousContent = data.source ? data.source : YAML_UTILS.stringify(data);
                        this.content = data.source ? data.source : YAML_UTILS.stringify(data);
                        this.onChange();

                        this.$router.push({
                            name: `${this.dataType}s/update`,
                            params: {
                                ...item,
                                tab: "source",
                                tenant: this.$route.params.tenant
                            }
                        });
                    })
                    .then(() => {
                        this.$toast().saved(item.id)
                    })
            }
        },
        updatePluginDocumentation(event) {
            const elementWrapper = YAML_UTILS.localizeElementAtIndex(event.model.getValue(), event.model.getOffsetAt(event.position));
            let element = elementWrapper?.value?.type !== undefined ? elementWrapper.value : elementWrapper?.parents?.findLast(p => p.type !== undefined);
            this.pluginsStore.updateDocumentation(element);
        },
    },
};
