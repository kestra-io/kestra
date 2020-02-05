<template>
  <div v-if="execution" class="log-wrapper text-white text-monospace">
    <div v-for="taskItem in execution.taskRunList" :key="taskItem.id">
      <template v-if="(!task || task.id === taskItem.id) && taskItem.attempts">
        <div class="bg-dark attempt-wrapper">
          <template v-for="(attempt, index) in taskItem.attempts">
            <div
              class="row"
              :id="`attempt-${index}-${attempt.state.startDate}`"
              :key="`attempt-${index}-${attempt.state.startDate}`"
            >
              <!-- Tooltip -->
              <b-tooltip
                placement="left"
                :target="`attempt-${index}-${attempt.state.startDate}`"
                triggers="hover"
              >
                {{$t('from') | cap}} : {{attempt.state.startDate | date('LLL:ss') }}
                <br />
                {{$t('to') | cap}} : {{attempt.state.endDate | date('LLL:ss') }}
                <br />
                <br />
                <clock />
                {{$t('duration') | cap}} : {{attempt.state.duration | humanizeDuration}}
              </b-tooltip>

              <!-- Task id -->
              <div class="attempt col-md-2">
                <span>[{{taskItem.taskId}}] &nbsp;</span>
              </div>

              <!-- Attempt Badge -->
              <div class="attempt col-md-9">
                <b-badge
                  :id="`attempt-badge-${taskItem.id}`"
                  variant="primary"
                >{{$t('attempt') | cap}} {{index + 1}}</b-badge>
              </div>

              <!-- Dropdown menu with actions -->
              <div class="col-md-1" style="float:right;">
                <b-dropdown right variant="primary" no-caret>
                  <template v-slot:button-content>
                    <Menu />
                  </template>
                  <b-dropdown-item v-if="taskItem.outputs" @click="toggleShowOutput(taskItem)">
                    <eye />
                    {{$t('toggle output') | cap}}
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
  padding: 10px;
  border-radius: 5px;

  .line:nth-child(odd) {
    background-color: lighten($dark, 5%);
  }
  div.attempt {
    margin-bottom: $paragraph-margin-bottom/2;
    font-family: $font-family-sans-serif;
    font-size: $font-size-base;
    padding-bottom: $paragraph-margin-bottom/2;

    .badge {
      font-size: $font-size-base;
      font-weight: bold;
      margin-right: 5px;
      margin-top: 5px;
    }
  }
  .attempt-wrapper {
    margin-bottom: 5px;
    padding: 7px;
    border-radius: 5px;
  }
  .attempt:first-child {
    margin-top: 0;
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