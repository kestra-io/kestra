<template>
    <KestraFilter
        :placeholder="$t('search')"
        legacy-query
    />

    <SelectTable
        :data="filteredKvs"
        ref="selectTable"
        :default-sort="{prop: 'id', order: 'ascending'}"
        table-layout="auto"
        fixed
        @selection-change="handleSelectionChange"
        @sort-change="handleSort"
        :infinite-scroll-load="namespace === undefined ? fetchKvs : undefined"
        :no-data-text="$t('no_results.kv_pairs')"
        class="fill-height"
        :show-selection="!paneView"
    >
        <template #select-actions>
            <BulkSelect
                :select-all="queryBulkAction"
                :selections="selection"
                @update:select-all="toggleAllSelection"
                @unselect="toggleAllUnselected"
            >
                <el-button :icon="Delete" type="default" @click="removeKvs()">
                    {{ $t("delete") }}
                </el-button>
            </BulkSelect>
        </template>
        <el-table-column
            v-if="namespace === undefined && !paneView"
            prop="namespace"
            sortable="custom"
            :sort-orders="['ascending', 'descending']"
            :label="$t('namespace')"
        />
        <el-table-column prop="key" sortable="custom" :sort-orders="['ascending', 'descending']" :label="$t('key')">
            <template #default="scope">
                <Id v-if="scope.row.key !== undefined" :value="scope.row.key" :shrink="false" />
            </template>
        </el-table-column>
        <el-table-column
            v-if="!paneView"
            prop="description"
            sortable="custom"
            :sort-orders="['ascending', 'descending']"
            :label="$t('description')"
        />
        <el-table-column
            prop="updateDate"
            sortable="custom"
            :sort-orders="['ascending', 'descending']"
            :label="$t('last modified')"
        />
        <el-table-column
            v-if="!paneView"
            prop="expirationDate"
            sortable="custom"
            :sort-orders="['ascending', 'descending']"
            :label="$t('expiration date')"
        />

        <el-table-column column-key="copy" class-name="row-action">
            <template #default="scope">
                <el-tooltip v-if="scope.row.key !== undefined" :content="$t('copy_to_clipboard')">
                    <el-button :icon="ContentCopy" link @click="Utils.copy(`\{\{ kv('${scope.row.key}') \}\}`)" />
                </el-tooltip>
            </template>
        </el-table-column>

        <el-table-column v-if="!paneView" column-key="update" class-name="row-action">
            <template #default="scope">
                <el-button
                    v-if="canUpdate(scope.row)"
                    :icon="FileDocumentEdit"
                    link
                    @click="updateKvModal(scope.row.namespace, scope.row.key)"
                />
            </template>
        </el-table-column>

        <el-table-column v-if="!paneView" column-key="delete" class-name="row-action">
            <template #default="scope">
                <el-button
                    v-if="canDelete(scope.row)"
                    :icon="Delete"
                    link
                    @click="removeKv(scope.row.namespace, scope.row.key)"
                />
            </template>
        </el-table-column>
    </SelectTable>

    <Drawer
        v-if="addKvDrawerVisible"
        v-model="addKvDrawerVisible"
        :title="kvModalTitle"
    >
        <el-form class="ks-horizontal" :model="kv" :rules="rules" ref="form">
            <el-form-item v-if="namespace === undefined" :label="$t('namespace')" prop="namespace" required>
                <NamespaceSelect
                    v-model="kv.namespace"
                    :readonly="kv.update"
                    :include-system-namespace="true"
                    all
                />
            </el-form-item>

            <el-form-item :label="$t('key')" prop="key" required>
                <el-input v-model="kv.key" :readonly="kv.update" />
            </el-form-item>

            <el-form-item :label="$t('kv.type')" prop="type" required>
                <el-select
                    v-model="kv.type"
                    @change="kv.value = undefined"
                >
                    <el-option value="STRING" />
                    <el-option value="NUMBER" />
                    <el-option value="BOOLEAN" />
                    <el-option value="DATETIME" />
                    <el-option value="DATE" />
                    <el-option value="DURATION" />
                    <el-option value="JSON" />
                </el-select>
            </el-form-item>

            <el-form-item :label="$t('value')" prop="value" :required="kv.type !== 'BOOLEAN'">
                <el-input v-if="kv.type === 'STRING'" type="textarea" :rows="5" v-model="kv.value" />
                <el-input v-else-if="kv.type === 'NUMBER'" type="number" v-model="kv.value" />
                <el-switch
                    v-else-if="kv.type === 'BOOLEAN'"
                    :active-text="$t('true')"
                    v-model="kv.value"
                    class="switch-text"
                    :active-action-icon="Check"
                />
                <el-date-picker
                    v-else-if="kv.type === 'DATETIME'"
                    v-model="kv.value"
                    type="datetime"
                />
                <el-date-picker
                    v-else-if="kv.type === 'DATE'"
                    v-model="kv.value"
                    type="date"
                />
                <TimeSelect
                    v-else-if="kv.type === 'DURATION'"
                    :from-now="false"
                    :time-range="kv.value"
                    clearable
                    allow-custom
                    @update:model-value="kv.value = $event.timeRange"
                />
                <Editor
                    :full-height="false"
                    :input="true"
                    :navbar="false"
                    v-else-if="kv.type === 'JSON'"
                    lang="json"
                    v-model="kv.value"
                />
            </el-form-item>

            <el-form-item :label="$t('description')" prop="description">
                <el-input v-model="kv.description" />
            </el-form-item>

            <el-form-item :label="$t('expiration')" prop="ttl">
                <TimeSelect
                    :from-now="false"
                    allow-infinite
                    allow-custom
                    :placeholder="kv.ttl ? $t('datepicker.custom') : $t('datepicker.never')"
                    :time-range="kv.ttl"
                    clearable
                    include-never
                    @update:model-value="onTtlChange"
                />
            </el-form-item>
        </el-form>

        <template #footer>
            <el-button :icon="ContentSave" @click="saveKv($refs.form)" type="primary">
                {{ $t('save') }}
            </el-button>
        </template>
    </Drawer>

    <drawer
        v-if="namespacesStore.inheritedKVModalVisible"
        v-model="namespacesStore.inheritedKVModalVisible"
        :title="$t('kv.inherited')"
    >
        <InheritedKVs :namespace="namespacesStore?.namespace?.id" />
    </drawer>
