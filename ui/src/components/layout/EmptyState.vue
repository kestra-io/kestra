<template>
    <div class="empty-state-container">
        <div class="empty-state-image">
            <img :src="image" :alt="title">
        </div>
        <div class="empty-state-content">
            <h4 class="empty-state-title">
                {{ title }}
            </h4>
            <div
                class="empty-state-description"
                :class="themeClass"
                v-html="description"
            />
        </div>
    </div>
</template>

<script>
    export default {
        name: "EmptyState",
        props: {
            title: {
                type: String,
                required: true,
            },
            description: {
                type: String,
                required: true,
            },
            image: {
                type: String,
                required: true,
            },
        },
        computed: {
            themeClass() {
                return (localStorage.getItem("theme") || "light") === "light"
                    ? "theme-light"
                    : "theme-dark";
            },
        },
    };
</script>

<style scoped>
.empty-state-container {
    text-align: center;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
}

.empty-state-image {
    margin-top: 2rem;
    margin-bottom: 2rem;
}

.empty-state-image img {
    max-width: 200px;
    height: auto;
}

.empty-state-content {
    max-width: 600px;
}

.empty-state-title {
    color: var(--el-text-color-regular);
}

.empty-state-description {
    font-size: var(--el-font-size);
    margin-bottom: 16px;
    color: var(--el-text-color-regular);
}

.theme-light {
    color: #000;
}

.theme-dark {
    color: #fff;
}
</style>
