<template>
    <div class="v-s" :class="extraClass" />
</template>
<script>
export default {
    props: {
        extraClass: {
            type: String,
            default: ""
        }
    }
};
</script>
<style scoped lang="scss">
@import "../../styles/_yeti-variables.scss";

.v-s {
    height: 30px;
    border-left: 1px solid $gray-300;
    margin-left: 8px;
    margin-right: 8px;
}

.xl {
    height:60px;
}
</style>