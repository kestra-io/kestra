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
.bottom-line {
    left:50px;
    box-shadow: 0 5px 5px 5px #333;
    border-radius: 0px;
    z-index: 90;
}
</style>