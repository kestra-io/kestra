package io.kestra.fethr.taxonomy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Fethr's editor taxonomy for a plugin: which category and subcategory it is grouped under in the
 * no-code editor, which icon represents it, where it sorts, and whether it is offered at all.
 *
 * <p>
 * This deliberately does <em>not</em> extend upstream's {@code @Plugin}. The 1.x fork added these
 * five attributes to {@code @Plugin} directly, which put every Fethr release on a collision course
 * with upstream edits to the same file -- and 2.0 duly changed it (adding {@code language()}).
 * Keeping the taxonomy in its own annotation, in a package upstream does not own, means a plugin
 * carries both annotations independently and neither side ever conflicts with the other.
 *
 * <p>
 * A plugin with no {@code @FethrTaxonomy}, or one left in {@link Category#INTEGRATION}, is reported
 * without taxonomy metadata exactly as an unannotated upstream plugin is.
 *
 * @see io.kestra.core.models.annotations.Plugin
 */
@Documented
@Inherited
@Retention(RUNTIME)
@Target({ ElementType.TYPE, ElementType.ANNOTATION_TYPE })
public @interface FethrTaxonomy {

    /**
     * The category this plugin is grouped under in the editor.
     */
    Category category() default Category.INTEGRATION;

    /**
     * The subcategory within {@link #category()}. Only {@link Category#CORE} offers subcategories.
     */
    SubCategory subCategory() default SubCategory.NONE;

    /**
     * The icon name the editor renders for this plugin.
     */
    String icon() default "";

    /**
     * Display order within the subcategory. {@code -1} leaves the plugin unordered.
     */
    int order() default -1;

    /**
     * Whether the plugin is offered in the editor at all.
     */
    boolean visible() default true;
}
