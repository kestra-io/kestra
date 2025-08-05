<template>
    <div class="d-flex flex-column fill-height">
        <KestraFilter
            :placeholder="$t('search')"
            legacy-query
        />

        <SelectTable
            :data="filteredSecrets"
            ref="selectTable"
            :default-sort="{prop: 'key', order: 'ascending'}"
            table-layout="auto"
            fixed
            :selectable="false"
            @sort-change="handleSort"
            :infinite-scroll-load="namespace === undefined ? fetchSecrets : undefined"
            :no-data-text="$t('no_results.secrets')"
            class="fill-height"
        >
            <el-table-column
                v-if="namespace === undefined || namespaceColumn"
                prop="namespace"
                sortable="custom"
                :sort-orders="['ascending', 'descending']"
                :label="$t('namespace')"
            />
            <el-table-column prop="key" sortable="custom" :sort-orders="['ascending', 'descending']" :label="keyOnly ? $t('secret.names') : $t('key')">
                <template #default="scope">
                    <Id v-if="scope.row.key !== undefined" :value="scope.row.key" :shrink="false" />
                </template>
            </el-table-column>

            <el-table-column v-if="!keyOnly" prop="description" :label="$t('description')">
                <template #default="scope">
                    {{ scope.row.description }}
                </template>
            </el-table-column>

            <el-table-column v-if="!keyOnly && !paneView" prop="tags" :label="$t('tags')">
                <template #default="scope">
                    <Labels v-if="scope.row.tags !== undefined" :labels="scope.row.tags" read-only />
                </template>
            </el-table-column>

            <el-table-column column-key="locked" class-name="row-action">
                <template #default="scope">
                    <el-tooltip v-if="scope.row.namespace !== undefined && areNamespaceSecretsReadOnly?.[scope.row.namespace]" transition="" :hide-after="0" :persistent="false" effect="light">
                        <template #content>
                            <span v-html="$t('secret.isReadOnly')" />
                        </template>
                        <el-icon class="d-flex justify-content-center text-base">
                            <Lock />
                        </el-icon>
                    </el-tooltip>
                </template>
            </el-table-column>

            <el-table-column column-key="copy" class-name="row-action">
                <template #default="scope">
                    <el-tooltip :content="$t('copy_to_clipboard')">
                        <el-button :icon="ContentCopy" link @click="Utils.copy(`\{\{ secret('${scope.row.key}') \}\}`)" />
                    </el-tooltip>
                </template>
            </el-table-column>

            <el-table-column v-if="!keyOnly && !paneView" column-key="update" class-name="row-action">
                <template #default="scope">
                    <el-button v-if="canUpdate(scope.row)" :icon="FileDocumentEdit" link @click="updateSecretModal(scope.row)" />
                </template>
            </el-table-column>

            <el-table-column v-if="!keyOnly && !paneView" column-key="delete" class-name="row-action">
                <template #default="scope">
                    <el-button v-if="canDelete(scope.row)" :icon="Delete" link @click="removeSecret(scope.row)" />
                </template>
            </el-table-column>
        </SelectTable>

        <Drawer
            v-if="addSecretDrawerVisible"
            v-model="addSecretDrawerVisible"
            :title="secretModalTitle"
        >
            <el-form class="ks-horizontal" :model="secret" :rules="rules" ref="form">
                <el-form-item
                    v-if="namespace === undefined"
                    :label="$t('namespace')"
                    prop="namespace"
                    required
                >
                    <NamespaceSelect
                        v-model="secret.namespace"
                        :readonly="secret.update"
                        :include-system-namespace="true"
                        all
                    />
                </el-form-item>
                <el-form-item :label="$t('secret.key')" prop="key">
                    <el-input v-model="secret.key" :disabled="secret.update" required />
                </el-form-item>
                <el-form-item v-if="!secret.update" :label="$t('secret.name')" prop="value">
                    <MultilineSecret v-model="secret.value" :placeholder="secretModalTitle" />
                </el-form-item>
                <el-form-item v-if="secret.update" :label="$t('secret.name')" prop="value">
                    <el-col :span="20">
                        <MultilineSecret v-model="secret.value" :placeholder="secretModalTitle" :disabled="!secret.updateValue" />
                    </el-col>
                    <el-col class="px-2" :span="4">
                        <el-switch
                            size="large"
                            inline-prompt
                            v-model="secret.updateValue"
                            :active-icon="PencilOutline"
                            :inactive-icon="PencilOff"
                        />
                    </el-col>
                </el-form-item>
                <el-form-item :label="$t('secret.description')" prop="description">
                    <el-input v-model="secret.description" :placeholder="$t('secret.descriptionPlaceholder')" required />
                </el-form-item>
                <el-form-item :label="$t('secret.tags')" prop="tags">
                    <el-row :gutter="20" v-for="(tag, index) in secret.tags" :key="index">
                        <el-col :span="8">
                            <el-input required v-model="tag.key" :placeholder="$t('key')" />
                        </el-col>
                        <el-col :span="12">
                            <el-input required v-model="tag.value" :placeholder="$t('value')" />
                        </el-col>
                        <el-button-group class="d-flex flex-nowrap">
                            <el-button
                                :icon="Delete"
                                @click="removeSecretTag(index)"
                            />
                        </el-button-group>
                    </el-row>
                    <el-button :icon="Plus" @click="addSecretTag" type="primary">
                        {{ $t('secret.addTag') }}
                    </el-button>
                </el-form-item>
            </el-form>

            <template #footer>
                <el-button :icon="ContentSave" @click="saveSecret($refs.form)" type="primary">
                    {{ $t('save') }}
                </el-button>
            </template>
        </Drawer>
    </div>
