package io.kestra.fethr.taxonomy;

import lombok.Getter;

@Getter
public enum SubCategory {
    FLOW_CONTROL("Flow Control", "Branching, looping, and parallel execution", "Split", 1),
    TIMING("Timing", "Delays and approval gates", "Clock", 2),
    VARIABLE_AND_DATA("Variables & Data", "Store and manage execution data", "Variable", 3),
    EXECUTION_MANAGEMENT("Execution Management", "Control execution state and lifecycle", "Logout", 4),
    LOGGING("Logging", "Execution logs and log management", "FileText", 5),
    DEVELOPER_TOOLS("Developer Tools", "Templates, debugging, and advanced utilities", "Terminal", 6),
    WEB_REQUEST("Web Requests", "HTTP request tasks for external API integration", "Globe", 7),
    TABLES("Tables", "Read and write rows in user-defined tables", "Table", 8),
    NONE("None", "None", "", 9);

    private final String displayName;
    private final String description;
    private final String icon;
    private final Integer order;

    SubCategory(String displayName, String description, String icon, Integer order) {
        this.description = description;
        this.displayName = displayName;
        this.icon = icon;
        this.order = order;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public static SubCategory getByDescription(String description) {
        for (SubCategory subCategory : values()) {
            if (subCategory.getDescription().equalsIgnoreCase(description)) {
                return subCategory;
            }
        }
        return NONE; // Return NONE if no match is found
    }
}