package io.kestra.fethr.taxonomy;

import lombok.Getter;

@Getter
public enum Category {
    CORE("Core Nodes", "Essential components for workflow construction", "Blocks", "bg-zinc-100", "text-zinc-500", 1),
    HL7("HL7 Nodes", "Send and receive HL7 messages", "Activity", "bg-blue-50", "text-blue-500", 2),
    FILE_OPERATOR("File Operations", "Manage and manipulate various file types", "FolderOpen", "bg-purple-50", "text-purple-400", 3),
    TRIGGER("Triggers", "Automate actions based on events", "Radar", "bg-teal-50", "text-teal-500", 4),
    SUBFLOW("Subflows", "Reuse flows as nodes in other workflows", "Workflow", "bg-amber-50", "text-amber-500", 5),
    INTEGRATION("Integrations", "Integrations", "", "", "", 6);

    private final String displayName;
    private final String description;
    private final String icon;
    private final String iconBgColor;
    private final String iconColor;
    private final Integer order;

    Category(String displayName, String description, String icon, String iconBgColor, String iconColor, Integer order) {
        this.description = description;
        this.displayName = displayName;
        this.icon = icon;
        this.iconBgColor = iconBgColor;
        this.iconColor = iconColor;
        this.order = order;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public static Category getByDescription(String description) {
        for (Category category : values()) {
            if (category.getDescription().equalsIgnoreCase(description)) {
                return category;
            }
        }
        return INTEGRATION; // Return NONE if no match is found
    }

}