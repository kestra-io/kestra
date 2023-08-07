<template>
  <div class="bulk-select">
    <el-checkbox
        :model-value="modelValue"
        :indeterminate="partialCheck"
        @change="toggleAll"
    >
      <span v-html="$t('selection.selected', {count: modelValue ? total : selections.length})"/>
    </el-checkbox>
    <el-button-group>
      <slot/>
    </el-button-group>
  </div>
</template>
<script>
  export default {
    props: {
      total: {type: Number, required: true},
      selections: {type: Array, required: true},
      modelValue: {type: Boolean, required: true},
    },
    emits: ["update:modelValue"],
    methods: {
      toggleAll() {
        this.$emit("update:modelValue", !this.modelValue);
      }
    },
    computed: {
      partialCheck() {
        return !this.modelValue && this.selections.length > 0;
      },
    }
  }
</script>

<style lang="scss" scoped>
  .bulk-select {
    height: 100%;
    display: flex;
    align-items: center;

    .el-checkbox {
      height: 100%;

      span {
        padding-left: calc(var(--spacer) * 1.5);
      }
    }

    > * {
      padding: 0 8px;
    }
  }

  span {
    font-weight: bold;
  }
</style>
