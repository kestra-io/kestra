<template>
    <div class="node-wrapper" :class="nodeClass">
        <div class="status-color" v-if="execution" :class="statusClass" />
        <div class="icon">
            <task-icon :cls="type" />
        </div>
        <div class="task-content">
            <div class="card-header">
                <div class="task-title">
                    <span>{{ id }}</span>
                </div>
            </div>
            <slot name="content" />
            <div class="info-wrapper">
                <slot name="info" />
            </div>
        </div>
    </div>
</template>

<script>
    import {mapState} from "vuex";
    import State from "../../utils/state";
    import taskIcon from "../plugins/TaskIcon.vue";

    export default {
        components: {
            taskIcon
        },
        props: {
            id: {
                type: String,
                default: undefined
            },
            type: {
                type: String,
                default: undefined
            },
            disabled: {
                type: Boolean,
                default: undefined
            },
            state: {
                type: String,
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
            ...mapState("execution", ["execution"]),
            hash() {
                return this.n.uid.hashCode();
            },
            nodeClass() {
                return {
                    ["node-disabled"]: this.disabled,
                };
            },
            statusClass() {
                return {
                    ["bg-" + State.colorClass()[this.state]]: true,
                };
            },
        },
    }
</script>

<style scoped lang="scss">
    .node-wrapper {
        cursor: pointer;
        display: flex;
        width: 200px;
        background: var(--bs-gray-100);

        .el-button, .card-header {
            border-radius: 0 !important;
        }

        &.node-disabled {
            .card-header .task-title {
                text-decoration: line-through;
            }
        }

        > .icon {
            width: 35px;
            height: 53px;
            background: var(--bs-white);
            position: relative;
        }

        .status-color {
            width: 10px;
            height: 53px;
            border-right: 1px solid var(--bs-border-color);
        }


        .is-success {
            background-color: var(--green);
        }

        .is-running {
            background-color: var(--blue);
        }

        .is-failed {
            background-color: var(--red);
        }

        .bg-undefined {
            background-color: var(--bs-gray-400);
        }

        .task-content {
            flex-grow: 1;
            width: 38px;

            .card-header {
                height: 25px;
                padding: 2px;
                margin: 0;
                border-bottom: 1px solid var(--bs-border-color);
                flex: 1;
                flex-wrap: nowrap;
                background-color: var(--bs-gray-200);
                color: var(--bs-body-color);

                html.dark & {
                    background-color: var(--bs-gray-300);
                }

                .task-title {
                    margin-left: 2px;
                    display: inline-block;
                    font-size: var(--font-size-sm);
                    flex-grow: 1;
                    overflow: hidden;
                    text-overflow: ellipsis;
                    max-width: 100%;
                    white-space: nowrap;
                }

                :deep(.node-action) {
                    flex-shrink: 2;
                    padding-top: 18px;
                    padding-right: 18px;
                }
            }

        }

        .card-wrapper {
            top: 50px;
            position: absolute;
        }

        .info-wrapper {
            display: flex;
        }

        .node-action {
            height: 28px;
            padding-top: 1px;
            padding-right: 5px;
            padding-left: 5px;
        }
    }
</style>