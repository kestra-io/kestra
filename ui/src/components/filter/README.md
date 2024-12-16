### Guide on How to Handle `Filters`

When adding a completely new filter, follow these steps:

1. **Update the `OPTIONS` Array**  
   Add a new object to the `OPTIONS` array inside the `./composables/useFilters.ts` component.  
   If necessary, also add a corresponding comparator to the appropriate array.

2. **Provide a Value for the New Filter**  
   Ensure the new filter has an assigned value. You can do this in one of the following locations:  
   - `./composables/useValues.ts`  
   - Directly inside the `./KestraFilter.vue` component.

3. **Amend the `valueOptions` Computed Property**  
   Update the `valueOptions` computed property in the `./KestraFilter.vue` component.  
   This ensures the correct values are passed to the third dropdown.
