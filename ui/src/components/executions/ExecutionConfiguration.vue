<template>
    <div class="container" v-if="flow">
        <b-row>
            <b-col md="8" sm="12" offset-md="2" offset-sm="0">
                <b-card :title="`${$t('trigger execution for flow').capitalize()} ${flow.id}`">
                    <b-card-text>
                        <b-form @submit.prevent="onSubmit">
                            <b-form-group
                                v-for="input in flow.inputs"
                                :key="input.id"
                                :label="input.name"
                                label-cols-sm="4"
                                label-cols-md="4"
                                label-cols-lg="3"
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
                                    :placeholder="$t('select datetime')"
                                ></date-picker>
                                <b-form-file
                                    @change="onFileChange(input.name, $event)"
                                    v-if="input.type === 'FILE'"
                                    v-model="input.value"
                                    :required="input.required"
                                    :state="Boolean(input.value)"
                                    :placeholder="$t('choose file')"
                                ></b-form-file>
                            </b-form-group>
                            <b-button type="submit" variant="primary float-right">
                                {{$t('trigger execution') | cap}}
                                <trigger title />
                            </b-button>
                        </b-form>
                    </b-card-text>
                </b-card>
            </b-col>
        </b-row>
    </div>
</template>
<script>
import { mapState } from "vuex";
import DatePicker from "vue2-datepicker";
import Trigger from "vue-material-design-icons/Cogs";

export default {
    components: { DatePicker, Trigger },
    created() {
        this.loadFlow();
    },
    computed: {
        ...mapState("flow", ["flow"]),
        placeholder() {
            return this.$t("set a value for").capitalize();
        }
    },
    methods: {
        loadFlow() {
            this.$store.dispatch("flow/loadFlow", this.$route.params);
        },
        onFileChange(name, file) {
            console.log(name, file);
            window.$a = file;
            window.$i = this.flow.inputs;
        },
        onSubmit() {
            const formData = new FormData();
            for (let input of this.flow.inputs) {
                if (input.value !== undefined) {
                    let value;
                    if (input.type === "DATETIME") {
                        formData.append(input.name, input.value.toISOString());
                    } else if (input.type === "FILE") {
                        formData.append("files", input.value, input.name);
                    } else {
                        formData.append(input.name, input.value);
                    }
                } else if (input.required) {
                    this.$bvToast.toast(
                        `${this.$t("invalid field").capitalize()} : ${
                            input.name
                        }`,
                        {
                            title: this.$t("form error").capitalize(),
                            autoHideDelay: 5000,
                            toaster: "b-toaster-top-right",
                            variant: "danger"
                        }
                    );
                    return;
                }
            }
            this.$store
                .dispatch("execution/triggerExecution", {
                    ...this.$route.params,
                    formData
                })
                .then(response => {
                    console.log('response post', response)
                    this.$bvToast.toast(this.$t("triggered").capitalize(), {
                        title: this.$t("execution").capitalize(),
                        autoHideDelay: 5000,
                        toaster: "b-toaster-top-right",
                        variant: "success"
                    });
                    this.$router.push({name: 'execution', params: response.data})
                });
        }
    }
};
</script>
<style scoped>
</style>