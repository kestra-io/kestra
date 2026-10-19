import { describe, expect, it } from "vitest";
import {
    BODY_PREVIEW_LIMIT,
    downloadFileName,
    formatBytes,
    parseHttpOutputs,
} from "../../src/components/httpSummary";
import {
    downloadOutputs,
    encryptedBodyOutputs,
    noRequestOutputs,
    redactedHeadersOutputs,
    successOutputs,
} from "../storybook/httpSummary.fixtures";

describe("parseHttpOutputs", () => {
    it("shouldReturnUndefinedWhenOutputsAreEmpty", () => {
        expect(parseHttpOutputs({}, "request")).toBeUndefined();
        expect(parseHttpOutputs(undefined, "request")).toBeUndefined();
    });

    it("shouldNeverExposeTheBodyWhenItIsEncrypted", () => {
        const summary = parseHttpOutputs({ ...encryptedBodyOutputs, body: "leaked" }, "request");
        expect(summary?.body).toEqual({ kind: "encrypted" });
    });

    it("shouldDeriveHostAndPathFromTheUriWhenRequestSummaryIsMissing", () => {
        const summary = parseHttpOutputs(noRequestOutputs, "request");
        expect(summary).toMatchObject({
            request: undefined,
            host: "api.example.com",
            path: "/v1/items",
            code: 204,
        });
    });

    it("shouldTruncateALongBody", () => {
        const body = "x".repeat(BODY_PREVIEW_LIMIT + 10);
        const summary = parseHttpOutputs({ ...successOutputs, body }, "request");
        expect(summary?.body).toMatchObject({ kind: "text", truncated: true });
    });

    it("shouldFlagRedactedHeaders", () => {
        const rows = parseHttpOutputs(redactedHeadersOutputs, "request")?.headers;
        expect(rows?.find((row) => row.name === "set-cookie")?.redacted).toBe(true);
        expect(rows?.find((row) => row.name === "x-request-id")?.redacted).toBe(false);
    });

    it("shouldExposeTheStoredFileAndNoBodyForDownload", () => {
        const summary = parseHttpOutputs({ ...downloadOutputs, body: "ignored" }, "download");
        expect(summary?.fileUri).toContain("data.csv");
        expect(summary?.body).toBeUndefined();
        expect(summary?.bytes).toBe(1048576);
    });
});

describe("downloadFileName", () => {
    it("shouldKeepANamedStoredFile", () => {
        expect(downloadFileName({ fileUri: "kestra:///ns/flow/exec/data.csv", path: "/export" })).toBe("data.csv");
    });

    it("shouldFallBackToTheRequestPathForAGeneratedTempName", () => {
        expect(downloadFileName({ fileUri: "kestra:///ns/flow/exec/1234567.tmp", path: "/v1/my%20file.csv" })).toBe("my file.csv");
    });

    it("shouldFallBackToTheFinalUriThenTheStoredName", () => {
        expect(downloadFileName({ fileUri: "kestra:///x/1234567.tmp", finalUri: "https://h.io/a/report.pdf" })).toBe("report.pdf");
        expect(downloadFileName({ fileUri: "kestra:///x/1234567.tmp", path: "/" })).toBe("1234567.tmp");
    });
});

describe("formatBytes", () => {
    it("shouldUseBinaryUnits", () => {
        expect(formatBytes(512)).toBe("512 B");
        expect(formatBytes(2048)).toBe("2.0 KiB");
        expect(formatBytes(1048576)).toBe("1.0 MiB");
    });
});