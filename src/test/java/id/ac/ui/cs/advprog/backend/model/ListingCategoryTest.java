package id.ac.ui.cs.advprog.backend.model;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListingCategoryTest {

    @Test
    void categoryHierarchyShouldExposeParentLabelPathAndChildren() {
        assertNull(ListingCategory.ELECTRONICS.parent());
        assertEquals("Smartphone", ListingCategory.ELECTRONICS_SMARTPHONE.label());
        assertTrue(ListingCategory.ELECTRONICS.isRoot());
        assertFalse(ListingCategory.ELECTRONICS_PHONE.isRoot());

        assertTrue(ListingCategory.ELECTRONICS_SMARTPHONE.isSameOrDescendantOf(ListingCategory.ELECTRONICS));
        assertTrue(ListingCategory.ELECTRONICS_SMARTPHONE.isSameOrDescendantOf(null));
        assertFalse(ListingCategory.BOOKS_FICTION.isSameOrDescendantOf(ListingCategory.ELECTRONICS));

        assertEquals(
            List.of("Elektronik", "Handphone", "Smartphone"),
            ListingCategory.ELECTRONICS_SMARTPHONE.pathSegments()
        );
        assertEquals(
            "Elektronik > Handphone > Smartphone",
            ListingCategory.ELECTRONICS_SMARTPHONE.pathLabel()
        );
        assertEquals(
            List.of(ListingCategory.ELECTRONICS_PHONE, ListingCategory.ELECTRONICS_LAPTOP),
            ListingCategory.ELECTRONICS.children()
        );
    }
}
