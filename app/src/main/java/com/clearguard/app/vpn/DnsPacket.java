package com.clearguard.app.vpn;

import java.util.Arrays;

final class DnsPacket {
    private static final int IPV4_HEADER_LENGTH = 20;
    private static final int IPV6_HEADER_LENGTH = 40;
    private static final int UDP_HEADER_LENGTH = 8;
    private static final int UDP_PROTOCOL = 17;

    private DnsPacket() {
    }

    static Request parse(byte[] packet, int length) {
        if (length < 1) {
            return null;
        }
        int version = (packet[0] >>> 4) & 0x0F;
        if (version == 4) {
            return parseIpv4(packet, length);
        }
        if (version == 6) {
            return parseIpv6(packet, length);
        }
        return null;
    }

    private static Request parseIpv4(byte[] packet, int length) {
        if (length < IPV4_HEADER_LENGTH + UDP_HEADER_LENGTH) {
            return null;
        }

        int headerLength = (packet[0] & 0x0F) * 4;
        if (headerLength < IPV4_HEADER_LENGTH || length < headerLength + UDP_HEADER_LENGTH) {
            return null;
        }

        int totalLength = unsignedShort(packet, 2);
        if (totalLength <= 0 || totalLength > length) {
            totalLength = length;
        }
        if ((packet[9] & 0xFF) != UDP_PROTOCOL) {
            return null;
        }

        int udpOffset = headerLength;
        int sourcePort = unsignedShort(packet, udpOffset);
        int destinationPort = unsignedShort(packet, udpOffset + 2);
        int udpLength = unsignedShort(packet, udpOffset + 4);
        if (destinationPort != 53 || udpLength < UDP_HEADER_LENGTH) {
            return null;
        }
        if (udpOffset + udpLength > totalLength) {
            return null;
        }

        int dnsOffset = udpOffset + UDP_HEADER_LENGTH;
        int dnsLength = udpLength - UDP_HEADER_LENGTH;
        return new Request(
                4,
                Arrays.copyOfRange(packet, 12, 16),
                Arrays.copyOfRange(packet, 16, 20),
                sourcePort,
                Arrays.copyOfRange(packet, dnsOffset, dnsOffset + dnsLength)
        );
    }

    private static Request parseIpv6(byte[] packet, int length) {
        if (length < IPV6_HEADER_LENGTH + UDP_HEADER_LENGTH) {
            return null;
        }

        // Extension headers are not expected on plain DNS queries; only handle
        // packets where UDP follows the fixed header directly.
        if ((packet[6] & 0xFF) != UDP_PROTOCOL) {
            return null;
        }

        int payloadLength = unsignedShort(packet, 4);
        if (payloadLength < UDP_HEADER_LENGTH || IPV6_HEADER_LENGTH + payloadLength > length) {
            return null;
        }

        int udpOffset = IPV6_HEADER_LENGTH;
        int sourcePort = unsignedShort(packet, udpOffset);
        int destinationPort = unsignedShort(packet, udpOffset + 2);
        int udpLength = unsignedShort(packet, udpOffset + 4);
        if (destinationPort != 53 || udpLength < UDP_HEADER_LENGTH || udpLength > payloadLength) {
            return null;
        }

        int dnsOffset = udpOffset + UDP_HEADER_LENGTH;
        int dnsLength = udpLength - UDP_HEADER_LENGTH;
        return new Request(
                6,
                Arrays.copyOfRange(packet, 8, 24),
                Arrays.copyOfRange(packet, 24, 40),
                sourcePort,
                Arrays.copyOfRange(packet, dnsOffset, dnsOffset + dnsLength)
        );
    }

    static byte[] buildUdpResponse(Request request, byte[] dnsPayload) {
        if (request.ipVersion == 6) {
            return buildIpv6UdpResponse(request, dnsPayload);
        }
        return buildIpv4UdpResponse(request, dnsPayload);
    }

