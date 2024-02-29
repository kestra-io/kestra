<template>
    <div class="w-100 d-flex flex-column align-items-center">
        <el-drawer
            v-if="isEditOpen"
            v-model="isEditOpen"
            destroy-on-close
            size=""
            :append-to-body="true"
        >
            <template #header>
                <code>inputs</code>
            </template>

            <template #footer>
                <div>
                    <el-button :icon="ContentSave" @click="update()" type="primary">
                        {{ $t('save') }}
                    </el-button>
                </div>
            </template>
            <div>
                <el-select
                    :model-value="selectedInput.type"
                    @update:model-value="onChangeType"
                >
                    <el-option
                        v-for="(input, index) in inputsType"
                        :key="index"
                        :label="input.type"
                        :value="input.type"
                    />
                </el-select>
                <task-root
                    v-loading="loading"
                    v-if="inputSchema"
                    name="root"
                    :model-value="selectedInput"
                    @update:model-value="updateSelected($event, selectedIndex)"
                    :schema="inputSchema.schema"
                    :definitions="inputSchema.schema.definitions"
                />
            </div>
        </el-drawer>
        <div class="w-100">
            <div>
                <div class="d-flex w-100" v-for="(input, index) in newInputs" :key="index">
                    <div class="flex-fill flex-grow-1 w-100 me-2">
                        <el-input
                            disabled
                            :model-value="input.id"
                        />
                    </div>
                    <div class="flex-shrink-1">
                        <el-button-group class="d-flex flex-nowrap">
                            <el-button :icon="TextSearch" @click="selectInput(input, index)" />
                            <el-button :icon="Plus" @click="addInput" />
                            <el-button
                                :icon="Minus"
                                @click="deleteInput(index)"
                            />
                        </el-button-group>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>
<script setup>
    import Plus from "vue-material-design-icons/Plus.vue";
    import Minus from "vue-material-design-icons/Minus.vue";
    import TextSearch from "vue-material-design-icons/TextSearch.vue";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
    import TaskRoot from "./tasks/TaskRoot.vue";
</script>
<script>
    import {mapState} from "vuex";

    export default {
        emits: ["update:modelValue"],
        props: {
            inputs: {
                type: Array,
                default: () => []
            }
        },
        computed: {
            ...mapState("plugin", ["inputSchema", "inputsType"]),
        },
        mounted() {
            if (this.inputs && this.inputs.length > 0) {
                this.newInputs = this.inputs;
            }

            this.$store.dispatch("plugin/loadInputsType")
                .then(_ => this.loading = false);

        },
        data() {
            return {
                newInputs: [{type: "STRING"}],
                selectedInput: undefined,
                selectedIndex: undefined,
                isEditOpen: false,
                loading: false
            }
        },
        methods: {
            selectInput(input, index) {
                this.loading = true;
                this.selectedInput = input;
                this.selectedIndex = index;
                this.isEditOpen = true;
                this.loadSchema(input.type)
            },
            getCls(type) {
                return this.inputsType.find(e => e.type === type).cls
            },
            getType(cls) {
                return this.inputsType.find(e => e.cls === cls).type
            },
            loadSchema(type) {
                this.$store.dispatch("plugin/loadInputSchema", {type: type})
                    .then(_ => this.loading = false);
            },
            update() {
                if (this.newInputs.map(e => e.id).length !== new Set(this.newInputs.map(e => e.id)).size) {
                    this.$store.dispatch("core/showMessage", {
                        variant: "error",
                        title: this.$t("error"),
                        message: this.$t("duplicate input id"),
                    });
                } else {
                    this.isEditOpen = false;
                    this.$emit("update:modelValue", this.newInputs);
                }
            },
            updateSelected(value) {
                this.newInputs[this.selectedIndex] = value;
            },
            deleteInput(index) {
                this.newInputs.splice(index, 1);
            },
            addInput() {
                this.newInputs.push({type: "STRING"});
            },
            onChangeType(value) {
                this.loading = true;
                this.selectedInput = {type: value, id: this.newInputs[this.selectedIndex].id};
                this.newInputs[this.selectedIndex] = this.selectedInput;
                this.loadSchema(value)
            }
        },
    };
</script>
