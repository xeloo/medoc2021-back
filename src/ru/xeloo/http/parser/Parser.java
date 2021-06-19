package ru.xeloo.http.parser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

public class Parser {

    public Reader reader;
    private int lineN = 1;
    private int linePos;

    private boolean theEnd;
    private final StringBuilder sb = new StringBuilder(256);

    /**
     * mark buffer
     */
    private int[] buf;
//	private int bufStart;
    /**
     * last buffered char + 1
     */
    private int bufEnd;
    /**
     * next buffered char
     */
    private int bufPos;
//	private int bufMark;
    /**
     * is marked
     */
    private boolean mark;
    /**
     * multilevel marking
     */
    private final int[] markStack = new int[8];
    /**
     * active level:<br>
     * 0 - first level, stack is not used, start position is 0;<br>
     * 1 - second level, first element of stack is used and points to buffer position
     */
    private int stackPos;


    public Parser(Reader reader) {
        this.reader = reader;
    }

    public Parser(File file) {
        try {
            reader = new FileReader(file);
        } catch( FileNotFoundException e ) {
            throw new RuntimeException(e);
        }
    }

    public Parser(String str) {
        reader = new StringReader(str);
    }


    public boolean endOfStream() {
        return theEnd;
    }

    public int getInt(String start, String stop) {
        return Integer.parseInt(get(start, stop, true));
    }

    public long getLong(String start, String stop) {
        return Long.parseLong(get(start, stop, true));
    }

    public double getDouble(String start, String stop) {
        return Double.parseDouble(get(start, stop, true));
    }


    public String get(String start, String stop) {
        return skip(start).get(stop, false);
    }

    public String get(String start, String stop, boolean trim) {
        return skip(start).get(stop, trim);
    }

    public String get(String stop) {
        return get(stop, false);
    }

    public String get(String stop, boolean trim) {
        int len = stop.length();
        boolean newLine = true;
        boolean firstLine = true;
        int nlNum = 0;
loop:
        while( true ) {
            int ch = ch();
            if( ch == -1 )
                return null;

            if( trim ) {
                if( ch == 10 ) {
                    if( firstLine ) {
                        continue;
                    }

                    if( !newLine ) {
                        nlNum = 0;
                    }
                    newLine = true;
                    nlNum++;
                } else {
                    if( newLine ) {
                        if( ch == ' ' || ch == '\t' ) {
                            continue;
                        }

                        firstLine = false;
                        newLine = false;
                    }
                }
            }

            sb.append((char)ch);

            if( ch == stop.charAt(0) ) {
                mark();
                int i = 1;
                while( i < len ) {
                    ch = ch();
                    if( ch == -1 )
                        return null;

                    if( ch != stop.charAt(i) ) {
                        back();
                        continue loop;
                    }
                    i++;
                }
                back();

                String str = sb.substring(0, sb.length() - 1 - (trim ? nlNum : 0));
                sb.setLength(0);
                return str;
            }
        }
    }

    /**
     * Skip substring in the current line, place char pointer to the next char after specified string.
     * If specified string is not found in the current line, set current line to null.
     *
     * @param str
     * @return
     */
    public Parser skip(String str) {
        // can be replaced with find(str)

        int len = str.length();
        if( len == 0 )
            return this;
loop:
        while( true ) {
            int ch = ch();
            if( ch == -1 )
                return this;

            if( ch == str.charAt(0) ) {
                int i = 1;
                while( i < len ) {
                    ch = ch();
                    if( ch == -1 )
                        return this;

                    if( ch != str.charAt(i) ) {
                        continue loop;
                    }
                    i++;
                }
                return this;
            }
        }
    }

    public int ch() {
        try {
            if( bufPos < bufEnd ) {
                return buf[bufPos++];
            }

            int ch = reader.read();
            if( ch != -1 ) {
                // TODO: it's not clear with new line characters, need to consider all variants
                if( ch == 13 ) {
                    ch = reader.read();
                    if( ch == 10 ) {
                        lineN++;
                        linePos = 0;
                    }
                } else if( ch == 10 ) {
                    lineN++;
                    linePos = 0;
                } else {
                    linePos++;
                }
            } else {
                theEnd = true;
            }

            if( mark ) {
                if( bufEnd >= buf.length ) {
                    int[] newBuf = new int[buf.length * 2];
                    System.arraycopy(buf, 0, newBuf, 0, buf.length);
                    buf = newBuf;
                }
                buf[bufEnd] = (char)ch;
                bufEnd++;
                bufPos++;
            }
            return ch;
        } catch( IOException e ) {
            return -1;
        }
    }

    private void mark() {
        if( !mark ) {
            mark = true;
            if( buf == null ) {
                buf = new int[256];
            } else if( bufPos < bufEnd ) {
                int length = bufEnd - bufPos;
                System.arraycopy(buf, bufPos, buf, 0, length);
                bufEnd = length;
                bufPos = 0;
            } else {
                bufPos = bufEnd = 0;
            }
        } else {
            // If more than one level of marking we use position stack
            if( stackPos < markStack.length ) {
                markStack[stackPos++] = bufPos;
            } else {
                throw new RuntimeException("Marker stack overflow !!");
            }
        }
    }

    private void back() {
        if( mark ) {
            if( stackPos > 0 ) {
                bufPos = markStack[--stackPos];
            } else {
                bufPos = 0;
                mark = false;
            }
        } else {
            throw new RuntimeException("Not Marked !!");
        }
    }

    private void drop() {
        if( !mark ) {
            throw new RuntimeException("Not Marked !!");
        }

        mark = false;
        stackPos = 0;
    }

    @Deprecated
    private int find(String str) {
        int pos = 0;
        int len = str.length();
loop:
        while( true ) {
            int ch = ch();
            if( ch == -1 )
                return -1;

            pos++;

            if( ch == str.charAt(0) ) {
                int i = 1;
                while( i < len ) {
                    ch = ch();
                    if( ch == -1 )
                        return -1;

                    pos++;

                    if( ch != str.charAt(i) ) {
                        continue loop;
                    }
                    i++;
                }

                return pos - len;
            }
        }
    }

    public int find(String... variants) {
        boolean skip;
        char[] firstChars = new char[variants.length];
        for( int v = 0; v < variants.length; v++ ) {
            firstChars[v] = variants[v].charAt(0);
        }

        mark();
        while( true ) {
            int ch = ch();
            if( ch == -1 )
                return -1;

            for( int v = 0; v < variants.length; v++ ) {
                if( ch == firstChars[v] ) {
                    mark();
                    String str = variants[v];
                    int len = str.length();
                    int i = 1;
                    while( true ) {
                        if( i == len ) {
                            drop();
                            return v;
                        }

                        int ch2 = ch();
                        if( ch2 == -1 )
                            return -1;

                        if( ch2 != str.charAt(i) ) {
                            break;
                        }
                        i++;
                    }
                    back();
                }
            }
        }
    }

    public void consume() {
        try {
            //noinspection StatementWithEmptyBody
            while( reader.read() != -1 ) ;
            reader.close();
        } catch( IOException e ) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            reader.close();
        } catch( IOException ignored ) {
        }
    }

}
