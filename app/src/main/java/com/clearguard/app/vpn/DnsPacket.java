package com.clearguard.app.vpn;

import java.util.Arrays;

final class DnsPacket {
    private static final int IPV4_HEADER_LENGTH = 20;
    private static final int UDP_HEADER_LENGTH = 8;
    private static final int UDP_PROTOCOL = 17;

    private DnsPacket() {
    }

    static Request parse(byte[] packet, int length) {
        if (length < IPV4_HEADER_LENGTH + UDP_HEADER_LENGTH) {
            return null;
        }

        int version = (packet[0] >>> 4) & 0x0F;
        int headerLength = (packet[0] & 0x0F) * 4;
        if (version != 4 || headerLength < IPV4_HEADER_LENGTH || length < headerLength + UDP_HEADER_LENGTH) {
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
                Arrays.copyOfRange(packet, 12, 16),
                Arrays.copyOfRange(packet, 16, 20),
                sourcePort,
                Arrays.copyOfRange(packet, dnsOffset, dnsOffset + dnsLength)
        );
    }

    static byte[] buildUdpResponse(Request request, byte[] dnsPayload) {
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
        final byte[] sourceAddress;
        final byte[] destinationAddress;
        final int sourcePort;
        final byte[] dnsPayload;

        Request(byte[] sourceAddress, byte[] destinationAddress, int sourcePort, byte[] dnsPayload) {
            this.sourceAddress = sourceAddress;
            this.destinationAddress = destinationAddress;
            this.sourcePort = sourcePort;
            this.dnsPayload = dnsPayload;
        }
    }
}
