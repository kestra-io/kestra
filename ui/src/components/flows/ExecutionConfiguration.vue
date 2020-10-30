<template>
    <div class="container" v-if="flow">
        <b-form v-hotkey="keymap" @submit.prevent="onSubmit">
            <b-form-group
                    v-for="input in flow.inputs"
                    :key="input.id"
                    :label="input.name"
                    label-cols-sm="2"
                    label-align-sm="right"
                    label-size="sm"
            >
                <b-form-input
                        v-if="input.type === 'STRING'"
                        v-model="input.value"
                        type="text"
                        :required="input.required"
                        :placeholder="`${placeholder} ${input.name}`"
                ></b-form-input>
                <b-form-input
                        v-if="input.type === 'INT'"
                        v-model="input.value"
                        type="number"
                        step="1"
                        :required="input.required"
                        :placeholder="`${placeholder} ${input.name}`"
                ></b-form-input>
                <b-form-input
                        v-if="input.type === 'FLOAT'"
                        v-model="input.value"
                        type="number"
                        step="0.001"
                        :required="input.required"
                        :placeholder="`${placeholder} ${input.name}`"
                ></b-form-input>
                <date-picker
                        v-if="input.type === 'DATETIME'"
                        v-model="input.value"
                        :required="input.required"
                        type="datetime"
                        class="w-100"
                        :placeholder="$t('select datetime')"
                ></date-picker>
                <b-form-file
                        v-if="input.type === 'FILE'"
                        v-model="input.value"
                        :required="input.required"
                        :state="Boolean(input.value)"
                        :placeholder="$t('choose file')"
                ></b-form-file>
            </b-form-group>
            <b-form-group class="text-right mb-0">
                <b-button type="submit" variant="primary">
                    {{$t('trigger execution')}}
                    <trigger title/>
                </b-button>

            </b-form-group>
        </b-form>
    </div>
</template>
<script>
    import {mapState} from "vuex";
    import DatePicker from "vue2-datepicker";
    import Trigger from "vue-material-design-icons/Cogs";

    export default {
        components: {DatePicker, Trigger},
        computed: {
            ...mapState("flow", ["flow"]),
            placeholder() {
                return this.$t("set a value for");
            },
            keymap () {
                return {
                    'ctrl+enter': this.onSubmit,
                }
            }
        },
        methods: {
            onSubmit() {
                const formData = new FormData();
                for (let input of this.flow.inputs || []) {
                    if (input.value !== undefined) {
                        if (input.type === "DATETIME") {
                            formData.append(input.name, input.value.toISOString());
                        } else if (input.type === "FILE") {
                            formData.append("files", input.value, input.name);
                        } else {
                            formData.append(input.name, input.value);
                        }
                    } else if (input.required) {
                        this.$toast().error(
                            this.$t("invalid field", {name: input.name}),
                            this.$t("form error")
                        )

                        return;
                    }
                }
                this.$store
                    .dispatch("execution/triggerExecution", {
                        ...this.$route.params,
                        formData
                    })
                    .then(response => {
                        this.$store.commit('execution/setExecution', response.data)
                        this.$router.push({name: 'executionEdit', params: response.data})

                        return response.data;
                    })
                    .then((execution) => {
                        this.$toast().success(this.$t('triggered done', {name: execution.id}));
                    })
            }
        }
    };
</script>
<style scoped>
</style>
