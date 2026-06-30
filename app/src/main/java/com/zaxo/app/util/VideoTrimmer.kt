package com.zaxo.app.util

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer

/**
 * Utility class for trimming video files using MediaExtractor + MediaMuxer.
 *
 * F40: Falls back to original video if trimming fails (codec incompatibility).
 * F42: Uses sample-by-sample streaming to avoid OOM on large videos.
 * F43: Copies all tracks (video + audio) to ensure trimmed video has audio.
 *
 * Algorithm:
 * 1. Create MediaExtractor and set data source from URI
 * 2. Create MediaMuxer with output file path
 * 3. For each track in the input:
 *    a. Add track to muxer with matching format
 *    b. Seek to start position (nearest sync frame)
 *    c. Copy samples until end position
 * 4. Release extractor + muxer
 * 5. Return output file path
 */
object VideoTrimmer {

    /** Minimum trim duration in milliseconds */
    const val MIN_TRIM_DURATION_MS = 3000L

    /** Maximum trim duration in milliseconds (30 seconds) */
    const val MAX_TRIM_DURATION_MS = 30_000L

    /**
     * Trim a video file from [startMs] to [endMs].
     *
     * F42: Processes sample-by-sample to avoid loading entire video into memory.
     * F43: Copies all tracks (video + audio) to preserve audio.
     * F40: On failure, returns null so caller can fall back to original video.
     *
     * @param context Android context for content resolver
     * @param inputUri URI of the source video file
     * @param startMs Start time in milliseconds
     * @param endMs End time in milliseconds
     * @return File path of the trimmed video, or null on failure
     */
    fun trimVideo(
        context: Context,
        inputUri: Uri,
        startMs: Long,
        endMs: Long
    ): String? {
        val startTimeUs = startMs * 1000
        val endTimeUs = endMs * 1000

        val extractor = MediaExtractor()
        val outputPath = File(
            context.cacheDir,
            "trimmed_${System.currentTimeMillis()}.mp4"
        ).absolutePath

        var muxer: MediaMuxer? = null
        var trackIndexMap = mutableMapOf<Int, Int>()

        try {
            // F40: Try to set data source — may fail on some codecs
            extractor.setDataSource(context, inputUri, null)

            val trackCount = extractor.trackCount
            if (trackCount == 0) {
                Timber.e("F40: No tracks found in video — cannot trim")
                return null
            }

            // F43: Create muxer and add all tracks (video + audio)
            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue

                // Add both video and audio tracks
                val muxerTrackIndex = muxer.addTrack(format)
                trackIndexMap[i] = muxerTrackIndex

                Timber.d("F43: Added track $i (mime=$mime) to muxer at index $muxerTrackIndex")
            }

            // Verify at least one video track exists
            val hasVideoTrack = trackIndexMap.isNotEmpty()
            if (!hasVideoTrack) {
                Timber.e("F40: No usable tracks found in video")
                return null
            }

            muxer.start()

            // F42: Process each track sample-by-sample
            val buffer = ByteBuffer.allocate(1024 * 1024) // 1MB buffer for streaming
            val bufferInfo = MediaCodec.BufferInfo()

            for ((extractorTrackIndex, muxerTrackIndex) in trackIndexMap) {
                extractor.selectTrack(extractorTrackIndex)

                // Seek to start position — use closest sync frame for accuracy
                extractor.seekTo(startTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                var samplesCopied = 0

                while (true) {
                    buffer.clear()
                    val sampleSize = extractor.readSampleData(buffer, 0)

                    if (sampleSize < 0) {
                        // End of stream for this track
                        break
                    }

                    val sampleTime = extractor.sampleTime

                    // Stop if we've passed the end time
                    if (sampleTime > endTimeUs) {
                        break
                    }

                    // Skip samples before start time
                    if (sampleTime < startTimeUs) {
                        extractor.advance()
                        continue
                    }

                    // Write sample to muxer
                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.flags = extractor.sampleFlags
                    bufferInfo.presentationTimeUs = sampleTime - startTimeUs

                    muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                    samplesCopied++

                    extractor.advance()
                }

                extractor.unselectTrack(extractorTrackIndex)
                Timber.d("F42: Copied $samplesCopied samples for track $extractorTrackIndex")
            }

            muxer.stop()
            Timber.d("Video trim complete: ${startMs}ms to ${endMs}ms → $outputPath")

            return outputPath

        } catch (e: Exception) {
            Timber.e(e, "F40: Video trim failed — codec may not be supported")
            // Clean up failed output file
            try {
                muxer?.release()
            } catch (_: Exception) {
            }
            try {
                outputPath.let { File(it).delete() }
            } catch (_: Exception) {
            }
            return null
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {
            }
            try {
                muxer?.release()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Get the duration of a video file in milliseconds.
     *
     * @param context Android context
     * @param uri URI of the video file
     * @return Duration in milliseconds, or 0 if unable to determine
     */
    fun getVideoDurationMs(context: Context, uri: Uri): Long {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            var maxDurationUs = 0L

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val duration = format.getLong(MediaFormat.KEY_DURATION)
                if (duration > maxDurationUs) {
                    maxDurationUs = duration
                }
            }

            maxDurationUs / 1000 // Convert microseconds to milliseconds
        } catch (e: Exception) {
            Timber.e(e, "Failed to get video duration")
            0L
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Validate trim range — enforce minimum 3s and maximum 30s segments.
     *
     * @param startMs Start position in milliseconds
     * @param endMs End position in milliseconds
     * @param durationMs Total video duration in milliseconds
     * @return Validated (startMs, endMs) pair
     */
    fun validateTrimRange(
        startMs: Long,
        endMs: Long,
        durationMs: Long
    ): Pair<Long, Long> {
        var start = startMs.coerceIn(0, durationMs)
        var end = endMs.coerceIn(0, durationMs)

        // Enforce minimum duration
        if (end - start < MIN_TRIM_DURATION_MS) {
            end = (start + MIN_TRIM_DURATION_MS).coerceAtMost(durationMs)
            if (end - start < MIN_TRIM_DURATION_MS) {
                start = (end - MIN_TRIM_DURATION_MS).coerceAtLeast(0)
            }
        }

        // Enforce maximum duration
        if (end - start > MAX_TRIM_DURATION_MS) {
            end = start + MAX_TRIM_DURATION_MS
        }

        return Pair(start, end)
    }
}
