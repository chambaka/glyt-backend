package tz.co.chambaka.glyt.domain.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.BagCondition;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.BagType;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.CustodyEventType;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ExceptionState;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ExceptionStatus;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ExceptionType;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ManifestStatus;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.PhotoStage;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.Priority;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

class OpsEnumsTest {

    @ParameterizedTest
    @CsvSource({
            "OVERWEIGHT,Bag overweight",
            "NOT_RECEIVED,Bag not received",
            "DELAYED,Bag delayed",
            "SEAL_MISMATCH,Seal mismatch",
            "SEAL_DAMAGED,Seal damaged",
            "DAMAGED,Bag damaged",
            "WRONG_BAG,Wrong bag",
            "MANIFEST_DISCREPANCY,Manifest discrepancy",
            "COLLECTION_DELAYED,Customer collection delayed",
            "AIRLINE_HANDOVER,Airline handover issue",
            "OTHER,Other"
    })
    void exceptionTypeLabels(ExceptionType type, String label) {
        assertThat(type.label()).isEqualTo(label);
    }

    @ParameterizedTest
    @EnumSource(ExceptionType.class)
    void everyExceptionTypeHasLabel(ExceptionType type) {
        assertThat(type.label()).isNotBlank();
    }

    @Test
    void exceptionTypeCount() {
        assertThat(ExceptionType.values()).hasSize(11);
    }

    @Test
    void exceptionStateAndStatusValues() {
        assertThat(ExceptionState.values()).containsExactly(ExceptionState.NONE, ExceptionState.OPEN, ExceptionState.RESOLVED);
        assertThat(ExceptionStatus.values()).containsExactly(ExceptionStatus.OPEN, ExceptionStatus.RESOLVED);
    }

    @Test
    void priorityOrderedFromLowToHigh() {
        assertThat(Priority.values()).containsExactly(Priority.NORMAL, Priority.HIGH, Priority.CRITICAL);
        assertThat(Priority.CRITICAL.compareTo(Priority.NORMAL)).isPositive();
    }

    @Test
    void bagTypeAndConditionValues() {
        assertThat(BagType.values()).containsExactly(BagType.SUITCASE, BagType.DUFFEL, BagType.BACKPACK, BagType.OTHER);
        assertThat(BagCondition.values()).containsExactly(BagCondition.GOOD, BagCondition.MINOR, BagCondition.DAMAGED);
    }

    @Test
    void photoStageValues() {
        assertThat(PhotoStage.values()).extracting(Enum::name).containsExactly(
                "KIA_FULL", "KIA_TAG", "KIA_SEAL", "ZNZ_CONDITION", "ZNZ_SEAL", "EXCEPTION", "RELEASE");
    }

    @Test
    void manifestStatusValues() {
        assertThat(ManifestStatus.values()).containsExactly(
                ManifestStatus.DRAFT, ManifestStatus.FINALISED, ManifestStatus.HANDED_OVER);
    }

    @Test
    void custodyEventTypeValues() {
        assertThat(CustodyEventType.values()).extracting(Enum::name).containsExactly(
                "KIA_INTAKE", "SECURITY_SEAL", "AIRLINE_HANDOVER", "ZNZ_RECEIPT", "STORAGE",
                "READY_FOR_COLLECTION", "CUSTOMER_RELEASE", "STATUS_CORRECTION", "CANCELLED");
    }

    @Test
    void valueOfRoundTrips() {
        for (CustodyEventType type : CustodyEventType.values()) {
            assertThat(CustodyEventType.valueOf(type.name())).isSameAs(type);
        }
    }

    @Test
    void holderClassIsNotInstantiable() throws Exception {
        Constructor<OpsEnums> ctor = OpsEnums.class.getDeclaredConstructor();
        assertThat(Modifier.isPrivate(ctor.getModifiers())).isTrue();
        ctor.setAccessible(true);
        assertThat(ctor.newInstance()).isNotNull();
    }
}
