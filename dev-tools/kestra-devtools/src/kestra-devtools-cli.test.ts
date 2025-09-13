import { describe, it, expect, vi } from "vitest";
import { main } from "./kestra-devtools-cli";

describe("cli tests", () => {
    it("prints hello with default", async () => {
        const spy = vi.spyOn(console, "log").mockImplementation(() => {});
        await main(["node", "cli"]);
        expect(spy).toHaveBeenCalledWith("Hello, world!");
        spy.mockRestore();
    });

    it("prints hello with name", async () => {
        const spy = vi.spyOn(console, "log").mockImplementation(() => {});
        await main(["node", "cli", "Roman"]);
        expect(spy).toHaveBeenCalledWith("Hello, Roman!");
        spy.mockRestore();
    });
});
