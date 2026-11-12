package tz.co.chambaka.glyt.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tz.co.chambaka.glyt.domain.ServiceCatalogItem;
import tz.co.chambaka.glyt.repo.ServiceCatalogRepository;
import tz.co.chambaka.glyt.repo.ServicePriceRepository;
import tz.co.chambaka.glyt.service.CustomerPortalService;
import tz.co.chambaka.glyt.web.dto.GlytDtos.QuoteRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.QuotationView;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class PlatformController {

    private final ServiceCatalogRepository services;
    private final ServicePriceRepository prices;
    private final CustomerPortalService portal;

    public PlatformController(
            ServiceCatalogRepository services,
            ServicePriceRepository prices,
            CustomerPortalService portal
    ) {
        this.services = services;
        this.prices = prices;
        this.portal = portal;
    }

    @GetMapping("/services")
    public List<Map<String, Object>> listServices() {
        return services.findByActiveTrueOrderBySortOrderAsc().stream().map(this::toService).toList();
    }

    @GetMapping("/services/{id}")
    public Map<String, Object> getService(@PathVariable Long id) {
        ServiceCatalogItem item = services.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Service was not found."));
        return toService(item);
    }

    @PostMapping("/quotations")
    public QuotationView quote(@RequestBody QuoteRequest request) {
        return portal.createQuote(request, false);
    }

    @GetMapping("/quotations/{id}")
    public QuotationView getQuote(@PathVariable Long id) {
        return portal.getQuote(id);
    }

    private Map<String, Object> toService(ServiceCatalogItem item) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", item.getId());
        row.put("code", item.getCode());
        row.put("name", item.getName());
        row.put("description", item.getDescription());
        prices.findFirstByServiceIdAndActiveTrueOrderByIdAsc(item.getId()).ifPresent(price -> {
            row.put("currency", price.getCurrency());
            row.put("amountMinor", price.getAmountMinor());
            row.put("unit", price.getUnit());
        });
        return row;
    }
}
