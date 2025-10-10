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
                {{ t("all executions") }}
                <span class="counter">{{ max }}</span>
            </router-link>

            <div v-for="state in State.allStates()" :key="state.key">
                <router-link
                    :to="goToExecutionsList(state.key)"
                    class="el-button count-button"
                    v-if="localSubflowStatus[state.key] >= 0"
                >
                    {{ capitalizeFirstLetter(getStateToBeDisplayed(state.key)) }}
                    <span class="counter">{{ localSubflowStatus[state.key] }}</span>
                    <div class="dot rounded-5" :class="`bg-${state.colorClass}`" />
                </router-link>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {ref, watch, onMounted} from "vue";
    import {State} from "@kestra-io/ui-libs";
    import {stateDisplayValues} from "../../utils/constants";
    import throttle from "lodash/throttle";
    import {useI18n} from "vue-i18n";

    // ✅ Define props inline instead of using interface
    const props = defineProps<{
        subflowsStatus?: Record<string, number>;
        executionId?: string;
        max?: number;
    }>();

    // i18n
    const {t} = useI18n();

    

    // Reactive local state
    const localSubflowStatus = ref<Record<string, number>>(props.subflowsStatus || {});
    const max = props.max || 0;
    const executionId = props.executionId || "";

    // Throttled update
    const updateThrottled = throttle(() => {
        localSubflowStatus.value = props.subflowsStatus || {};

    }, 500);

    // Lifecycle hook
    onMounted(() => {
        localSubflowStatus.value = props.subflowsStatus || {};

    });

    // Watch props
    watch(
        () => props.subflowsStatus,
        () => updateThrottled()
    );

    // Methods
    function getPercentage(state: string): number {
        if (!localSubflowStatus.value[state]) return 0;
        return Math.round((localSubflowStatus.value[state] / max) * 100);
    }

    function capitalizeFirstLetter(str: string): string {
        return str.charAt(0).toUpperCase() + str.slice(1).toLowerCase();
    }

    function getStateToBeDisplayed(str: string): string {
        if (str === State.RUNNING) {
            return stateDisplayValues.INPROGRESS;
        }
        return str;
    }

    function goToExecutionsList(state: string | null) {
        const queries: Record<string, string> = {
            "filters[triggerExecutionId][EQUALS]": executionId,
        };
        if (state) {
            queries["filters[state][EQUALS]"] = state;
        }
        return {name: "executions/list", query: queries};
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
