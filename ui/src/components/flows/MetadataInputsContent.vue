<template>
    <el-select
        :model-value="selectedInput.type"
        @update:model-value="onChangeType"
        class="mb-3"
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
        name="root"
        :model-value="selectedInput"
        @update:model-value="updateSelected($event, selectedIndex)"
        :schema="inputSchema?.schema"
        :definitions="inputSchema?.schema?.definitions"
    />

    <Save @click="update" what="input" class="w-100 mt-3" />
</template>

<script setup>
    import TaskRoot from "./tasks/TaskRoot.vue";
    import Save from "../code/components/Save.vue";
</script>

<script>
    import {mapState} from "vuex";

    export default {
        emits: ["update:modelValue"],
        props: {
            modelValue: {
                type: Object,
                default: () => {},
            },
            inputs: {
                type: Array,
                default: () => [],
            },
            label: {type: String, required: true},
            selectedIndex: {type: Number, required: true},
            required: {type: Boolean, default: false},
            disabled: {type: Boolean, default: false},
        },
        computed: {
            ...mapState("plugin", ["inputSchema", "inputsType"]),
        },
        created() {
            if (this.inputs && this.inputs.length > 0) {
                this.newInputs = this.inputs;
            }

            this.selectedInput = this.modelValue ?? {type: "STRING"};

            this.$store
                .dispatch("plugin/loadInputsType")
                .then((_) => (this.loading = false));
        },
        data() {
            return {
                newInputs: [{type: "STRING"}],
                selectedInput: undefined,
                loading: false,
            };
        },
        methods: {
            selectInput(input) {
                this.loading = true;
                this.selectedInput = input;
                this.loadSchema(input.type);
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
                    this.$store.commit("code/unsetPanel");
                    this.$emit("update:modelValue", [...this.inputs]);
                }
            },
            updateSelected(value) {
                if (!this.selectedIndex) {
                    return;
                }
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
