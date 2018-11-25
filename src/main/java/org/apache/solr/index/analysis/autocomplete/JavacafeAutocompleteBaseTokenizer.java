package org.apache.solr.index.analysis.autocomplete;

import org.apache.lucene.analysis.CharacterUtils;
import org.apache.lucene.analysis.CharacterUtils.CharacterBuffer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.util.AttributeFactory;
import org.elasticsearch.index.common.parser.AutocompleteKoreanDecomposer;

import java.io.*;

public class JavacafeAutocompleteBaseTokenizer extends Tokenizer{

    private static AutocompleteKoreanDecomposer decomposer;

    private int offset = 0, bufferIndex = 0, dataLen = 0, finalOffset = 0;
    private static final int MAX_WORD_LEN = 2048;
    private static final int IO_BUFFER_SIZE = 4096;

    private CharTermAttribute termAtt;
    private OffsetAttribute offsetAtt;

    private static final CharacterUtils charUtils = null;
    private final CharacterBuffer ioBuffer = CharacterUtils.newCharacterBuffer(IO_BUFFER_SIZE);

    public JavacafeAutocompleteBaseTokenizer(AttributeFactory factory) {
        super(factory);
        termAtt = addAttribute(CharTermAttribute.class);
        offsetAtt = addAttribute(OffsetAttribute.class);

        offset = 0;
        bufferIndex = 0;
        dataLen = 0;
        finalOffset = 0;
    }

    protected boolean isTokenChar(int c) {
        throw new UnsupportedOperationException("Subclasses of CharTokenizer must implement isTokenChar(int)");
    }

    protected int normalize(int c) {
        return c;
    }

    @Override
    public final boolean incrementToken() throws IOException {
        clearAttributes();

        int length = 0;
        int start = -1; // this variable is always initialized
        char[] buffer = termAtt.buffer();
        while (true) {
            if (bufferIndex >= dataLen) {

                offset += dataLen;
                boolean isDecompose = charUtils.fill(ioBuffer, jasoDecompose(input));

                //버퍼사이즈가 있으면 분석한다. (return false일때까지... 재귀호출)
                if (ioBuffer.getLength() == 0) {
                    dataLen = 0; // so next offset += dataLen won't decrement offset
                    if (length > 0) {
                        break;
                    } else {
                        finalOffset = correctOffset(offset);
                        return false;
                    }
                }
                dataLen = ioBuffer.getLength();
                bufferIndex = 0;
            }
            // use CharacterUtils here to support < 3.1 UTF-16 code unit behavior if the char based methods are gone
            final int c = Character.codePointAt(ioBuffer.getBuffer(), bufferIndex, dataLen);
            bufferIndex += Character.charCount(c);

            // if it's a token char
            if (isTokenChar(c)) {

                // start of token
                if (length == 0) {
                    assert start == -1;
                    start = offset + bufferIndex - 1;

                    // check if a supplementary could run out of bounds
                } else if (length >= buffer.length - 1) {

                    // make sure a supplementary fits in the buffer
                    buffer = termAtt.resizeBuffer(2 + length);
                }

                // buffer it, normalized
                length += Character.toChars(normalize(c), buffer, length);
                if (length >= MAX_WORD_LEN) {
                    break;
                }
            } else if (length > 0) {
                // return 'em
                break;
            }
        }

        termAtt.setLength(length);
        assert start != -1;
        offsetAtt.setOffset(correctOffset(start), finalOffset = correctOffset(start + length));
        return true;
    }

    @Override
    public final void end() {
        // set final offset
        offsetAtt.setOffset(finalOffset, finalOffset);
    }

    /**
     * Reader -> String -> 자소변환 -> String -> Reader
     *
     * @param in
     * @return
     */
    public static Reader jasoDecompose(Reader in) {
        Writer writer = new StringWriter();
        decomposer = new AutocompleteKoreanDecomposer();
        char[] buffer = new char[2048];
        String temp;

        try {
            int n;
            while ((n = in.read(buffer)) != -1) {
                writer.write(buffer, 0, n);
            }
            temp = writer.toString();
            temp = decomposer.jasoDecompose(temp);
            // System.out.println(temp);
            StringReader myStringReader = new StringReader(temp);
            in = myStringReader;
        } catch (Exception e) {
            StringWriter errors = new StringWriter();
            e.printStackTrace(new PrintWriter(errors));
        } finally {
        }

        return in;
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        bufferIndex = 0;
        offset = 0;
        dataLen = 0;
        finalOffset = 0;
        ioBuffer.reset(); // make sure to reset the IO buffer!!
    }
}
