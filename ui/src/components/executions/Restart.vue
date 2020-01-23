<template>
  <b-button
    @click="restart"
    v-if="enabled"
    class="restart"
    variant="primary"
  >{{$t("restart") | cap}}</b-button>
</template>
<script>
export default {
  props: {
    execution: {
      type: Object,
      required: true
    },
    taskId: {
      type: String,
      required: false
    }
  },
  methods: {
    restart() {
      this.$store.dispatch("execution/restartExecution", {
        id: this.execution.id,
        taskId: this.taskId
      });
    }
  },
  computed: {
    enabled: function() {
      if (this.taskId) {
        let i = 0;
        let taskRunListSize = this.execution.taskRunList.length;
        while (i < taskRunListSize) {
          let taskRun = this.execution.taskRunList[i];
          if (taskRun.taskId == this.taskId) {
            return true;
          }
          if (taskRun.state.current == "FAILED") {
            return false;
          }
          i++;
        }
        return false;
      }
      return this.execution.state.current == "FAILED";
    }
  }
};
</script>
<style scoped>
.restart {
  margin-left: 10px;
  margin-right: 10px;
  padding-top: 5px;
  padding-bottom: 5px;
  border-radius: 5px;
}
</style>