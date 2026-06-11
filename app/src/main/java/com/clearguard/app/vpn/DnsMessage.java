package com.clearguard.app.vpn;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

final class DnsMessage {
    static final int TYPE_A = 1;
    static final int TYPE_AAAA = 28;

    private DnsMessage() {
    }

    static Question parseQuestion(byte[] dnsPayload, int length) throws IOException {
        if (length < 12) {
            throw new IOException("DNS payload too short");
        }

        int qdCount = unsignedShort(dnsPayload, 4);
        if (qdCount < 1) {
            throw new IOException("DNS query has no question");
        }

        int offset = 12;
        StringBuilder name = new StringBuilder();
        while (offset < length) {
            int labelLength = dnsPayload[offset] & 0xFF;
            offset++;
            if (labelLength == 0) {
                break;
            }
            if ((labelLength & 0xC0) != 0) {
                throw new IOException("Compressed query names are not supported");
            }
            if (labelLength > 63 || offset + labelLength > length) {
                throw new IOException("Invalid DNS label");
            }
            if (name.length() > 0) {
                name.append('.');
            }
            for (int i = 0; i < labelLength; i++) {
                char c = (char) (dnsPayload[offset + i] & 0xFF);
                name.append(Character.toLowerCase(c));
            }
            offset += labelLength;
        }

        if (offset + 4 > length || name.length() == 0) {
            throw new IOException("Invalid DNS question");
        }

        int type = unsignedShort(dnsPayload, offset);
        int queryClass = unsignedShort(dnsPayload, offset + 2);
        int questionEnd = offset + 4;
        int id = unsignedShort(dnsPayload, 0);
        return new Question(id, name.toString().toLowerCase(Locale.US), type, queryClass,
                Arrays.copyOfRange(dnsPayload, 12, questionEnd));
    }

    static byte[] blockedResponse(byte[] originalPayload, Question question) {
        if (question.type == TYPE_A) {
            return responseWithSingleAnswer(originalPayload, question, new byte[]{0, 0, 0, 0});
        }
        if (question.type == TYPE_AAAA) {
            return responseWithSingleAnswer(originalPayload, question, new byte[16]);
        }
        return responseWithoutAnswers(originalPayload, question, 0x8183);
    }

    static byte[] servfailResponse(byte[] originalPayload, Question question) {
        return responseWithoutAnswers(originalPayload, question, 0x8182);
    }

    private static byte[] responseWithSingleAnswer(
            byte[] originalPayload,
            Question question,
            byte[] addressBytes
    ) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(
                12 + question.questionBytes.length + 16 + addressBytes.length);
        writeHeader(out, originalPayload, 0x8180, 1);
        out.write(question.questionBytes, 0, question.questionBytes.length);

        writeShort(out, 0xC00C);
        writeShort(out, question.type);
        writeShort(out, question.queryClass);
        writeInt(out, 60);
        writeShort(out, addressBytes.length);
        out.write(addressBytes, 0, addressBytes.length);
        return out.toByteArray();
    }

    private static byte[] responseWithoutAnswers(
            byte[] originalPayload,
            Question question,
            int flags
    ) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(12 + question.questionBytes.length);
        writeHeader(out, originalPayload, flags, 0);
        out.write(question.questionBytes, 0, question.questionBytes.length);
        return out.toByteArray();
    }

    private static void writeHeader(
            ByteArrayOutputStream out,
            byte[] originalPayload,
            int flags,
            int answerCount
    ) {
        out.write(originalPayload[0] & 0xFF);
        out.write(originalPayload[1] & 0xFF);
        writeShort(out, flags);
        writeShort(out, 1);
        writeShort(out, answerCount);
        writeShort(out, 0);
        writeShort(out, 0);
    }

    private static int unsignedShort(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }

    private static void writeShort(ByteArrayOutputStream out, int value) {
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    static final class Question {
        final int id;
        final String name;
        final int type;
        final int queryClass;
        final byte[] questionBytes;

        Question(int id, String name, int type, int queryClass, byte[] questionBytes) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.queryClass = queryClass;
            this.questionBytes = questionBytes;
        }

        String cacheKey() {
            return name + "|" + type + "|" + queryClass;
        }
    }
}