</template>

<script setup lang="ts">
    import BulkSelect from "../layout/BulkSelect.vue";
    import SelectTable from "../layout/SelectTable.vue";
    import Editor from "../inputs/Editor.vue";
    import FileDocumentEdit from "vue-material-design-icons/FileDocumentEdit.vue";
    import Delete from "vue-material-design-icons/Delete.vue";
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
    import TimeSelect from "../executions/date-select/TimeSelect.vue";
    import Check from "vue-material-design-icons/Check.vue";
    import NamespaceSelect from "../namespaces/components/NamespaceSelect.vue";

    import Utils from "../../utils/utils";
    import KestraFilter from "../filter/KestraFilter.vue";
    import Id from "../Id.vue";
    import Drawer from "../Drawer.vue";

    import InheritedKVs from "./InheritedKVs.vue";
</script>

<script lang="ts">
    import {mapStores} from "pinia";
    import {groupBy} from "lodash";
    import {useNamespacesStore} from "override/stores/namespaces";
    import useNamespaces from "../../composables/useNamespaces";
    import {NamespaceIterator} from "../../composables/useNamespaces";
    import SelectTableActions from "../../mixins/selectTableActions";
    import action from "../../models/action";
    import permission from "../../models/permission";
    import {useAuthStore} from "override/stores/auth"

    export default {
        inheritAttrs: false,
        mixins: [SelectTableActions],
        computed: {
            ...mapStores(useNamespacesStore, useAuthStore),
            searchQuery() {
                return this.$route.query.q;
            },
            filteredKvs() {
                return this.kvs?.filter(kv =>
                    !this.searchQuery ||
                    kv.key.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
                    kv.description.toLowerCase().includes(this.searchQuery.toLowerCase())
                );
            },
            kvModalTitle() {
                return this.kv.key ? this.$t("kv.update", {key: this.kv.key}) : this.$t("kv.add");
            },
            addKvDrawerVisible: {
                get() {
                    return this.namespacesStore.addKvModalVisible;
                },
                set(newValue:boolean) {
                    this.namespacesStore.addKvModalVisible = newValue;
                }
            }
        },
        mounted() {
            if (this.namespace !== undefined) {
                this.fetchKvs();
            }
        },
        props: {
            namespace: {
                type: String,
                default: undefined
            },
            paneView: {
                type: Boolean,
                default: false
            },
        },
        watch: {
            addKvDrawerVisible(newValue) {
                if (!newValue) {
                    this.resetKv();
                }
            },
            "kv.type"() {
                if (this.$refs.form) {
                    this.$refs.form.clearValidate("value");
                }
            },
            searchQuery(newValue, oldValue) {
                if (newValue !== oldValue) {
                    this.reloadKvs();
                }
            }
        },
        data() {
            return {
                kv: {
                    namespace: this.namespace,
                    key: undefined,
                    type: "STRING",
                    value: undefined,
                    ttl: undefined,
                    update: undefined
                },
                kvs: undefined,
                namespaceIterator: undefined,
                rules: {
                    key: [
                        {required: true, trigger: "change"},
                        {validator: this.kvKeyDuplicate, trigger: "change"},
                    ],
                    value: [
                        {required: true, trigger: "change"},
                        {
                            validator: (rule, value, callback) => {
                                if (this.kv.type === "DURATION") {
                                    this.durationValidator(rule, value, callback);
                                } else if (this.kv.type === "JSON") {
                                    this.jsonValidator(rule, value, callback)
                                } else {
                                    callback();
                                }
                            },
                            trigger: "change"
                        }
                    ],
                    ttl: [
                        {validator: this.durationValidator, trigger: "change"}
                    ]
                }
            };
        },
        methods: {
            canUpdate(kv: {namespace: string}) {
                return kv.namespace !== undefined && this.authStore.user?.isAllowed(permission.KVSTORE, action.UPDATE, kv.namespace)
            },
            canDelete(kv: {namespace: string}) {
                return kv.namespace !== undefined && this.authStore.user?.isAllowed(permission.KVSTORE, action.DELETE, kv.namespace)
            },
            jsonValidator(_rule: any, value: string, callback: (error?: Error) => void) {
                try {
                    const parsed = JSON.parse(value);
                    if (typeof parsed !== "object" || parsed === null) {
                        callback(new Error(this.$t("Invalid input: Expected a JSON object or array")));
                    } else {
                        callback();
                    }
                } catch {
                    callback(new Error(this.$t("Invalid input: Expected a JSON formatted string")));
                }
            },
            durationValidator(_rule: any, value: string, callback: (error?: Error) => void) {
                if (value !== undefined && !value.match(/^P(?=[^T]|T.)(?:\d*D)?(?:T(?=.)(?:\d*H)?(?:\d*M)?(?:\d*S)?)?$/)) {
                    callback(new Error(this.$t("datepicker.error")));
                } else {
                    callback();
                }
            },
            async fetchKvs() {
                let kvFetch;
                if (this.namespace === undefined) {
                    if (this.namespaceIterator === undefined) {
                        this.namespaceIterator = useNamespaces(this.$store, 20);
                    }

                    const namespaces = (await ((this.namespaceIterator as NamespaceIterator).next())).map(n => n.id);
                    if (namespaces.length !== 0) {
                        const kvsPromises = Promise.all(namespaces.filter(n => this.authStore.user?.isAllowed(permission.KVSTORE, action.READ, n)).map(async n => {
                            const kvs = await this.namespacesStore.kvsList({id: n});

                            return kvs.map(kv => {
                                kv.namespace = n;
                                return kv;
                            });
                        }));

                        kvFetch = (await kvsPromises).flat();
                    }
                } else {
                    kvFetch = (await this.namespacesStore.kvsList({id: this.namespace})).map(kv => {
                        kv.namespace = this.namespace;
                        return kv;
                    });
                }

                if (kvFetch === undefined) {
                    return undefined;
                }

                this.kvs = this.kvs?.concat(kvFetch) ?? kvFetch;

                if (this.namespace === undefined && this.filteredKvs.length === 0) {
                    return this.fetchKvs();
                }

                return kvFetch;
            },
            kvKeyDuplicate(rule, value, callback) {
                if (this.kv.update === undefined && this.kvs && this.kvs.find(r => r.namespace === this.kv.namespace && r.key === value)) {
                    return callback(new Error(this.$t("kv.duplicate")));
                } else {
                    callback();
                }
            },
            async updateKvModal(namespace, key) {
                this.kv.namespace = namespace;
                this.kv.key = key;
                const {type, value} = await this.namespacesStore.kv({namespace, key});
                this.kv.type = type;
                if (type === "JSON") {
                    this.kv.value = JSON.stringify(value);
                } else if (type === "BOOLEAN") {
                    this.kv.value = value;
                } else {
                    this.kv.value = value.toString();
                }
                this.kv.update = true;
                this.addKvDrawerVisible = true;
            },
            removeKv(namespace, key) {
                this.$toast().confirm(this.$t("delete confirm", {name: key}), () => {
                    return this.namespacesStore
                        .deleteKv({namespace, key: key})
                        .then(() => {
                            this.$toast().deleted(key);
                            this.reloadKvs();
                        });
                });
            },
            removeKvs() {
                const groupedByNamespace = groupBy(this.selection, "namespace");
                const withDeletePermissionGroupedKvs = Object.fromEntries(Object.entries(groupedByNamespace).filter(([namespace]) => this.authStore.user.isAllowed(permission.KVSTORE, action.DELETE, namespace)));
                const withDeletePermissionNamespaces = Object.keys(withDeletePermissionGroupedKvs);
                const withoutDeletePermissionNamespaces = Object.keys(groupedByNamespace).filter(n => !withDeletePermissionNamespaces.includes(n));
                this.$toast().confirm(
                    this.$t("kv.delete multiple.confirm", {name: Object.values(withDeletePermissionGroupedKvs).reduce((count, kvs) => count + kvs.length, 0)}) +
                        (withoutDeletePermissionNamespaces.length === 0 ? "" : `\n${this.$t("kv.delete multiple.warning", {namespaces: withoutDeletePermissionNamespaces.join(", ")})}`),
                    () => {
                        Object.entries(withDeletePermissionGroupedKvs).forEach(([namespace, kvs]) => {
                            this.namespacesStore
                                .deleteKvs({namespace, request: {keys: kvs.map(kv => kv.key)}})
                                .then(() => {
                                    this.$toast().deleted(`${kvs.length} KV(s) from ${namespace} namespace`);
                                    this.reloadKvs();
                                });
                        });
                    });
            },
            async reloadKvs() {
                this.namespaceIterator = undefined;

                const previousLength = this.secrets?.length ?? 0;
                await this.$refs.selectTable.resetInfiniteScroll();
                this.kvs = [];

                // If we are in the global KV view we let the infinite scroll handling the fetch
                if (this.namespace !== undefined || previousLength === 0) {
                    this.fetchKvs();
                }
            },
            saveKv(formRef) {
                formRef.validate((valid) => {
                    if (!valid) {
                        return false;
                    }

                    const type = this.kv.type;
                    let value = this.kv.value;

                    if (["STRING", "DURATION"].includes(type)) {
                        value = JSON.stringify(value);
                    } else if (type === "DATETIME") {
                        value = this.$moment(value).toISOString()
                    } else if (type === "DATE") {
                        value = this.$moment(value).toISOString(true).split("T")[0]
                    }

                    return this.namespacesStore
                        .createKv({
                            ...this.kv,
                            contentType: ["DATE", "DATETIME"].includes(type) ? "text/plain" : "application/json",
                            value
                        })
                        .then(() => {
                            this.$toast().saved(this.kv.key);
                            this.addKvDrawerVisible = false;
                            this.reloadKvs();
                        })
                });
            },
            resetKv() {
                this.kv = {
                    namespace: this.namespace,
                    type: "STRING"
                }
            },
            onTtlChange(value) {
                this.kv.ttl = value.timeRange
            },
            handleSort({prop, order}) {
                if (prop && order) {
                    this.kvs.sort((a, b) => {
                        const [valueA, valueB] = [a[prop] ?? "", b[prop] ?? ""];
                        const modifier = order === "ascending" ? 1 : -1;

                        return typeof valueA === "string"
                            ? modifier * valueA.localeCompare(valueB)
                            : modifier * (valueA - valueB);
                    });
                }
            }
        },
    };
</script>
