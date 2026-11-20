package org.eyeatlas.store

import java.security.MessageDigest
import kotlin.math.sqrt

data class DatasetProvenance(
    val datasetId: String,
    val name: String,
    val licenseType: String,
    val status: String, // "VERIFIED_OPEN", "HOLD_LICENSE", "EXCLUDED_RESTRICTED"
    val attribution: String,
    val isGated: Boolean
)

data class ConfidenceInterval(
    val estimate: Double,
    val lowerBound: Double,
    val upperBound: Double,
    val confidenceLevel: Double = 0.95
)

// --- Phase P20 Clinical Pilot Models ---

data class ClinicalPilotStudyRecord(
    val studyPreregistrationId: String, // e.g. "CTRI/2026/11/048821"
    val ethicsApprovalRef: String,       // e.g. "IEC/EYEATLAS/2026/08"
    val operatorRole: String,            // "COMMUNITY_HEALTH_WORKER" or "TRAINED_OPTOMETRIST"
    val operatorId: String,
    val durationSeconds: Int,            // Test completion duration
    val patientComfortScore: Double,     // SUS 0-100 or Likert 1.0-5.0
    val protocolMode: String,            // "FULL_COMPREHENSIVE" or "ABBREVIATED_TRIAGE"
    val omittedTests: List<String> = emptyList()
)

data class MaskedClinicalReferenceComparison(
    val condition: String,               // e.g. "GLAUCOMA_FIELD", "DIABETIC_RETINOPATHY", "CATARACT", "DRY_EYE", "STRABISMUS"
    val referenceTestType: String,       // e.g. "HUMPHREY_SAP_24_2", "SLIT_LAMP_BIOMICROSCOPY", "MYDRIATIC_7FIELD_FUNDUS"
    val referencePositive: Boolean,      // Masked clinical ground truth
    val candidatePositive: Boolean?,     // Null if ungradable/missing
    val isUngradable: Boolean
)

data class SubgroupQualityStratification(
    val ageGroup: String,                // "PEDIATRIC_UNDER_5", "YOUTH_5_17", "ADULT_18_64", "SENIOR_65_PLUS"
    val mediaClarity: String,            // "CLEAR_MEDIA", "MILD_OPACITY", "DENSE_CATARACT"
    val cooperationStatus: String,       // "FULL_COOPERATION", "VARIABLE_FIXATION", "LIMITED_COOPERATION"
    val totalScreened: Int,
    val completeExaminations: Int,
    val ungradableCount: Int
)

data class ClinicalPilotAgreementSummary(
    val totalCases: Int,
    val concordanceRate: Double,
    val sensitivity: Double?,
    val sensitivityCi: ConfidenceInterval?,
    val specificity: Double?,
    val specificityCi: ConfidenceInterval?,
    val ungradableRate: Double,
    val auditPass: Boolean
)

// --- Phase P21 External Validation & Release Models ---

data class ValidationSiteInfo(
    val siteId: String,
    val siteName: String,
    val siteTier: String,                // "TERTIARY_HOSPITAL", "RURAL_VISION_CENTER", "MOBILE_OUTREACH"
    val hardwareBatch: String            // "PHONE_BATCH_A", "PHONE_BATCH_B"
)

data class PerConditionValidationMetrics(
    val condition: String,
    val siteId: String,
    val totalEnrolled: Int,
    val truePositives: Int,
    val falsePositives: Int,
    val trueNegatives: Int,
    val falseNegatives: Int,
    val ungradableCount: Int,
    val prevalence: Double,
    val sensitivity: Double,
    val sensitivityCi: ConfidenceInterval,
    val specificity: Double,
    val specificityCi: ConfidenceInterval,
    val ppv: Double,
    val npv: Double,
    val nonInferiorityMargin: Double,
    val isNonInferior: Boolean
)

data class ReleaseDecisionSummary(
    val isReleaseApproved: Boolean,
    val regulatoryStatus: String,
    val cdscoReviewPending: Boolean,
    val residualRiskAcceptable: Boolean,
    val attritionRateWithinLimit: Boolean,
    val totalScreenedAcrossSites: Int,
    val failingConditions: List<String>
)

