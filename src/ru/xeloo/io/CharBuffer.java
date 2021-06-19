package ru.xeloo.io;

import java.util.Arrays;

/**
 * Reusable StringBuilder analog with minimal overhead.
 */
public class CharBuffer {

    protected char[] buf;

    protected int count;

    public CharBuffer() {
        this(32);
    }

    public CharBuffer(int initialSize) {
        buf = new char[initialSize];
    }

    public boolean isEmpty() {
        return count == 0;
    }

    public CharBuffer append(int ch) {
        int newCount = count + 1;
        if (newCount > buf.length) {
            buf = Arrays.copyOf(buf, buf.length << 1);
        }
        buf[count] = (char)ch;
        count = newCount;
        return this;
    }

    public CharBuffer append(String str) {
        return append(str, 0, str.length());
    }

    public CharBuffer append(String str, int s, int e) {
        int newCount = count + e - s;
        if (newCount > buf.length) {
            buf = Arrays.copyOf(buf, Math.max(buf.length << 1, newCount));
        }
        str.getChars(s, e, buf, count);
        count = newCount;
        return this;
    }

    public CharBuffer append(char[] str) {
        return append(str, 0, str.length);
    }

    public CharBuffer append(char[] str, int s, int e) {
        int len = e - s;
        int newCount = count + len;
        if (newCount > buf.length) {
            buf = Arrays.copyOf(buf, Math.max(buf.length << 1, newCount));
        }
        System.arraycopy(str, s, buf, count, len);
        count = newCount;
        return this;
    }

    public CharBuffer append(Object obj) {
        return append(String.valueOf(obj));
    }

    public CharBuffer back(int len) {
        count -= len;
        return this;
    }

    public void reset() {
        count = 0;
    }


    public String toString() {
        return new String(buf, 0, count);
    }

}
