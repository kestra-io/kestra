import type {Locator} from "@playwright/test";
import {expect} from "@playwright/test";
import {BasePage} from "./base.page";
import {shared} from "../fixtures/shared";

interface McpServerFormData {
    name?: string;
    description?: string;
    systemPrompt?: string;
    serverType?: "PRIVATE" | "PUBLIC";
    authType?: "BASIC" | "API_TOKEN";
}

export class McpPage extends BasePage {
    async goto(): Promise<void> {
        await this.page.goto("/ui/main/admin/mcp-servers");

        // If redirected to the login page, authenticate then navigate back
        const emailInput = this.page.getByRole("textbox", {name: "Email"});
        if (await emailInput.isVisible({timeout: 3000}).catch(() => false)) {
            await emailInput.fill(shared.username);
            await this.page.getByRole("textbox", {name: "Password"}).fill(shared.password);
            await this.page.getByRole("button", {name: "Login"}).click();
            await expect(this.page.getByRole("link", {name: "Flows"})).toBeVisible();
            await this.page.goto("/ui/main/admin/mcp-servers");
        }

        await expect(this.page.getByRole("heading", {name: "MCP Servers"})).toBeVisible();
    }

    async openCreateModal(): Promise<void> {
        await this.page.getByRole("button", {name: "+ Create Server"}).click();
    }

    async fillServerForm(data: McpServerFormData): Promise<void> {
        if (data.name !== undefined) {
            await this.page.getByRole("textbox").first().fill(data.name);
        }
        if (data.description !== undefined) {
            await this.page.getByPlaceholder("description").fill(data.description);
        }
        if (data.systemPrompt !== undefined) {
            await this.page.getByPlaceholder("System Prompt").fill(data.systemPrompt);
        }
        if (data.serverType !== undefined) {
            const label = data.serverType === "PRIVATE" ? "Private" : "Public";
            await this.page.getByRole("button", {name: label}).click();
        }
        if (data.authType !== undefined) {
            await this.page.locator(`input[type="radio"][value="${data.authType}"]`).check();
        }
    }

    async saveServerForm(): Promise<void> {
        await this.page.getByRole("button", {name: /Create Server|Save Changes/}).last().click();
    }

    getCardByName(name: string): Locator {
        return this.page.locator(".mcp-card").filter({hasText: name});
    }

    async openEditModal(name: string): Promise<void> {
        const card = this.getCardByName(name);
        await card.locator(".mcp-card__actions-right button").first().click();
    }

    async openConnectModal(name: string): Promise<void> {
        const card = this.getCardByName(name);
        await card.getByRole("button", {name: "Connect"}).click();
    }

    async toggleServer(name: string): Promise<void> {
        const card = this.getCardByName(name);
        // el-switch hides the <input role="switch"> off-screen; click the visible core span
        await card.locator(".el-switch__core").click();
    }

    async deleteServer(name: string): Promise<void> {
        this.page.on("dialog", dialog => dialog.accept());
        const card = this.getCardByName(name);
        await card.locator(".mcp-card__delete-btn").click();
    }

    async isServerEnabled(name: string): Promise<boolean> {
        const card = this.getCardByName(name);
        const classes = await card.getAttribute("class") ?? "";
        return !classes.includes("mcp-card--disabled");
    }
}
