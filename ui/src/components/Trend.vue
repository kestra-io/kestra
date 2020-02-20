<template>
  <b-button class="trend rounded-lg" :class="cls">
    <component :is="icon"></component>
  </b-button>
</template>
<script>
import TrendingDown from "vue-material-design-icons/TrendingDown";
import TrendingUp from "vue-material-design-icons/TrendingUp";
import TrendingNeutral from "vue-material-design-icons/TrendingNeutral";

export default {
  components: {
    TrendingDown,
    TrendingUp,
    TrendingNeutral
  },
  props: {
    trend: {
      type: String,
      required: true
    },
    size: {
      type: String,
      default: ""
    }
  },
  computed: {
    cls() {
      return (
        {
          NEUTRAL: "btn-info",
          DOWN: "btn-success",
          UP: "btn-warning"
        }[this.trend] + (this.size ? " btn-" + this.size : "")
      );
    },
    icon() {
      return {
        NEUTRAL: "trending-neutral",
        DOWN: "trending-down",
        UP: "trending-up"
      }[this.trend];
    }
  }
};
</script>
<style scoped>
button.trend {
  cursor: default;
  white-space: nowrap;
  float: right;
  margin-right: 20px;
}
</style>