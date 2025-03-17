<template>
    <div class="empty-state-container">
        <div class="empty-state-image">
            <img :src="image" :alt="title" width="150px">
        </div>
        <div class="empty-state-content">
            <h5 class="empty-state-title mt-3">
                {{ title }}
            </h5>
            <p
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
    margin-top: 4rem;
    margin-bottom: 2rem;
}

.empty-state-image img {
    height: auto;
}

.empty-state-content {
    max-width: 600px;
}

.empty-state-title {
    color: var(--ks-content-primary);
    font-weight: 700;
    font-size: var(--el-font-size-large);
}

.empty-state-description {
    font-size: var(--el-font-size-small);
    color: var(--ks-content-primary);
}

.theme-light {
    color: #000;
}

.theme-dark {
    color: #fff;
}
</style>
