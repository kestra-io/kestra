import {defineComponent} from "vue"
import {canSaveFlowTemplate, saveFlowTemplate} from "../utils/flowTemplate"

import ContentSave from "vue-material-design-icons/ContentSave.vue"
import Delete from "vue-material-design-icons/Delete.vue"
import {KsEditor} from "@kestra-io/design-system"
import RouteContext from "./routeContext"
import {flowYamlUtils as YAML_UTILS} from "@kestra-io/topology"
import action from "../models/action"
import resource from "../models/resource"
import {apiUrl} from "override/utils/route"
import {mapStores} from "pinia"
import {usePluginsStore} from "../stores/plugins"
import {useAuthStore} from "override/stores/auth"
import {useFlowStore} from "../stores/flow"
import {useClient} from "@kestra-io/kestra-sdk"

const FlowTemplateEditMixin: any = defineComponent({ // FIXME: any - avoids TS4082 from private plugin store types
    mixins: [RouteContext],
    components: {
        KsEditor,
        ContentSave,
        Delete,
    },
    data() {
        return {
            content: "" as string,
            previousContent: "" as string,
            readOnlyEditFields: {} as Record<string, string>,
            resource: resource,
            action: action,
        }
    },
    computed: {
        ...mapStores(usePluginsStore, useFlowStore, useAuthStore),
        isEdit(): boolean {
            return (
                this.$route.name === `${(this as unknown as {dataType: string}).dataType}s/update` &&
                ((this as unknown as {dataType: string}).dataType === "template" || this.$route.params.tab === "source")
            )
        },
        canSave(): boolean {
            const self = this as unknown as {dataType: string; item: Record<string, unknown>}
            return !!canSaveFlowTemplate(true, this.authStore.user, self.item, self.dataType)
        },
        canCreate(): boolean {
            const self = this as unknown as {dataType: string; item: {namespace: string}}
            return self.dataType === "flow" && !!(this.authStore.user?.isAllowed(resource.FLOW, action.CREATE, self.item.namespace))
        },
        canExecute(): boolean {
            const self = this as unknown as {dataType: string; item: {namespace: string}}
            return self.dataType === "flow" && !!(this.authStore.user?.isAllowed(resource.EXECUTION, action.CREATE, self.item.namespace))
        },
        // FIXME: any - breadcrumb link type is complex
        routeInfo(): {title: string; breadcrumb: {label: string; link: any}[]} { // FIXME: any
            const self = this as unknown as {dataType: string}
            const route: {title: string; breadcrumb: {label: string; link: any}[]} = { // FIXME: any
                title: this.isEdit ? (this.$route.params.id as string) : this.$t(`${self.dataType}`),
                breadcrumb: [
                    {
                        label: this.$t(`${self.dataType}s`),
                        link: {
                            name: `${self.dataType}s/list`,
                        },
                    },
                ],
            }

            if (this.isEdit) {
                route.breadcrumb.push(
                    {
                        label: this.$route.params.namespace as string,
                        link: {
                            name: `${self.dataType}s/list`,
                            query: {
                                namespace: this.$route.params.namespace as string,
                            },
                        },
                    },
                )
            }

            return route
        },
        item(): unknown {
            const self = this as unknown as Record<string, unknown>
            return self[(self as unknown as {dataType: string}).dataType]
        },
        canDelete(): boolean {
            const self = this as unknown as {dataType: string; item: {namespace: string}}
            return (
                !!self.item &&
                this.isEdit &&
                !!this.authStore.user?.isAllowed(
                    resource[(self.dataType.toUpperCase() as keyof typeof resource)],
                    action.DELETE,
                    self.item.namespace,
                )
            )
        },
    },
    setup() {
        const $http = useClient()
        return {
            $http,
        }
    },
    methods: {
        loadFile() {
            const self = this as unknown as {
                dataType: string;
                item: {id: string; namespace: string; revision?: unknown};
                content: string;
                previousContent: string;
                readOnlyEditFields: Record<string, string>;
                templateStore?: {template: unknown};
            }
            if (this.$route.query.copy) {
                self.item.id = ""
                self.item.namespace = ""
                delete self.item.revision
            }

            if (self.dataType === "template" && self.templateStore) {
                self.content = YAML_UTILS.stringify(self.templateStore.template)
                self.previousContent = self.content
            } else {
                if (this.flowStore.flow) {
                    self.content = this.flowStore.flow.source
                    self.previousContent = self.content
                } else {
                    self.content = ""
                    self.previousContent = ""
                }
            }

            if (this.isEdit) {
                self.readOnlyEditFields = {
                    id: self.item.id,
                }
            }
        },
        deleteConfirmMessage(): Promise<string> {
            return (this.$http as ReturnType<typeof useClient>)
                .get(`${apiUrl()}/flows/${this.flowStore.flow?.namespace}/${this.flowStore.flow?.id}/dependencies`, {params: {destinationOnly: true}})
                .then((response: {data?: {nodes?: {namespace: string; id: string}[]}}) => {
                    let warning = ""

                    if (response.data && response.data.nodes) {
                        const deps = response.data.nodes
                            .filter((n: {namespace: string; id: string}) => !(n.namespace === this.flowStore.flow?.namespace && n.id  === this.flowStore.flow?.id))
                            .map((n: {namespace: string; id: string}) => "<li>" + n.namespace + ".<code>" + n.id  + "</code></li>")
                            .join("\n")

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

                    return this.$t("delete confirm", {name: (this as unknown as {item: {id: string}}).item.id}) + warning
                })
        },
        deleteFile() {
            const self = this as unknown as {
                dataType: string;
                item: {id: string; namespace: string};
                content: string;
                previousContent: string;
                templateStore?: {deleteTemplate: (item: unknown) => Promise<void>};
            }
            if (self.item) {
                const item = self.item

                this.deleteConfirmMessage()
                    .then(message => {
                        (this as any).$toast()
                            .confirm(message, () => {
                                const deletePromise = self.dataType === "template" && self.templateStore
                                    ? self.templateStore.deleteTemplate(item)
                                    : self.dataType === "flow"
                                        ? this.flowStore.deleteFlow(item)
                                        : undefined

                                return deletePromise
                                    ?.then(() => {
                                        self.content = ""
                                        self.previousContent = ""
                                        return this.$router.push({
                                            name: self.dataType + "s/list",
                                            params: {
                                                tenant: this.$route.params.tenant,
                                            },
                                        })
                                    })
                                    .then(() => {
                                        (this as any).$toast().deleted(item.id)
                                    })
                            })
                    })
            }
        },
        save() {
            const self = this as unknown as {
                dataType: string;
                item: {id: string; namespace: string} | undefined;
                content: string;
                previousContent: string;
                readOnlyEditFields: Record<string, string>;
                templateStore?: {
                    createTemplate: (arg: {template: string}) => Promise<{source?: string; id: string}>;
                };
                onChange: () => void;
            }
            if (self.item) {
                let item: Record<string, unknown>
                try {
                    item = YAML_UTILS.parse(self.content) as Record<string, unknown>
                } catch (err) {
                    (this as any).$toast().warning(
                        (err as Error).message,
                        this.$t("invalid yaml"),
                    )

                    return
                }
                if (this.isEdit) {
                    for (const key in self.readOnlyEditFields) {
                        if (item[key] !== self.readOnlyEditFields[key]) {
                            (this as any).$toast().warning(this.$t("read only fields have changed (id, namespace...)"))

                            return
                        }
                    }
                }
                self.previousContent = self.content
                saveFlowTemplate(this as any, self.content, self.dataType) // FIXME: any
                    .then((flow: {source?: string} & Record<string, unknown>) => {
                        self.previousContent = YAML_UTILS.stringify(flow)
                        self.content = YAML_UTILS.stringify(flow)
                        self.onChange()

                        this.loadFile()
                    })
            } else {
                let item: Record<string, unknown>
                try {
                    item = YAML_UTILS.parse(self.content) as Record<string, unknown>
                } catch (err) {
                    (this as any).$toast().warning(
                        (err as Error).message,
                        this.$t("invalid yaml"),
                    )

                    return
                }
                self.previousContent = YAML_UTILS.stringify(self.item as any) // FIXME: any
                const createPromise = self.dataType === "template" && self.templateStore
                    ? self.templateStore.createTemplate({template: self.content})
                    : self.dataType === "flow"
                        ? this.flowStore.createFlow({flow: self.content})
                        : undefined

                createPromise
                    ?.then((data: {source?: string} & Record<string, unknown>) => {
                        self.previousContent = data.source ? data.source : YAML_UTILS.stringify(data)
                        self.content = data.source ? data.source : YAML_UTILS.stringify(data)
                        self.onChange()

                        this.$router.push({
                            name: `${self.dataType}s/update`,
                            params: {
                                ...item,
                                tab: "source",
                                tenant: this.$route.params.tenant,
                            },
                        })
                    })
                    .then(() => {
                        (this as any).$toast().saved(item.id as string)
                    })
            }
        },
        updatePluginDocumentation(event: any) { // FIXME: any - monaco editor event type
            const elementWrapper = YAML_UTILS.localizeElementAtIndex(event.model.getValue(), event.model.getOffsetAt(event.position))
            let element = elementWrapper?.value?.type !== undefined ? elementWrapper.value : elementWrapper?.parents?.findLast((p: {type?: unknown}) => p.type !== undefined)
            this.pluginsStore.updateDocumentation(element as any) // FIXME: any
        },
    },
})

export default FlowTemplateEditMixin
