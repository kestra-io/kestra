

<template>
    <el-form label-position="top" :model="inputs" ref="form">
        <el-form-item
            v-for="input in flowInputs"
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
            />
            <el-input
                type="password"
                v-if="input.type === 'SECRET'"
                v-model="inputs[input.id]"
                show-password
            />
            <el-input-number
                v-if="input.type === 'INT'"
                v-model="inputs[input.id]"
                :step="1"
            />
            <el-input-number
                v-if="input.type === 'FLOAT'"
                v-model="inputs[input.id]"
                :step="0.001"
            />
            <el-radio-group
                v-if="input.type === 'BOOLEAN'"
                v-model="inputs[input.id]"
            >
                <el-radio-button label="true" />
                <el-radio-button label="false" />
                <el-radio-button label="undefined" />
            </el-radio-group>
            <el-date-picker
                v-if="input.type === 'DATETIME'"
                v-model="inputs[input.id]"
                type="datetime"
            />
            <el-date-picker
                v-if="input.type === 'DATE'"
                v-model="inputs[input.id]"
                type="date"
            />
            <el-time-picker
                v-if="input.type === 'TIME' || input.type === 'DURATION'"
                v-model="inputs[input.id]"
                type="time"
            />
            <div class="el-input el-input-file">
                <div class="el-input__wrapper" v-if="input.type === 'FILE'">
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
                v-if="input.type === 'JSON'"
                lang="json"
                v-model="inputs[input.id]"
            />

            <markdown v-if="input.description" class="markdown-tooltip text-muted" :source="input.description" font-size-var="font-size-xs" />
        </el-form-item>
    </el-form>
</template>
<script>

    import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
    import Flash from "vue-material-design-icons/Flash.vue";
    import Editor from "./Editor.vue";
    import Markdown from "../layout/Markdown.vue";

    export default {
        components: {
            Editor,
            Markdown
        },
        props: {
          flowInputs: {
              type: Object,
              default: null
          }
        },
        data() {
            return {
                inputs: {}
            }
        },
        watch: {
            inputs: {
                handler(newValue) {
                    this.$emit("update", this.inputs);
                },
                deep: true
            }
        }
    }
</script>
<style scoped lang="scss">

</style>