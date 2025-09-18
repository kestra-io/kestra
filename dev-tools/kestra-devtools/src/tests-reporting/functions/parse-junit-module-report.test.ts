import { describe, expect, it } from "vitest";
import { parseJunitModuleReport } from "./parse-junit-module-report";

describe("parse-junit-report test", () => {
    it("parse OK for all tests success", async () => {
        const junitReport = `
        <?xml version="1.0" encoding="UTF-8"?>
        <testsuite name="io.kestra.core.validations.ScheduleValidationTest" tests="6" skipped="0" failures="0" errors="0" timestamp="2025-09-11T17:32:18.116Z" hostname="Romans-MacBook-Pro.local" time="0.202">
          <properties/>
          <testcase name="sundayDayOfTheWeekAlias()" classname="io.kestra.core.validations.ScheduleValidationTest" time="0.202"/>
          <testcase name="withSecondsValidation()" classname="io.kestra.core.validations.ScheduleValidationTest" time="0.202"/>
          <testcase name="lateMaximumDelayValidation()" classname="io.kestra.core.validations.ScheduleValidationTest" time="0.202"/>
          <testcase name="intervalValidation()" classname="io.kestra.core.validations.ScheduleValidationTest" time="0.202"/>
          <testcase name="nicknameValidation()" classname="io.kestra.core.validations.ScheduleValidationTest" time="0.203"/>
          <testcase name="cronValidation()" classname="io.kestra.core.validations.ScheduleValidationTest" time="0.202"/>
          <system-out><![CDATA[]]></system-out>
          <system-err><![CDATA[]]></system-err>
        </testsuite>
        `;

        const res = parseJunitModuleReport(junitReport);

        expect(res).toBeDefined();
        expect(res.testsuites).toEqual([
            {
                name: "io.kestra.core.validations.ScheduleValidationTest",
                errors: 0,
                failures: 0,
                skipped: 0,
                success: 6,
                tests: 6,
                status: "success",
                time: 0.202,
                testcases: [
                    {
                        name: "sundayDayOfTheWeekAlias()",
                        classname: "io.kestra.core.validations.ScheduleValidationTest",
                        time: 0.202,
                        status: "success",
                    },
                    {
                        name: "withSecondsValidation()",
                        classname: "io.kestra.core.validations.ScheduleValidationTest",
                        time: 0.202,
                        status: "success",
                    },
                    {
                        name: "lateMaximumDelayValidation()",
                        classname: "io.kestra.core.validations.ScheduleValidationTest",
                        time: 0.202,
                        status: "success",
                    },
                    {
                        name: "intervalValidation()",
                        classname: "io.kestra.core.validations.ScheduleValidationTest",
                        time: 0.202,
                        status: "success",
                    },
                    {
                        name: "nicknameValidation()",
                        classname: "io.kestra.core.validations.ScheduleValidationTest",
                        time: 0.203,
                        status: "success",
                    },
                    {
                        name: "cronValidation()",
                        classname: "io.kestra.core.validations.ScheduleValidationTest",
                        time: 0.202,
                        status: "success",
                    },
                ],
            },
        ]);
    });
    it("parse OK for test in error", async () => {
        const junitReport = `
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="io.kestra.core.validations.ScheduleValidationTest" tests="1" skipped="0" failures="1" errors="0" timestamp="2025-09-11T17:56:02.292Z" hostname="Romans-MacBook-Pro.local" time="0.265">
              <properties/>
              <testcase name="intervalValidation()" classname="io.kestra.core.validations.ScheduleValidationTest" time="0.043">
                <failure message="java.lang.RuntimeException: I failed and this is my log" type="java.lang.RuntimeException">java.lang.RuntimeException: I failed and this is my log
                    \tat io.kestra.core.validations.ScheduleValidationTest.intervalValidation(ScheduleValidationTest.java:93)
                    \tat java.base/java.lang.reflect.Method.invoke(Method.java:580)
                    \tat io.micronaut.test.extensions.junit5.MicronautJunit5Extension$2.proceed(MicronautJunit5Extension.java:142)
                    \tat io.micronaut.test.extensions.AbstractMicronautExtension.interceptEach(AbstractMicronautExtension.java:162)
                    \tat io.micronaut.test.extensions.AbstractMicronautExtension.interceptTest(AbstractMicronautExtension.java:119)
                    \tat io.micronaut.test.extensions.junit5.MicronautJunit5Extension.interceptTestMethod(MicronautJunit5Extension.java:129)
                    \tat java.base/java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:387)
                    \tat java.base/java.util.concurrent.ForkJoinPool$WorkQueue.topLevelExec(ForkJoinPool.java:1312)
                    \tat java.base/java.util.concurrent.ForkJoinPool.scan(ForkJoinPool.java:1843)
                    \tat java.base/java.util.concurrent.ForkJoinPool.runWorker(ForkJoinPool.java:1808)
                    \tat java.base/java.util.concurrent.ForkJoinWorkerThread.run(ForkJoinWorkerThread.java:188)
                  </failure>
              </testcase>
              <system-out><![CDATA[]]></system-out>
              <system-err><![CDATA[]]></system-err>
            </testsuite>
        `;

        const res = parseJunitModuleReport(junitReport);

        expect(res.testsuites).length(1);
        expect(res.testsuites[0].testcases).length(1);
        expect(res.testsuites[0].testcases[0].status).equal("failed");
        expect(res.testsuites[0].testcases[0].message).contain("I failed and this is my log");
        expect(res.testsuites[0].testcases[0].details).contain("I failed and this is my log");
        expect(res.testsuites[0].testcases[0].details).contain("ForkJoinWorkerThread");
    });
});