    private static byte[] buildIpv4UdpResponse(Request request, byte[] dnsPayload) {
        int totalLength = IPV4_HEADER_LENGTH + UDP_HEADER_LENGTH + dnsPayload.length;
        byte[] packet = new byte[totalLength];

        packet[0] = 0x45;
        packet[1] = 0;
        putShort(packet, 2, totalLength);
        putShort(packet, 4, 0);
        putShort(packet, 6, 0);
        packet[8] = 64;
        packet[9] = UDP_PROTOCOL;
        System.arraycopy(request.destinationAddress, 0, packet, 12, 4);
        System.arraycopy(request.sourceAddress, 0, packet, 16, 4);
        putShort(packet, 10, checksum(packet, 0, IPV4_HEADER_LENGTH));

        int udpOffset = IPV4_HEADER_LENGTH;
        putShort(packet, udpOffset, 53);
        putShort(packet, udpOffset + 2, request.sourcePort);
        putShort(packet, udpOffset + 4, UDP_HEADER_LENGTH + dnsPayload.length);
        putShort(packet, udpOffset + 6, 0);
        System.arraycopy(dnsPayload, 0, packet, udpOffset + UDP_HEADER_LENGTH, dnsPayload.length);
        return packet;
    }

    private static byte[] buildIpv6UdpResponse(Request request, byte[] dnsPayload) {
        int udpLength = UDP_HEADER_LENGTH + dnsPayload.length;
        byte[] packet = new byte[IPV6_HEADER_LENGTH + udpLength];

        packet[0] = 0x60;
        putShort(packet, 4, udpLength);
        packet[6] = UDP_PROTOCOL;
        packet[7] = 64;
        System.arraycopy(request.destinationAddress, 0, packet, 8, 16);
        System.arraycopy(request.sourceAddress, 0, packet, 24, 16);

        int udpOffset = IPV6_HEADER_LENGTH;
        putShort(packet, udpOffset, 53);
        putShort(packet, udpOffset + 2, request.sourcePort);
        putShort(packet, udpOffset + 4, udpLength);
        putShort(packet, udpOffset + 6, 0);
        System.arraycopy(dnsPayload, 0, packet, udpOffset + UDP_HEADER_LENGTH, dnsPayload.length);

        // Unlike IPv4, the UDP checksum is mandatory over IPv6 (RFC 8200 §8.1);
        // the stack drops packets that carry zero.
        putShort(packet, udpOffset + 6, udpChecksumIpv6(packet, udpOffset, udpLength));
        return packet;
    }

    private static int udpChecksumIpv6(byte[] packet, int udpOffset, int udpLength) {
        long sum = 0;
        sum = addWords(packet, 8, 32, sum); // pseudo-header: source + destination addresses
        sum += udpLength;                   // pseudo-header: upper-layer packet length
        sum += UDP_PROTOCOL;                // pseudo-header: next header
        sum = addWords(packet, udpOffset, udpLength, sum);
        while ((sum & 0xFFFF0000L) != 0) {
            sum = (sum & 0xFFFFL) + (sum >>> 16);
        }
        int checksum = (int) (~sum) & 0xFFFF;
        // RFC 768: a computed checksum of zero is transmitted as all ones.
        return checksum == 0 ? 0xFFFF : checksum;
    }

    private static long addWords(byte[] bytes, int offset, int length, long sum) {
        for (int i = offset; i < offset + length; i += 2) {
            int high = bytes[i] & 0xFF;
            int low = (i + 1 < offset + length) ? (bytes[i + 1] & 0xFF) : 0;
            sum += (high << 8) | low;
        }
        return sum;
    }

    private static int unsignedShort(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }

    private static void putShort(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) ((value >>> 8) & 0xFF);
        bytes[offset + 1] = (byte) (value & 0xFF);
    }

    private static int checksum(byte[] bytes, int offset, int length) {
        long sum = 0;
        for (int i = offset; i < offset + length; i += 2) {
            int high = bytes[i] & 0xFF;
            int low = (i + 1 < offset + length) ? (bytes[i + 1] & 0xFF) : 0;
            sum += (high << 8) | low;
            while ((sum & 0xFFFF0000L) != 0) {
                sum = (sum & 0xFFFFL) + (sum >>> 16);
            }
        }
        return (int) (~sum) & 0xFFFF;
    }

    static final class Request {
        final int ipVersion;
        final byte[] sourceAddress;
        final byte[] destinationAddress;
        final int sourcePort;
        final byte[] dnsPayload;

        Request(int ipVersion, byte[] sourceAddress, byte[] destinationAddress, int sourcePort, byte[] dnsPayload) {
            this.ipVersion = ipVersion;
            this.sourceAddress = sourceAddress;
            this.destinationAddress = destinationAddress;
            this.sourcePort = sourcePort;
            this.dnsPayload = dnsPayload;
        }
    }
}
