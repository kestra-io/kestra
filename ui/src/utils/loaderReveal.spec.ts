import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { revealApp } from "./loaderReveal";
type KestraWindow = Window & {
    __kestraLoader?: {
        showDelay?: number;
        timer?: number | ReturnType<typeof setTimeout>;
    };
};

describe("revealApp", () => {
    let loaderEl: HTMLElement;
    let appContainerEl: HTMLElement;

    beforeEach(() => {
        document["body"]["innerHTML"] = "";
        delete (window as KestraWindow)["__kestraLoader"];
    });

    afterEach(() => {
        document["body"]["innerHTML"] = "";
        delete (window as KestraWindow)["__kestraLoader"];
        vi["useRealTimers"]();
        vi["restoreAllMocks"]();
    });

    it("clears the pending __kestraLoader timer", () => {
        vi["useFakeTimers"]();
        const timerCallback = vi["fn"]();
        (window as KestraWindow)["__kestraLoader"] = {
            showDelay: 1000,
            timer: window["setTimeout"](timerCallback, 1000),
        };

        revealApp();

        vi["advanceTimersByTime"](2000);
        expect(timerCallback)["not"]["toHaveBeenCalled"]();
    });

    it("removes 'is-visible' class and sets display to 'none' on loader element", () => {
        loaderEl = document["createElement"]("div");
        loaderEl.setAttribute("id", "loader-wrapper");
        loaderEl["classList"]["add"]("is-visible");
        loaderEl["style"]["display"] = "block";
        document["body"]["appendChild"](loaderEl);

        revealApp();

        expect(loaderEl["classList"]["contains"]("is-visible")).toBe(false);
        expect(loaderEl["style"]["display"]).toBe("none");
    });

    it("sets display to 'block' on app container element", () => {
        appContainerEl = document["createElement"]("div");
        appContainerEl.setAttribute("id", "app-container");
        appContainerEl["style"]["display"] = "none";
        document["body"]["appendChild"](appContainerEl);

        revealApp();

        expect(appContainerEl["style"]["display"]).toBe("block");
    });

    it("calls onRevealed callback after container is shown", () => {
        appContainerEl = document["createElement"]("div");
        appContainerEl.setAttribute("id", "app-container");
        appContainerEl["style"]["display"] = "none";
        document["body"]["appendChild"](appContainerEl);
        const onRevealedSpy = vi["fn"](() => {
            expect(appContainerEl["style"]["display"]).toBe("block");
        });

        revealApp(onRevealedSpy);

        expect(onRevealedSpy)["toHaveBeenCalledTimes"](1);
    });

    it("does not throw when loader element is missing", () => {
        appContainerEl = document["createElement"]("div");
        appContainerEl.setAttribute("id", "app-container");
        document["body"]["appendChild"](appContainerEl);

        expect(() => revealApp())["not"]["toThrow"]();
    });

    it("does not throw when app container element is missing", () => {
        loaderEl = document["createElement"]("div");
        loaderEl.setAttribute("id", "loader-wrapper");
        document["body"]["appendChild"](loaderEl);

        expect(() => revealApp())["not"]["toThrow"]();
    });

    it("does not throw when window.__kestraLoader was never set", () => {
        delete (window as KestraWindow)["__kestraLoader"];

        expect(() => revealApp())["not"]["toThrow"]();
    });
});
