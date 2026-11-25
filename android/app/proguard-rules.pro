# The Rust engine looks these up by name over JNI.
-keep class io.github.priyansx01.stayline.vpn.NativeBridge {
    native <methods>;
    public void onEvent(byte[]);
    public int establish(byte[]);
}
-keep interface io.github.priyansx01.stayline.vpn.NativeCallbacks { *; }
