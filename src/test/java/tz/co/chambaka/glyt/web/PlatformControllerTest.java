package tz.co.chambaka.glyt.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import tz.co.chambaka.glyt.domain.ServiceCatalogItem;
import tz.co.chambaka.glyt.domain.ServicePrice;
import tz.co.chambaka.glyt.repo.ServiceCatalogRepository;
import tz.co.chambaka.glyt.repo.ServicePriceRepository;
import tz.co.chambaka.glyt.service.CustomerPortalService;
import tz.co.chambaka.glyt.web.dto.GlytDtos.QuoteRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.QuotationView;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformControllerTest {

    @Mock
    ServiceCatalogRepository services;
    @Mock
    ServicePriceRepository prices;
    @Mock
    CustomerPortalService portal;
    @Mock
    QuotationView quotation;
    @InjectMocks
    PlatformController controller;

    private static ServiceCatalogItem item(long id, String code) {
        ServiceCatalogItem item = new ServiceCatalogItem();
        item.setId(id);
        item.setCode(code);
        item.setName(code + " name");
        item.setDescription(code + " description");
        return item;
    }

    private static ServicePrice price() {
        ServicePrice price = new ServicePrice();
        price.setCurrency("TZS");
        price.setAmountMinor(500_000L);
        price.setUnit("PER_BAG");
        return price;
    }

    @Test
    void listServicesIncludesPriceWhenPresent() {
        ServiceCatalogItem priced = item(1L, "STORE");
        ServiceCatalogItem unpriced = item(2L, "WRAP");
        when(services.findByActiveTrueOrderBySortOrderAsc()).thenReturn(List.of(priced, unpriced));
        when(prices.findFirstByServiceIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(Optional.of(price()));
        when(prices.findFirstByServiceIdAndActiveTrueOrderByIdAsc(2L)).thenReturn(Optional.empty());

        List<Map<String, Object>> rows = controller.listServices();

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0))
                .containsEntry("id", 1L)
                .containsEntry("code", "STORE")
                .containsEntry("name", "STORE name")
                .containsEntry("description", "STORE description")
                .containsEntry("currency", "TZS")
                .containsEntry("amountMinor", 500_000L)
                .containsEntry("unit", "PER_BAG");
        assertThat(rows.get(1)).doesNotContainKeys("currency", "amountMinor", "unit");
    }

    @Test
    void listServicesEmpty() {
        when(services.findByActiveTrueOrderBySortOrderAsc()).thenReturn(List.of());
        assertThat(controller.listServices()).isEmpty();
    }

    @Test
    void getServiceReturnsRow() {
        when(services.findById(1L)).thenReturn(Optional.of(item(1L, "STORE")));
        when(prices.findFirstByServiceIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(Optional.of(price()));
        Map<String, Object> row = controller.getService(1L);
        assertThat(row).containsEntry("code", "STORE").containsEntry("amountMinor", 500_000L);
    }

    @Test
    void getServiceNotFound() {
        when(services.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> controller.getService(9L))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getMessage()).isEqualTo("Service was not found.");
                });
    }

    @Test
    void quoteUsesPublicFlag() {
        QuoteRequest request = mock(QuoteRequest.class);
        when(portal.createQuote(request, false)).thenReturn(quotation);
        assertThat(controller.quote(request)).isSameAs(quotation);
    }

    @Test
    void getQuoteDelegates() {
        when(portal.getQuote(4L)).thenReturn(quotation);
        assertThat(controller.getQuote(4L)).isSameAs(quotation);
    }
}
