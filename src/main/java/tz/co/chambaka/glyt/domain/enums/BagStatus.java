package tz.co.chambaka.glyt.domain.enums;

public enum BagStatus {
    BOOKED,
    RECEIVED_KIA,
    SEALED,
    HANDED_AIRLINE,
    RECEIVED_ZNZ,
    STORED,
    READY,
    COLLECTED,
    CANCELLED;

    public int rank() {
        return switch (this) {
            case BOOKED -> 0;
            case RECEIVED_KIA -> 1;
            case SEALED -> 2;
            case HANDED_AIRLINE -> 3;
            case RECEIVED_ZNZ -> 4;
            case STORED -> 5;
            case READY -> 6;
            case COLLECTED -> 7;
            case CANCELLED -> -1;
        };
    }

    public String staffLabel() {
        return switch (this) {
            case BOOKED -> "Booked";
            case RECEIVED_KIA -> "Received at KIA";
            case SEALED -> "Sealed / ready for transfer";
            case HANDED_AIRLINE -> "Handed to airline";
            case RECEIVED_ZNZ -> "Received in Zanzibar";
            case STORED -> "Stored in Zanzibar";
            case READY -> "Ready for collection";
            case COLLECTED -> "Collected";
            case CANCELLED -> "Cancelled";
        };
    }

    public String customerLabel() {
        return switch (this) {
            case BOOKED -> "Booking confirmed";
            case RECEIVED_KIA -> "Bag received at Kilimanjaro";
            case SEALED -> "Secured for transfer";
            case HANDED_AIRLINE -> "On the way to Zanzibar";
            case RECEIVED_ZNZ -> "Arrived in Zanzibar";
            case STORED -> "Secured in Zanzibar";
            case READY -> "Ready for collection";
            case COLLECTED -> "Collected";
            case CANCELLED -> "Booking cancelled";
        };
    }
}
