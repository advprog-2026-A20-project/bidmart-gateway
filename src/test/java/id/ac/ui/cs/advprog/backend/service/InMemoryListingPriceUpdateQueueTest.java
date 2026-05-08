package id.ac.ui.cs.advprog.backend.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class InMemoryListingPriceUpdateQueueTest {

    @Mock
    private ListingService listingService;

    @Test
    void queueShouldIgnoreNullMessagesAndExposePendingCount() {
        InMemoryListingPriceUpdateQueue queue = new InMemoryListingPriceUpdateQueue(listingService);

        queue.publish(null);

        assertEquals(0, queue.pendingCount());
        queue.flushPendingUpdates();

        verifyNoMoreInteractions(listingService);
    }

    @Test
    void consumePendingUpdatesShouldDrainQueuedMessages() {
        InMemoryListingPriceUpdateQueue queue = new InMemoryListingPriceUpdateQueue(listingService);
        ListingPriceUpdateMessage firstMessage = new ListingPriceUpdateMessage(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new BigDecimal("120.00"),
            Instant.parse("2026-04-17T10:00:00Z")
        );
        ListingPriceUpdateMessage secondMessage = new ListingPriceUpdateMessage(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new BigDecimal("150.00"),
            Instant.parse("2026-04-17T10:01:00Z")
        );

        queue.publish(firstMessage);
        queue.publish(secondMessage);

        assertEquals(2, queue.pendingCount());

        queue.consumePendingUpdates();

        assertEquals(0, queue.pendingCount());
        verify(listingService).updateDisplayedPrice(firstMessage.listingId(), firstMessage.latestPrice());
        verify(listingService).updateDisplayedPrice(secondMessage.listingId(), secondMessage.latestPrice());
        verifyNoMoreInteractions(listingService);
    }
}
