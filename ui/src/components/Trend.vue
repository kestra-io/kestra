<template>
  <component class="trend icon-2x" :title="title" :is="icon" :class="cls"></component>
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
    title() {
      return this.$t(
        {
          NEUTRAL: "neutral_trend",
          DOWN: "down_trend",
          UP: "up_trend"
        }[this.trend]
      ).capitalize();
    },
    cls() {
      return {
        NEUTRAL: "neutral",
        DOWN: "down",
        UP: "up"
      }[this.trend];
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
<style lang="scss" scoped>
@import "../styles/variable";
.trend {
  float: right;
  margin-top: 5px;
}
.neutral {
  color: $blue;
}
.down {
  color: $green;
}
.up {
  color: $red;
}
</style>
