<template>
  <div v-if="execution" class="log-wrapper text-white text-monospace">
    <div v-for="taskItem in execution.taskRunList" :key="taskItem.id">
      <template v-if="(!task || task.id === taskItem.id) && taskItem.attempts">
        <div class="bg-dark attempt-wrapper">
          <template v-for="(attempt, index) in taskItem.attempts">
            <div
              :id="`attempt-${index}-${attempt.state.startDate}`"
              :key="`attempt-${index}-${attempt.state.startDate}`"
            >
              <!-- Tooltip -->
              <b-tooltip
                placement="top"
                :target="`attempt-${index}-${attempt.state.startDate}`"
                triggers="hover"
              >
                {{$t('from')}} : {{attempt.state.startDate | date('LLL:ss') }}
                <br />
                {{$t('to')}} : {{attempt.state.endDate | date('LLL:ss') }}
                <br />
                <br />
                <clock />
                {{$t('duration')}} : {{attempt.state.duration | humanizeDuration}}
              </b-tooltip>

              <div class="attempt">
                <!-- Attempt Badge -->
                <div>
                  <b-badge
                          :id="`attempt-badge-${taskItem.id}`"
                          variant="primary mr-1"
                  >{{$t('attempt')}} {{index + 1}}</b-badge>
                </div>

                <!-- Task id -->
                <div class="task-id flex-grow-1">
                  <span>{{taskItem.taskId | ellipsis(30)}}</span>
                </div>

                <!-- Dropdown menu with actions -->
                <div>
                  <b-dropdown size="sm" right variant="primary" no-caret>
                    <template v-slot:button-content>
                      <Menu />
                    </template>
                    <b-dropdown-item v-if="taskItem.outputs" @click="toggleShowOutput(taskItem)">
                      <eye />
                      {{$t('toggle output')}}
                    </b-dropdown-item>
                    <b-dropdown-item>
                      <restart
                              :key="`restart-${index}-${attempt.state.startDate}`"
                              :isButton="false"
                              :execution="execution"
                              :task="taskItem"
                      />
                    </b-dropdown-item>
                  </b-dropdown>
                </div>
              </div>
            </div>

            <!-- Log lines -->
            <template v-if="attempt.logs">
              <template v-for="(log, i) in attempt.logs">
                <log-line
                  :level="level"
                  :filter="filter"
                  :log="log"
                  :key="`${i}-${log.timestamp}`"
                />
              </template>
            </template>
          </template>
        </div>
      </template>

      <!-- Outputs -->
      <pre :key="taskItem.id" v-if="showOutputs[taskItem.id] && taskItem.outputs">{{taskItem.outputs}}</pre>
    </div>
  </div>
</template>
<script>
import { mapState } from "vuex";
import LogLine from "./LogLine";
import Restart from "./Restart";
import Clock from "vue-material-design-icons/Clock";
import Eye from "vue-material-design-icons/Eye";
import Menu from "vue-material-design-icons/Menu";
export default {
  components: { LogLine, Restart, Clock, Eye, Menu },
  props: {
    level: {
      type: String,
      default: "ALL"
    },
    filter: {
      type: String,
      default: ""
    }
  },
  data() {
    return {
      showOutputs: {}
    };
  },
  computed: {
    ...mapState("execution", ["execution", "task"])
  },
  methods: {
    toggleShowOutput(task) {
      this.showOutputs[task.id] = !this.showOutputs[task.id];
      this.$forceUpdate();
    }
  }
};
</script>
<style lang="scss" scoped>
@import "../../styles/_variable.scss";
.log-wrapper {
  border-radius: 5px;
  .line:nth-child(odd) {
    background-color: lighten($dark, 5%);
  }
  div.attempt {
    display: flex;
    font-family: $font-family-sans-serif;
    font-size: $font-size-base;
    margin-top: $paragraph-margin-bottom*1.5;
    margin-bottom: 2px;
    .badge {
      font-size: $font-size-base;
      height: 100%;
      line-height: 100%;
      padding-bottom: 0;
    }
  }
  .attempt-wrapper {
    padding: 0.75rem;
    div:first-child > *  {
      margin-top: 0;
    }
  }
  .output {
    margin-right: 5px;
  }
  pre {
    border: 1px solid $light;
    background-color: $gray-200;
    padding: 10px;
    margin-top: 5px;
    margin-bottom: 20px;
  }
}
</style>
