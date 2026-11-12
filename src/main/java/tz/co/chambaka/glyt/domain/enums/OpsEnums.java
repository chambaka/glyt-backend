package tz.co.chambaka.glyt.domain.enums;

public final class OpsEnums {
    private OpsEnums() {}

    public enum ExceptionState { NONE, OPEN, RESOLVED }

    public enum ExceptionStatus { OPEN, RESOLVED }

    public enum ExceptionType {
        OVERWEIGHT,
        NOT_RECEIVED,
        DELAYED,
        SEAL_MISMATCH,
        SEAL_DAMAGED,
        DAMAGED,
        WRONG_BAG,
        MANIFEST_DISCREPANCY,
        COLLECTION_DELAYED,
        AIRLINE_HANDOVER,
        OTHER;

        public String label() {
            return switch (this) {
                case OVERWEIGHT -> "Bag overweight";
                case NOT_RECEIVED -> "Bag not received";
                case DELAYED -> "Bag delayed";
                case SEAL_MISMATCH -> "Seal mismatch";
                case SEAL_DAMAGED -> "Seal damaged";
                case DAMAGED -> "Bag damaged";
                case WRONG_BAG -> "Wrong bag";
                case MANIFEST_DISCREPANCY -> "Manifest discrepancy";
                case COLLECTION_DELAYED -> "Customer collection delayed";
                case AIRLINE_HANDOVER -> "Airline handover issue";
                case OTHER -> "Other";
            };
        }
    }

    public enum Priority { NORMAL, HIGH, CRITICAL }

    public enum BagType { SUITCASE, DUFFEL, BACKPACK, OTHER }

    public enum BagCondition { GOOD, MINOR, DAMAGED }

    public enum PhotoStage { KIA_FULL, KIA_TAG, KIA_SEAL, ZNZ_CONDITION, ZNZ_SEAL, EXCEPTION, RELEASE }

    public enum ManifestStatus { DRAFT, FINALISED, HANDED_OVER }

    public enum CustodyEventType {
        KIA_INTAKE,
        SECURITY_SEAL,
        AIRLINE_HANDOVER,
        ZNZ_RECEIPT,
        STORAGE,
        READY_FOR_COLLECTION,
        CUSTOMER_RELEASE,
        STATUS_CORRECTION,
        CANCELLED
    }
}
