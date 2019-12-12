<template>
    <b-row v-if="execution">
        <b-col>
            <h2>{{$t('namespace') | cap}} : {{execution.namespace}}</h2>
            <h3>{{$t('flow') | cap }} : {{execution.flowId}}</h3>
            <h3>{{$t('from') | cap}} {{execution.state.histories[0].date | date('LLLL')}} {{$t('to')}} {{execution.state.histories[execution.state.histories.length - 1].date | date('LLLL')}}</h3>
            <hr/>
            <h3>
                {{execution.taskRunList.length}} {{$t('steps')}} :
                <status :status="execution.state.current" />
            </h3>
        </b-col>
    </b-row>
</template>
<script>
import { mapState } from "vuex";
import Status from "../Status";

export default {
    components: {
        Status
    },
    computed: {
        ...mapState("execution", ["execution"])
    }
};
</script>