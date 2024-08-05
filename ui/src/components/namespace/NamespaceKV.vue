<template>
    <el-table
        :data="kvs"
        ref="table"
        :default-sort="{prop: 'id', order: 'ascending'}"
        stripe
        table-layout="auto"
        fixed
    >
        <el-table-column prop="key" sortable="custom" :sort-orders="['ascending', 'descending']" :label="$t('key')">
            <template #default="scope">
                <id :value="scope.row.key" :shrink="false" />
            </template>
        </el-table-column>
        <el-table-column prop="creationDate" sortable="custom" :sort-orders="['ascending', 'descending']" :label="$t('created date')" />
        <el-table-column prop="updateDate" sortable="custom" :sort-orders="['ascending', 'descending']" :label="$t('updated date')" />
        <el-table-column prop="expirationDate" sortable="custom" :sort-orders="['ascending', 'descending']" :label="$t('expiration date')" />

        <el-table-column column-key="update" class-name="row-action" v-if="canUpdateKv">
            <template #default="scope">
                <el-button :icon="FileDocumentEdit" link @click="updateKvModal(scope.row.key)" />
            </template>
        </el-table-column>

        <el-table-column column-key="delete" class-name="row-action" v-if="canDeleteKv">
            <template #default="scope">
                <el-button :icon="Delete" link @click="removeKv(scope.row.key)" />
            </template>
        </el-table-column>
    </el-table>

    <drawer
        v-if="addKvDrawerVisible"
        v-model="addKvDrawerVisible"
        :title="kvModalTitle"
    >
        <el-form class="ks-horizontal" :model="kv" :rules="rules" ref="form">
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
                <time-select
                    v-else-if="kv.type === 'DURATION'"
                    :from-now="false"
                    :time-range="kv.value"
                    clearable
                    allow-custom
                    @update:model-value="kv.value = $event.timeRange"
                />
                <editor
                    :full-height="false"
                    :input="true"
                    :navbar="false"
                    v-else-if="kv.type === 'JSON'"
                    lang="json"
                    v-model="kv.value"
                />
            </el-form-item>

            <el-form-item :label="$t('expiration')" prop="ttl">
                <time-select
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
    </drawer>
</template>

<script setup>
    import Editor from "../inputs/Editor.vue";
    import FileDocumentEdit from "vue-material-design-icons/FileDocumentEdit.vue";
    import Delete from "vue-material-design-icons/Delete.vue";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
    import TimeSelect from "../executions/date-select/TimeSelect.vue";
    import Check from "vue-material-design-icons/Check.vue";
</script>

<script>
    import {mapState} from "vuex";
    import Drawer from "../Drawer.vue";
    import Id from "../Id.vue";

    export default {
        components: {
            Id,
            Drawer
        },
        computed: {
            ...mapState("namespace", ["kvs"]),
            kvModalTitle() {
                return this.kv.key ? this.$t("kv.update", {key: this.kv.key}) : this.$t("kv.add");
            },
            canUpdateKv() {
                return this.$route.params.id
            },
            canDeleteKv() {
                return this.$route.params.id
            },
            addKvDrawerVisible: {
                get() {
                    return this.addKvModalVisible;
                },
                set(newValue) {
                    this.$emit("update:addKvModalVisible", newValue);
                }
            },
            rules() {
                return {
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
            }
        },
        props: {
            addKvModalVisible: {
                type: Boolean,
                default: false
            }
        },
        emits: [
            "update:addKvModalVisible"
        ],
        watch: {
            addKvDrawerVisible(newValue) {
                if (!newValue) {
                    this.resetKv();
                }
            }
        },
        data() {
            return {
                kv: {
                    key: undefined,
                    type: "STRING",
                    value: undefined,
                    ttl: undefined,
                    update: undefined
                }
            }
        },
        mounted () {
            this.loadKvs();
        },
        methods: {
            durationValidator(rule, value, callback) {
                if (value !== undefined && !value.match(/^P(?=[^T]|T.)(?:\d*D)?(?:T(?=.)(?:\d*H)?(?:\d*M)?(?:\d*S)?)?$/)) {
                    callback(new Error(this.$t("datepicker.error")));
                } else {
                    callback();
                }
            },
            loadKvs() {
                this.$store.dispatch("namespace/kvsList", {id: this.$route.params.id})
            },
            kvKeyDuplicate(rule, value, callback) {
                if (this.kv.update === undefined && this.kvs && this.kvs.find(r => r.key === value)) {
                    return callback(new Error(this.$t("kv.duplicate")));
                } else {
                    callback();
                }
            },
            async updateKvModal(key) {
                this.kv.key = key;
                const {type, value} = await this.$store.dispatch("namespace/kv", {namespace: this.$route.params.id, key});
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
            removeKv(key) {
                this.$toast().confirm(this.$t("delete confirm", {name: key}), () => {
                    return this.$store
                        .dispatch("namespace/deleteKv", {namespace: this.$route.params.id, key: key})
                        .then(() => {
                            this.$toast().deleted(key);
                        });
                });
            },
            saveKv(formRef) {
                formRef.validate((valid) => {
                    if (!valid) {
                        return false;
                    }

                    let value = this.kv.value;
                    if (this.kv.type === "BOOLEAN" && !this.kv.value) {
                        value = "false"
                    } else if (this.kv.type === "STRING" && !this.kv.value.startsWith("\"")) {
                        value = `"${value}"`
                    } else if (this.kv.type === "DATETIME") {
                        value = this.$moment(this.kv.value).toISOString()
                    } else if (this.kv.type === "DATE") {
                        value = this.$moment(this.kv.value).toISOString(true).split("T")[0]
                    }

                    return this.$store
                        .dispatch(
                            "namespace/createKv",
                            {namespace: this.$route.params.id, ...this.kv, value}
                        )
                        .then(() => {
                            this.$toast().saved(this.kv.key);
                            this.addKvDrawerVisible = false;
                        })
                });
            },
            resetKv() {
                this.kv = {
                    type: "STRING"
                }
            },
            onTtlChange(value) {
                this.kv.ttl = value.timeRange
            }
        },
    };
</script>