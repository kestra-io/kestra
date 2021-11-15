<template>
    <div class="container" v-if="flow">
        <b-form v-hotkey="keymap" @submit.prevent="onSubmit">
            <b-form-group
                v-for="input in flow.inputs"
                :key="input.id"
                :label="input.name"
                label-cols-sm="2"
                label-align-sm="right"
            >
                <b-form-input
                    v-if="input.type === 'STRING'"
                    v-model="input.value"
                    type="text"
                    :required="input.required"
                    :placeholder="`${placeholder} ${input.name}`"
                />
                <b-form-input
                    v-if="input.type === 'INT'"
                    v-model="input.value"
                    type="number"
                    step="1"
                    :required="input.required"
                    :placeholder="`${placeholder} ${input.name}`"
                />
                <b-form-input
                    v-if="input.type === 'FLOAT'"
                    v-model="input.value"
                    type="number"
                    step="0.001"
                    :required="input.required"
                    :placeholder="`${placeholder} ${input.name}`"
                />
                <date-picker
                    v-if="input.type === 'DATETIME'"
                    v-model="input.value"
                    :required="input.required"
                    type="datetime"
                    class="w-100"
                    :placeholder="$t('select datetime')"
                />
                <b-form-file
                    v-if="input.type === 'FILE'"
                    v-model="input.value"
                    :required="input.required"
                    :state="Boolean(input.value)"
                    :placeholder="$t('choose file')"
                />
                <small v-if="input.description" class="form-text text-muted">{{ input.description }}</small>
            </b-form-group>
            <b-form-group class="text-right mb-0">
                <b-button type="submit" variant="primary" v-b-tooltip.hover.top="'(Ctrl + Enter)'">
                    {{ $t('launch execution') }}
                    <trigger title />
                </b-button>
            </b-form-group>
        </b-form>
    </div>
</template>
<script>
    import {mapState} from "vuex";
    import DatePicker from "vue2-datepicker";
    import Trigger from "vue-material-design-icons/Cogs";
    import {executeTask} from "../../utils/submitTask"
    export default {
        components: {DatePicker, Trigger},
        props: {
            redirect: {
                type: Boolean,
                default: true
            }
        },
        mounted() {
            setTimeout(() => {
                const input = this.$el.querySelector("input")
                if (input) {
                    input.focus()
                }
            }, 500)
        },
        computed: {
            ...mapState("flow", ["flow"]),
            placeholder() {
                return this.$t("set a value for");
            },
            keymap () {
                return {
                    "ctrl+enter": this.onSubmit,
                }
            }
        },
        methods: {
            onSubmit() {
                executeTask(this, this.flow, {redirect: this.redirect, id: this.flow.id, namespace: this.flow.namespace})
                this.$emit("onExecutionTrigger")
            }
        }
    };
</script>
<style scoped>
</style>
