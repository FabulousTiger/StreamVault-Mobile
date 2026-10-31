package com.streamvault.data.sync.foreground

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncCancellationRegistryTest {
    private var launches = 0

    @Before
    fun setUp() {
        SyncCancellationRegistry.resetForTest()
        launches = 0
    }

    @After
    fun tearDown() = SyncCancellationRegistry.resetForTest()

    @Test
    fun `tracked in-process work is visible only while it runs`() = runTest {
        val release = CompletableDeferred<Unit>()
        val result = async { SyncCancellationRegistry.track(7L) { release.await(); "done" } }
        runCurrent()

        assertThat(SyncCancellationRegistry.active.value).containsExactly(ActiveProviderSync(7L, inProcess = true))
        release.complete(Unit)

        assertThat(result.await()).isEqualTo("done")
        assertThat(SyncCancellationRegistry.active.value).isEmpty()
    }

    @Test
    fun `notification cancel stops the provider work but not its caller`() = runTest {
        var callerStillActive = false
        val outcome = async {
            try {
                SyncCancellationRegistry.track(7L) { awaitCancellation() }
            } catch (expected: CancellationException) {
                callerStillActive = isActive
                "stopped"
            }
        }
        runCurrent()
        val generation = SyncCancellationRegistry.userCancelGeneration()

        assertThat(SyncCancellationRegistry.cancel(7L)).isTrue()

        assertThat(outcome.await()).isEqualTo("stopped")
        assertThat(callerStillActive).isTrue()
        assertThat(SyncCancellationRegistry.userCancelGeneration()).isGreaterThan(generation)
        assertThat(SyncCancellationRegistry.active.value).isEmpty()
    }

    @Test
    fun `cancel leaves other providers running`() = runTest {
        val other = async { SyncCancellationRegistry.track(8L) { awaitCancellation() } }
        runCurrent()

        assertThat(SyncCancellationRegistry.cancel(7L)).isFalse()
        assertThat(other.isActive).isTrue()
        other.cancel()
    }

    @Test
    fun `worker-owned work is tracked without starting the keep-alive`() = runTest {
        SyncCancellationRegistry.installKeepAliveLauncher { launches++; true }
        val release = CompletableDeferred<Unit>()
        val work = async(SyncWorkerContext()) { SyncCancellationRegistry.track(7L) { release.await() } }
        runCurrent()

        assertThat(SyncCancellationRegistry.active.value).containsExactly(ActiveProviderSync(7L, inProcess = false))
        assertThat(launches).isEqualTo(0)
        assertThat(SyncCancellationRegistry.hasInProcessWork()).isFalse()
        release.complete(Unit)
        work.await()
    }

    @Test
    fun `keep-alive is launched once per busy period`() = runTest {
        SyncCancellationRegistry.installKeepAliveLauncher { launches++; true }
        val release = CompletableDeferred<Unit>()
        val first = async { SyncCancellationRegistry.track(7L) { release.await() } }
        val second = async { SyncCancellationRegistry.track(8L) { release.await() } }
        runCurrent()
        assertThat(launches).isEqualTo(1)
        assertThat(SyncCancellationRegistry.finishKeepAliveIfIdle()).isFalse()

        release.complete(Unit)
        first.await()
        second.await()
        assertThat(SyncCancellationRegistry.finishKeepAliveIfIdle()).isTrue()

        SyncCancellationRegistry.track(7L) { Unit }
        assertThat(launches).isEqualTo(2)
    }

    @Test
    fun `a keep-alive that could not be scheduled is retried by the next sync`() = runTest {
        SyncCancellationRegistry.installKeepAliveLauncher { launches++; false }

        SyncCancellationRegistry.track(7L) { Unit }
        SyncCancellationRegistry.track(7L) { Unit }

        assertThat(launches).isEqualTo(2)
    }

    @Test
    fun `a stopped keep-alive lets the running sync request a new one`() = runTest {
        SyncCancellationRegistry.installKeepAliveLauncher { launches++; true }
        val release = CompletableDeferred<Unit>()
        val first = async { SyncCancellationRegistry.track(7L) { release.await() } }
        runCurrent()

        SyncCancellationRegistry.keepAliveStopped()
        val second = async { SyncCancellationRegistry.track(8L) { release.await() } }
        runCurrent()

        assertThat(launches).isEqualTo(2)
        release.complete(Unit)
        first.await()
        second.await()
    }
}
