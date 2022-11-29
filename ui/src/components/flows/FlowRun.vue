<template>
    <div class="container" v-if="flow">
        <el-alert v-if="flow.disabled" type="warning" show-icon :closable="false">
            <strong>{{ $t('disabled flow title') }}</strong><br>
            {{ $t('disabled flow desc') }}
        </el-alert>
        <el-form class="ks-horizontal">
            <el-form-item
                v-for="input in flow.inputs"
                :key="input.id"
                :label="input.name"
            >
                <el-input
                    v-if="input.type === 'STRING' || input.type === 'URI'"
                    v-model="input.value"
                    :required="input.required"
                    :placeholder="`${placeholder} ${input.name}`"
                    :state="state(input)"
                />
                <el-input-number
                    v-if="input.type === 'INT'"
                    v-model="input.value"
                    :step="1"
                    :required="input.required"
                    :placeholder="`${placeholder} ${input.name}`"
                    :state="state(input)"
                />
                <el-input-number
                    v-if="input.type === 'FLOAT'"
                    v-model="input.value"
                    :step="0.001"
                    :required="input.required"
                    :placeholder="`${placeholder} ${input.name}`"
                    :state="state(input)"
                />
                <el-checkbox
                    v-if="input.type === 'BOOLEAN'"
                    v-model="input.value"
                    value="true"
                    unchecked-value="false"
                    :required="input.required"
                    :state="state(input)"
                />
                <el-date-picker
                    v-if="input.type === 'DATETIME'"
                    v-model="input.value"
                    type="datetime"
                    :required="input.required"
                    :state="state(input)"
                    :placeholder="`${placeholder} ${input.name}`"
                />
                <el-date-picker
                    v-if="input.type === 'DATE'"
                    v-model="input.value"
                    :required="input.required"
                    :state="state(input)"
                    type="date"
                    :placeholder="input.description"
                />
                <el-time-picker
                    v-if="input.type === 'TIME' || input.type === 'DURATION'"
                    v-model="input.value"
                    :required="input.required"
                    :state="state(input)"
                    type="time"
                    :placeholder="`${placeholder} ${input.name}`"
                />
                <el-input
                    class="el-input-file"
                    v-if="input.type === 'FILE'"
                    v-model="input.value"
                    type="file"
                    :required="input.required"
                    :placeholder="$t('choose file')"
                    :state="state(input)"
                />
                <el-input
                    v-if="input.type === 'JSON'"
                    v-model="input.value"
                    type="textarea"
                    :required="input.required"
                    :state="state(input)"
                    :placeholder="`${placeholder} ${input.name}`"
                />

                <small v-if="input.description" class="form-text text-muted">{{ input.description }}</small>
            </el-form-item>
            <el-form-item class="mb-0">
                <el-button :icon="icon.Trigger" @click="onSubmit" type="primary" :disabled="flow.disabled">
                    {{ $t('launch execution') }}
                </el-button>
            </el-form-item>
        </el-form>
    </div>
</template>
<script>
    import {shallowRef} from "vue";
    import {mapState} from "vuex";
    import Trigger from "vue-material-design-icons/Cogs";
    import {executeTask} from "../../utils/submitTask"

    export default {
        components: {Trigger},
        props: {
            redirect: {
                type: Boolean,
                default: true
            }
        },
        emits: ["executionTrigger"],
        data() {
            return {icon: {Trigger: shallowRef(Trigger)}};
        },
        mounted() {
            setTimeout(() => {
                const input = this.$el && this.$el.querySelector && this.$el.querySelector("input")
                if (input && !input.className.includes("mx-input")) {
                    input.focus()
                }
            }, 500)
        },
        computed: {
            ...mapState("flow", ["flow"]),
            placeholder() {
                return this.$t("set a value for");
            },
        },
        methods: {
            onSubmit() {
                executeTask(this, this.flow, {redirect: this.redirect, id: this.flow.id, namespace: this.flow.namespace})
                this.$emit("executionTrigger")
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
