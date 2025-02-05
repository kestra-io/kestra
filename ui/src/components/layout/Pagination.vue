<style scoped lang="scss">
    @use 'element-plus/theme-chalk/src/mixins/mixins' as *;

    .pagination {
        margin-top: 1rem;

        &.top {
            margin-bottom: 1rem;
            margin-top: 0;
        }

        .el-select {
            width: 105px;
        }

        .page-size {
            @include res(xs) {
                display: none;
            }
        }

        .text-total {
            color: var(--ks-content-secondary);
            font-weight: normal;
        }

        .total {
            padding: 0 4px;
            line-height: 1.85;
            font-size: var(--el-font-size-extra-small);
            color: var(--ks-content-secondary); // Ensure it uses non-primary color
            white-space: nowrap;
        }

        :deep(.el-pagination .el-pager li) {
            background-color: var(--ks-button-background-secondary); // Non-primary background for non-selected pages
            border: 1px solid var(--ks-border-primary); // Non-primary border color
            color: var(--ks-content-primary); // Non-primary text color for non-selected pages

            &:hover {
                background-color: var(--ks-button-background-secondary-hover); // Purple background on hover
                border: 1px solid var(--ks-border-active); // Purple border on hover
                color: white; // White text when hovered
            }

            &.is-active {
                background-color: var(--ks-button-background-secondary-active); // Purple background for active page
                border: 1px solid var(--ks-border-active); // Purple border for active page
                color: white; // White text for the selected page
            }
        }

        @media (prefers-color-scheme: light) {
            :deep(.el-pagination .el-pager li) {
                background-color: white; // Set background to white in light mode for non-selected pages
                color: var(--ks-content-primary); // Non-primary text color for non-selected pages
                border: 1px solid var(--ks-border-primary); // Border color for non-selected pages
            }

            :deep(.el-pagination .el-pager li.is-active) {
                background-color: var(--ks-button-background-secondary-active); // Purple background for active page
                border: 1px solid var(--ks-border-active); // Purple border for active page
                color: white; // White text for the selected page
            }
        }
    }
</style>
