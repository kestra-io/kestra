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
                <el-form label-position="top">
                    <el-form-item>
                        <template #label>
                            <code>name</code>
                        </template>
                        <el-input
                            :model-value="selectedInput.name"
                            @update:model-value="updateProps($event, selectedIndex, 'name')"
                        />
                    </el-form-item>
                    <el-form-item>
                        <template #label>
                            <code>type</code>
                        </template>
                        <el-select
                            class="flex-fill flex-grow-1 w-100 me-2"
                            :model-value="selectedInput.type"
                            @update:model-value="updateProps($event, selectedIndex, 'type')"
                        >
                            <el-option
                                v-for="input in inputsType"
                                :key="input.type"
                                :value="input.type"
                            />
                        </el-select>
                    </el-form-item>
                    <el-form-item>
                        <template #label>
                            <code>description</code>&nbsp
                        </template>
                        <editor
                            :model-value="selectedInput.description"
                            :navbar="false"
                            :full-height="false"
                            :input="true"
                            lang="text"
                            @update:model-value="updateProps($event, selectedIndex, 'description')"
                        />
                    </el-form-item>
                    <el-form-item>
                        <template #label>
                            <code>required</code>
                        </template>
                        <el-switch
                            active-color="green"
                            :model-value="selectedInput.required"
                            @change="updateRequired(selectedIndex)"
                        />
                    </el-form-item>
                    <el-form-item v-if="selectedInput.type !== 'FILE'">
                        <template #label>
                            <div class="d-flex">
                                <code class="flex-grow-1">defaults</code>
                                <el-switch
                                    active-color="green"
                                    :model-value="selectedInput.defaults !== undefined"
                                    @change="updateHaveDefaults(selectedIndex)"
                                />
                            </div>
                        </template>

                        <component
                            v-if="selectedInput.defaults !== undefined"
                            :is="inputsType.find(e => e.type === selectedInput.type).component"
                            v-bind="inputsType.find(e => e.type === selectedInput.type).props"
                            :model-value="selectedInput.defaults"
                            @update:model-value="updateProps($event, selectedIndex, 'defaults')"
                        />
                    </el-form-item>
                </el-form>
            </div>
        </el-drawer>
        <div class="w-100">
            <div>
                <div class="d-flex w-100" v-for="(input, index) in newInputs" :key="index">
                    <div class="flex-fill flex-grow-1 w-100 me-2">
                        <el-input
                            disabled
                            :model-value="input.name"
                        />
                    </div>
                    <div class="flex-shrink-1">
                        <el-button-group class="d-flex flex-nowrap">
                            <el-button :icon="Eye" @click="selectInput(input, index)" />
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
    import Eye from "vue-material-design-icons/Eye.vue";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
</script>
<script>
    import Editor from "../inputs/Editor.vue";

    export default {
        components: {Editor},
        emits: ["update:modelValue"],
        props: {
            inputs: {
                type: Object,
            }
        },
        mounted() {
            if (this.inputs && this.inputs.length > 0) this.newInputs = this.inputs;
        },
        data() {
            const editorProps = {
                navbar: false,
                fullHeight: false,
                input: true,
                lang: "text"
            }

            return {
                newInputs: [{}],
                inputsType: [
                    {
                        component: "editor",
                        props: editorProps,
                        type: "STRING",
                    },
                    {
                        component: "el-input-number",
                        props: {},
                        type: "INT",
                    },
                    {
                        component: "el-switch",
                        props: {
                            activeColor: "green"
                        },
                        type: "BOOLEAN",
                    },
                    {
                        component: "el-input",
                        props: {},
                        type: "FLOAT",
                    },
                    {
                        component: "el-date-picker",
                        props: {
                            type: "datetime"
                        },
                        type: "DATETIME",
                    },
                    {
                        component: "el-date-picker",
                        props: {
                            type: "date"
                        },
                        type: "DATE",
                    },
                    {
                        component: "el-time-picker",
                        props: {},
                        type: "TIME",
                    },
                    {
                        component: "el-input",
                        props: {},
                        type: "DURATION",
                    },
                    {
                        component: "el-input",
                        props: {},
                        type: "FILE",
                    },
                    {
                        component: "editor",
                        props: {...editorProps, ...{lang: "json"}},
                        type: "JSON",
                    },
                    {
                        component: "el-input",
                        props: {},
                        type: "URI",
                    }
                ],
                selectedInput: undefined,
                selectedIndex: undefined,
                isEditOpen: false
            }
        },
        methods: {
            selectInput(input, index) {
                this.selectedInput = input;
                this.selectedIndex = index;
                this.isEditOpen = true;
            },
            update() {
                if(this.newInputs.map(e => e.name).length !== new Set(this.newInputs.map(e => e.name)).size) {
                    this.$store.dispatch("core/showMessage", {
                        variant: "error",
                        title: this.$t("error"),
                        message: this.$t("duplicate input name"),
                    });
                } else {
                    this.isEditOpen = false;
                    this.$emit("update:modelValue", this.newInputs);
                }
            },
            updateProps(event, index, property) {
                this.newInputs[index][property] = event;
                if (property === "type") {
                    this.newInputs[index].required = false;
                    delete this.newInputs[index].defaults;
                }
            },
            updateRequired(index) {
                this.newInputs[index].required = !this.newInputs[index].required;
                if (this.newInputs[index].required) {
                    delete this.newInputs[index].defaults;
                }
            },
            updateHaveDefaults(index) {
                if (this.newInputs[index].defaults !== undefined) {
                    delete this.newInputs[index].defaults;
                } else {
                    this.newInputs[index].defaults = "";
                    this.newInputs[index].required = false;
                }
            },
            deleteInput(index) {
                this.newInputs.splice(index, 1);
            },
            addInput() {
                this.newInputs.push({})
            }
        },
    };
</script>
