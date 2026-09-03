import type {Locator} from "@playwright/test"
import {expect} from "@playwright/test"
import {BasePage} from "./base.page"

interface McpServerFormData {
    id?: string;
    description?: string;
    systemPrompt?: string;
    serverType?: "PRIVATE" | "PUBLIC";
    authType?: "BASIC" | "API_TOKEN";
}

export class McpPage extends BasePage {
    async goto(): Promise<void> {
        await this.page.goto("/ui/main/admin/mcp-servers")

        await expect(this.page.getByRole("heading", {name: "MCP Servers"})).toBeVisible()
    }

    async openCreateModal(): Promise<void> {
        await this.page.getByRole("link", {name: /Create Server/i}).click()
        await expect(this.page.getByRole("textbox").first()).toBeVisible()
    }

    async fillServerForm(data: McpServerFormData): Promise<void> {
        if (data.id !== undefined) {
            await this.page.getByRole("textbox").first().fill(data.id)
        }
        if (data.description !== undefined) {
            await this.page.getByPlaceholder("description").fill(data.description)
        }
        if (data.systemPrompt !== undefined) {
            await this.page.getByPlaceholder("Instructions").fill(data.systemPrompt)
        }
        if (data.serverType !== undefined) {
            const label = data.serverType === "PRIVATE" ? "Private" : "Public"
            await this.page.getByRole("button", {name: label}).click()
        }
        if (data.authType !== undefined) {
            await this.page.locator(`input[type="radio"][value="${data.authType}"]`).check()
        }
    }

    async saveServerForm(): Promise<void> {
        await this.page.getByRole("button", {name: /Create Server|Save Changes/}).last().click()
    }

    getRowById(id: string): Locator {
        return this.page.locator(".mcp-list__row").filter({hasText: id})
    }

    getCardById(id: string): Locator {
        return this.getRowById(id)
    }

    async openEditModal(id: string): Promise<void> {
        await this.getRowById(id).click()
        await expect(this.page.getByRole("button", {name: "Save Changes"})).toBeVisible()
    }

    async openConnectModal(id: string): Promise<void> {
        await this.openEditModal(id)
        await this.page.getByRole("tab", {name: "Connect"}).click()
    }

    async toggleServer(id: string): Promise<void> {
        await this.openEditModal(id)
        await this.page.locator(".mcp-edit__toggle .el-switch__core").click()
        await this.page.getByRole("button", {name: "Save Changes"}).click()
        await this.goto()
    }

    async deleteServer(id: string): Promise<void> {
        this.page.on("dialog", dialog => dialog.accept())
        await this.openEditModal(id)
        await this.page.getByRole("button", {name: "Delete"}).click()
    }

    async isServerEnabled(id: string): Promise<boolean> {
        const row = this.getRowById(id)
        return await row.locator(".mcp-list__status--enabled").isVisible()
    }
}
