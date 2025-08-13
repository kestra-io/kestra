<template>
    <el-select
        class="fit-text"
        :model-value="value"
        @update:model-value="$emit('update:modelValue', $event)"
        :disabled="readonly"
        clearable
        :placeholder="$t('Select namespace')"
        :persistent="false"
        remote
        :remote-method="onInput"
        filterable
        :allow-create="allowCreate"
        default-first-option
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
    import {useMiscStore} from "override/stores/misc";
    import {useNamespacesStore} from "override/stores/namespaces";
    import _uniqBy from "lodash/uniqBy";

    export default {
        props: {
            dataType: {
                type: String,
                default: undefined,
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
        computed: {
            ...mapState("auth", ["user"]),
            ...mapStores(useMiscStore, useNamespacesStore),
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
                this.load(value);
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
            async load(value) {
                try {
                    let namespaces;
                    if (this.all) {
                        namespaces = await this.namespacesStore.loadAutocomplete({
                            q: value || ""
                        });
                    } else {
                        namespaces = await this.namespacesStore.loadNamespacesForDatatype({
                            dataType: this.dataType
                        });
                    }

                    this.groupedNamespaces = this.groupNamespaces(namespaces)
                        .filter(namespace => 
                            this.includeSystemNamespace || 
                            namespace.code !== (this.miscStore.configs?.systemNamespace || "system")
                        )
                        .sort((a, b) => a.code.localeCompare(b.code));
                } catch (error) {
                    console.error("Error loading namespaces:", error);
                }
            }
        },
    };
</script>
