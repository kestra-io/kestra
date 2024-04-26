<template>
    <el-input
        :model-value="JSON.stringify(values)"
        :disabled="true"
    >
        <template #append>
            <el-button :icon="Eye" @click="isOpen = true" />
        </template>
    </el-input>

    <drawer
        v-if="isOpen"
        v-model="isOpen"
    >
        <template #header>
            <code>{{ root }}</code>
        </template>
        <el-form-item>
            <template #label>
                <span class="d-flex flex-grow-1">
                    <span class="me-auto">
                        <code> One of</code>&nbsp;
                    </span>
                </span>
            </template>
            <el-select
                :model-value="selectedSchema"
                @update:model-value="onSelect"
            >
                <el-option
                    v-for="schema in schemaOptions"
                    :key="schema.label"
                    :label="schema.label"
                    :value="schema.value"
                />
            </el-select>
        </el-form-item>
        <el-form label-position="top" v-if="selectedSchema">
            <component
                :is="`task-${getType(currentSchema)}`"
                v-if="currentSchema"
                :model-value="modelValue"
                @update:model-value="onInput"
                :schema="currentSchema"
                :definitions="definitions"
            />
        </el-form>
        <template #footer>
            <el-button :icon="ContentSave" @click="isOpen = false" type="primary">
                {{ $t("save") }}
            </el-button>
        </template>
    </drawer>
</template>

<script setup>
    import Eye from "vue-material-design-icons/Eye.vue";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
</script>

<script>
    import Task from "./Task"
    import Drawer from "../../Drawer.vue"

    export default {
        mixins: [Task],
        components: {Drawer},
        data() {
            return {
                isOpen: false,
                schemas: [],
                selectedSchema: undefined
            };
        },
        created() {
            this.schemas = this.schema?.oneOf ?? []
        },
        methods: {
            onSelect(value) {
                this.selectedSchema = value
                // Set up default values
                if (this.currentSchema.properties && this.modelValue === undefined) {
                    const defaultValues = {};
                    for (let prop in this.currentSchema.properties) {
                        if(this.currentSchema.properties[prop].$required && this.currentSchema.properties[prop].default) {
                            defaultValues[prop] = this.currentSchema.properties[prop].default
                        }
                    }
                    this.onInput(defaultValues);
                }
            }
        },
        computed: {
            currentSchema() {
                return this.definitions[this.selectedSchema] ?? this.schemaByType[this.selectedSchema]
            },
            schemaByType() {
                return this.schemas.reduce((acc, schema) => {
                    acc[schema.type] = schema
                    return acc
                }, {})
            },
            schemaOptions() {
                return this.schemas.map(schema => {
                    const label = schema.$ref ? schema.$ref.split("/").pop() : schema.type
                    return {
                        label: label.capitalize(),
                        value: label
                    }
                })
            }
        },
    };
</script>
