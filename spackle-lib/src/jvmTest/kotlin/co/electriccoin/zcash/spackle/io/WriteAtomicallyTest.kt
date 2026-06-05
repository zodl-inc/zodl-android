package co.electriccoin.zcash.spackle.io

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WriteAtomicallyTest {
    // Putting in the build directory so that it doesn't show up as dirty in git
    private fun newFile() = File(File("build"), "atomic_file_test-${UUID.randomUUID()}")

    @Test
    fun `file has temp name`() =
        runTest {
            val testFile = newFile()
            try {
                testFile.writeAtomically {
                    it.writeText("test text")
                    assertNotEquals(testFile.name, it.name)
                }
            } finally {
                testFile.delete()
            }
        }

    @Test
    fun `temp file deleted`() =
        runTest {
            val testFile = newFile()
            try {
                var tempFile: File? = null

                testFile.writeAtomically {
                    tempFile = it
                    it.writeText("test text")
                }

                assertNotNull(tempFile)
                assertFalse(tempFile.exists())
            } finally {
                testFile.delete()
            }
        }

    @Test
    fun `file is renamed`() =
        runTest {
            val testFile = newFile()
            try {
                testFile.writeAtomically {
                    it.writeText("test text")
                }

                assertTrue(testFile.exists())
            } finally {
                testFile.delete()
            }
        }
}