class ReportsAndExports {

    companion object {
        const val RESEARCH_PROTOTYPE_DISCLAIMER =
            "*** RESEARCH PROTOTYPE ONLY - NOT CLEARED BY CDSCO / FDA FOR COMMERCIAL DEPLOYMENT OR STANDALONE DIAGNOSIS ***"

        val CANONICAL_DATASETS = listOf(
            DatasetProvenance("RFMiD", "Retinal Fundus Multi-Disease", "CC BY 4.0", "VERIFIED_OPEN", "Pachade et al. (CC BY 4.0)", false),
            DatasetProvenance("IDRiD", "Indian Diabetic Retinopathy", "CC BY 4.0", "VERIFIED_OPEN", "Porwal et al. (CC BY 4.0)", false),
            DatasetProvenance("CONJUNCTIVITIS_MENDELEY", "Conjunctivitis Recognition", "CC BY 4.0", "VERIFIED_OPEN", "Mendeley Data (CC BY 4.0)", false),
            DatasetProvenance("DRY_EYE_NIKBUT", "Dry Eye NIKBUT", "CC BY 4.0", "VERIFIED_OPEN", "RepOD Data (CC BY 4.0)", false),
            DatasetProvenance("SLID", "Slit-Lamp Image Dataset", "UNCONFIRMED_CC_BY", "HOLD_LICENSE", "Missing repo license", true),
            DatasetProvenance("PAPILA", "Papila Glaucoma", "GPL3_VS_CC_BY_CONFLICT", "HOLD_LICENSE", "Conflicting license claims", true),
            DatasetProvenance("CSDI", "Cataract Slit-Lamp Dataset", "NC_CONFLICT", "HOLD_LICENSE", "Host NC restriction", true),
            DatasetProvenance("APTOS_2019", "APTOS Blindness Detection", "KAGGLE_NONCOMMERCIAL", "EXCLUDED_RESTRICTED", "Login/CAPTCHA gated", true)
        )

        fun computeWilsonScoreInterval(k: Int, n: Int, z: Double = 1.96): ConfidenceInterval {
            if (n <= 0) return ConfidenceInterval(0.0, 0.0, 0.0)
            val p = k.toDouble() / n.toDouble()
            val z2 = z * z
            val denominator = 1.0 + z2 / n
            val center = (p + z2 / (2.0 * n)) / denominator
            val spread = (z * sqrt(p * (1.0 - p) / n + z2 / (4.0 * n * n))) / denominator
            val lower = (center - spread).coerceIn(0.0, 1.0)
            val upper = (center + spread).coerceIn(0.0, 1.0)
            return ConfidenceInterval(estimate = p, lowerBound = lower, upperBound = upper)
        }
    }

    fun exportReport(patientId: String): String {
        return "$RESEARCH_PROTOTYPE_DISCLAIMER\nPatient Report for: $patientId\nStatus: Evaluated in Research Prototype Mode."
    }

    fun getRegisteredDatasets(): List<DatasetProvenance> = CANONICAL_DATASETS

    fun isDatasetEligibleForClinicalUse(datasetId: String): Boolean {
        val ds = CANONICAL_DATASETS.firstOrNull { it.datasetId == datasetId } ?: return false
        return ds.status == "VERIFIED_OPEN" && !ds.isGated
    }

    fun verifyPatientLevelIsolation(trainPatientIds: Set<String>, testPatientIds: Set<String>): Boolean {
        val overlap = trainPatientIds.intersect(testPatientIds)
        return overlap.isEmpty()
    }

    fun exportProvenanceReport(sessionId: String): String {
        val lines = mutableListOf<String>()
        lines.add("EYEATLAS VR - DATASET PROVENANCE AND LICENSE MANIFEST")
        lines.add("Session ID: $sessionId")
        lines.add("Registered Sources: ${CANONICAL_DATASETS.size}")
        lines.add("Verified Permissive Datasets: ${CANONICAL_DATASETS.count { !it.isGated }}")
        lines.add("Gated / Withheld Datasets: ${CANONICAL_DATASETS.count { it.isGated }}")
        lines.add("----------------------------------------------------------------")
        for (ds in CANONICAL_DATASETS) {
            val gateStr = if (ds.isGated) "[GATED/WITHHELD]" else "[PERMITTED]"
            lines.add("${ds.datasetId}: ${ds.name} | ${ds.licenseType} | $gateStr | Attribution: ${ds.attribution}")
        }
        val content = lines.joinToString("\n")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "$content\n----------------------------------------------------------------\nManifest SHA256: $digest"
    }

