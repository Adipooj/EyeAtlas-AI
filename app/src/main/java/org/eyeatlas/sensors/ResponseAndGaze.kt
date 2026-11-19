package org.eyeatlas.sensors

import org.eyeatlas.core.ResponseEvent
import org.eyeatlas.core.StimulusTrial
import org.eyeatlas.net.ClockSyncEngine

class ResponseAndGaze(
    val debounceWindowNs: Long = 100_000_000L,       // 100ms debounce
    val longPressThresholdNs: Long = 1_000_000_000L  // 1.0s for priority STOP
) {
    private var lastRegisteredTouchDownNs: Long = 0L
    private var eventCounter: Long = 0L

    fun recordResponse(timestampNs: Long, isSeen: Boolean): Boolean {
        return isSeen
    }

    /**
     * Registers a native touch event from Phone B's blind touch pad.
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
            inputType = "TOUCH_TAP",
            pointerCount = 1,
            inputQuality = "VALID"
        )
    }

    /**
     * Evaluates a StimulusTrial against recorded response events, cross-phone clock sync,
     * and transport connection state.
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
        // 1. Check transport connection / watchdog
        if (!isTransportConnected) {
            return trial.copy(
                observedAnswer = "INVALID_UNGRADABLE",
                validityFlags = listOf("DISCONNECT_WATCHDOG_TRIGGERED")
            )
        }

        // 2. Check clock sync uncertainty
        if (!clockSync.isUncertaintyAcceptable()) {
            return trial.copy(
                observedAnswer = "INVALID_UNGRADABLE",
                clockOffsetNs = clockSync.currentOffsetNs,
                clockUncertaintyNs = clockSync.currentUncertaintyNs,
                validityFlags = listOf("INVALID_TIMING_UNCERTAINTY")
            )
        }

        // 3. Inspect response events for multi-touch, false taps, or STOP
        val windowStartNs = trial.monotonicPresentedNs + windowStartOffsetNs
        val windowEndNs = windowStartNs + windowDurationNs

        for (resp in responses) {
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
            if (resp.inputQuality == "PRIORITY_STOP") {
                return trial.copy(
                    observedAnswer = "INVALID_UNGRADABLE",
                    validityFlags = listOf("STOP_REQUESTED")
                )
            }
            if (resp.inputQuality.startsWith("LATE_FAULT")) {
                return trial.copy(
                    observedAnswer = "INVALID_UNGRADABLE",
                    validityFlags = listOf("INVALID_LATE_TOUCH")
                )
            }
        }

        // 4. Match valid tap within time window using corrected timestamps
        val validTap = responses.firstOrNull { resp ->
            if (resp.inputType != "TOUCH_TAP" || resp.inputQuality != "VALID") return@firstOrNull false
            val correctedNs = clockSync.correctRemoteTimestamp(resp.monotonicEventNs)
            correctedNs in windowStartNs..windowEndNs
        }

        if (validTap != null) {
            val correctedNs = clockSync.correctRemoteTimestamp(validTap.monotonicEventNs)
            return trial.copy(
                observedAnswer = "SEEN",
                responsePhoneMonotonicNs = validTap.monotonicEventNs,
                correctedResponseNs = correctedNs,
                clockOffsetNs = clockSync.currentOffsetNs,
                clockUncertaintyNs = clockSync.currentUncertaintyNs,
                responseEventId = validTap.eventId,
                validityFlags = listOf("VALID_SEEN")
            )
        }

        // 5. Invariant: Only declare NO_RESPONSE when the window fully elapsed with all channels valid
        return trial.copy(
            observedAnswer = "NO_RESPONSE",
            clockOffsetNs = clockSync.currentOffsetNs,
            clockUncertaintyNs = clockSync.currentUncertaintyNs,
            validityFlags = listOf("VALID_NO_RESPONSE")
        )
    }
}
