<template>
    <div class="d-flex flex-column fill-height">
        <DataTable @page-changed="onPageChanged" ref="dataTable" :total="total">
            <template #top>
                <KestraFilter
                    :placeholder="$t('search')"
                    :language="SecretFilterLanguage"
                />
            </template>
            <template #table>
                <SelectTable
                    :data="secrets"
                    ref="selectTable"
                    :defaultSort="{prop: 'key', order: 'ascending'}"
                    tableLayout="auto"
                    fixed
                    :selectable="false"
                    @sort-change="onSort"
                    :no-data-text="$t('no_results.secrets')"
                    class="fill-height"
                >
                    <el-table-column
                        v-if="namespace === undefined || namespaceColumn"
                        prop="namespace"
                        sortable="custom"
                        :sortOrders="['ascending', 'descending']"
                        :label="$t('namespace')"
                    >
                        <template #default="scope">
                            <el-tag
                                type="info"
                                class="namespace-tag"
                            >
                                <DotsSquare />
                                {{ scope.row.namespace }}
                            </el-tag>
                        </template>
                    </el-table-column>
                    <el-table-column
                        prop="key"
                        sortable="custom"
                        :sortOrders="['ascending', 'descending']"
                        :label="keyOnly ? $t('secret.names') : $t('key')"
                    >
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
                            <Labels v-if="scope.row.tags !== undefined" :labels="scope.row.tags" readOnly />
                        </template>
                    </el-table-column>

                    <el-table-column columnKey="locked" className="row-action">
                        <template>
                            <el-tooltip
                                v-if="areNamespaceSecretsReadOnly"
                                transition=""
                                :hideAfter="0"
                                :persistent="false"
                                effect="light"
                            >
                                <template #content>
                                    <span v-html="$t('secret.isReadOnly')" />
                                </template>
                                <el-icon class="d-flex justify-content-center text-base">
                                    <Lock />
                                </el-icon>
                            </el-tooltip>
                        </template>
                    </el-table-column>

                    <el-table-column columnKey="copy" className="row-action">
                        <template #default="scope">
                            <el-tooltip :content="$t('copy_to_clipboard')">
                                <el-button
                                    :icon="ContentCopy"
                                    link
                                    @click="Utils.copy(`\{\{ secret('${scope.row.key}') \}\}`)"
                                />
                            </el-tooltip>
                        </template>
                    </el-table-column>

                    <el-table-column v-if="!keyOnly && !paneView" columnKey="update" className="row-action">
                        <template #default="scope">
                            <el-button
                                v-if="canUpdate(scope.row)"
                                :icon="FileDocumentEdit"
                                link
                                @click="updateSecretModal(scope.row)"
                            />
                        </template>
                    </el-table-column>

                    <el-table-column v-if="!keyOnly && !paneView" columnKey="delete" className="row-action">
                        <template #default="scope">
                            <el-button
                                v-if="canDelete(scope.row)"
                                :icon="Delete"
                                link
                                @click="removeSecret(scope.row)"
                            />
                        </template>
                    </el-table-column>
                </SelectTable>
            </template>
        </DataTable>

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
                        :includeSystemNamespace="true"
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
                        <MultilineSecret
                            v-model="secret.value"
                            :placeholder="secretModalTitle"
                            :disabled="!secret.updateValue"
                        />
                    </el-col>
                    <el-col class="px-2" :span="4">
                        <el-switch
                            size="large"
                            inlinePrompt
                            v-model="secret.updateValue"
                            :activeIcon="PencilOutline"
                            :inactiveIcon="PencilOff"
                        />
                    </el-col>
                </el-form-item>
                <el-form-item :label="$t('secret.description')" prop="description">
                    <el-input
                        v-model="secret.description"
                        :placeholder="$t('secret.descriptionPlaceholder')"
                        required
                    />
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
    import DotsSquare from "vue-material-design-icons/DotsSquare.vue";
    import KestraFilter from "../filter/KestraFilter.vue";

    import Utils from "../../utils/utils";
    import Labels from "../layout/Labels.vue";
    import Plus from "vue-material-design-icons/Plus.vue";
    import NamespaceSelect from "../namespaces/components/NamespaceSelect.vue";
    import MultilineSecret from "./MultilineSecret.vue";
    import DataTable from "../layout/DataTable.vue";
    import SecretFilterLanguage from "../../composables/monaco/languages/filters/impl/secretFilterLanguage.ts";
</script>

