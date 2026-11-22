package com.mobileftp.data.repository

import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.ftpserver.FtpServer
import org.apache.ftpserver.FtpServerFactory
import org.apache.ftpserver.ftplet.Ftplet
import org.apache.ftpserver.listener.ListenerFactory
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory
import org.apache.ftpserver.usermanager.impl.BaseUser
import org.apache.ftpserver.usermanager.impl.WritePermission
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.ServerSocket
import java.nio.file.Files

/**
 * Runs the embedded server the way FtpServerRepository wires it, with a real
 * FTP client, and checks that the dashboard tracker sees the activity (issue #4).
 */
class ServerActivityTrackingTest {

    private lateinit var root: File
    private lateinit var tracker: ServerActivityTracker
    private lateinit var server: FtpServer
    private var port = 0

    @Before
    fun setUp() {
        root = Files.createTempDirectory("ftp-root").toFile()
        tracker = ServerActivityTracker()
        port = ServerSocket(0).use { it.localPort }

        val factory = FtpServerFactory()
        factory.addListener("default", ListenerFactory().apply { port = this@ServerActivityTrackingTest.port }.createListener())
        val users = PropertiesUserManagerFactory().createUserManager()
        users.save(BaseUser().apply {
            name = "quest"
            password = "secret"
            homeDirectory = root.absolutePath
            authorities = listOf(WritePermission())
        })
        factory.userManager = users
        factory.ftplets = mutableMapOf<String, Ftplet>("stats" to ServerStatsFtplet(tracker))
        factory.fileSystem = TrackingFileSystemFactory(tracker)
        server = factory.createServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.stop()
        root.deleteRecursively()
    }

    /**
     * The server sends its final reply before running afterCommand, so the
     * client can get ahead of the tracker by a few milliseconds.
     */
    private fun awaitSnapshot(condition: (ServerActivitySnapshot) -> Boolean): ServerActivitySnapshot {
        val deadline = System.currentTimeMillis() + 2_000L
        var snap = tracker.snapshot()
        while (!condition(snap) && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
            snap = tracker.snapshot()
        }
        return snap
    }

    private fun connect(): FTPClient = FTPClient().apply {
        connect("127.0.0.1", port)
        assertTrue(login("quest", "secret"))
        enterLocalPassiveMode()
        setFileType(FTP.BINARY_FILE_TYPE)
    }

    @Test
    fun connectedClientShowsLoginAndLastCommand() {
        val client = connect()
        client.listFiles()
        val snap = awaitSnapshot { it.clients.singleOrNull()?.lastCommand?.startsWith("LIST") == true }
        assertEquals(1, snap.clients.size)
        val c = snap.clients.single()
        assertEquals("quest", c.username)
        assertEquals("127.0.0.1", c.ip)
        assertTrue("last command was ${c.lastCommand}", c.lastCommand!!.startsWith("LIST"))
        client.logout(); client.disconnect()
    }

    @Test
    fun bytesAreCountedWhileUploadIsRunning() {
        val client = connect()
        val size = 512 * 1024
        var midTransfer: ServerActivitySnapshot? = null
        // Slow stream: take a snapshot after half the data has gone out.
        val payload = object : InputStream() {
            var sent = 0
            override fun read(): Int = throw UnsupportedOperationException()
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (sent >= size) return -1
                if (sent >= size / 2 && midTransfer == null) {
                    Thread.sleep(300)
                    midTransfer = tracker.snapshot()
                }
                val n = minOf(len, 8192, size - sent)
                sent += n
                return n
            }
        }
        assertTrue(client.storeFile("big.bin", payload))

        val mid = midTransfer!!
        val live = mid.clients.single().currentTransfer
        assertNotNull("upload should be visible while running", live)
        assertEquals(ServerTransferStatus.ACTIVE, live!!.status)
        assertTrue("live bytes ${live.bytes} should be > 0", live.bytes > 0)
        assertTrue(mid.totalBytes > 0)

        val done = awaitSnapshot { snap -> snap.transfers.none { it.status == ServerTransferStatus.ACTIVE } }
        val t = done.transfers.single()
        assertEquals(ServerTransferStatus.COMPLETED, t.status)
        assertEquals(ServerTransferDirection.UPLOAD, t.direction)
        assertEquals("big.bin", t.fileName)
        assertEquals(size.toLong(), t.bytes)
        assertEquals(size.toLong(), done.totalBytes)
        assertNull(done.clients.single().currentTransfer)
        client.logout(); client.disconnect()
    }

    @Test
    fun downloadIsCountedAndFailedTransferIsReported() {
        File(root, "sub").mkdirs()
        File(root, "sub/movie.mp4").writeBytes(ByteArray(100_000) { it.toByte() })
        val client = connect()
        assertTrue(client.changeWorkingDirectory("sub"))
        val out = ByteArrayOutputStream()
        assertTrue(client.retrieveFile("movie.mp4", out))
        assertEquals(100_000, out.size())
        // Missing file: the client sees an error and so should the dashboard.
        client.retrieveFile("missing.mp4", ByteArrayOutputStream())

        val snap = awaitSnapshot { s -> s.transfers.size == 2 && s.transfers.none { it.status == ServerTransferStatus.ACTIVE } }
        assertEquals(2, snap.transfers.size)
        val (failed, ok) = snap.transfers
        assertEquals(ServerTransferStatus.FAILED, failed.status)
        assertTrue("error was ${failed.error}", failed.error!!.startsWith("550"))
        assertEquals(ServerTransferStatus.COMPLETED, ok.status)
        assertEquals(ServerTransferDirection.DOWNLOAD, ok.direction)
        assertEquals(100_000L, ok.bytes)
        assertEquals(100_000L, snap.totalBytes)
        client.logout(); client.disconnect()
    }

    @Test
    fun wrappedFileSystemStillSupportsRenameDeleteAndListing() {
        val client = connect()
        assertTrue(client.storeFile("a.txt", ByteArrayInputStream("hello".toByteArray())))
        assertTrue(client.rename("a.txt", "b.txt"))
        assertEquals(listOf("b.txt"), client.listNames().toList())
        assertTrue(client.makeDirectory("dir"))
        assertTrue(client.deleteFile("b.txt"))
        assertEquals(listOf("dir"), client.listFiles().map { it.name })
        client.logout(); client.disconnect()
    }

    @Test
    fun disconnectRemovesClient() {
        val client = connect()
        client.logout(); client.disconnect()
        assertTrue(awaitSnapshot { it.clients.isEmpty() }.clients.isEmpty())
    }
}
