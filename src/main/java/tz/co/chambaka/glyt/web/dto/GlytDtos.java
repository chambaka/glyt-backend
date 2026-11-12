package tz.co.chambaka.glyt.web.dto;

import java.util.List;

public final class GlytDtos {
    private GlytDtos() {}

    public record LoginRequest(String email, String password) {}

    public record LoginResponse(String token, String refreshToken, long expiresIn, boolean mustChangePassword, UserView user) {}

    public record RefreshRequest(String refreshToken) {}

    public record ForgotRequest(String email) {}

    public record MessageResponse(String message, String resetToken) {}

    public record ResetRequest(String token, String password) {}

    public record ChangePasswordRequest(String currentPassword, String newPassword) {}

    public record ScanRequest(String code) {}

    public record UserView(
            long id,
            String fullName,
            String email,
            String phone,
            String role,
            String location,
            boolean active,
            boolean mustChangePassword
    ) {}

    public record UserWrite(
            String fullName,
            String email,
            String phone,
            String password,
            String role,
            String location,
            Boolean active
    ) {}

    public record CustomerInput(
            String fullName,
            String phone,
            String email,
            String nationality,
            String passportNumber
    ) {}

    public record BookingInput(
            Long customerId,
            String internationalArrivalFlight,
            String kiaArrivalDate,
            String zanzibarTravelDate,
            String zanzibarFlight,
            String tourOperator,
            String bookingReference,
            String zanzibarHotel,
            String notes
    ) {}

    public record BagInput(
            String bagType,
            String color,
            Double weightKg,
            String description,
            String originalAirlineTag
    ) {}

    public record CreateBagRequest(Long bookingId, BagInput bag) {}

    public record ReceiveRequest(Double weightKg, String condition, boolean prohibitedConfirmed, String damageNote) {}

    public record SealRequest(String sealNumber, String condition, boolean acceptOverweight, String damageNote) {}

    public record ZnzReceiveRequest(
            String sealObserved,
            boolean sealIntact,
            boolean sealDamaged,
            String condition,
            String exceptionNote
    ) {}

    public record StoreRequest(String storageCode) {}

    public record ReleaseRequest(String collectedBy, boolean idVerified, String notes, boolean confirmed) {}

    public record CorrectRequest(String status, String reason, String storageCode) {}

    public record CreateManifestRequest(
            String airline,
            String flightNumber,
            String manifestDate,
            String route,
            String notes
    ) {}

    public record AddBagsRequest(List<Long> bagIds) {}

    public record ExceptionRequest(Long bagId, String type, String description, String priority, Long assignedToId) {}

    public record ResolveRequest(String notes) {}

    public record ExceptionUpdateRequest(String description, String priority, Long assignedToId) {}

    public record CustomerView(
            long id,
            String fullName,
            String phone,
            String email,
            String nationality,
            String passportNumber,
            String internationalArrivalFlight,
            String kiaArrivalDate,
            String zanzibarTravelDate,
            String zanzibarFlight,
            String tourOperator,
            String bookingReference,
            String zanzibarHotel,
            String notes,
            long bookingId,
            String bookingNumber
    ) {}

    public record CustomerRecord(long id, String customerNumber, String fullName, String phone, String email, String nationality, String passportNumber) {}

    public record BookingView(
            long id,
            String bookingNumber,
            long customerId,
            String customerName,
            String status,
            String internationalArrivalFlight,
            String kiaArrivalDate,
            String zanzibarTravelDate,
            String zanzibarFlight,
            String tourOperator,
            String bookingReference,
            String zanzibarHotel,
            String notes
    ) {}

    public record PhotoView(long id, String stage, String capturedAt, String capturedBy) {}

    public record CustodyView(
            long id,
            String eventType,
            String userName,
            String location,
            String notes,
            String sealNumber,
            String previousStatus,
            String newStatus,
            String createdAt
    ) {}

    public record AuditView(
            long id,
            String userName,
            String location,
            String action,
            String previousValue,
            String newValue,
            String createdAt
    ) {}

    public record ExceptionView(
            long id,
            long bagId,
            String bagCode,
            String type,
            String typeLabel,
            String description,
            String priority,
            String status,
            String location,
            String raisedBy,
            String assignedTo,
            String resolutionNotes,
            String resolvedBy,
            String resolvedAt,
            String createdAt,
            String dueAt,
            boolean overdue,
            long ageHours
    ) {}

    public record ManifestBrief(long id, String number, String airline, String flightNumber, String status) {}

    public record BagSummary(
            long id,
            String code,
            String customerName,
            String phone,
            String status,
            String statusLabel,
            String exceptionState,
            String currentLocation,
            String storageCode,
            String sealNumber,
            double weightKg,
            String updatedAt,
            String manifestNumber,
            String zanzibarTravelDate
    ) {}

    public record BagDetail(
            long id,
            String code,
            String trackingUrl,
            String shareMessage,
            String status,
            String statusLabel,
            String customerStatusLabel,
            String exceptionState,
            String bagType,
            String color,
            double weightKg,
            String description,
            String originalAirlineTag,
            String sealNumber,
            String condition,
            boolean prohibitedConfirmed,
            String currentLocation,
            String storageCode,
            CustomerView customer,
            ManifestBrief manifest,
            List<PhotoView> photos,
            List<CustodyView> custody,
            List<AuditView> audits,
            List<ExceptionView> exceptions,
            List<String> allowedActions,
            String createdAt,
            String updatedAt
    ) {}

