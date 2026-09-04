package co.electriccoin.zcash.ui.common.repository

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/**
 * The rate limit the automatic-selection lane applies to the SDK benchmark (MOB-1832), since the app
 * foreground signal it is driven by has no throttle of its own.
 */
class EvaluationIntervalTest {
    private val interval = 10.minutes

    @Test
    fun theFirstEvaluationIsAllowed() {
        assertTrue(EvaluationInterval(interval, TestTimeSource()).hasElapsed())
    }

    @Test
    fun anEvaluationInsideTheIntervalIsSkipped() {
        val timeSource = TestTimeSource()
        val evaluationInterval = EvaluationInterval(interval, timeSource)

        evaluationInterval.markCompleted()
        timeSource += interval - 1.seconds

        assertFalse(evaluationInterval.hasElapsed())
    }

    @Test
    fun anEvaluationOnceTheIntervalElapsedIsAllowed() {
        val timeSource = TestTimeSource()
        val evaluationInterval = EvaluationInterval(interval, timeSource)

        evaluationInterval.markCompleted()
        timeSource += interval

        assertTrue(evaluationInterval.hasElapsed())
    }

    @Test
    fun everyCompletedEvaluationRestartsTheInterval() {
        val timeSource = TestTimeSource()
        val evaluationInterval = EvaluationInterval(interval, timeSource)

        evaluationInterval.markCompleted()
        timeSource += interval
        evaluationInterval.markCompleted()
        timeSource += interval - 1.seconds

        assertFalse(evaluationInterval.hasElapsed())
    }
}
