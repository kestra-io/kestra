import { describe, expect, it } from "vitest";
import { getJavaProjectNameFromBuildAbsolutePath } from "./file-path-utils";

describe("test getJavaProjectNameFromBuildAbsolutePath", () => {
    it("should work for Kestra modules paths", async () => {
        expect(
            getJavaProjectNameFromBuildAbsolutePath(
                "/Users/roman/Documents/git-repos/kestra/core/build/test-results/junit/TEST-io.kestra.core.validations.ScheduleValidationTest.xml",
            ),
        ).toEqual("core");
        expect(
            getJavaProjectNameFromBuildAbsolutePath(
                "/kestra/runner-memory/build/test-results/junit/open-test-report.xml",
            ),
        ).toEqual("runner-memory");
        expect(
            getJavaProjectNameFromBuildAbsolutePath(
                "/kestra-ee/executor/build/test-results/junit/open-test-report.xml",
            ),
        ).toEqual("executor");
    });
});
