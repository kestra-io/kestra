package io.kestra.fethr.taxonomy;

/**
 * API view of a {@link Category}, as the editor's category rail consumes it.
 *
 * <p>
 * {@code id} is the enum ordinal, which is also what {@code GET /plugins/{categoryId}/subcategories}
 * takes as its path variable.
 */
public record CategoryVo(
    int id,
    String displayName,
    String description,
    String icon,
    String iconBgColor,
    String iconColor,
    Integer order) {
    public static CategoryVo of(Category category) {
        return new CategoryVo(
            category.ordinal(),
            category.getDisplayName(),
            category.getDescription(),
            category.getIcon(),
            category.getIconBgColor(),
            category.getIconColor(),
            category.getOrder()
        );
    }
}
