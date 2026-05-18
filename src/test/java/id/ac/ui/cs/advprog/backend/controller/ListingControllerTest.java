package id.ac.ui.cs.advprog.backend.controller;

import id.ac.ui.cs.advprog.backend.model.ListingCategory;
import id.ac.ui.cs.advprog.backend.service.ListingReadGateway;
import id.ac.ui.cs.advprog.backend.service.ListingService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ListingControllerTest {

    @Test
    void categoriesShouldReturnAllAvailableListingCategories() {
        ListingReadGateway gateway = Mockito.mock(ListingReadGateway.class);
        Mockito.when(gateway.categories()).thenReturn(List.of(ListingCategory.values()));
        ListingController controller = new ListingController(Mockito.mock(ListingService.class), gateway);

        List<ListingCategory> categories = controller.categories();

        assertEquals(List.of(ListingCategory.values()), categories);
    }
}
