<template>
    <KsRow class="my-5 settings-wrapper">
        <KsCol
            :xs="layout(24, 0)"
            :sm="layout(20, 2)"
            :md="layout(18, 3)"
            :lg="layout(16, 4)"
            :xl="layout(14, 5)"
        >
            <slot />
        </KsCol>
    </KsRow>
</template>

<script setup lang="ts">
    const layout = (span: number, offset: number) => {
        return {span, offset}
    }
</script>
