package com.naaammme.bbspace.infra.grpc

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * gRPC Frame 编解码器
 * B站使用 gRPC over HTTP/1.1，需要手动构建 5 字节 frame 头部
 *
 * 数据包格式: [1字节压缩标志][4字节数据长度(big-endian)][实际数据]
 *
 * 压缩策略（B站逆向 k21.a / k21.b）:
 * - protobuf > 1000 字节 → gzip 压缩（标志=1）
 * - protobuf ≤ 1000 字节 → 不压缩（标志=0）
 */
object GrpcFrameCodec {

    private const val COMPRESSION_THRESHOLD = 1000

    data class EncodeResult(
        val frame: ByteArray,
        val compressed: Boolean
    )

    /**
     * 编码：根据 payload 大小决定是否 gzip 压缩
     * - > 1000 字节：gzip 压缩，标志=1
     * - ≤ 1000 字节：不压缩，标志=0
     */
    fun encode(protobufBytes: ByteArray): EncodeResult {
        return if (protobufBytes.size > COMPRESSION_THRESHOLD) {
            encodeCompressed(protobufBytes)
        } else {
            encodeRaw(protobufBytes)
        }
    }

    /**
     * gzip 压缩模式 (k21.a)
     */
    private fun encodeCompressed(protobufBytes: ByteArray): EncodeResult {
        val compressed = gzipCompress(protobufBytes)
        val frame = ByteArrayOutputStream(5 + compressed.size)
        frame.write(1) // 压缩标志 = 1
        frame.write(toLengthBytes(compressed.size))
        frame.write(compressed)
        return EncodeResult(frame.toByteArray(), compressed = true)
    }

    /**
     * 无压缩模式 (k21.b)
     */
    private fun encodeRaw(protobufBytes: ByteArray): EncodeResult {
        val frame = ByteArrayOutputStream(5 + protobufBytes.size)
        frame.write(0) // 压缩标志 = 0
        frame.write(toLengthBytes(protobufBytes.size))
        frame.write(protobufBytes)
        return EncodeResult(frame.toByteArray(), compressed = false)
    }

    /**
     * 解码：检查压缩标志
     * - 标志=0 → 原始数据
     * - 标志!=0 且响应头 grpc-encoding=gzip → gzip 解压
     */
    fun decode(responseBytes: ByteArray, grpcEncoding: String? = null): ByteArray {
        if (responseBytes.size < 5) return ByteArray(0)

        val compressionFlag = responseBytes[0].toInt()
        val payload = responseBytes.copyOfRange(5, responseBytes.size)

        return if (compressionFlag == 0) {
            payload
        } else if (grpcEncoding == "gzip" || compressionFlag == 1) {
            GZIPInputStream(ByteArrayInputStream(payload)).use { it.readBytes() }
        } else {
            payload
        }
    }

    private fun gzipCompress(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream(data.size)
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    private fun toLengthBytes(length: Int): ByteArray {
        return byteArrayOf(
            ((length shr 24) and 0xFF).toByte(),
            ((length shr 16) and 0xFF).toByte(),
            ((length shr 8) and 0xFF).toByte(),
            (length and 0xFF).toByte()
        )
    }
}
