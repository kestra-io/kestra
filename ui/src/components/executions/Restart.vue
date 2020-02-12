<template>
  <div v-if="isButton" class="restart-wrapper">
    <b-button @click="restart" v-if="enabled" class="rounded-lg btn-info restart mr-1">
      <restart-icon />
      {{$t("restart") | cap}}
    </b-button>
  </div>
  <div v-else>
    <div @click="restart" v-if="enabled">
      <restart-icon />
      {{$t("restart") | cap}}
    </div>
  </div>
</template>
<script>
import RestartIcon from "vue-material-design-icons/Restart";
export default {
  components: { RestartIcon },
  props: {
    isButton: {
      type: Boolean,
      default: true
    },
    execution: {
      type: Object,
      required: true
    },
    task: {
      type: Object,
      required: false
    }
  },
  methods: {
    restart() {
      this.$store
        .dispatch("execution/restartExecution", {
          id: this.execution.id,
          taskId: this.task ? this.task.taskId : null
        })
        .then(response => {
          this.$bvToast.toast(this.$t("restarted").capitalize(), {
            title: this.$t("execution").capitalize(),
            autoHideDelay: 5000,
            toaster: "b-toaster-top-right",
            variant: "success"
          });
          this.$store.commit('execution/setExecution', response.data);
          this.$router.push({name: 'execution', params: response.data});
          this.$emit('restart')
        })
    }
  },
  computed: {
    enabled() {
      // TODO : Add a "restartable" property on task run object (backend side)

      // If a specific task has been passed, we see if it can be restarted
      if (this.task && this.task.taskId) {
        // We find the taskRun based on its taskId
        let taskRunIndex = this.execution.taskRunList.findIndex(
          t => t.taskId == this.task.taskId
        );

        if (taskRunIndex == -1) return false;

        // There can be no taskRun with a failed state before
        // our specific task for it to be restarted
        let subList = this.execution.taskRunList.slice(0, taskRunIndex);

        let indexOfFailedTaskRun = subList.findIndex(
          t => t.state.current == "FAILED"
        );

        return indexOfFailedTaskRun == -1;
      }
      return this.execution.state.current == "FAILED";
    }
  }
};
</script>
<style scoped>
.restart-wrapper {
  display: inline;
}
.restart {
  margin-left: 10px;
  margin-right: 10px;
  padding-left: 15px;
  padding-right: 15px;
  border-radius: 5px;
}
</style>