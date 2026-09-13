package io.kestra.fethr.taxonomy;

/**
 * API view of a {@link SubCategory}, as the editor's category rail consumes it.
 */
public record SubCategoryVo(
    int id,
    String displayName,
    String description,
    String icon,
    Integer order) {
    public static SubCategoryVo of(SubCategory subCategory) {
        return new SubCategoryVo(
            subCategory.ordinal(),
            subCategory.getDisplayName(),
            subCategory.getDescription(),
            subCategory.getIcon(),
            subCategory.getOrder()
        );
    }
}
