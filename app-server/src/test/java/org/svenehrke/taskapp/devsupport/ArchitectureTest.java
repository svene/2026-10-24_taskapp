package org.svenehrke.taskapp.devsupport;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The package rules the Maven module graph cannot enforce (spec §4.1). The modules already keep core free of
 * adapters; these rules work inside the flat packages {@code card}, {@code recurring} and {@code board}, which
 * every module shares.
 */
class ArchitectureTest {
    private static final String PKG_ROOT = "org.svenehrke.taskapp";
    private static final String GROUP_ID_REPO_PATH = "/org/svenehrke/";
    private static final Pattern INBOUND_MODULE_PATH = Pattern.compile("/inbound-[^/]+/");

    /**
     * Skips opening third-party JARs during the scan. Our own module JARs (resolved from the local repo under this
     * project's groupId path) stay included; when the reactor builds, they come from {@code target/classes} anyway.
     */
    private static final ImportOption EXCLUDE_THIRD_PARTY_JARS =
        location -> !location.isJar() || location.contains(GROUP_ID_REPO_PATH);

    /**
     * Matches both ways a module's classes reach this scan: {@code .../inbound-http-html/target/classes/...}
     * (reactor build) and {@code .../org/svenehrke/inbound-http-html/<version>/...jar!/...} (local repo).
     */
    private static final DescribedPredicate<JavaClass> RESIDE_IN_INBOUND_MODULE = DescribedPredicate.describe(
        "reside in an inbound-* module",
        javaClass -> javaClass.getSource()
            .map(source -> INBOUND_MODULE_PATH.matcher(source.getUri().toString()).find())
            .orElse(false)
    );

    static JavaClasses importedClasses;

    @BeforeAll
    static void importClasses() {
        importedClasses = new ClassFileImporter(List.of(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS, EXCLUDE_THIRD_PARTY_JARS))
            .importPackages(PKG_ROOT);
    }

    @Test
    void packages_are_free_of_cycles() {
        slices().matching(PKG_ROOT + ".(*)..").should().beFreeOfCycles()
            .check(importedClasses);
    }

    /** Occurrence inserts and adoption go through {@code RecurringTaskRepositorySPI}, not through {@code card}. */
    @Test
    void recurring_does_not_depend_on_card() {
        noClasses().that().resideInAPackage(PKG_ROOT + ".recurring..")
            .should().dependOnClassesThat().resideInAPackage(PKG_ROOT + ".card..")
            .allowEmptyShould(true)
            .check(importedClasses);
    }

    /** Shared logic between Handlers lives in a non-Handler bean (e.g. {@code OccurrenceProducer}). */
    @Test
    void handlers_never_call_handlers() {
        noClasses().that().haveSimpleNameEndingWith("Handler")
            .should().callMethodWhere(DescribedPredicate.describe(
                "a method of another Handler",
                (JavaMethodCall call) -> call.getTargetOwner().getSimpleName().endsWith("Handler")
                    && !call.getTargetOwner().equals(call.getOriginOwner())))
            .check(importedClasses);
    }

    /**
     * Inbound adapters drive the domain through a {@code *Handler} only, never through an outbound port: a Receiver
     * injecting a {@code *SPI} would compile (SPIs live in core) and CDI would hand it the outbound adapter directly,
     * bypassing the Handler and its transaction. Module-based, so helpers of a Receiver are covered too.
     */
    @Test
    void inbound_adapters_do_not_use_spis() {
        noClasses().that(RESIDE_IN_INBOUND_MODULE)
            .should().dependOnClassesThat().haveSimpleNameEndingWith("SPI")
            .because("inbound adapters must go through a Handler, not bypass it via an outbound port")
            .check(importedClasses);
    }
}