    // --- Phase P20 Methods ---

    /**
     * Invariant check: Ungradable/missing candidate tests must NEVER be treated as negative (TN or FN).
     * Any missing data must remain UNGRADABLE_WITHHELD.
     */
    fun calculateMaskedAgreement(comparisons: List<MaskedClinicalReferenceComparison>): ClinicalPilotAgreementSummary {
        val invalidClassifications = comparisons.count { it.isUngradable && it.candidatePositive != null }
        if (invalidClassifications > 0) {
            throw IllegalStateException(
                "Clinical Invariant Violation: $invalidClassifications ungradable comparisons have a non-null diagnostic result. Missing data must be UNGRADABLE_WITHHELD!"
            )
        }

        val totalCases = comparisons.size
        val ungradables = comparisons.count { it.isUngradable }
        val evaluable = comparisons.filter { !it.isUngradable && it.candidatePositive != null }

        val concordant = evaluable.count { it.referencePositive == it.candidatePositive }
        val concordanceRate = if (evaluable.isNotEmpty()) concordant.toDouble() / evaluable.size else 0.0

        val refPositives = evaluable.filter { it.referencePositive }
        val tp = refPositives.count { it.candidatePositive == true }
        val sens = if (refPositives.isNotEmpty()) tp.toDouble() / refPositives.size else null
        val sensCi = if (refPositives.isNotEmpty()) computeWilsonScoreInterval(tp, refPositives.size) else null

        val refNegatives = evaluable.filter { !it.referencePositive }
        val tn = refNegatives.count { it.candidatePositive == false }
        val spec = if (refNegatives.isNotEmpty()) tn.toDouble() / refNegatives.size else null
        val specCi = if (refNegatives.isNotEmpty()) computeWilsonScoreInterval(tn, refNegatives.size) else null

        val ungradableRate = if (totalCases > 0) ungradables.toDouble() / totalCases else 0.0

        return ClinicalPilotAgreementSummary(
            totalCases = totalCases,
            concordanceRate = concordanceRate,
            sensitivity = sens,
            sensitivityCi = sensCi,
            specificity = spec,
            specificityCi = specCi,
            ungradableRate = ungradableRate,
            auditPass = (ungradableRate <= 0.15) && (concordanceRate >= 0.80)
        )
    }

    fun auditSubgroupQuality(subgroups: List<SubgroupQualityStratification>): Boolean {
        for (sg in subgroups) {
            if (sg.completeExaminations + sg.ungradableCount != sg.totalScreened) {
                throw IllegalStateException("Subgroup audit failure: counts do not sum to totalScreened in ${sg.ageGroup}/${sg.mediaClarity}")
            }
        }
        return true
    }

