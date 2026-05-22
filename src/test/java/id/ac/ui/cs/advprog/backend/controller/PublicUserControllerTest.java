package id.ac.ui.cs.advprog.backend.controller;

import id.ac.ui.cs.advprog.backend.dto.PublicSellerProfileResponse;
import id.ac.ui.cs.advprog.backend.model.Role;
import id.ac.ui.cs.advprog.backend.service.ListingCommandServiceClient;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicUserControllerTest {

    private final ListingCommandServiceClient client = mock(ListingCommandServiceClient.class);
    private final PublicUserController controller = new PublicUserController(client);

    @Test
    void publicProfileShouldDelegateToListingCommandClient() {
        UUID userId = UUID.randomUUID();
        PublicSellerProfileResponse expected = new PublicSellerProfileResponse(userId, "seller@mail.com", Role.SELLER, 2, 1, 1);
        when(client.publicProfile(userId)).thenReturn(expected);

        PublicSellerProfileResponse result = controller.publicProfile(userId);

        assertEquals(expected, result);
        verify(client).publicProfile(userId);
    }
}
