<template>
    <tree-node
        :id="trigger.id"
        :type="trigger.type"
        :disabled="trigger.disabled"
    >
        <template #info>
            <span class="bottom" />
            <el-button-group>
                <el-button
                    v-if="trigger.description"
                    class="node-action"
                    size="small"
                    @click="$refs.descriptionTask.open()"
                >
                    <markdown-tooltip
                        ref="descriptionTask"
                        :description="trigger.description"
                        :id="hash"
                        :title="trigger.id"
                    />
                </el-button>

                <el-tooltip v-if="!this.execution" content="Delete" transition="" :hide-after="0" :persistent="false">
                    <el-button
                        class="node-action"
                        size="small"
                        @click="forwardEvent('delete', {id: this.trigger.id, section: 'triggers'})"
                        :icon="Delete"
                    />
                </el-tooltip>

                <task-edit
                    class="node-action"
                    :modal-id="`modal-source-${hash}`"
                    :task="trigger"
                    :flow-id="flowId"
                    size="small"
                    :namespace="namespace"
                    :revision="revision"
                    section="triggers"
                    :emit-only="true"
                    @update:task="forwardEvent('edit', $event)"
                />
            </el-button-group>
        </template>
    </tree-node>
</template>
<script>
    import {mapState} from "vuex";
    import MarkdownTooltip from "../../components/layout/MarkdownTooltip.vue";
    import TaskEdit from "../flows/TaskEdit.vue";
    import TreeNode from "./TreeNode.vue"
    import Delete from "vue-material-design-icons/Delete.vue";

    export default {
        components: {
            MarkdownTooltip,
            TaskEdit,
            TreeNode,
        },
        emits: ["edit", "delete"],
        props: {
            n: {
                type: Object,
                default: undefined
            },
            flowId: {
                type: String,
                required: true
            },
            namespace: {
                type: String,
                required: true
            },
            revision: {
                type: Number,
                default: undefined
            },
        },
        methods: {
            forwardEvent(type, event) {
                this.$emit(type, event);
            },
        },
        data() {
            return {
                filter: undefined,
                isOpen: false,
            };
        },
        computed: {
            ...mapState("graph", ["node"]),
            ...mapState("auth", ["user"]),
            ...mapState("execution", ["execution"]),
            hash() {
                return this.n.uid.hashCode();
            },
            trigger() {
                return this.n.trigger;
            },
            Delete() {
                return Delete;
            },
        },
    };
</script>

<style scoped lang="scss">
    .node-action {
        height: 28px;
        padding-top: 1px;
        padding-right: 5px;
        padding-left: 5px;
    }


    .info-wrapper {
        .bottom {
            padding: 4px 4px;
            color: var(--bs-body-color);
            opacity: 0.7;
            font-size: var(--font-size-xs);
            flex-grow: 2;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            position: relative;
        }
    }
</style>