    fun generateClinicalPilotReport(
        study: ClinicalPilotStudyRecord,
        comparisons: List<MaskedClinicalReferenceComparison>,
        subgroups: List<SubgroupQualityStratification>
    ): String {
        auditSubgroupQuality(subgroups)
        val agreement = calculateMaskedAgreement(comparisons)

        val lines = mutableListOf<String>()
        lines.add(RESEARCH_PROTOTYPE_DISCLAIMER)
        lines.add("EYEATLAS VR - CLINICAL PILOT FEASIBILITY AND ACCURACY REPORT")
        lines.add("Study Preregistration ID: ${study.studyPreregistrationId}")
        lines.add("Ethics Board Reference: ${study.ethicsApprovalRef}")
        lines.add("Operator Role: ${study.operatorRole} (ID: ${study.operatorId})")
        lines.add("Protocol Mode: ${study.protocolMode}")
        if (study.omittedTests.isNotEmpty()) {
            lines.add("Omitted Tests (Abbreviated): ${study.omittedTests.joinToString(", ")}")
        }
        lines.add("Examination Duration: ${study.durationSeconds}s (Target <= 900s)")
        lines.add("Patient Comfort Score: ${study.patientComfortScore} (Scale 1.0-5.0 Likert or 0-100 SUS)")
        lines.add("----------------------------------------------------------------")
        lines.add("Total Screened Cases: ${agreement.totalCases}")
        lines.add("Ungradable Rate: ${"%.2f".format(agreement.ungradableRate * 100)}%")
        lines.add("Concordance Rate: ${"%.2f".format(agreement.concordanceRate * 100)}%")
        if (agreement.sensitivityCi != null) {
            lines.add(
                "Sensitivity: ${"%.2f".format(agreement.sensitivityCi.estimate * 100)}% (95% CI: ${"%.2f".format(agreement.sensitivityCi.lowerBound * 100)}% - ${"%.2f".format(agreement.sensitivityCi.upperBound * 100)}%)"
            )
        }
        if (agreement.specificityCi != null) {
            lines.add(
                "Specificity: ${"%.2f".format(agreement.specificityCi.estimate * 100)}% (95% CI: ${"%.2f".format(agreement.specificityCi.lowerBound * 100)}% - ${"%.2f".format(agreement.specificityCi.upperBound * 100)}%)"
            )
        }
        lines.add("Audit Status: ${if (agreement.auditPass) "PILOT_FEASIBILITY_VERIFIED" else "REQUIRES_TUNING"}")
        lines.add("Zero Missed Disease Masked as Negative: VERIFIED_AUDIT_PASS")
        lines.add("Commercial Release Claim: FORBIDDEN (Research prototype mode locked)")
        lines.add("----------------------------------------------------------------")
        lines.add("Subgroup Breakdowns:")
        for (sg in subgroups) {
            lines.add(" - ${sg.ageGroup} | ${sg.mediaClarity} | ${sg.cooperationStatus}: N=${sg.totalScreened}, Complete=${sg.completeExaminations}, Ungradable=${sg.ungradableCount}")
        }

        val content = lines.joinToString("\n")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "$content\n----------------------------------------------------------------\nReport SHA256: $digest"
    }

    // --- Phase P21 Methods ---

    fun evaluateConditionValidationMetrics(
        condition: String,
        siteId: String,
        truePositives: Int,
        falsePositives: Int,
        trueNegatives: Int,
        falseNegatives: Int,
        ungradableCount: Int,
        prevalence: Double,
        nonInferioritySensitivityThreshold: Double
    ): PerConditionValidationMetrics {
        val totalEnrolled = truePositives + falsePositives + trueNegatives + falseNegatives + ungradableCount
        val totalRefPositives = truePositives + falseNegatives
        val totalRefNegatives = trueNegatives + falsePositives

        val sensCi = computeWilsonScoreInterval(truePositives, totalRefPositives)
        val specCi = computeWilsonScoreInterval(trueNegatives, totalRefNegatives)

        val totalPredictedPositives = truePositives + falsePositives
        val ppv = if (totalPredictedPositives > 0) truePositives.toDouble() / totalPredictedPositives else 0.0

        val totalPredictedNegatives = trueNegatives + falseNegatives
        val npv = if (totalPredictedNegatives > 0) trueNegatives.toDouble() / totalPredictedNegatives else 0.0

        val isNonInferior = sensCi.lowerBound >= nonInferioritySensitivityThreshold

        return PerConditionValidationMetrics(
            condition = condition,
            siteId = siteId,
            totalEnrolled = totalEnrolled,
            truePositives = truePositives,
            falsePositives = falsePositives,
            trueNegatives = trueNegatives,
            falseNegatives = falseNegatives,
            ungradableCount = ungradableCount,
            prevalence = prevalence,
            sensitivity = sensCi.estimate,
            sensitivityCi = sensCi,
            specificity = specCi.estimate,
            specificityCi = specCi,
            ppv = ppv,
            npv = npv,
            nonInferiorityMargin = nonInferioritySensitivityThreshold,
            isNonInferior = isNonInferior
        )
    }

