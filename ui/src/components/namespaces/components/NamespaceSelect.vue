<template>
    <el-select
        class="fit-text"
        :model-value="value"
        @update:model-value="onInput"
        :disabled="readonly"
        clearable
        :placeholder="$t('Select namespace')"
        :persistent="false"
        filterable
        :allow-create="allowCreate"
    >
        <el-option
            v-for="item in groupedNamespaces"
            :key="item.code"
            :class="'level-' + item.level"
            :label="item.label"
            :value="item.code"
        />
    </el-select>
</template>
<script>
    import {mapState} from "vuex";
    import {mapStores} from "pinia";
    import {useMiscStore} from "../../../stores/misc";
    import _uniqBy from "lodash/uniqBy";
    import permission from "../../../models/permission";
    import action from "../../../models/action";

    export default {
        props: {
            dataType: {
                type: String,
                required: true,
            },
            value: {
                type: String,
                default: undefined,
            },
            allowCreate: {
                type: Boolean,
                default: false,
            },
            isFilter: {
                type: Boolean,
                default: true,
            },
            includeSystemNamespace: {
                type: Boolean,
                default: false,
            },
            readonly: {
                type: Boolean,
                default: false,
            },
            all: {
                type: Boolean,
                default: false,
            }
        },
        emits: ["update:modelValue"],
        created() {
            if (
                this.user &&
                this.user.hasAnyActionOnAnyNamespace(
                    permission.NAMESPACE,
                    action.READ,
                )
            ) {
                this.load();
            }
        },
        computed: {
            ...mapState("namespace", ["datatypeNamespaces"]),
            ...mapState("auth", ["user"]),
            ...mapStores(useMiscStore),
        },
        data() {
            return {
                groupedNamespaces: [],
                localNamespaceInput: "",
            };
        },
        methods: {
            onInput(value) {
                this.$emit("update:modelValue", value);
                this.localNamespaceInput = value;
                this.load();
            },
            groupNamespaces(namespaces) {
                let res = [];
                namespaces.forEach((ns) => {
                    // Let's say one of our namespace is com.domain.service.product
                    // We want to get the following "groups" from it :
                    // com
                    // com.domain
                    // com.domain.service
                    // com.domain.service.product

                    let parts = ns.split(".");
                    let previousPart = "";

                    parts.forEach((part) => {
                        let currentPart =
                            (previousPart ? previousPart + "." : "") + part;
                        let level = currentPart.split(".").length - 1;
                        res.push({
                            code: currentPart,
                            label: currentPart,
                            level: level,
                        });
                        previousPart = currentPart;
                    });
                });

                // Remove duplicate namespaces ...
                return _uniqBy(res, "code").filter(
                    (ns) => namespaces.includes(ns.code) || this.isFilter,
                );
            },
            load() {
                this.$store
                    .dispatch("namespace/loadNamespacesForDatatype", {
                        dataType: this.dataType
                    })
                    .then(() => {
                        this.groupedNamespaces = this.groupNamespaces(
                            this.datatypeNamespaces
                        ).filter(
                            (namespace) =>
                                this.includeSystemNamespace ||
                                namespace.code !==
                                (this.miscStore.configs?.systemNamespace || "system")
                        );
                    });
                if (this.all) {
                    // Then include datatype namespaces + all from namespaces tables
                    this.$store.dispatch("namespace/autocomplete" + (this.value ? "?q=" + this.value : "")).then(namespaces => {
                        const concatNamespaces = this.groupedNamespaces.concat(this.groupNamespaces(
                            namespaces
                        ).filter(
                            (namespace) =>
                                this.includeSystemNamespace ||
                                namespace.code !==
                                (this.miscStore.configs?.systemNamespace || "system")
                        ));
                        // Remove duplicates after merge
                        this.groupedNamespaces = _uniqBy(concatNamespaces, "code").filter(
                            (ns) => namespaces.includes(ns.code) || this.isFilter,
                        ).sort((a,b) => a.code > b.code)
                    })
                }
            }
        },
    };
</script>
