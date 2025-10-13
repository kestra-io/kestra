<template>
    <div class="m-3" v-if="localSubflowStatus">
        <div class="progress">
            <div
                v-for="state in State.arrayAllStates()"
                :key="state.name"
                class="progress-bar"
                role="progressbar"
                :class="`bg-${state.colorClass} ${localSubflowStatus[State.RUNNING] > 0 ? 'progress-bar-striped' : ''}`"
                :style="`width: ${getPercentage(state.name)}%`"
                :aria-valuenow="getPercentage(state.name)"
                aria-valuemin="0"
                :aria-valuemax="max"
            />
        </div>
        <div class="mt-2 d-flex">
            <router-link :to="goToExecutionsList(null)" class="el-button count-button">
                {{ $t("all executions") }} <span class="counter">{{ max }}</span>
            </router-link>
            <div v-for="state in State.arrayAllStates()" :key="state.name">
                <router-link :to="goToExecutionsList(state.name)" class="el-button count-button" v-if="localSubflowStatus[state.name] >= 0">
                    {{ capitalizeFirstLetter(getStateToBeDisplayed(state.name)) }}
                    <span class="counter">{{ localSubflowStatus[state.name] }}</span>
                    <div class="dot rounded-5" :class="`bg-${state.colorClass}`" />
                </router-link>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {ref, onMounted, watch} from "vue";
    import {State} from "@kestra-io/ui-libs";
    import {stateDisplayValues} from "../../utils/constants";
    import throttle from "lodash/throttle";

    const props = defineProps<{
        subflowsStatus: Record<string, number>;
        executionId: string;
        max: number;
    }>();

    const localSubflowStatus = ref<Record<string, number>>({});

    const updateThrottled = throttle(() => {
        localSubflowStatus.value = props.subflowsStatus;
    }, 500);

    onMounted(() => {
        localSubflowStatus.value = props.subflowsStatus;
    });

    watch(() => props.subflowsStatus, () => {
        updateThrottled();
    }, {deep: true});

    const getPercentage = (state: string): number => {
        if (!localSubflowStatus.value[state]) {
            return 0;
        }
        return Math.round((localSubflowStatus.value[state] / props.max) * 100);
    };

    const capitalizeFirstLetter = (str: string): string => {
        return str.charAt(0).toUpperCase() + str.slice(1).toLowerCase();
    };

    const getStateToBeDisplayed = (str: string): string => {
        if (str === State.RUNNING) {
            return stateDisplayValues.INPROGRESS;
        } else {
            return str;
        }
    };

    const goToExecutionsList = (state: string | null) => {
        const queries: Record<string, string> = {};

        queries["filters[triggerExecutionId][EQUALS]"] = props.executionId;

        if (state) {
            queries["filters[state][EQUALS]"] = state;
        }

        return {
            name: "executions/list",
            query: queries
        };
    };
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