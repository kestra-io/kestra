package io.kestra.tests.architecture;

import java.util.Map;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "io.kestra.webserver.controllers", importOptions = ImportOption.DoNotIncludeTests.class)
public class PaginationArchitectureTest {

    private static final Map<String, String> DIFFERENT_PAGING_CONTRACT = Map.of(
        "PluginController#listPlugins", "the page size defaults to 1000, the whole catalogue",
        "LogController#listLogsFromExecution", "pagination is optional and only enabled when page or size is set",
        "ExecutionController#findDistinctFieldValues", "size is a limit of distinct values, there is no page",
        "PluginController#listTriggerPlugins", "the whole catalogue is returned in one page, there is no paging parameter",
        "PluginController#listTicketingSystems", "the whole catalogue is returned in one page, there is no paging parameter"
    );

    @ArchTest
    public static final ArchRule controllers_do_not_redeclare_paging = PaginationRules.controllersDoNotRedeclarePaging(DIFFERENT_PAGING_CONTRACT);

    @ArchTest
    public static final ArchRule paged_controllers_use_the_pagination_beans = PaginationRules.pagedControllersUsePaginationBeans(DIFFERENT_PAGING_CONTRACT);
}