</template>

<script setup lang="ts">
    import SelectTable from "../layout/SelectTable.vue";
    import FileDocumentEdit from "vue-material-design-icons/FileDocumentEdit.vue";
    import Delete from "vue-material-design-icons/Delete.vue";
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
    import Lock from "vue-material-design-icons/Lock.vue";
    import KestraFilter from "../filter/KestraFilter.vue";

    import Utils from "../../utils/utils";
    import Labels from "../layout/Labels.vue";
    import Plus from "vue-material-design-icons/Plus.vue";
    import NamespaceSelect from "../namespaces/components/NamespaceSelect.vue";
    import MultilineSecret from "./MultilineSecret.vue";
</script>

<script lang="ts">
    import {mapState} from "vuex";
    import {mapStores} from "pinia";
    import {useNamespaceSecrets, useAllSecrets, SecretIterator} from "../../composables/useSecrets";
    import {useNamespacesStore} from "override/stores/namespaces";
    import action from "../../models/action";
    import permission from "../../models/permission";
    import SelectTableActions from "../../mixins/selectTableActions";
    import Id from "../Id.vue";
    import Drawer from "../Drawer.vue";

    export default {
        mixins: [SelectTableActions],
        components: {
            Id,
            Drawer
        },
        computed: {
            ...mapState("auth", ["user"]),
            ...mapStores(useNamespacesStore),
            searchQuery() {
                return this.$route.query.q;
            },
            filteredSecrets() {
                return this.namespace === undefined
                    ? this.secrets?.filter((secret: {key: string}) => !this.searchQuery || secret.key.toLowerCase().includes(this.searchQuery.toLowerCase()))
                    : this.secrets;
            },
            secretModalTitle() {
                return this.secret?.update ? this.$t("secret.update", {name: this.secret.key}) : this.$t("secret.add");
            },
            addSecretDrawerVisible: {
                get() {
                    return this.addSecretModalVisible;
                },
                set(newValue) {
                    this.$emit("update:addSecretModalVisible", newValue);
                }
            },
            areNamespaceSecretsReadOnly() {
                const areNamespaceSecretsReadOnly = this.secretsIterator?.areNamespaceSecretsReadOnly;
                return this.namespace === undefined ? areNamespaceSecretsReadOnly : {[this.namespace]: areNamespaceSecretsReadOnly};
            }
        },
        mounted() {
            if (this.namespace !== undefined) {
                this.fetchSecrets();
            }
        },
        props: {
            addSecretModalVisible: {
                type: Boolean,
                default: false
            },
            namespace: {
                type: String,
                default: undefined
            },
            filterable: {
                type: Boolean,
                default: true
            },
            keyOnly: {
                type: Boolean,
                default: false
            },
            paneView: {
                type: Boolean,
                default: false
            },
            namespaceColumn: {
                type: Boolean,
                default: undefined
            }
        },
        emits: [
            "update:addSecretModalVisible",
            "update:isSecretReadOnly",
            "hasData"
        ],
        watch: {
            addSecretModalVisible(newValue) {
                if (!newValue) {
                    this.resetForm();
                }
            },
            hasData(newValue, oldValue) {
                if (oldValue === undefined) {
                    this.$emit("hasData", newValue);
                }
            },
            searchQuery(newValue, oldValue) {
                if (newValue !== oldValue) {
                    this.reloadSecrets();
                }
            }
        },
        data() {
            return {
                secret: {
                    namespace: this.namespace,
                    key: undefined,
                    value: "",
                    description: undefined,
                    tags: [{key:undefined,value:undefined}],
                    update: undefined,
                    updateValue: undefined
                },
                secretsIterator: undefined as SecretIterator | undefined,
                secrets: undefined,
                rules: {
                    key: [
                        {required: true, trigger: "change"},
                        {validator: this.secretKeyDuplicate, trigger: "change"},
                    ],
                    value: [
                        {
                            validator: this.checkSecretValue,
                            trigger: ["blur"],
                            required: false,
                        },
                    ],
                    secret: [
                        {required: true, trigger: "change"},
                    ],
                    tags: [
                        {
                            validator: this.checkSecretTags,
                            trigger: ["blur"],
                            required: false,
                        },
                    ]
                },
                hasData: undefined,
            };
        },
        methods: {
            canUpdate(secret) {
                return secret.namespace !== undefined && this.user.isAllowed(permission.SECRET, action.UPDATE, secret.namespace) && !this.areNamespaceSecretsReadOnly?.[secret.namespace];
            },
            canDelete(secret) {
                return secret.namespace !== undefined && this.user.isAllowed(permission.SECRET, action.DELETE, secret.namespace) && !this.areNamespaceSecretsReadOnly?.[secret.namespace];
            },
            async fetchSecrets() {
                if (this.secretsIterator === undefined) {
                    this.secretsIterator = this.namespace === undefined ? useAllSecrets(this.$store, 20) : useNamespaceSecrets(this.$store, this.namespace, 20, {
                        sort: this.$route.query.sort || "key:asc",
                        ...(this.searchQuery === undefined ? {} : {filters: {
                            q: {
                                EQUALS: this.searchQuery
                            }
                        }})
                    });
                }

                let emitReadOnly = false;
                if (this.namespace !== undefined && this.secretsIterator.areNamespaceSecretsReadOnly === undefined) {
                    emitReadOnly = true;
                }
                const fetch = await (this.secretsIterator as SecretIterator).next();
                if (emitReadOnly && this.secretsIterator.areNamespaceSecretsReadOnly !== undefined) {
                    this.$emit("update:isSecretReadOnly", this.secretsIterator.areNamespaceSecretsReadOnly);
                }

                if (fetch.length === 0) {
                    this.hasData = false;
                    return undefined;
                }

                this.hasData = true;
                this.secrets = [...(this.secrets || []), ...fetch];

                if (this.namespace === undefined && this.filteredSecrets.length === 0) {
                    return this.fetchSecrets();
                }

                return fetch;
            },
            updateSecretModal(secret) {
                this.secret.namespace = secret.namespace;
                this.secret.key = secret.key;
                this.secret.description = secret.description;
                this.secret.tags = secret.tags?.map((x) => x) || [{key:undefined,value:undefined}];
                this.secret.update = true;
                this.secret.updateValue = false;
                this.addSecretDrawerVisible = true;
            },
            checkSecretValue(rule, value, callback)  {
                if (this.secret.updateValue && (this.secret.value === undefined || this.secret.value.length === 0)) {
                    callback(new Error("Value must not be empty."));
                } else {
                    callback();
                }
            },
            checkSecretTags(rule, value, callback)  {
                const keys = this.secret.tags.map((it) => it.key);

                if (this.secret.tags.length === 1) {
                    if (this.secret.tags[0].key === undefined &&
                        this.secret.tags[0].value === undefined) {
                        callback();
                        return;
                    }
                }

                const nullKeys = keys.filter(item => item === undefined);
                const duplicateKeys = keys.filter((item, index) => keys.indexOf(item) !== index);
                if (nullKeys.length > 0) {
                    callback(new Error("Tag key must not be empty."));
                } else if (duplicateKeys.length > 0) {
                    callback(new Error("Duplicate tags for keys: " + Array.from(new Set(duplicateKeys))));
                } else {
                    callback();
                }
            },
            addSecretTag() {
                this.secret.tags.push({key:"",value:""});
            },
            removeSecretTag(index) {
                this.secret.tags.splice(index, 1);
            },
            async reloadSecrets() {
                this.secretsIterator = undefined;

                const previousLength = this.secrets?.length ?? 0;
                await this.$refs.selectTable.resetInfiniteScroll();
                this.secrets = [];

                // If we are in the global Secrets view we let the infinite scroll handling the fetch
                if (this.namespace !== undefined || previousLength === 0) {
                    return this.fetchSecrets();
                }
            },
            removeSecret({key, namespace}) {
                this.$toast().confirm(this.$t("delete confirm", {name: key}), () => {
                    return this.namespacesStore
                        .deleteSecrets({namespace: namespace, key})
                        .then(() => {
                            this.$toast().deleted(key);
                        })
                        .then(() => this.reloadSecrets())
                });
            },
            isSecretValueUpdated() {
                return !this.secret.update || this.secret.updateValue;
            },
            saveSecret(formRef) {
                formRef.validate((valid) => {
                    if (!valid) {
                        return false;
                    }

                    let secret = {
                        key: this.secret.key,
                        description: this.secret.description,
                        tags: this.secret.tags
                            .map(item => item.value !== undefined ? item : {key:item.key,value: ""})
                            .filter(item => item.key !== undefined)
                    };

                    if (this.isSecretValueUpdated()) {
                        secret.value = this.secret.value;
                    }

                    const action = this.isSecretValueUpdated() ? this.namespacesStore.createSecrets : this.namespacesStore.patchSecret;
                    return action({namespace: this.secret.namespace, secret: secret})
                        .then(() => {
                            this.secret.update = true;
                            this.$toast().saved(this.secret.key);
                            this.addSecretDrawerVisible = false;
                            this.resetForm();
                            return this.reloadSecrets();
                        })
                });
            },
            resetForm() {
                this.secret = {
                    namespace: this.namespace,
                    key: undefined,
                    value: "",
                    description: undefined,
                    tags: [{key:undefined,value:undefined}],
                    update: undefined,
                    updateValue: undefined
                }
            },
            onTtlChange(value) {
                this.kv.ttl = value.timeRange
            },
            handleSort({prop, order}) {
                if (prop && order) {
                    this.secrets?.sort((a, b) => {
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
