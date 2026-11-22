package com.mobileftp.data.repository

import org.apache.ftpserver.filesystem.nativefs.NativeFileSystemFactory
import org.apache.ftpserver.ftplet.FileSystemFactory
import org.apache.ftpserver.ftplet.FileSystemView
import org.apache.ftpserver.ftplet.FtpFile
import org.apache.ftpserver.ftplet.User
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Wraps the native file system so every byte a client uploads or downloads is
 * reported to [tracker] while the transfer is running.
 *
 * Apache FtpServer only tells ftplets when a transfer starts and ends, so
 * without this the dashboard could only guess at the size afterwards and the
 * speed stayed at 0 B/s for the whole transfer.
 */
class TrackingFileSystemFactory(
    private val tracker: ServerActivityTracker,
    private val delegate: FileSystemFactory = NativeFileSystemFactory()
) : FileSystemFactory {
    override fun createFileSystemView(user: User): FileSystemView =
        TrackingFileSystemView(delegate.createFileSystemView(user), tracker)
}

/** One view per FTP session; [sessionId] is filled in by [ServerStatsFtplet] at login. */
class TrackingFileSystemView(
    private val delegate: FileSystemView,
    private val tracker: ServerActivityTracker
) : FileSystemView {
    @Volatile
    var sessionId: String? = null

    internal fun report(delta: Long) = tracker.addBytes(sessionId, delta)

    private fun wrap(file: FtpFile?): FtpFile? = file?.let { TrackingFtpFile(it, this) }

    override fun getHomeDirectory(): FtpFile? = wrap(delegate.homeDirectory)
    override fun getWorkingDirectory(): FtpFile? = wrap(delegate.workingDirectory)
    override fun changeWorkingDirectory(dir: String?): Boolean = delegate.changeWorkingDirectory(dir)
    override fun getFile(file: String?): FtpFile? = wrap(delegate.getFile(file))
    override fun isRandomAccessible(): Boolean = delegate.isRandomAccessible
    override fun dispose() = delegate.dispose()
}

internal class TrackingFtpFile(
    val delegate: FtpFile,
    private val view: TrackingFileSystemView
) : FtpFile by delegate {

    override fun createInputStream(offset: Long): InputStream =
        CountingInputStream(delegate.createInputStream(offset), view::report)

    override fun createOutputStream(offset: Long): OutputStream =
        CountingOutputStream(delegate.createOutputStream(offset), view::report)

    override fun listFiles(): List<FtpFile>? = delegate.listFiles()?.map { TrackingFtpFile(it, view) }

    // NativeFtpFile.move() casts its argument to NativeFtpFile, so unwrap first.
    override fun move(destination: FtpFile): Boolean =
        delegate.move((destination as? TrackingFtpFile)?.delegate ?: destination)

    override fun equals(other: Any?): Boolean =
        delegate == ((other as? TrackingFtpFile)?.delegate ?: other)

    override fun hashCode(): Int = delegate.hashCode()
    override fun toString(): String = delegate.toString()
}

internal class CountingInputStream(
    input: InputStream,
    private val onBytes: (Long) -> Unit
) : FilterInputStream(input) {
    override fun read(): Int = super.read().also { if (it >= 0) onBytes(1L) }

    override fun read(b: ByteArray, off: Int, len: Int): Int =
        `in`.read(b, off, len).also { if (it > 0) onBytes(it.toLong()) }

    override fun skip(n: Long): Long = `in`.skip(n)
}

internal class CountingOutputStream(
    output: OutputStream,
    private val onBytes: (Long) -> Unit
) : FilterOutputStream(output) {
    override fun write(b: Int) {
        out.write(b)
        onBytes(1L)
    }

    // FilterOutputStream's default writes one byte at a time; go straight to the delegate.
    override fun write(b: ByteArray, off: Int, len: Int) {
        out.write(b, off, len)
        if (len > 0) onBytes(len.toLong())
    }
}
