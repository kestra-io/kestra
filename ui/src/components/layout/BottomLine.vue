<template>
 <b-navbar class="bottom-line" fixed="bottom" toggleable="lg" type="dark" variant="light">
     <slot/>
 </b-navbar>
</template>
<script>
export default {

}
</script>
<style lang="scss" scoped>
@import "../../styles/variable";

.bottom-line {
    left: 50px;
    border-radius: 0;
    border-top: 4px solid $navbar-dark-color;
    z-index: 90;
}
</style>