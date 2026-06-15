import {describe, expect, it} from "vitest"
import {
    detectStructured,
    parseStructured,
    normalizeLogTemplate,
    pushLog,
    groupConsecutive,
    isCollapsible,
    STRUCTURED_PARSE_LIMIT,
    COLLAPSE_THRESHOLD,
    formatLogsAsText,
    logsDownloadFilename,
    executionLogsDownloadFilename,
} from "../../../src/utils/logs"
import type {Log} from "../../../src/stores/logs"

function log(message: string, over: Partial<Log> = {}): Log {
    return {
        level: "INFO",
        namespace: "ns",
        flowId: "f",
        executionId: "e",
        thread: "t",
        index: 0,
        attemptNumber: 0,
        executionKind: "flow",
        timestamp: "2026-06-04T13:33:56.680Z",
        message,
        ...over,
    }
}

describe("detectStructured", () => {
    it("detects objects and arrays", () => {
        expect(detectStructured("{\"a\":1}")).toBe(true)
        expect(detectStructured("  [1,2,3]  ")).toBe(true)
    })

    it("rejects non-structured, partial, and empty input", () => {
        expect(detectStructured("ok: deploy started")).toBe(false)
        expect(detectStructured("{\"a\":1")).toBe(false)
        expect(detectStructured("")).toBe(false)
        expect(detectStructured(null)).toBe(false)
        expect(detectStructured(undefined)).toBe(false)
        expect(detectStructured("{")).toBe(false)
    })
})

describe("parseStructured", () => {
    it("parses valid JSON payloads", () => {
        expect(parseStructured("{\"event\":\"deploy.completed\",\"n\":3}")).toEqual({event: "deploy.completed", n: 3})
        expect(parseStructured("[1,2]")).toEqual([1, 2])
    })

    it("returns undefined for invalid, non-structured, or oversized input", () => {
        expect(parseStructured("plain text")).toBeUndefined()
        expect(parseStructured("{\"a\":}")).toBeUndefined()
        const huge = "[" + "0,".repeat(STRUCTURED_PARSE_LIMIT) + "0]"
        expect(parseStructured(huge)).toBeUndefined()
    })
})

describe("normalizeLogTemplate", () => {
    it("masks the bracketed host so Ansible lines share a template", () => {
        const a = normalizeLogTemplate("ok: [web-01] => gather_facts")
        const b = normalizeLogTemplate("ok: [web-02] => gather_facts")
        expect(a).toBe(b)
    })

    it("masks ip, uuid, timestamp and long integers", () => {
        expect(normalizeLogTemplate("conn from 10.0.0.1")).toBe(normalizeLogTemplate("conn from 192.168.1.254"))
        expect(normalizeLogTemplate("id 550e8400-e29b-41d4-a716-446655440000 done"))
            .toBe(normalizeLogTemplate("id 550e8400-e29b-41d4-a716-446655440001 done"))
        expect(normalizeLogTemplate("took 1840 ms")).toBe(normalizeLogTemplate("took 9912 ms"))
        expect(normalizeLogTemplate("at 2026-06-04T13:33:56.680Z")).toBe(normalizeLogTemplate("at 2026-06-04T13:34:02.551Z"))
    })

    it("keeps semantically different lines apart", () => {
        expect(normalizeLogTemplate("ok: [web-01] => gather_facts"))
            .not.toBe(normalizeLogTemplate("ok: [web-01] => install_packages"))
    })

    it("masks full 8-group IPv6 addresses", () => {
        const a = normalizeLogTemplate("connected from 2001:db8:85a3:0:0:8a2e:370:7334")
        const b = normalizeLogTemplate("connected from 2001:db8:85a3:0:0:8a2e:370:0001")
        expect(a).toBe(b)
    })

    it("groups repeated hex-colon sequences as the same template (variable channel/node IDs)", () => {
        const a = normalizeLogTemplate("channel dead:beef:cafe timeout")
        const b = normalizeLogTemplate("channel a1b2:c3d4:ef56 timeout")
        expect(a).toBe(b)
    })
})

describe("pushLog / groupConsecutive", () => {
    it("extends the last group for same-template adjacent lines", () => {
        const groups = groupConsecutive([
            log("ok: [web-01] => gather_facts"),
            log("ok: [web-02] => gather_facts"),
            log("ok: [web-03] => gather_facts"),
        ])
        expect(groups).toHaveLength(1)
        expect(groups[0].logs).toHaveLength(3)
    })

    it("starts a new group when the template changes", () => {
        const groups = groupConsecutive([
            log("ok: [web-01] => gather_facts"),
            log("Task failed on remote host"),
            log("ok: [web-02] => gather_facts"),
        ])
        expect(groups).toHaveLength(3)
    })

    it("appends incrementally without re-clustering (streaming)", () => {
        const groups: ReturnType<typeof groupConsecutive> = []
        pushLog(groups, log("ok: [web-01] => gather_facts"))
        pushLog(groups, log("ok: [web-02] => gather_facts"))
        expect(groups).toHaveLength(1)
        expect(groups[0].logs).toHaveLength(2)
    })
})

describe("isCollapsible", () => {
    it("collapses only runs at or above the threshold", () => {
        const small = groupConsecutive(Array.from({length: COLLAPSE_THRESHOLD - 1}, () => log("ok: [h] => x")))
        const big = groupConsecutive(Array.from({length: COLLAPSE_THRESHOLD}, () => log("ok: [h] => x")))
        expect(isCollapsible(small[0])).toBe(false)
        expect(isCollapsible(big[0])).toBe(true)
    })
})

describe("formatLogsAsText", () => {
    it("renders padded level, timestamp and message per line", () => {
        const text = formatLogsAsText([
            {level: "INFO", timestamp: "2026-01-01T00:00:00Z", message: "started"},
            {level: "ERROR", timestamp: "2026-01-01T00:00:01Z", message: "boom"},
        ])
        expect(text).toBe("INFO  2026-01-01T00:00:00Z started\nERROR 2026-01-01T00:00:01Z boom")
    })

    it("trims trailing whitespace and tolerates missing fields", () => {
        const text = formatLogsAsText([{level: undefined, timestamp: "t", message: "msg   "}])
        expect(text).toBe("      t msg")
    })

    it("returns an empty string for no logs", () => {
        expect(formatLogsAsText([])).toBe("")
    })
})

describe("download filenames", () => {
    const date = new Date(2026, 5, 4, 9, 7, 3)

    it("builds a timestamped global logs filename", () => {
        expect(logsDownloadFilename(date)).toBe("logs-2026-06-04-090703.log")
    })

    it("builds an execution logs filename with the execution id", () => {
        expect(executionLogsDownloadFilename("exec-42", date)).toBe("kestra-execution-20260604090703-exec-42.log")
    })
})
