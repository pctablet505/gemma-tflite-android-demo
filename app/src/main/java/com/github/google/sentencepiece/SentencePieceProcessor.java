package com.github.google.sentencepiece;

public class SentencePieceProcessor implements AutoCloseable {

    private final long rawPtr;

    public SentencePieceProcessor() {
        rawPtr = SentencePieceJNI.sppCtor();
    }

    @Override
    public void close() {
        SentencePieceJNI.sppDtor(rawPtr);
    }

    public void loadOrDie(String filename) {
        SentencePieceJNI.sppLoadOrDie(rawPtr, filename);
    }

    public int[] encodeAsIds(String input) throws SentencePieceException {
        return SentencePieceJNI.sppEncodeAsIds(rawPtr, input);
    }

    public String decodeIds(int... ids) throws SentencePieceException {
        return SentencePieceJNI.sppDecodeIds(rawPtr, ids);
    }

    public int getPieceSize() {
        return SentencePieceJNI.sppGetPieceSize(rawPtr);
    }

    public int pieceToId(String piece) {
        return SentencePieceJNI.sppPieceToId(rawPtr, piece);
    }

    public int unkId() {
        return SentencePieceJNI.sppUnkId(rawPtr);
    }

    public int bosId() {
        return SentencePieceJNI.sppBosId(rawPtr);
    }

    public int eosId() {
        return SentencePieceJNI.sppEosId(rawPtr);
    }

    public int padId() {
        return SentencePieceJNI.sppPadId(rawPtr);
    }
}
