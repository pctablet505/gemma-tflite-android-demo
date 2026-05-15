package com.github.google.sentencepiece;

class SentencePieceJNI {

    static {
        System.loadLibrary("sentencepiece_jni");
    }

    static native long sppCtor();
    static native void sppDtor(long spp);
    static native void sppLoadOrDie(long spp, String filename);
    static native int[] sppEncodeAsIds(long spp, String input) throws SentencePieceException;
    static native String sppDecodeIds(long spp, int[] ids) throws SentencePieceException;
    static native int sppGetPieceSize(long spp);
    static native int sppPieceToId(long spp, String piece);
    static native int sppUnkId(long spp);
    static native int sppBosId(long spp);
    static native int sppEosId(long spp);
    static native int sppPadId(long spp);
}
