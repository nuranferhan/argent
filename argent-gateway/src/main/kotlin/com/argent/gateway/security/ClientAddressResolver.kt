package com.argent.gateway.security

import java.net.InetAddress

/** Uses forwarded addresses only when the immediate peer belongs to an explicitly trusted proxy range. */
class ClientAddressResolver(private val trustedProxyCidrs: List<String>) {
    private val trustedRanges = trustedProxyCidrs.mapNotNull(::parseRange)

    fun resolve(remoteAddress: String, forwardedFor: String?): String {
        val remote = parseAddress(remoteAddress) ?: return remoteAddress
        if (trustedRanges.none { it.contains(remote.address) }) return remoteAddress
        val chain = forwardedFor.orEmpty().split(',').mapNotNull { parseAddress(it.trim()) }
        if (chain.isEmpty()) return remoteAddress
        var candidate = remote.address
        for (address in chain.asReversed()) {
            if (trustedRanges.none { it.contains(candidate) }) break
            candidate = address.address
        }
        return InetAddress.getByAddress(candidate).hostAddress
    }

    private data class Cidr(val network: ByteArray, val prefix: Int) {
        fun contains(address: ByteArray): Boolean {
            if (network.size != address.size) return false
            val wholeBytes = prefix / 8
            val remainingBits = prefix % 8
            for (i in 0 until wholeBytes) if (network[i] != address[i]) return false
            if (remainingBits == 0) return true
            val mask = (0xff shl (8 - remainingBits)) and 0xff
            return (network[wholeBytes].toInt() and mask) == (address[wholeBytes].toInt() and mask)
        }
    }

    private fun parseAddress(value: String): InetAddress? {
        val clean = value.removePrefix("[").substringBefore(']').ifBlank { value }
        if (clean.isBlank() || clean.any { !(it.isDigit() || it in "abcdefABCDEF:.") }) return null
        return runCatching { InetAddress.getByName(clean) }.getOrNull()
    }

    private fun parseRange(value: String): Cidr? {
        val parts = value.trim().split('/', limit = 2)
        val address = parseAddress(parts[0]) ?: return null
        val prefix = parts.getOrNull(1)?.toIntOrNull() ?: address.address.size * 8
        if (prefix !in 0..address.address.size * 8) return null
        return Cidr(address.address, prefix)
    }
}