<script lang="ts">
    import {mapStores} from "pinia";
    import {useNamespacesStore} from "override/stores/namespaces";
    import {useAuthStore} from "override/stores/auth";
    import action from "../../models/action";
    import permission from "../../models/permission";
    import DataTableActions from "../../mixins/dataTableActions";
    import SelectTableActions from "../../mixins/selectTableActions";
    import Id from "../Id.vue";
    import Drawer from "../Drawer.vue";
    import {useSecretsStore} from "../../stores/secrets.ts";
    import _merge from "lodash/merge";

    export default {
        mixins: [DataTableActions, SelectTableActions],
        components: {
            Id,
            Drawer
        },
        computed: {
            ...mapStores(useNamespacesStore, useAuthStore, useSecretsStore),
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
            }
        },
        data() {
            return {
                secret: {
                    namespace: this.namespace,
                    key: undefined,
                    value: "",
                    description: undefined,
                    tags: [{key: undefined, value: undefined}] as ({
                        key: string | undefined,
                        value: string | undefined
                    }[]),
                    update: undefined,
                    updateValue: undefined
                },
                secrets: undefined,
                areNamespaceSecretsReadOnly: false,
                total: 0,
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
            loadQuery(base) {
                let queryFilter = this.queryWithFilter();

                return _merge(base, queryFilter)
            },
            canUpdate(secret) {
                return secret.namespace !== undefined && this.authStore.user.isAllowed(permission.SECRET, action.UPDATE, secret.namespace) && !this.areNamespaceSecretsReadOnly;
            },
            canDelete(secret) {
                return secret.namespace !== undefined && this.authStore.user.isAllowed(permission.SECRET, action.DELETE, secret.namespace) && !this.areNamespaceSecretsReadOnly;
            },
            async loadData(callback) {
                this.isLoading = true;
                try {
                    const secretsResponse = await this.secretsStore.find(this.loadQuery({
                        size: parseInt(this.$route.query.size || 25),
                        page: parseInt(this.$route.query.page || 1),
                        sort: this.$route.query.sort || "name:asc",
                        ...(this.namespace === undefined ? {} : {
                            filters: {
                                namespace: {
                                    EQUALS: this.namespace
                                }
                            }
                        })
                    }));

                    this.$emit("update:isSecretReadOnly", secretsResponse.readOnly ?? false);
                    if (secretsResponse.results?.length == 0) {
                        this.hasData = false;
                    }

                    this.areNamespaceSecretsReadOnly = secretsResponse.readOnly ?? false;
                    this.secrets = secretsResponse.results;
                    this.total = secretsResponse.total;
                } finally {
                    this.isLoading = false;
                    if (callback) callback();
                }
            },
            updateSecretModal(secret) {
                this.secret.namespace = secret.namespace;
                this.secret.key = secret.key;
                this.secret.description = secret.description;
                this.secret.tags = secret.tags?.map((x) => x) || [{key: undefined, value: undefined}];
                this.secret.update = true;
                this.secret.updateValue = false;
                this.addSecretDrawerVisible = true;
            },
            checkSecretValue(rule, value, callback) {
                if (this.secret.updateValue && (this.secret.value === undefined || this.secret.value.length === 0)) {
                    callback(new Error("Value must not be empty."));
                } else {
                    callback();
                }
            },
            checkSecretTags(rule, value, callback) {
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
                this.secret.tags.push({key: "", value: ""});
            },
            removeSecretTag(index) {
                this.secret.tags.splice(index, 1);
            },
            removeSecret({key, namespace}) {
                this.$toast().confirm(this.$t("delete confirm", {name: key}), () => {
                    return this.namespacesStore
                        .deleteSecrets({namespace: namespace, key})
                        .then(() => {
                            this.$toast().deleted(key);
                        })
                        .then(() => this.loadData())
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

                    let secret: typeof this.secret = {
                        key: this.secret.key,
                        description: this.secret.description,
                        tags: this.secret.tags
                            .map(item => item.value !== undefined ? item : {key: item.key, value: ""})
                            .filter(item => item.key !== undefined)
                    };

                    if (this.isSecretValueUpdated()) {
                        secret.value = this.secret.value;
                    }

                    const action = this.isSecretValueUpdated() ? this.namespacesStore?.createSecrets : this.namespacesStore?.patchSecret;
                    return action({namespace: this.secret.namespace, secret: secret})
                        .then(() => {
                            this.secret.update = true;
                            this.$toast().saved(this.secret.key);
                            this.addSecretDrawerVisible = false;
                            this.resetForm();
                            return this.loadData();
                        })
                });
            },
            resetForm() {
                this.secret = {
                    namespace: this.namespace,
                    key: undefined,
                    value: "",
                    description: undefined,
                    tags: [{key: undefined, value: undefined}],
                    update: undefined,
                    updateValue: undefined
                }
            }
        },
    };
</script>
<style scoped lang="scss">
    .namespace-tag {
        background-color: var(--ks-log-background-debug) !important;
        color: var(--ks-log-content-debug);
        border: 1px solid var(--ks-log-border-debug);
        padding: 0 6px;

        :deep(.el-tag__content) {
            display: flex;
            align-items: center;
            gap: 4px;
        }
    }
</style>
