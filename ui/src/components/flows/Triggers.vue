<template>
    <div class="accordion" role="tablist">
        <b-card
            v-for="trigger in flow.triggers"
            :key="trigger.id"
            no-body
            class="mb-1"
        >
            <b-card-header header-tag="header" class="p-1" role="tab">
                <b-button
                    block
                    v-b-toggle="`trigger-${trigger.id}`"
                    variant="success"
                >
                    <b>{{ trigger.id }}</b> : {{ trigger.type }}
                </b-button>
            </b-card-header>
            <b-collapse
                :id="`trigger-${trigger.id}`"
                accordion="my-accordion"
                role="tabpanel"
            >
                <b-card-body>
                    <vars :data="trigger"/>
                </b-card-body>
            </b-collapse>
        </b-card>
    </div>
</template>
<script>
import { mapState } from "vuex";
import Vars from '../executions/Vars'
export default {
    components: { Vars },
    computed: {
        ...mapState("flow", ["flow"]),
    }
};
</script>
