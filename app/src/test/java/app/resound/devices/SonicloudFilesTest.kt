/**
 * [INPUT]: SonicloudFiles、DeviceLink 假通道与公开 BLE 文件帧格式。
 * [OUTPUT]: 续传偏移、取消清理和删除快照的协议回归。
 * [POS]: 文件插件边界测试；持久化与安全保存仍由 DeviceSync120Test 覆盖。
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package app.resound.devices

import app.resound.devices.plugins.sonicloud.FileFrames
import app.resound.devices.plugins.sonicloud.SonicloudFiles
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SonicloudFilesTest {
    private val file = DeviceFile("note20260710-162938.", "note20260710-162938.wav",
        123, 0, 2000, FileFormat.Wav)

    private class Link : DeviceLink {
        override val mtu = 247
        val notifications = MutableSharedFlow<ByteArray>()
        val writes = mutableListOf<ByteArray>()
        private val responses = FileFrames()
        var respond: suspend Link.(Int) -> Unit = {}
        override suspend fun read(service: String, characteristic: String) = byteArrayOf()
        override fun notifications(service: String, characteristic: String) = notifications
        override suspend fun write(service: String, characteristic: String, bytes: ByteArray, withResponse: Boolean) {
            assertEquals(SonicloudFiles.SERVICE, service)
            assertEquals(SonicloudFiles.WRITE, characteristic)
            assertFalse(withResponse)
            writes += bytes
            respond(bytes[7].toInt() and 255)
        }
        suspend fun reply(cmd: Int, body: ByteArray = byteArrayOf()) {
            notifications.emit(responses.command(cmd, body))
        }
        fun commands() = writes.map { it[7].toInt() and 255 }
    }

    private fun entry(size: Int = 123, name: String = "note20260710-162938.") =
        ByteBuffer.allocate(28).order(ByteOrder.BIG_ENDIAN).putInt(2).putInt(size)
            .put(name.toByteArray().copyOf(20)).array()

    private fun listing(vararg entries: ByteArray) =
        ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(entries.size).array() +
            entries.fold(byteArrayOf()) { result, bytes -> result + bytes }

    private fun listLink(raw: ByteArray = entry()) = Link().apply {
        respond = { cmd ->
            if (cmd == 0) { reply(1, listing(raw)); reply(18, byteArrayOf(0)) }
            if (cmd == 8) reply(13, byteArrayOf(0))
        }
    }

    @Test fun resumesWithUnsignedLittleEndianOffsetAndOneWholeWrite() = runBlocking {
        val link = Link().apply {
            respond = { cmd -> if (cmd == 2) {
                reply(3); reply(4, byteArrayOf(7, 8)); reply(5, byteArrayOf(0))
            } }
        }
        val plugin = SonicloudFiles()
        assertTrue(plugin.caps.resume)
        assertArrayEquals(byteArrayOf(7, 8), plugin.download(link, file, 0x89abcdefL).toList().single())
        assertEquals(listOf(2), link.commands())
        assertEquals(36, link.writes.single().size)
        assertArrayEquals(byteArrayOf(0xef.toByte(), 0xcd.toByte(), 0xab.toByte(), 0x89.toByte()),
            link.writes.single().copyOfRange(8, 12))
        assertArrayEquals(file.name.toByteArray().copyOf(24), link.writes.single().copyOfRange(12, 36))
    }

    @Test fun invalidOffsetsNeverReachDevice() = runBlocking {
        val link = Link()
        for (offset in listOf(-1L, 0x100000000L)) {
            try { SonicloudFiles().download(link, file, offset).collect(); fail("应拒绝无效偏移") }
            catch (_: IllegalArgumentException) {}
        }
        assertTrue(link.writes.isEmpty())
    }

    @Test(timeout = 3000) fun cancellationSendsAbortAndReleasesSubscription() = runBlocking {
        val received = CompletableDeferred<Unit>()
        val link = Link().apply {
            respond = { cmd -> if (cmd == 2) { reply(3); reply(4, byteArrayOf(1)) } }
        }
        val task = launch { SonicloudFiles().download(link, file, 0).collect { received.complete(Unit) } }
        received.await()
        task.cancelAndJoin()
        assertEquals(listOf(2, 7), link.commands())
        assertEquals(0, link.notifications.subscriptionCount.value)
        assertTrue(task.isCancelled)
    }

    @Test fun failedTransferAbortsAndPreservesOriginalFailure() = runBlocking {
        val link = Link().apply {
            respond = { cmd ->
                if (cmd == 2) { reply(3); reply(5, byteArrayOf(3)) }
                if (cmd == 7) error("终止写入失败")
            }
        }
        try { SonicloudFiles().download(link, file, 0).collect(); fail("应保留传输失败") }
        catch (e: IllegalStateException) { assertEquals("设备下载失败", e.message) }
        assertEquals(listOf(2, 7), link.commands())
        assertEquals(0, link.notifications.subscriptionCount.value)
    }

    @Test fun deletionUsesExactDirectoryBytesAndConsumesSnapshot() = runBlocking {
        val raw = entry()
        val link = listLink(raw)
        val plugin = SonicloudFiles()
        val row = plugin.list(link).single()
        plugin.delete(link, row)
        assertArrayEquals(raw, link.writes.last().copyOfRange(8, 36))
        try { plugin.delete(link, row); fail("不得重复删除") }
        catch (_: IllegalArgumentException) {}
        assertEquals(listOf(0, 8), link.commands())
    }

    @Test fun changedFileOrDifferentDeviceCannotUseSnapshot() = runBlocking {
        val plugin = SonicloudFiles()
        val link = listLink()
        val row = plugin.list(link).single()
        try { plugin.delete(link, row.copy(bytes = 456)); fail("不得删除变化后的文件") }
        catch (_: IllegalArgumentException) {}
        val other = listLink()
        try { plugin.delete(other, row); fail("不得跨设备删除") }
        catch (_: IllegalStateException) {}
        assertEquals(listOf(0), link.commands())
        assertTrue(other.writes.isEmpty())
    }

    @Test fun failedRefreshInvalidatesOldDeletionSnapshot() = runBlocking {
        val plugin = SonicloudFiles()
        val link = listLink()
        val row = plugin.list(link).single()
        link.respond = { cmd -> if (cmd == 0) error("目录请求失败") }
        try { plugin.list(link); fail("目录读取应失败") } catch (_: IllegalStateException) {}
        try { plugin.delete(link, row); fail("不得使用旧快照") } catch (_: IllegalStateException) {}
        assertEquals(listOf(0, 0), link.commands())
    }

    @Test fun malformedOrAmbiguousDirectoryDoesNotAuthorizeDeletion() = runBlocking {
        val invalid = listOf(listing(entry()) + byteArrayOf(0), listing(entry(), entry(456)), listing(entry(name = "")))
        for (body in invalid) {
            val plugin = SonicloudFiles()
            val link = Link().apply { respond = { cmd -> if (cmd == 0) reply(1, body) } }
            try { plugin.list(link); fail("目录应拒绝") } catch (_: IllegalArgumentException) {}
            try { plugin.delete(link, file); fail("不得发布部分目录") } catch (_: IllegalStateException) {}
            assertEquals(listOf(0), link.commands())
        }
    }
}