    fun evaluateReleaseReadiness(
        metricsList: List<PerConditionValidationMetrics>,
        maxAllowedAttritionRate: Double = 0.10
    ): ReleaseDecisionSummary {
        val failingConditions = metricsList.filter { !it.isNonInferior }.map { it.condition }
        val totalEnrolled = metricsList.sumOf { it.totalEnrolled }
        val totalUngradable = metricsList.sumOf { it.ungradableCount }
        val overallAttritionRate = if (totalEnrolled > 0) totalUngradable.toDouble() / totalEnrolled else 0.0
        val attritionAcceptable = overallAttritionRate <= maxAllowedAttritionRate

        // Critical Invariant: Research prototype cannot be commercially distributed without CDSCO SaMD clearance
        return ReleaseDecisionSummary(
            isReleaseApproved = false, // Must remain false until formal regulatory clearance
            regulatoryStatus = "INVESTIGATIONAL_ONLY_NOT_FOR_COMMERCIAL_DISTRIBUTION",
            cdscoReviewPending = true,
            residualRiskAcceptable = true,
            attritionRateWithinLimit = attritionAcceptable,
            totalScreenedAcrossSites = totalEnrolled,
            failingConditions = failingConditions
        )
    }

    fun generateExternalValidationReleaseReport(
        sites: List<ValidationSiteInfo>,
        metrics: List<PerConditionValidationMetrics>,
        releaseSummary: ReleaseDecisionSummary
    ): String {
        val lines = mutableListOf<String>()
        lines.add(RESEARCH_PROTOTYPE_DISCLAIMER)
        lines.add("EYEATLAS VR - MULTI-SITE EXTERNAL VALIDATION & RELEASE ASSESSMENT REPORT")
        lines.add("Participating Sites (${sites.size}):")
        for (site in sites) {
            lines.add(" - [${site.siteId}] ${site.siteName} (${site.siteTier}, Hardware: ${site.hardwareBatch})")
        }
        lines.add("----------------------------------------------------------------")
        lines.add("Condition Performance & Non-Inferiority Evaluation:")
        for (m in metrics) {
            val statusStr = if (m.isNonInferior) "NON_INFERIOR_PASS" else "BELOW_MARGIN_FAIL"
            lines.add(
                "Condition: ${m.condition} @ ${m.siteId} | N=${m.totalEnrolled} (Ungradable=${m.ungradableCount})"
            )
            lines.add(
                "  Sens: ${"%.2f".format(m.sensitivity * 100)}% (95% CI: ${"%.2f".format(m.sensitivityCi.lowerBound * 100)}% - ${"%.2f".format(m.sensitivityCi.upperBound * 100)}%) [Margin: >=${"%.2f".format(m.nonInferiorityMargin * 100)}%] -> $statusStr"
            )
            lines.add(
                "  Spec: ${"%.2f".format(m.specificity * 100)}% (95% CI: ${"%.2f".format(m.specificityCi.lowerBound * 100)}% - ${"%.2f".format(m.specificityCi.upperBound * 100)}%)"
            )
            lines.add(
                "  PPV: ${"%.2f".format(m.ppv * 100)}% | NPV: ${"%.2f".format(m.npv * 100)}% (Prevalence: ${"%.2f".format(m.prevalence * 100)}%)"
            )
        }
        lines.add("----------------------------------------------------------------")
        lines.add("RELEASE DECISION SUMMARY:")
        lines.add("Total Evaluated Participants: ${releaseSummary.totalScreenedAcrossSites}")
        lines.add("Attrition Rate Acceptable: ${releaseSummary.attritionRateWithinLimit}")
        lines.add("Failing Conditions: ${if (releaseSummary.failingConditions.isEmpty()) "NONE" else releaseSummary.failingConditions.joinToString(", ")}")
        lines.add("Commercial Release Approved: ${releaseSummary.isReleaseApproved}")
        lines.add("Regulatory Status: ${releaseSummary.regulatoryStatus}")
        lines.add("CDSCO SaMD Clearance Gate: ${if (releaseSummary.cdscoReviewPending) "PENDING_REGULATORY_SUBMISSION" else "CLEARED"}")

        val content = lines.joinToString("\n")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "$content\n----------------------------------------------------------------\nReport SHA256: $digest"
    }
}
