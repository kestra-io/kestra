<template>
    <div class="w-100">
        <el-drawer
            v-if="isEditOpen"
            v-model="isEditOpen"
            :title="$t('Edit variable')"
            destroy-on-close
            size=""
            :append-to-body="true"
        >
            <el-form-item>
                <template #label>
                    <code>{{ $t("name") }}</code>
                </template>
                <el-input
                    placeholder="name"
                    :model-value="newVariables[selectedIndex][0]"
                    @update:model-value="updateIndex($event, selectedIndex, 'key')"
                />
            </el-form-item>
            <el-form-item>
                <template #label>
                    <code>{{ $t("value") }}</code>
                </template>
                <el-input
                    autosize
                    type="textarea"
                    :model-value="newVariables[selectedIndex][1]"
                    @update:model-value="updateIndex($event, selectedIndex, 'value')"
                />
            </el-form-item>
            <el-button @click="update()">
                {{ $t("validate") }}
            </el-button>
        </el-drawer>
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
                            <el-button :icon="Eye" @click="selectVariable(index)" />
                            <el-button :icon="Plus" @click="addVariable" />
                            <el-button
                                :icon="Minus"
                                @click="deleteInput(index)"
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
    import Eye from "vue-material-design-icons/Eye.vue";
</script>
<script>
    export default {
        components: {},
        emits: ["update:modelValue"],
        props: {
            variables: {
                type: Object,
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
            deleteInput(key) {
                delete this.newVariables[key];
            },
            addVariable() {
                this.newVariables.push(["", undefined]);

            }
        },
    };
</script>
