import {test} from "@playwright/test";

/**
 * E2E tests for the no-code editor — Apps (layout blocks).
 *
 * NOTE: The Apps editor is an ENTERPRISE-ONLY feature. It is NOT available
 * in the open-source edition of Kestra. The OSS build renders a marketing
 * demo page at /ui/main/apps with no functional editor.
 *
 * These tests are intentionally skipped in the OSS build. To run them,
 * use the Enterprise edition where the Apps editor is available.
 *
 * Covers (Enterprise):
 * - Creating a block in the layout of an App
 * - Editing an existing block in the layout of an App
 */

// Skip all tests in this file — they require the Enterprise edition
test.skip(true, "Apps editor is an Enterprise-only feature not available in the OSS build");

test.describe("No-code editor — Apps (Enterprise only)", () => {
    test("should create a block in a layout of an app", async ({page}) => {
        // Navigate to the app editor
        await page.goto("/ui/main/apps/new");

        // Open the layout editor and add a block
        // (Implementation requires Enterprise-specific components and routes)

        // Expected interactions:
        // 1. Click "Add block" or equivalent in the layout editor
        // 2. Select a block type from the no-code picker
        // 3. Configure the block properties via the no-code form
        // 4. Verify the block appears in the layout
        // 5. Save the app and confirm no error
    });

    test("should edit an existing block in a layout of an app", async ({page}) => {
        // Navigate to an existing app's editor
        // (Implementation requires Enterprise-specific components and routes)

        // Expected interactions:
        // 1. Navigate to an existing app with a layout block
        // 2. Click on the block to open its edit panel
        // 3. Modify block properties via the no-code form
        // 4. Verify the changes are reflected in the layout
        // 5. Save the app and confirm no error
    });
});
