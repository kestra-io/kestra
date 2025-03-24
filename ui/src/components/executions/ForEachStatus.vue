<template>
    <div class="m-3" v-if="localSubflowStatus">
        <div class="progress">
            <div
                v-for="state in State.allStates()"
                :key="state.key"
                class="progress-bar"
                role="progressbar"
                :class="`bg-${state.colorClass} ${localSubflowStatus[State.RUNNING] > 0 ? 'progress-bar-striped' : ''}`"
                :style="`width: ${getPercentage(state.key)}%`"
                :aria-valuenow="getPercentage(state.key)"
                aria-valuemin="0"
                :aria-valuemax="max"
            />
        </div>
        <div class="mt-2 d-flex">
            <router-link :to="goToExecutionsList(null)" class="el-button count-button">
                {{ $t("all executions") }} <span class="counter">{{ max }}</span>
            </router-link>
            <div v-for="state in State.allStates()" :key="state.key">
                <router-link :to="goToExecutionsList(state.key)" class="el-button count-button" v-if="localSubflowStatus[state.key] >= 0">
                    {{ capitalizeFirstLetter(getStateToBeDisplayed(state.key)) }}
                    <span class="counter">{{ localSubflowStatus[state.key] }}</span>
                    <div class="dot rounded-5" :class="`bg-${state.colorClass}`" />
                </router-link>
            </div>
        </div>
    </div>
</template>
<script>
    import {cssVariable} from "@kestra-io/ui-libs";
    import {stateDisplayValues} from "../../utils/constants";
    import {State} from "@kestra-io/ui-libs"
    import throttle from "lodash/throttle"

    export default {
        computed: {
            State() {
                return State
            }
        },
        data() {
            return {
                localSubflowStatus: {},
                updateThrottled: throttle(function () {
                    this.localSubflowStatus = this.subflowsStatus
                }, 500)
            }
        },
        created() {
            this.localSubflowStatus = this.subflowsStatus
        },
        props: {
            subflowsStatus: {
                type: Object,
                required: true
            },
            executionId: {
                type: String,
                required: true
            },
            max: {
                type: Number,
                required:true
            }
        },
        watch: {
            subflowsStatus() {
                this.updateThrottled();
            }
        },
        methods: {
            cssVariable,
            getPercentage(state) {
                if (!this.localSubflowStatus[state]) {
                    return 0;
                }
                return Math.round((this.localSubflowStatus[state] / this.max) * 100);
            },
            capitalizeFirstLetter(str) {
                return str.charAt(0).toUpperCase() + str.slice(1).toLowerCase();
            },
            getStateToBeDisplayed(str){
                if(str === State.RUNNING){
                    return stateDisplayValues.INPROGRESS;
                }else{
                    return str;
                }
            },
            goToExecutionsList(state) {
                const queries = {
                    triggerExecutionId: this.executionId,
                }

                if (state) {
                    queries.state = state;
                }

                return {
                    name: "executions/list",
                    query: queries
                };
            }
        }
    }
</script>
<style scoped lang="scss">
    .dot {
        width: 6.413px;
        height: 6.413px;
        margin-right: 0.5rem;
    }

    .progress {
        height: 5px;
    }

    .el-button {
        padding: 0.5rem 1rem;
        &:hover {
            html.dark & {
                border-color: #404559;
            }
        }
        &:focus {
            html.dark & {
                border-color: #404559;
            }
        }
    }

    .count-button {
        padding: 4px 8px;
        margin-right: 0.5rem;
        align-items: center;
        gap: 8px;
        border-radius: 2px;
        font-size: 0.75rem;
    }

    .counter {
        padding: 0 4px;
        margin-left: 0.5rem;
        align-items: flex-start;
        gap: 10px;
        border-radius: 2px;
        background-color: var(--ks-tag-background);
        font-size: 0.65rem;
        line-height: 1.0625rem;
    }
</style>