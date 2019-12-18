<template>
    <div>
        <dag :topology="topology" />
    </div>
</template>
<script>
import Dag from "./Dag";
export default {
    components: { Dag },
    computed: {
        topology() {
            return {};
        }
    }
};
</script>
<style scoped>
</style>