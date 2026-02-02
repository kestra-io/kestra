import {test, expect, type Page} from "@playwright/test";

const NAMESPACE = "io.kestra.tests";
const BASE_URL = "/ui/namespaces";

async function navigateToKVPage(page: Page) {
    await page.goto(`${BASE_URL}/${NAMESPACE}/kv`);
    await page.waitForLoadState("networkidle");
}

async function openAddKVModal(page: Page) {
    await page.locator("[data-testid='add-kv-button']").click();
    await expect(page.locator(".el-drawer")).toBeVisible();
}

async function fillKVKey(page: Page, key: string) {
    await page.locator("input[placeholder]").first().fill(key);
}

async function selectKVType(page: Page, type: string) {
    await page.locator(".el-select").first().click();
    await page.locator(`.el-select-dropdown__item:has-text("${type}")`).click();
}

async function saveKV(page: Page) {
    await page.locator("button:has-text('Save')").click();
    await page.waitForLoadState("networkidle");
}

async function editKV(page: Page, key: string) {
    const row = page.locator(`tr:has-text("${key}")`);
    await row.locator("[data-testid='edit-kv-button']").click();
    await expect(page.locator(".el-drawer")).toBeVisible();
}

async function deleteKV(page: Page, key: string) {
    const row = page.locator(`tr:has-text("${key}")`);
    await row.locator("[data-testid='delete-kv-button']").click();
    await page.locator("button:has-text('Confirm')").click();
    await page.waitForLoadState("networkidle");
}

