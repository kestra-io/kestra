<template>
    <template v-if="inputsList">
        <el-form-item
            v-for="input in inputsList || []"
            :key="input.id"
            :label="input.id"
            :required="input.required !== false"
            :prop="input.id"
        >
            <editor
                :full-height="false"
                :input="true"
                :navbar="false"
                v-if="input.type === 'STRING' || input.type === 'URI'"
                v-model="inputs[input.id]"
                @update:model-value="onChange"
            />
            <el-select
                :full-height="false"
                :input="true"
                :navbar="false"
                v-if="input.type === 'ENUM'"
                v-model="inputs[input.id]"
                @update:model-value="onChange"
            >
                <el-option
                    v-for="item in input.values"
                    :key="item"
                    :label="item"
                    :value="item"
                >
                    {{ item }}
                </el-option>
            </el-select>
            <el-input
                type="password"
                v-if="input.type === 'SECRET'"
                v-model="inputs[input.id]"
                @update:model-value="onChange"
                show-password
            />
            <el-input-number
                v-if="input.type === 'INT'"
                v-model="inputs[input.id]"
                @update:model-value="onChange"
                :step="1"
            />
            <el-input-number
                v-if="input.type === 'FLOAT'"
                v-model="inputs[input.id]"
                @update:model-value="onChange"
                :step="0.001"
            />
            <el-radio-group
                v-if="input.type === 'BOOLEAN'"
                v-model="inputs[input.id]"
                @update:model-value="onChange"
            >
                <el-radio-button :label="$t('true')" value="true" />
                <el-radio-button :label="$t('false')" value="false" />
                <el-radio-button :label="$t('undefined')" value="undefined" />
            </el-radio-group>
            <el-date-picker
                v-if="input.type === 'DATETIME'"
                v-model="inputs[input.id]"
                @update:model-value="onChange"
                type="datetime"
            />
            <el-date-picker
                v-if="input.type === 'DATE'"
                v-model="inputs[input.id]"
                @update:model-value="onChange"
                type="date"
            />
            <el-time-picker
                v-if="input.type === 'TIME' || input.type === 'DURATION'"
                v-model="inputs[input.id]"
                @update:model-value="onChange"
                type="time"
            />
            <div class="el-input el-input-file" v-if="input.type === 'FILE'">
                <div class="el-input__wrapper">
                    <input
                        :id="input.id+'-file'"
                        class="el-input__inner"
                        type="file"
                        @change="onFileChange(input, $event)"
                        autocomplete="off"
                        :style="{display: typeof(inputs[input.id]) === 'string' && inputs[input.id].startsWith('kestra:///') ? 'none': ''}"
                    >
                    <label
                        v-if="typeof(inputs[input.id]) === 'string' && inputs[input.id].startsWith('kestra:///')"
                        :for="input.id+'-file'"
                    >Kestra Internal Storage File</label>
                </div>
            </div>
            <editor
                :full-height="false"
                :input="true"
                :navbar="false"
                v-if="input.type === 'JSON' || input.type === 'ARRAY'"
                lang="json"
                v-model="inputs[input.id]"
            />

            <markdown v-if="input.description" class="markdown-tooltip text-muted" :source="input.description" font-size-var="font-size-xs" />
        </el-form-item>
    </template>
    <el-alert type="info" :show-icon="true" :closable="false" v-else>
        {{ $t("no inputs") }}
    </el-alert>
</template>

<script>
    import Editor from "../../components/inputs/Editor.vue";
    import Markdown from "../layout/Markdown.vue";
    import Inputs from "../../utils/inputs";

    export default {
        components: {Editor, Markdown},
        props: {
            modelValue: {
                default: undefined,
                type: Object
            },
            inputsList: {
                type: Array,
                default: undefined
            },
        },
        data() {
            return {
                inputs: {},
            };
        },
        emits: ["update:modelValue"],
        created() {
            this.updateDefaults();
        },
        mounted() {
            setTimeout(() => {
                const input = this.$el && this.$el.querySelector && this.$el.querySelector("input")
                if (input && !input.className.includes("mx-input")) {
                    input.focus()
                }
            }, 500)

            this._keyListener = function(e) {
                if (e.keyCode === 13 && (e.ctrlKey || e.metaKey))  {
                    e.preventDefault();
                    this.onSubmit(this.$refs.form);
                }
            };

            document.addEventListener("keydown", this._keyListener.bind(this));
        },
        beforeUnmount() {
            document.removeEventListener("keydown", this._keyListener);
        },
        computed: {

        },
        methods: {
            updateDefaults() {
                for (const input of this.inputsList || []) {
                    this.inputs[input.id] = Inputs.normalize(input.type, input.defaults);
                    this.onChange();
                }
            },
            onChange() {
                this.$emit("update:modelValue", this.inputs);
            },
            onFileChange(input, e) {
                if (!e.target) {
                    return;
                }

                const files = e.target.files || e.dataTransfer.files;
                if (!files.length) {
                    return;
                }
                this.inputs[input.id] = e.target.files[0];
                this.onChange();
            },
        },
        watch: {
            inputs: {
                handler() {
                    this.$emit("update:modelValue", this.inputs);
                },
            },
            inputsList: {
                handler() {
                    this.updateDefaults();
                },
            }
        }
    };
</script>

<style scoped lang="scss">

</style>