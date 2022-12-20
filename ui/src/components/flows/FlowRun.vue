<template>
    <div class="container" v-if="flow">
        <el-alert v-if="flow.disabled" type="warning" show-icon :closable="false">
            <strong>{{ $t('disabled flow title') }}</strong><br>
            {{ $t('disabled flow desc') }}
        </el-alert>
        <el-form class="ks-horizontal" :model="inputs" ref="form">
            <el-form-item
                v-for="input in flow.inputs"
                :key="input.id"
                :label="input.name"
                :required="input.required !== false"
                :prop="input.name"
            >
                <el-input
                    v-if="input.type === 'STRING' || input.type === 'URI'"
                    v-model="inputs[input.name]"
                />
                <el-input-number
                    v-if="input.type === 'INT'"
                    v-model="inputs[input.name]"
                    :step="1"
                />
                <el-input-number
                    v-if="input.type === 'FLOAT'"
                    v-model="inputs[input.name]"
                    :step="0.001"
                />
                <el-checkbox
                    v-if="input.type === 'BOOLEAN'"
                    v-model="inputs[input.name]"
                    value="true"
                    unchecked-value="false"
                />
                <el-date-picker
                    v-if="input.type === 'DATETIME'"
                    v-model="inputs[input.name]"
                    type="datetime"
                />
                <el-date-picker
                    v-if="input.type === 'DATE'"
                    v-model="inputs[input.name]"
                    type="date"
                />
                <el-time-picker
                    v-if="input.type === 'TIME' || input.type === 'DURATION'"
                    v-model="inputs[input.name]"
                    type="time"
                />
                <div class="el-input el-input-file">
                    <div class="el-input__wrapper" v-if="input.type === 'FILE'">
                        <input
                            class="el-input__inner"
                            type="file"
                            @change="onFileChange(input, $event)"
                            autocomplete="off"
                        >
                    </div>
                </div>
                <el-input
                    v-if="input.type === 'JSON'"
                    v-model="inputs[input.name]"
                    type="textarea"
                    autosize
                    :state="state(input)"
                />

                <small v-if="input.description" class="text-muted">{{ input.description }}</small>
            </el-form-item>
            <el-form-item class="submit">
                <el-button :icon="Flash" @click="onSubmit($refs.form)" type="primary" :disabled="flow.disabled">
                    {{ $t('launch execution') }}
                </el-button>
            </el-form-item>
        </el-form>
    </div>
</template>

<script setup>
    import Flash from "vue-material-design-icons/Flash.vue";
</script>

<script>
    import {mapState} from "vuex";
    import {executeTask} from "../../utils/submitTask"

    export default {
        props: {
            redirect: {
                type: Boolean,
                default: true
            }
        },
        emits: ["executionTrigger"],
        data() {
            return {
                inputs: {},
            };
        },
        mounted() {
            for (const input of this.flow.inputs) {
                this.inputs[input.name] = input.defaults;

                if (input.type === "DATETIME" && input.defaults) {
                    this.inputs[input.name] = new Date(input.defaults);
                }
            }

            setTimeout(() => {
                const input = this.$el && this.$el.querySelector && this.$el.querySelector("input")
                if (input && !input.className.includes("mx-input")) {
                    input.focus()
                }
            }, 500)
        },
        computed: {
            ...mapState("flow", ["flow"]),
        },
        methods: {
            onSubmit(formRef) {
                formRef.validate((valid) => {
                    if (!valid) {
                        return false;
                    }

                    executeTask(this, this.flow, this.inputs, {
                        redirect: this.redirect,
                        id: this.flow.id,
                        namespace: this.flow.namespace
                    })
                    this.$emit("executionTrigger");
                });
            },
            onFileChange(input, e) {
                if (!e.target) {
                    return;
                }

                const files = e.target.files || e.dataTransfer.files;
                if (!files.length) {
                    return;
                }
                this.inputs[input.name] = e.target.files[0];
            },
            state(input) {
                const required = input.required === undefined ? true : input.required;

                if (!required && input.value === undefined) {
                    return null;
                }

                if (required && input.value === undefined) {
                    return false;
                }

                return true;
            }

        }
    };
</script>
