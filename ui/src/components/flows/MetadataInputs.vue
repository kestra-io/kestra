<template>
    <span v-if="required" class="me-1 text-danger">*</span>
    <span class="label">{{ label }}</span>
    <div class="mt-1 mb-2 wrapper">
        <el-row
            v-for="(input, index) in newInputs"
            :key="index"
            @click="selectInput(input, index)"
        >
            <el-col :span="24" class="d-flex">
                <InputText disabled :model-value="input.id" class="w-100" />
                <DeleteOutline
                    @click.prevent.stop="deleteInput(index)"
                    class="ms-2 delete"
                />
            </el-col>
        </el-row>
        <Add @add="addInput(index)" />
    </div>
</template>

<script setup>
    import MetadataInputsContent from "./MetadataInputsContent.vue";
    import InputText from "../code/components/inputs/InputText.vue";
    import Add from "../code/components/Add.vue";

    import {DeleteOutline} from "../code/utils/icons";
</script>

<script>
    import {h} from "vue";

    import {mapState} from "vuex";

    export default {
        emits: ["update:modelValue"],
        props: {
            modelValue: {
                type: Array,
                default: () => [],
            },
            inputs: {
                type: Array,
                default: () => [],
            },
            label: {type: String, required: true},
            required: {type: Boolean, default: false},
            disabled: {type: Boolean, default: false},
        },
        computed: {
            ...mapState("plugin", ["inputSchema", "inputsType"]),
        },
        mounted() {
            this.newInputs = this.inputs;

            this.$store
                .dispatch("plugin/loadInputsType")
                .then((_) => (this.loading = false));
        },
        data() {
            return {
                newInputs: [],
                selectedInput: undefined,
                selectedIndex: undefined,
                isEditOpen: false,
                loading: false,
            };
        },
        methods: {
            selectInput(input, index) {
                this.loading = true;
                this.selectedInput = input;
                this.selectedIndex = index;
                // this.isEditOpen = true;
                this.loadSchema(input.type);

                this.$store.commit("code/setPanel", {
                    breadcrumb: {
                        label: this.$t("inputs").toLowerCase(),
                        to: {
                            name: this.$route.name,
                            params: this.$route.params,
                            query: this.$route.query,
                        },
                    },
                    panel: h(MetadataInputsContent, {
                        modelValue: input,
                        inputs: this.inputs,
                        label: this.$t("inputs"),
                        selectedIndex: index,
                        "onUpdate:modelValue": this.updateSelected,
                    }),
                });
            },
            getCls(type) {
                return this.inputsType.find((e) => e.type === type).cls;
            },
            getType(cls) {
                return this.inputsType.find((e) => e.cls === cls).type;
            },
            loadSchema(type) {
                this.$store
                    .dispatch("plugin/loadInputSchema", {type: type})
                    .then((_) => (this.loading = false));
            },
            update() {
                if (
                    this.newInputs.map((e) => e.id).length !==
                    new Set(this.newInputs.map((e) => e.id)).size
                ) {
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
                this.newInputs = value;
            },
            deleteInput(index) {
                this.newInputs.splice(index, 1);
                this.$emit("update:modelValue", this.newInputs);
            },
            addInput() {
                this.newInputs.push({type: "STRING"});
                this.selectInput(this.newInputs.at(-1), 0);
            },
            onChangeType(value) {
                this.loading = true;
                this.selectedInput = {
                    type: value,
                    id: this.newInputs[this.selectedIndex].id,
                };
                this.newInputs[this.selectedIndex] = this.selectedInput;
                this.loadSchema(value);
            },
        },
    };
</script>

<style scoped lang="scss">
@import "../../components/code/styles/code.scss";
</style>
