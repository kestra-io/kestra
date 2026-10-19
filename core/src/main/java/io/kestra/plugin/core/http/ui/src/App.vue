<script setup lang="ts">
import HttpTopologyDetails from "./components/HttpTopologyDetails.vue";
import { SLOTS } from "@kestra-io/artifact-sdk";
</script>

<template>
    <div style="padding: 1rem; font-family: sans-serif">
        <HttpTopologyDetails v-bind="SLOTS['topology-details'].defaultProps" />
    </div>
</template>