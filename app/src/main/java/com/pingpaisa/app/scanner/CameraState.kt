package com.pingpaisa.app.scanner

enum class CameraPermission { UNKNOWN, GRANTED, DENIED, DENIED_FOREVER }

enum class CameraPhase { OFF, NO_PERMISSION, DENIED_FOREVER, NO_CAMERA, ACTIVE, ERROR }

data class CameraInputs(
    val userOn: Boolean,
    val permission: CameraPermission,
    val hasCamera: Boolean,
    val bindFailed: Boolean = false
)

/**
 * One function decides what the camera should be doing. The user's ON/OFF choice always wins: nothing (gallery,
 * crop, result, permission change, resume) can switch a camera that the user turned OFF back on.
 */
object CameraState {
    fun phase(i: CameraInputs): CameraPhase = when {
        !i.userOn -> CameraPhase.OFF
        !i.hasCamera -> CameraPhase.NO_CAMERA
        i.permission == CameraPermission.GRANTED -> if (i.bindFailed) CameraPhase.ERROR else CameraPhase.ACTIVE
        i.permission == CameraPermission.DENIED_FOREVER -> CameraPhase.DENIED_FOREVER
        else -> CameraPhase.NO_PERMISSION
    }

    fun canCapture(phase: CameraPhase, flow: FlowState) = phase == CameraPhase.ACTIVE && !ScanFlow.isBusy(flow)
    fun shouldAnalyse(phase: CameraPhase, flow: FlowState, resumed: Boolean) = phase == CameraPhase.ACTIVE && !ScanFlow.isBusy(flow) && resumed
}

/** Ignores taps that arrive faster than the camera can start or stop (rapid ON/OFF switching). */
class ToggleGuard(private val minGapMs: Long = 350) {
    private var last = Long.MIN_VALUE / 2
    fun accept(nowMs: Long): Boolean {
        if (nowMs - last < minGapMs) return false
        last = nowMs
        return true
    }
}