test.describe("KV Store", () => {
    test.beforeEach(async ({page}) => {
        await navigateToKVPage(page);
    });

    test("should create and edit STRING KV", async ({page}) => {
        const key = `test-string-${Date.now()}`;
        const value = "Hello World";

        await openAddKVModal(page);
        await fillKVKey(page, key);
        await selectKVType(page, "STRING");
        await page.locator("textarea").fill(value);
        await saveKV(page);

        await expect(page.locator(`tr:has-text("${key}")`)).toBeVisible();

        await editKV(page, key);
        await expect(page.locator("textarea")).toHaveValue(value);

        await page.locator(".el-drawer__close-btn").click();
        await deleteKV(page, key);
    });

    test("should create and edit NUMBER KV", async ({page}) => {
        const key = `test-number-${Date.now()}`;
        const value = "42.5";

        await openAddKVModal(page);
        await fillKVKey(page, key);
        await selectKVType(page, "NUMBER");
        await page.locator("input[type='number']").fill(value);
        await saveKV(page);

        await expect(page.locator(`tr:has-text("${key}")`)).toBeVisible();

        await editKV(page, key);
        await expect(page.locator("input[type='number']")).toHaveValue(value);

        await page.locator(".el-drawer__close-btn").click();
        await deleteKV(page, key);
    });

    test("should create and edit BOOLEAN KV with true value", async ({page}) => {
        const key = `test-boolean-true-${Date.now()}`;

        await openAddKVModal(page);
        await fillKVKey(page, key);
        await selectKVType(page, "BOOLEAN");
        await page.locator(".el-switch").click();
        await saveKV(page);

        await expect(page.locator(`tr:has-text("${key}")`)).toBeVisible();

        await editKV(page, key);
        await expect(page.locator(".el-switch")).toHaveClass(/is-checked/);

        await page.locator(".el-drawer__close-btn").click();
        await deleteKV(page, key);
    });

    test("should create and edit BOOLEAN KV with false value", async ({page}) => {
        const key = `test-boolean-false-${Date.now()}`;

        await openAddKVModal(page);
        await fillKVKey(page, key);
        await selectKVType(page, "BOOLEAN");
        await saveKV(page);

        await expect(page.locator(`tr:has-text("${key}")`)).toBeVisible();

        await editKV(page, key);
        await expect(page.locator(".el-switch")).not.toHaveClass(/is-checked/);

        await page.locator(".el-drawer__close-btn").click();
        await deleteKV(page, key);
    });

    test("should create and edit DATETIME KV", async ({page}) => {
        const key = `test-datetime-${Date.now()}`;
        const dateValue = "2024-01-15 10:30:00";

        await openAddKVModal(page);
        await fillKVKey(page, key);
        await selectKVType(page, "DATETIME");
        
        await page.locator(".el-date-editor").click();
        await page.locator(".el-date-editor input").fill(dateValue);
        await page.keyboard.press("Enter");
        await saveKV(page);

        await expect(page.locator(`tr:has-text("${key}")`)).toBeVisible();

        await editKV(page, key);
        const inputValue = await page.locator(".el-date-editor input").inputValue();
        expect(inputValue).toContain("2024-01-15");

        await page.locator(".el-drawer__close-btn").click();
        await deleteKV(page, key);
    });

    test("should create and edit DATE KV", async ({page}) => {
        const key = `test-date-${Date.now()}`;
        const dateValue = "2024-06-20";

        await openAddKVModal(page);
        await fillKVKey(page, key);
        await selectKVType(page, "DATE");
        
        await page.locator(".el-date-editor").click();
        await page.locator(".el-date-editor input").fill(dateValue);
        await page.keyboard.press("Enter");
        await saveKV(page);

        await expect(page.locator(`tr:has-text("${key}")`)).toBeVisible();

        await editKV(page, key);
        const inputValue = await page.locator(".el-date-editor input").inputValue();
        expect(inputValue).toContain("2024-06-20");

        await page.locator(".el-drawer__close-btn").click();
        await deleteKV(page, key);
    });

    test("should create and edit DURATION KV", async ({page}) => {
        const key = `test-duration-${Date.now()}`;
        const durationValue = "PT1H30M";

        await openAddKVModal(page);
        await fillKVKey(page, key);
        await selectKVType(page, "DURATION");
        
        await page.locator("[data-testid='time-select']").click();
        await page.locator("input[placeholder]").last().fill(durationValue);
        await saveKV(page);

        await expect(page.locator(`tr:has-text("${key}")`)).toBeVisible();

        await editKV(page, key);
        await expect(page.locator(`text=${durationValue}`)).toBeVisible();

        await page.locator(".el-drawer__close-btn").click();
        await deleteKV(page, key);
    });

    test("should create and edit JSON object KV", async ({page}) => {
        const key = `test-json-object-${Date.now()}`;
        const jsonValue = "{\"name\": \"test\", \"count\": 42}";

        await openAddKVModal(page);
        await fillKVKey(page, key);
        await selectKVType(page, "JSON");
        
        const editor = page.locator(".monaco-editor");
        await editor.click();
        await page.keyboard.type(jsonValue);
        await saveKV(page);

        await expect(page.locator(`tr:has-text("${key}")`)).toBeVisible();

        await editKV(page, key);
        const editorContent = await page.locator(".monaco-editor .view-lines").textContent();
        expect(editorContent).toContain("name");
        expect(editorContent).toContain("test");
        expect(editorContent).toContain("42");

        await page.locator(".el-drawer__close-btn").click();
        await deleteKV(page, key);
    });

    test("should create and edit JSON array KV", async ({page}) => {
        const key = `test-json-array-${Date.now()}`;
        const jsonValue = "[\"item1\", \"item2\", \"item3\"]";

        await openAddKVModal(page);
        await fillKVKey(page, key);
        await selectKVType(page, "JSON");
        
        const editor = page.locator(".monaco-editor");
        await editor.click();
        await page.keyboard.type(jsonValue);
        await saveKV(page);

        await expect(page.locator(`tr:has-text("${key}")`)).toBeVisible();

        await editKV(page, key);
        const editorContent = await page.locator(".monaco-editor .view-lines").textContent();
        expect(editorContent).toContain("item1");
        expect(editorContent).toContain("item2");
        expect(editorContent).toContain("item3");

        await page.locator(".el-drawer__close-btn").click();
        await deleteKV(page, key);
    });

    test("should create KV with description", async ({page}) => {
        const key = `test-with-description-${Date.now()}`;
        const value = "test value";
        const description = "This is a test description";

        await openAddKVModal(page);
        await fillKVKey(page, key);
        await selectKVType(page, "STRING");
        await page.locator("textarea").first().fill(value);
        
        await page.locator("input[placeholder]").nth(1).fill(description);
        await saveKV(page);

        const row = page.locator(`tr:has-text("${key}")`);
        await expect(row).toBeVisible();
        await expect(row).toContainText(description);

        await editKV(page, key);
        await expect(page.locator("input").nth(1)).toHaveValue(description);

        await page.locator(".el-drawer__close-btn").click();
        await deleteKV(page, key);
    });

    test("should create KV with TTL", async ({page}) => {
        const key = `test-with-ttl-${Date.now()}`;
        const value = "test value with ttl";

        await openAddKVModal(page);
        await fillKVKey(page, key);
        await selectKVType(page, "STRING");
        await page.locator("textarea").fill(value);
        
        await page.locator("[data-testid='ttl-select']").click();
        await page.locator("input[placeholder]").last().fill("PT1H");
        await saveKV(page);

        await expect(page.locator(`tr:has-text("${key}")`)).toBeVisible();

        await deleteKV(page, key);
    });

    test("should validate invalid JSON", async ({page}) => {
        const key = `test-invalid-json-${Date.now()}`;
        const invalidJson = "not a valid json";

        await openAddKVModal(page);
        await fillKVKey(page, key);
        await selectKVType(page, "JSON");
        
        const editor = page.locator(".monaco-editor");
        await editor.click();
        await page.keyboard.type(invalidJson);
        
        await page.locator("button:has-text('Save')").click();
        
        await expect(page.locator(".el-form-item__error")).toBeVisible();
    });

    test("should prevent duplicate keys", async ({page}) => {
        const key = `test-duplicate-${Date.now()}`;
        const value = "original value";

        await openAddKVModal(page);
        await fillKVKey(page, key);
        await selectKVType(page, "STRING");
        await page.locator("textarea").fill(value);
        await saveKV(page);

        await openAddKVModal(page);
        await fillKVKey(page, key);
        await selectKVType(page, "STRING");
        await page.locator("textarea").fill("duplicate value");
        
        await page.locator("button:has-text('Save')").click();
        await expect(page.locator(".el-form-item__error")).toBeVisible();

        await page.locator(".el-drawer__close-btn").click();
        await deleteKV(page, key);
    });

    test("should copy KV expression to clipboard", async ({page}) => {
        const key = `test-copy-${Date.now()}`;
        const value = "copy test value";

        await openAddKVModal(page);
        await fillKVKey(page, key);
        await selectKVType(page, "STRING");
        await page.locator("textarea").fill(value);
        await saveKV(page);

        const row = page.locator(`tr:has-text("${key}")`);
        await row.locator("[data-testid='copy-kv-button']").click();

        await expect(page.locator(".el-notification")).toBeVisible();

        await deleteKV(page, key);
    });

    test("should update existing KV value", async ({page}) => {
        const key = `test-update-${Date.now()}`;
        const originalValue = "original";
        const updatedValue = "updated";

        await openAddKVModal(page);
        await fillKVKey(page, key);
        await selectKVType(page, "STRING");
        await page.locator("textarea").fill(originalValue);
        await saveKV(page);

        await editKV(page, key);
        await page.locator("textarea").fill(updatedValue);
        await saveKV(page);

        await editKV(page, key);
        await expect(page.locator("textarea")).toHaveValue(updatedValue);

        await page.locator(".el-drawer__close-btn").click();
        await deleteKV(page, key);
    });
});
