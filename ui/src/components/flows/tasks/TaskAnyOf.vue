<template>
    <el-input
        :model-value="JSON.stringify(values)"
        :disabled="true"
    >
        <template #append>
            <el-button :icon="Eye" @click="isOpen = true"/>
        </template>
    </el-input>


    <el-drawer
        v-if="isOpen"
        v-model="isOpen"
        destroy-on-close
        size=""
        :append-to-body="true"
    >
        <template #header>
            <code>{{ root }}</code>
        </template>
        <el-form-item>
            <template #label>
                <span class="d-flex flex-grow-1">
                    <span class="me-auto">
                        <code> Any of</code>&nbsp;
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
    </el-drawer>
</template>

<script setup>
    import Eye from "vue-material-design-icons/Eye.vue";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
    import Markdown from "../../layout/Markdown.vue";
    import Help from "vue-material-design-icons/HelpBox.vue";
    import TaskObject from "./TaskObject.vue";
</script>

<script>
    import Task from "./Task"

    export default {
        mixins: [Task],
        data() {
            return {
                isOpen: false,
                schemas: [],
                selectedSchema: undefined
            };
        },
        created() {
            this.schemas = this.schema?.anyOf ?? []
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
                return this.definitions[this.selectedSchema] ?? {type: this.selectedSchema}
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
