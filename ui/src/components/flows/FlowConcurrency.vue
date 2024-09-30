<script>
    import Executions from "../executions/Executions.vue";
    import {mapState} from "vuex";
    import State from "../../utils/state.js";
    import Status from "../Status.vue";

    export default {
        components: {Status, Executions},
        data() {
            return {
                runningCount: 0
            }
        },
        methods: {
            setRunningCount(count) {
                this.runningCount = count
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

<template>
    <div v-if="flow.concurrency">
        <el-card class="mb-1">
            <div class="row mb-3">
                <span class="col d-flex align-items-center">
                    <h5 class="m-3">RUNNING</h5> {{ runningCount }} / {{ flow.concurrency.limit }} {{ $t('active-slots') }}
                </span>
                <span class="col d-flex justify-content-end align-items-center">
                    {{ $t('behavior') }}: <status class="mx-2" :status="flow.concurrency.behavior" />
                </span>
            </div>
            <div class="progressbar mb-3">
                <el-progress :stroke-width="40" color="#5BB8FF" :percentage="progress" :show-text="false" />
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
    <div v-else class="d-flex flex-grow-1 flex-column align-items-center justify-content-center">
        <img src="../../../src/assets/no_concurrency.svg" alt="No Concurrency" class="img-size my-3">
        <h4>{{ $t('concurrency-view.title_no_limit') }}</h4>
        <span v-html="$t('concurrency-view.desc_no_limit')" />
    </div>
</template>

<style  lang="scss">
    .img-size {
        max-width: 200px;
    }
    .bg-purple {
        height: 100%;
        width: 100%;
    }
</style>