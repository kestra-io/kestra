<template>
    <div>
        <el-tag
            v-for="label in labels"
            :key="label"
            closable
            class="me-1 labels"
            size="small"
            disable-transitions
            @close="handleClose(label)"
        >
            {{ label }}
        </el-tag>

        <el-input
            v-if="inputVisible"
            ref="input"
            v-model="inputValue"
            placeholder="Label as 'key:value'"
            @keyup.enter="handleInputConfirm"
            @blur="handleInputConfirm"
        >
            <template #suffix>
                <tag-outline />
            </template>
        </el-input>

        <el-button v-else @click="showInput">
            <plus /> Label
        </el-button>
    </div>
  </template>

<script>
    import { nextTick } from "vue";
    import { ElInput } from "element-plus";
    import TagOutline from "vue-material-design-icons/TagOutline.vue";
    import Plus from "vue-material-design-icons/Plus.vue";

    const isValidLabel = (label) => {
        return label.match(".+:.+") !== null;
    };

    const isValidLabels = (labels) => {
        return labels.every((label) => isValidLabel(label));
    };

    export default {
        components: {
            TagOutline,
            Plus
        },
        props: {
            value: {
                type: Array,
                default: [],
                validator(value) {
                    return isValidLabels(value);
                }
            }
        },
        emits: ["update:modelValue"],
        data() {
            return {
                inputVisible: false,
                inputValue: "",
                labels: this.value.slice()
            }
        },
        methods: {
            handleClose(tag) {
                this.labels.splice(this.labels.indexOf(tag), 1);
                this.$emit("update:modelValue", this.labels);
            },
            showInput() {
                this.inputVisible = true;
                nextTick(() => {
                    this.$refs.input.focus();
                })
            },
            handleInputConfirm() {
                if (this.inputValue) {
                    if (isValidLabel(this.inputValue)) {
                        this.labels.push(this.inputValue);
                        this.$emit("update:modelValue", this.labels);
                    } else {
                        return;
                    }
                }
                this.inputVisible = false;
                this.inputValue = "";
            }
      }
    };
</script>