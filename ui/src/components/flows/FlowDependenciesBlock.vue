<template>
    <Handle type="source" position="right" />
    <div class="deps-block-wrapper" @mouseover="mouseover" @mouseleave="mouseleave">
        <div>
            <el-tooltip placement="top" :persistent="false" :hide-after="0" transition="" :content="node.namespace">
                <div class="namespace">
                    {{ node.namespace === undefined ? "?" : node.namespace }}
                </div>
            </el-tooltip>

            <el-tooltip placement="top" :persistent="false" :hide-after="0" transition="" :content="node.id">
                <div class="flow-id">
                    <code>{{ node.id === undefined ? $t('dependencies missing acls') : node.id }}</code>
                </div>
            </el-tooltip>
        </div>
        <div>
            <el-button-group size="small" vertical>
                <el-button @click="expand" :disabled="isExpandDisabled">
                    <kicon :tooltip="$t('see dependencies')" placement="right">
                        <arrow-expand-all />
                    </kicon>
                </el-button>

                <el-button @click="link" :disabled="isFlowLinkDisabled">
                    <kicon :tooltip="$t('sub flow')" placement="right">
                        <link-variant />
                    </kicon>
                </el-button>
            </el-button-group>
        </div>
    </div>
    <Handle type="target" position="left" />
</template>

<script>
    import {Handle} from "@vue-flow/core"
    import Kicon from "../../components/Kicon.vue";
    import ArrowExpandAll from "vue-material-design-icons/ArrowExpandAll.vue";
    import LinkVariant from "vue-material-design-icons/LinkVariant.vue";

    export default {
        components:{
            Handle,
            Kicon,
            ArrowExpandAll,
            LinkVariant,
        },
        props: {
            node: {
                type: Object,
                required: true
            },
            loaded: {
                type: Boolean,
                required: true
            },
        },
        emits: ["expand", "mouseover", "mouseleave"],
        methods: {
            expand() {
                this.$emit("expand", this.node);
            },
            mouseover() {
                this.$emit("mouseover", this.node);
            },
            mouseleave() {
                this.$emit("mouseleave", this.node);
            },
            link() {
                this.$router.push({
                    name: "flows/update",
                    params: {
                        "namespace": this.node.namespace,
                        "id": this.node.id,
                        tab: "dependencies",
                        tenant: this.$route.params.tenant
                    },
                });
            }
        },
        computed: {
            isExpandDisabled() {
                return this.noRight || this.loaded;
            },
            isFlowLinkDisabled() {
                return this.noRight || (this.$route.params.id === this.node.id && this.$route.params.namespace === this.node.namespace)
            },
            noRight() {
                return this.node.namespace === undefined || this.node.id === undefined;
            },
        }
    };
</script>

<style lang="scss" scoped>
    .deps-block-wrapper {
        font-size: var(--font-size-xs);
        background: var(--bs-gray-100);
        height: 60px;
        text-align: center;
        display: flex;
        width: 248px;

        > div {
            flex-direction: column;
        }

        .el-button-group {
            height: 100%;
        }

        .el-button {
            border-radius: 0;
            height: 30px;
        }

        .namespace, .flow-id {
            padding: 6px ;
            width: 212px;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        .namespace {
            border-bottom: 1px solid var(--table-border-color);
            background-color: var(--bs-gray-200);
            html.dark & {
                background-color: var(--bs-gray-300);
            }
        }

        .flow-id {
            font-size: 100%;
        }
    }
</style>
