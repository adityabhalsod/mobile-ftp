package com.mobileftp.network

import com.mobileftp.domain.model.ConnectionProfile
import com.mobileftp.util.FtpClientException
import kotlinx.coroutines.runBlocking
import org.apache.ftpserver.FtpServer
import org.apache.ftpserver.FtpServerFactory
import org.apache.ftpserver.listener.ListenerFactory
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory
import org.apache.ftpserver.usermanager.impl.BaseUser
import org.apache.ftpserver.usermanager.impl.WritePermission
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.nio.file.Files
import kotlin.random.Random

/**
 * Runs the real chunk engine against an embedded server (issue #3: downloads were
 * reported as completed although data was missing).
 */
class ChunkTransferEngineTest {

    private lateinit var root: File
    private lateinit var outDir: File
    private lateinit var server: FtpServer
    private var port = 0

    @Before
    fun setUp() {
        root = Files.createTempDirectory("ftp-root").toFile()
        outDir = Files.createTempDirectory("ftp-out").toFile()
        port = ServerSocket(0).use { it.localPort }
        val factory = FtpServerFactory()
        factory.addListener("default", ListenerFactory().apply { port = this@ChunkTransferEngineTest.port }.createListener())
        val users = PropertiesUserManagerFactory().createUserManager()
        users.save(BaseUser().apply {
            name = "user"
            password = "pass"
            homeDirectory = root.absolutePath
            authorities = listOf(WritePermission())
        })
        factory.userManager = users
        server = factory.createServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.stop()
        root.deleteRecursively()
        outDir.deleteRecursively()
    }

    private fun <T> withEngine(chunks: Int, block: suspend (ChunkTransferEngine) -> T): T = runBlocking {
        val profile = ConnectionProfile(
            name = "test", host = "127.0.0.1", port = port,
            username = "user", password = "pass", chunkCount = chunks
        )
        val pool = FtpConnectionPool(profile, chunks)
        try {
            pool.warmUp(chunks)
            block(ChunkTransferEngine(pool, ThroughputMonitor(), AdaptiveBufferEngine(), Lz4CompressionEngine(true)))
        } finally {
            pool.closeAll()
        }
    }

    private fun assertDownloads(size: Int, chunks: Int, listedSize: Long = size.toLong()) {
        val data = Random(size).nextBytes(size)
        File(root, "f.bin").writeBytes(data)
        val target = File(outDir, "f-$size-$chunks.bin")

        val returned = withEngine(chunks) { it.download("/f.bin", target, listedSize, chunks) }

        assertEquals(size.toLong(), returned)
        assertArrayEquals(data, target.readBytes())
    }

    @Test
    fun smallFileIsDownloadedCompletely() = assertDownloads(size = 2_478, chunks = 2)

    @Test
    fun parallelChunksDownloadEveryByte() = assertDownloads(size = 5_000_003, chunks = 8)

    @Test
    fun missingSizeFromListingIsAskedFromServer() = assertDownloads(size = 3_000, chunks = 2, listedSize = 0L)

    @Test
    fun missingRemoteFileFailsInsteadOfCompleting() {
        try {
            withEngine(2) { it.download("/nope.bin", File(outDir, "nope.bin"), 2_478L, 2) }
            fail("Expected the download to fail")
        } catch (e: FtpClientException) {
            // expected: the server refused the file
        }
    }

    @Test
    fun fileShorterThanListedSizeFails() {
        File(root, "short.bin").writeBytes(ByteArray(1_000))
        try {
            withEngine(2) { it.download("/short.bin", File(outDir, "short.bin"), 2_000L, 2) }
            fail("Expected the download to fail")
        } catch (e: FtpClientException) {
            // expected: only 1000 of 2000 bytes arrived
        }
    }

    @Test
    fun uploadSendsEveryByte() {
        val data = Random(7).nextBytes(10_000)
        val source = File(outDir, "up.bin").apply { writeBytes(data) }

        val sent = withEngine(1) { it.upload("/up.bin", source, 1) }

        assertEquals(data.size.toLong(), sent)
        assertArrayEquals(data, File(root, "up.bin").readBytes())
    }
}