    public record ManifestBag(long id, String code, String customerName, double weightKg, String sealNumber, String status, String statusLabel) {}

    public record ManifestView(
            long id,
            String number,
            String manifestDate,
            String route,
            String airline,
            String flightNumber,
            String notes,
            String status,
            String preparedBy,
            String handoverAt,
            int bagCount,
            double totalWeight,
            int receivedCount,
            List<ManifestBag> bags,
            List<ManifestBag> eligible
    ) {}

    public record StorageSlot(long id, String code, String status, Long bagId, String bagCode, String customerName, String arrivalDate, String expectedCollection) {}

    public record AlertView(String level, String message, Long bagId, String bagCode) {}

    public record DashboardView(
            long receivedToday,
            long readyForAirline,
            long inAirlineCustody,
            long zanzibarStorage,
            long readyForCollection,
            long openExceptions,
            long handedToday,
            long receivedZnzToday,
            long collectedToday,
            List<BagSummary> recent,
            List<AlertView> alerts
    ) {}

    public record TimelineStep(String label, boolean done) {}

    public record PickupView(String name, String place, String hours, String phone, String whatsapp) {}

    public record PublicTrack(String bagCode, String status, String lastUpdated, List<TimelineStep> timeline, PickupView pickup) {}

    public record CustomerRegisterRequest(
            String fullName,
            String email,
            String phone,
            String password,
            String nationality,
            String passportNumber,
            String preferredLanguage
    ) {}

    public record CustomerProfileView(
            long id,
            Long customerId,
            String fullName,
            String email,
            String phone,
            String preferredLanguage,
            boolean active
    ) {}

    public record CustomerAuthResponse(
            String token,
            String refreshToken,
            long expiresIn,
            CustomerProfileView user
    ) {}

    public record QuoteRequest(Long serviceId, Integer bagCount) {}

    public record QuotationView(
            long id,
            long serviceId,
            String serviceCode,
            String serviceName,
            int bagCount,
            String currency,
            long totalAmountMinor,
            String expiresAt,
            String createdAt
    ) {}

    public record CustomerBookingRequest(
            Long quotationId,
            String internationalArrivalFlight,
            String kiaArrivalDate,
            String zanzibarTravelDate,
            String zanzibarFlight,
            String tourOperator,
            String bookingReference,
            String zanzibarHotel,
            String notes
    ) {}

    public record SupportTicketRequest(String subject, String body, Long bagId) {}

    public record SupportTicketView(
            long id,
            String subject,
            String body,
            String status,
            Long bagId,
            String createdAt
    ) {}

    public record DeviceRegisterRequest(String deviceToken, String platform) {}

    public record DeviceRegisterResponse(long id, String platform, boolean active) {}

    public record PaymentInitiateRequest(
            String provider,
            Long bookingId,
            Long quotationId,
            Long amountMinor,
            String currency,
            String idempotencyKey
    ) {}

    public record PaymentWebhookRequest(
            String eventId,
            String externalRef,
            String status,
            String failureReason
    ) {}

    public record PaymentRefundRequest(Long amountMinor, String reason) {}

    public record PaymentView(
            long id,
            String provider,
            String status,
            String currency,
            long amountMinor,
            String externalRef,
            String idempotencyKey,
            String checkoutUrl,
            String receiptNumber,
            Long bookingId,
            String bookingNumber,
            Long quotationId,
            String customerEmail,
            String failureReason,
            String createdAt,
            String paidAt,
            Long refundAmountMinor,
            String refundedAt
    ) {}

    public record WorkloadView(
            long openExceptions,
            long overdueExceptions,
            long openSupportTickets,
            long pendingPayments,
            long bagsAtKia,
            long bagsAtZnz,
            long readyForCollection,
            List<StaffLoadRow> staffLoads,
            List<ExceptionView> slaQueue
    ) {}

    public record StaffLoadRow(long userId, String fullName, String role, String location, long openExceptions) {}

    public record LocationView(long id, String code, String name, String locationType, boolean active) {}

    public record LocationWrite(String code, String name, String locationType, Boolean active) {}

    public record StorageAdminView(long id, String code, String facilityCode, boolean active, boolean occupied, String bagCode) {}

    public record StorageWrite(String code, String facilityCode, Boolean active) {}

    public record ServiceAdminView(
            long id,
            String code,
            String name,
            String description,
            boolean active,
            int sortOrder,
            String currency,
            Long amountMinor,
            String unit,
            Long priceId
    ) {}

    public record ServiceWrite(
            String code,
            String name,
            String description,
            Boolean active,
            Integer sortOrder,
            String currency,
            Long amountMinor,
            String unit
    ) {}

    public record SupportAdminView(
            long id,
            String subject,
            String body,
            String status,
            Long bagId,
            String bagCode,
            String customerName,
            String customerEmail,
            String assignedTo,
            String resolutionNotes,
            String createdAt,
            String resolvedAt
    ) {}

    public record SupportAssignRequest(Long assignedToId) {}

    public record SupportResolveRequest(String notes) {}
}
