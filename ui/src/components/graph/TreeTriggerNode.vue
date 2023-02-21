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

                <task-edit
                    class="node-action"
                    :modal-id="`modal-source-${hash}`"
                    :task="trigger"
                    :flow-id="flowId"
                    size="small"
                    :namespace="namespace"
                    :revision="revision"
                    section="triggers"
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

    export default {
        components: {
            MarkdownTooltip,
            TaskEdit,
            TreeNode,
        },
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
        methods: {},
        data() {
            return {
                filter: undefined,
                isOpen: false,
            };
        },
        computed: {
            ...mapState("graph", ["node"]),
            ...mapState("auth", ["user"]),
            hash() {
                return this.n.uid.hashCode();
            },
            trigger() {
                return this.n.trigger;
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
