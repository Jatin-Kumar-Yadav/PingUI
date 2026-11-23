package com.pingpaisa.app.scanner

/** One image waiting to be cropped. [key] lets the view model find its source (file path or content URI). */
data class CropJob(val key: String, val fromCamera: Boolean)

/** Where the scan is. Exactly one of these at any time, so the screen never mixes up booleans. */
sealed interface FlowState {
    data object Idle : FlowState
    data class Cropping(val job: CropJob, val queue: List<CropJob> = emptyList()) : FlowState
    data class Processing(val productId: String, val previewPath: String, val queue: List<CropJob> = emptyList()) : FlowState
    data class Reviewing(val productId: String, val queue: List<CropJob> = emptyList()) : FlowState
}

sealed interface FlowEvent {
    data class Captured(val job: CropJob) : FlowEvent
    data class Picked(val jobs: List<CropJob>) : FlowEvent
    data class CropConfirmed(val productId: String, val previewPath: String) : FlowEvent
    data object CropCancelled : FlowEvent
    data object Retake : FlowEvent
    data class Analysed(val productId: String, val needsReview: Boolean) : FlowEvent
    data object ProcessingCancelled : FlowEvent
    data object ReviewClosed : FlowEvent
}

/**
 * Camera ON -> Capture -> Crop -> Process -> Verify -> Result;  Gallery -> Crop -> Process -> Verify -> Result.
 * Images picked while something is in progress wait in a queue (nothing is dropped, nothing interrupts the user).
 * Events that make no sense in the current state are ignored, and a result for an older product never moves the flow.
 */
object ScanFlow {
    fun reduce(s: FlowState, e: FlowEvent): FlowState = when (s) {
        FlowState.Idle -> when (e) {
            is FlowEvent.Captured -> FlowState.Cropping(e.job)
            is FlowEvent.Picked -> next(e.jobs)
            else -> s
        }
        is FlowState.Cropping -> when (e) {
            is FlowEvent.Picked -> s.copy(queue = s.queue + e.jobs)
            is FlowEvent.CropConfirmed -> FlowState.Processing(e.productId, e.previewPath, s.queue)
            FlowEvent.CropCancelled -> next(s.queue)
            FlowEvent.Retake -> if (s.job.fromCamera) next(s.queue) else s
            else -> s
        }
        is FlowState.Processing -> when (e) {
            is FlowEvent.Picked -> s.copy(queue = s.queue + e.jobs)
            is FlowEvent.Analysed -> when {
                e.productId != s.productId -> s
                e.needsReview -> FlowState.Reviewing(s.productId, s.queue)
                else -> next(s.queue)
            }
            FlowEvent.ProcessingCancelled -> next(s.queue)
            else -> s
        }
        is FlowState.Reviewing -> when (e) {
            is FlowEvent.Picked -> s.copy(queue = s.queue + e.jobs)
            FlowEvent.ReviewClosed -> next(s.queue)
            else -> s
        }
    }

    private fun next(queue: List<CropJob>): FlowState =
        if (queue.isEmpty()) FlowState.Idle else FlowState.Cropping(queue.first(), queue.drop(1))

    /** True while any overlay is open: the live camera must not analyse frames or take photos then. */
    fun isBusy(s: FlowState) = s !is FlowState.Idle
}
