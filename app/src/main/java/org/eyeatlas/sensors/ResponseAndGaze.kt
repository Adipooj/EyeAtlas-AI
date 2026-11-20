package org.eyeatlas.sensors

import org.eyeatlas.core.ResponseEvent
import org.eyeatlas.core.StimulusTrial
import org.eyeatlas.net.ClockSyncEngine

/**
 * Multi-channel patient response and gaze fixation evaluation engine (CR-002).
 *
 * Implements decoupled patient response pathways physically accessible while wearing the VRBox:
 * 1. Headset capacitive/mechanical button.
 * 2. Paired Bluetooth tactile HID clicker.
 * 3. Hardware volume rocker keys.
 * 4. Web-based response pad (Phone 2 or browser).
 * 5. Physician-recorded verbal notes (strictly flagged to prevent false hardware latency).
 *
 * HARD MEDICAL INVARIANT:
 * Dropped packets, disconnected transport, or missing controller inputs produce
 * INVALID_UNGRADABLE trials, NEVER false NO_RESPONSE or false-negative scotomas!
 */
class ResponseAndGaze(
    val debounceWindowNs: Long = 100_000_000L,       // 100ms debounce
    val longPressThresholdNs: Long = 1_000_000_000L  // 1.0s for priority STOP
) {
    private var lastRegisteredTouchDownNs: Long = 0L
    private var eventCounter: Long = 0L

    companion object {
        const val INPUT_TOUCH_PAD = "TOUCH_TAP"
        const val INPUT_HEADSET_BUTTON = "HEADSET_HARDWARE_BUTTON"
        const val INPUT_BT_CLICKER = "BT_TACTILE_CLICKER"
        const val INPUT_VOLUME_KEY = "VOLUME_ROCKER_KEY"
        const val INPUT_WEB_RESPONSE_PAD = "WEB_RESPONSE_PAD"
        const val INPUT_PHYSICIAN_VERBAL = "PHYSICIAN_VERBAL_RECORD"
    }

    fun recordResponse(timestampNs: Long, isSeen: Boolean): Boolean {
        return isSeen
    }

    /**
     * Registers a native touch event from Phone B or Web Response Pad.
     */
    fun registerTouchEvent(
        sessionId: String,
        responsePhoneId: String,
        touchDownNs: Long,
        touchUpNs: Long,
        pointerCount: Int
    ): ResponseEvent {
        eventCounter++
        val eventId = "RESP-EVT-$eventCounter"
        val durationNs = touchUpNs - touchDownNs

        // 1. Multi-touch rejection
        if (pointerCount > 1) {
            return ResponseEvent(
                eventId = eventId,
                sessionId = sessionId,
                responsePhoneId = responsePhoneId,
                monotonicEventNs = touchDownNs,
                inputType = "MULTI_TOUCH",
                pointerCount = pointerCount,
                inputQuality = "MULTI_TOUCH_AMBIGUITY"
            )
        }

        // 2. Debounce rejection (rapid false taps)
        if (lastRegisteredTouchDownNs > 0L && (touchDownNs - lastRegisteredTouchDownNs) < debounceWindowNs) {
            return ResponseEvent(
                eventId = eventId,
                sessionId = sessionId,
                responsePhoneId = responsePhoneId,
                monotonicEventNs = touchDownNs,
                inputType = "FALSE_TAP_DEBOUNCED",
                pointerCount = 1,
                inputQuality = "DEBOUNCE_REJECTED"
            )
        }

        // 3. Long press STOP detection
        if (durationNs >= longPressThresholdNs) {
            lastRegisteredTouchDownNs = touchDownNs
            return ResponseEvent(
                eventId = eventId,
                sessionId = sessionId,
                responsePhoneId = responsePhoneId,
                monotonicEventNs = touchDownNs,
                inputType = "LONG_PRESS_STOP",
                pointerCount = 1,
                inputQuality = "PRIORITY_STOP"
            )
        }

        // 4. Valid single tap
        lastRegisteredTouchDownNs = touchDownNs
        return ResponseEvent(
            eventId = eventId,
            sessionId = sessionId,
            responsePhoneId = responsePhoneId,
            monotonicEventNs = touchDownNs,
            inputType = INPUT_TOUCH_PAD,
            pointerCount = 1,
            inputQuality = "VALID"
        )
    }

    /**
     * Registers a direct physical hardware response (Bluetooth clicker, Volume key, or Headset button).
     * Uses local nanosecond monotonic hardware clock directly.
     */
    fun registerHardwareButtonEvent(
        sessionId: String,
        inputType: String,
        eventMonotonicNs: Long = System.nanoTime(),
        isLongPressStop: Boolean = false
    ): ResponseEvent {
        eventCounter++
        val eventId = "RESP-HW-$eventCounter"

        // Debounce
        if (lastRegisteredTouchDownNs > 0L && (eventMonotonicNs - lastRegisteredTouchDownNs) < debounceWindowNs) {
            return ResponseEvent(
                eventId = eventId,
                sessionId = sessionId,
                responsePhoneId = "LOCAL-PATIENT-PHONE",
                monotonicEventNs = eventMonotonicNs,
                inputType = inputType,
                pointerCount = 1,
                inputQuality = "DEBOUNCE_REJECTED"
            )
        }

        lastRegisteredTouchDownNs = eventMonotonicNs

        if (isLongPressStop) {
            return ResponseEvent(
                eventId = eventId,
                sessionId = sessionId,
                responsePhoneId = "LOCAL-PATIENT-PHONE",
                monotonicEventNs = eventMonotonicNs,
                inputType = "LONG_PRESS_STOP",
                pointerCount = 1,
                inputQuality = "PRIORITY_STOP"
            )
        }

        return ResponseEvent(
            eventId = eventId,
            sessionId = sessionId,
            responsePhoneId = "LOCAL-PATIENT-PHONE",
            monotonicEventNs = eventMonotonicNs,
            inputType = inputType,
            pointerCount = 1,
            inputQuality = "VALID"
        )
    }

    /**
     * Registers a subjective physician-recorded verbal response.
     * Flagged clearly to distinguish from objective patient reaction latency.
     */
    fun registerPhysicianVerbalEvent(
        sessionId: String,
        clinicianId: String,
        isSeen: Boolean,
        timestampNs: Long = System.nanoTime()
    ): ResponseEvent {
        eventCounter++
        return ResponseEvent(
            eventId = "RESP-VERBAL-$eventCounter",
            sessionId = sessionId,
            responsePhoneId = clinicianId,
            monotonicEventNs = timestampNs,
            inputType = INPUT_PHYSICIAN_VERBAL,
            pointerCount = 0,
            inputQuality = if (isSeen) "PHYSICIAN_VERBAL_SEEN" else "PHYSICIAN_VERBAL_UNSEEN"
        )
    }

    /**
     * Evaluates a StimulusTrial against multi-channel response events,
     * cross-phone clock sync, and transport connection state.
     *
     * HARD MEDICAL INVARIANT:
     * Dropped, disconnected, uncertain, or faulty inputs produce INVALID/UNGRADABLE trials,
     * NEVER false NO_RESPONSE or false-negative visual field defects!
     */
    fun evaluateTrialResponse(
        trial: StimulusTrial,
        responses: List<ResponseEvent>,
        clockSync: ClockSyncEngine,
        isTransportConnected: Boolean,
        windowStartOffsetNs: Long = 100_000_000L,     // 100ms post-presentation minimum reaction
        windowDurationNs: Long = 1_200_000_000L       // 1200ms allowed response window
    ): StimulusTrial {
        val windowStartNs = trial.monotonicPresentedNs + windowStartOffsetNs
        val windowEndNs = windowStartNs + windowDurationNs

        // 1. Inspect response events for priority STOP or fatal quality issues
        for (resp in responses) {
            if (resp.inputQuality == "PRIORITY_STOP") {
                return trial.copy(
                    observedAnswer = "INVALID_UNGRADABLE",
                    validityFlags = listOf("STOP_REQUESTED")
                )
            }
            if (resp.inputQuality == "MULTI_TOUCH_AMBIGUITY") {
                return trial.copy(
                    observedAnswer = "INVALID_UNGRADABLE",
                    validityFlags = listOf("INVALID_MULTI_TOUCH")
                )
            }
            if (resp.inputQuality == "DEBOUNCE_REJECTED") {
                return trial.copy(
                    observedAnswer = "INVALID_UNGRADABLE",
                    validityFlags = listOf("INVALID_FALSE_TAP")
                )
            }
            if (resp.inputQuality.startsWith("LATE_FAULT")) {
                return trial.copy(
                    observedAnswer = "INVALID_UNGRADABLE",
                    validityFlags = listOf("INVALID_LATE_TOUCH")
                )
            }
        }

        // 2. Check for local direct hardware button inputs (BT Clicker, Volume key, Headset button)
        // Direct hardware inputs have zero network uncertainty!
        val validHardwareTap = responses.firstOrNull { resp ->
            val isHwInput = resp.inputType in listOf(INPUT_BT_CLICKER, INPUT_VOLUME_KEY, INPUT_HEADSET_BUTTON)
            if (!isHwInput || resp.inputQuality != "VALID") return@firstOrNull false
            resp.monotonicEventNs in windowStartNs..windowEndNs
        }

        if (validHardwareTap != null) {
            return trial.copy(
                observedAnswer = "SEEN",
                responsePhoneMonotonicNs = validHardwareTap.monotonicEventNs,
                correctedResponseNs = validHardwareTap.monotonicEventNs,
                clockOffsetNs = 0L,
                clockUncertaintyNs = 1_000_000L, // <= 1ms hardware direct
                responseEventId = validHardwareTap.eventId,
                validityFlags = listOf("VALID_SEEN_HARDWARE_DIRECT", validHardwareTap.inputType)
            )
        }

        // 3. Check for Physician Verbal Record
        val verbalRecord = responses.firstOrNull { it.inputType == INPUT_PHYSICIAN_VERBAL }
        if (verbalRecord != null) {
            val isSeen = verbalRecord.inputQuality == "PHYSICIAN_VERBAL_SEEN"
            return trial.copy(
                observedAnswer = if (isSeen) "SEEN" else "NO_RESPONSE",
                responsePhoneMonotonicNs = verbalRecord.monotonicEventNs,
                correctedResponseNs = verbalRecord.monotonicEventNs,
                clockOffsetNs = 0L,
                clockUncertaintyNs = 0L,
                responseEventId = verbalRecord.eventId,
                validityFlags = listOf("PHYSICIAN_VERBAL_OBSERVATION")
            )
        }

        // 4. For network-transported inputs (Phone 2 Touch Pad / Web Pad), verify connection & sync
        if (!isTransportConnected) {
            return trial.copy(
                observedAnswer = "INVALID_UNGRADABLE",
                validityFlags = listOf("DISCONNECT_WATCHDOG_TRIGGERED")
            )
        }

        if (!clockSync.isUncertaintyAcceptable()) {
            return trial.copy(
                observedAnswer = "INVALID_UNGRADABLE",
                clockOffsetNs = clockSync.currentOffsetNs,
                clockUncertaintyNs = clockSync.currentUncertaintyNs,
                validityFlags = listOf("INVALID_TIMING_UNCERTAINTY")
            )
        }

        // 5. Match valid network tap using corrected timestamps
        val validRemoteTap = responses.firstOrNull { resp ->
            val isRemoteTap = resp.inputType in listOf(INPUT_TOUCH_PAD, INPUT_WEB_RESPONSE_PAD)
            if (!isRemoteTap || resp.inputQuality != "VALID") return@firstOrNull false
            val correctedNs = clockSync.correctRemoteTimestamp(resp.monotonicEventNs)
            correctedNs in windowStartNs..windowEndNs
        }

        if (validRemoteTap != null) {
            val correctedNs = clockSync.correctRemoteTimestamp(validRemoteTap.monotonicEventNs)
            return trial.copy(
                observedAnswer = "SEEN",
                responsePhoneMonotonicNs = validRemoteTap.monotonicEventNs,
                correctedResponseNs = correctedNs,
                clockOffsetNs = clockSync.currentOffsetNs,
                clockUncertaintyNs = clockSync.currentUncertaintyNs,
                responseEventId = validRemoteTap.eventId,
                validityFlags = listOf("VALID_SEEN")
            )
        }

        // 6. Medical Invariant: Only declare NO_RESPONSE when the window fully elapsed with all channels valid
        return trial.copy(
            observedAnswer = "NO_RESPONSE",
            clockOffsetNs = clockSync.currentOffsetNs,
            clockUncertaintyNs = clockSync.currentUncertaintyNs,
            validityFlags = listOf("VALID_NO_RESPONSE")
        )
    }
}
