<template>
    <template v-if="flow.concurrency">
        <div v-if="runningCount > 0 || !runningCountSet" :class="{'d-none': !runningCountSet}">
            <el-card class="mb-3">
                <div class="row mb-3">
                    <span class="col d-flex align-items-center">
                        <h5 class="m-3">RUNNING</h5> {{ runningCount }}/{{ flow.concurrency.limit }} {{ $t('active-slots') }}
                    </span>
                    <span class="col d-flex justify-content-end align-items-center">
                        {{ $t('behavior') }}: <status class="mx-2" :status="flow.concurrency.behavior" size="small" />
                    </span>
                </div>
                <div class="progressbar mb-3">
                    <el-progress :stroke-width="16" color="#5BB8FF" :percentage="progress" :show-text="false" />
                </div>
            </el-card>
            <el-card>
                <executions
                    :restore-url="false"
                    :topbar="false"
                    :namespace="flow.namespace"
                    :flow-id="flow.id"
                    is-concurrency
                    :statuses="[State.QUEUED, State.RUNNING, State.PAUSED]"
                    @state-count="setRunningCount"
                    :filter="false"
                />
            </el-card>
        </div>
        <empty-state
            v-else
            :title="$t('concurrency-view.title_no_executions')"
            :description="$t('concurrency-view.desc_no_executions')"
            :image="noConcurrencyImage"
        />
    </template>
    <empty-state
        v-else
        :title="$t('concurrency-view.title_no_limit')"
        :description="$t('concurrency-view.desc_no_limit')"
        :image="noConcurrencyImage"
    />
</template>

<script>
    import Executions from "../executions/Executions.vue";
    import EmptyState from "../layout/EmptyState.vue";
    import {mapState} from "vuex";
    import {State} from "@kestra-io/ui-libs";
    import Status from "../Status.vue";
    import noConcurrencyImage from "../../assets/no_concurrency.svg";

    export default {
        inheritAttrs: false,
        components: {
            Status,
            Executions,
            EmptyState
        },
        emits: ["expand-subflow"],
        data() {
            return {
                runningCount: 0,
                runningCountSet: false,
                noConcurrencyImage
            }
        },
        methods: {
            setRunningCount(count) {
                this.runningCount = count
                this.runningCountSet = true
            }
        },
        computed: {
            ...mapState("flow", ["flow"]),
            State() {
                return State
            },
            progress() {
                return this.runningCount / this.flow.concurrency.limit * 100
            }
        }
    }
</script>

<style lang="scss" scoped>
    .img-size {
        max-width: 200px;
    }
    .bg-purple {
        height: 100%;
        width: 100%;
    }
    h5 {
        font-weight: bold;
        margin-left: 0 !important;
    }

    :deep(.el-progress) {
        .el-progress-bar, .el-progress-bar__outer, .el-progress-bar__inner {
            border-radius: var(--bs-border-radius);
        }
    }
</style>