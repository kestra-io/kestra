<template>
    <div class="w-100">
        <drawer
            v-if="isEditOpen"
            v-model="isEditOpen"
        >
            <template #header>
                <code>variables</code>
            </template>

            <template #footer>
                <div>
                    <el-button :icon="ContentSave" @click="update()" type="primary">
                        {{ $t('save') }}
                    </el-button>
                </div>
            </template>

            <el-form label-position="top">
                <el-form-item>
                    <template #label>
                        <code>name</code>
                    </template>
                    <el-input
                        :model-value="newVariables[selectedIndex][0]"
                        @update:model-value="updateIndex($event, selectedIndex, 'key')"
                    />
                </el-form-item>
                <el-form-item>
                    <template #label>
                        <code>value</code>
                    </template>
                    <editor
                        :model-value="newVariables[selectedIndex][1]"
                        :navbar="false"
                        :full-height="false"
                        :input="true"
                        lang="text"
                        @update:model-value="updateIndex($event, selectedIndex, 'value')"
                    />
                </el-form-item>
            </el-form>
        </drawer>
        <div class="w-100">
            <div v-if="variables">
                <div class="d-flex w-100" v-for="(value, index) in newVariables" :key="index">
                    <div class="flex-fill flex-grow-1 w-100 me-2">
                        <el-input
                            disabled
                            :model-value="value[0]"
                        />
                    </div>
                    <div class="flex-shrink-1">
                        <el-button-group class="d-flex flex-nowrap">
                            <el-button :icon="TextSearch" @click="selectVariable(index)" />
                            <el-button :icon="Plus" @click="addVariable" />
                            <el-button
                                :icon="Minus"
                                @click="deleteInput(index)"
                                :disabled="index === 0 && newVariables.length === 1"
                            />
                        </el-button-group>
                    </div>
                </div>
            </div>
            <div v-else class="d-flex justify-content-center">
                <el-button :icon="Plus" type="success" class="w-25" @click="addVariable">
                    Add
                </el-button>
            </div>
        </div>
    </div>
</template>

<script setup>
    import Plus from "vue-material-design-icons/Plus.vue";
    import Minus from "vue-material-design-icons/Minus.vue";
    import TextSearch from "vue-material-design-icons/TextSearch.vue";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
</script>

<script>
    import Editor from "../inputs/Editor.vue";
    import Drawer from "../Drawer.vue";

    export default {
        components: {Editor, Drawer},
        emits: ["update:modelValue"],
        props: {
            variables: {
                type: Array,
                default: () => []
            }
        },
        created() {
            this.newVariables = this.variables ? this.variables : this.newVariables
        },
        data() {
            return {
                newVariables: ["",undefined],
                selectedIndex: undefined,
                isEditOpen: false
            }
        },
        methods: {
            selectVariable(index) {
                this.selectedIndex = index;
                this.isEditOpen = true;
            },
            update() {
                this.isEditOpen = false;
                this.$emit("update:modelValue", this.newVariables);
            },
            updateIndex(event, index, edited) {
                if (edited === "key") {
                    this.newVariables[index][0] = event;
                } else {
                    this.newVariables[index][1] = event;
                }
            },
            deleteInput(index) {
                this.newVariables.splice(index, 1);
            },
            addVariable() {
                this.newVariables.push(["", undefined]);

            }
        },
    };
</script>
