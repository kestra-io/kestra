### Guide on How to Handle `Filters`

> Code within `Vue` components must be written using the `<script setup>` block, as enforced by the `ESLint` rule.

When adding a new filter, follow these steps:

1. **Update the `OPTIONS` Array**  
   Add a new object to the `OPTIONS` array inside the `./composables/useFilters.ts` file.  
   If needed, also add a corresponding comparator to the appropriate array.

2. **Provide a Value for the New Filter**  
   Ensure the new filter has an assigned value. You can do this in one of the following locations:

   - `./composables/useValues.ts`
   - Directly within the `./KestraFilter.vue` component.

3. **Amend the `valueOptions` Computed Property**  
   Update the `valueOptions` computed property in the `./KestraFilter.vue` component.  
   This ensures the correct values are provided for the third dropdown.
