//! JNI entry points for `io.github.priyansx01.stayline.vpn.NativeBridge`.
//!
//! Strings cross as UTF-8 byte arrays rather than Java strings, which JNI
//! hands over in "modified UTF-8" (wrong for emoji in a password, say).
//!
//! ```kotlin
//! object NativeBridge {
//!     external fun nativeInit(callbacks: NativeCallbacks)
//!     external fun nativeConnect(profileJson: ByteArray, password: ByteArray)
//!     external fun nativeDisconnect()
//!     external fun nativeInspectCertificate(gateway: ByteArray)
//!     external fun nativeNetworkChanged(handle: Long, available: Boolean)
//!     external fun nativeResumed()
//! }
//! interface NativeCallbacks {
//!     fun onEvent(json: ByteArray)            // stayline_ipc::Event
//!     fun establish(linkJson: ByteArray): Int // LinkSpec -> fd, or -1
//! }
//! ```

use std::ffi::{CStr, c_void};
use std::ptr::null_mut;
use std::sync::{Arc, OnceLock};

use jni_sys::{
    JNI_EDETACHED, JNI_OK, JNI_VERSION_1_6, JNIEnv, JavaVM, jboolean, jbyteArray, jint, jlong,
    jmethodID, jobject, jvalue,
};
use stayline_ipc::{Event, Profile};
use zeroize::Zeroizing;

use crate::engine::{Engine, Platform};
use crate::link::LinkSpec;

static ENGINE: OnceLock<Engine> = OnceLock::new();

/// Calls back into the app's `NativeCallbacks` object.
struct JniPlatform {
    vm: *mut JavaVM,
    callbacks: jobject,
    on_event: jmethodID,
    establish: jmethodID,
}

// SAFETY: the JavaVM pointer, global references and method IDs are valid on
// every thread (JNI spec); each thread gets its own JNIEnv in `with_env`.
unsafe impl Send for JniPlatform {}
unsafe impl Sync for JniPlatform {}

impl JniPlatform {
    /// Runs `f` with this thread's JNIEnv, attaching the thread if needed.
    fn with_env<T>(&self, f: impl FnOnce(*mut JNIEnv) -> T) -> Option<T> {
        let mut env: *mut c_void = null_mut();
        // SAFETY: `vm` is the process's JavaVM.
        unsafe {
            let invoke = &(**self.vm).v1_4;
            let mut rc = (invoke.GetEnv)(self.vm, &mut env, JNI_VERSION_1_6);
            if rc == JNI_EDETACHED {
                // Daemon, so engine threads never keep the VM alive.
                rc = (invoke.AttachCurrentThreadAsDaemon)(self.vm, &mut env, null_mut());
            }
            (rc == JNI_OK).then(|| f(env.cast()))
        }
    }
}

impl Platform for JniPlatform {
    fn emit(&self, event: &Event) {
        let Ok(json) = serde_json::to_vec(event) else {
            return;
        };
        self.with_env(|env| unsafe {
            // SAFETY: `env` belongs to this thread; the method takes a byte[].
            let array = new_byte_array(env, &json);
            if array.is_null() {
                return;
            }
            let args = [jvalue { l: array }];
            ((**env).v1_1.CallVoidMethodA)(env, self.callbacks, self.on_event, args.as_ptr());
            clear_exception(env);
            ((**env).v1_1.DeleteLocalRef)(env, array);
        });
    }

    fn establish(&self, link: &LinkSpec) -> Result<i32, String> {
        let json = serde_json::to_vec(link).map_err(|e| e.to_string())?;
        let fd = self
            .with_env(|env| unsafe {
                // SAFETY: as in `emit`; the method returns an int.
                let array = new_byte_array(env, &json);
                if array.is_null() {
                    return -1;
                }
                let args = [jvalue { l: array }];
                let fd = ((**env).v1_1.CallIntMethodA)(
                    env,
                    self.callbacks,
                    self.establish,
                    args.as_ptr(),
                );
                let failed = clear_exception(env);
                ((**env).v1_1.DeleteLocalRef)(env, array);
                if failed { -1 } else { fd }
            })
            .unwrap_or(-1);
        if fd < 0 {
            return Err("Android refused to open the VPN interface".into());
        }
        Ok(fd)
    }

    fn thread_stopping(&self) {
        let mut env: *mut c_void = null_mut();
        // SAFETY: an attached thread must detach before it exits, or ART
        // aborts the process. Only detach threads that are attached.
        unsafe {
            let invoke = &(**self.vm).v1_4;
            if (invoke.GetEnv)(self.vm, &mut env, JNI_VERSION_1_6) == JNI_OK {
                (invoke.DetachCurrentThread)(self.vm);
            }
        }
    }
}

/// Logs and clears a pending Java exception. `true` if there was one.
unsafe fn clear_exception(env: *mut JNIEnv) -> bool {
    // SAFETY: the caller passes this thread's env.
    unsafe {
        if ((**env).v1_2.ExceptionCheck)(env) {
            ((**env).v1_1.ExceptionDescribe)(env);
            ((**env).v1_1.ExceptionClear)(env);
            return true;
        }
    }
    false
}

unsafe fn new_byte_array(env: *mut JNIEnv, bytes: &[u8]) -> jbyteArray {
    let Ok(len) = jint::try_from(bytes.len()) else {
        return null_mut();
    };
    // SAFETY: the caller passes this thread's env; `bytes` holds `len` bytes.
    unsafe {
        let array = ((**env).v1_1.NewByteArray)(env, len);
        if array.is_null() {
            clear_exception(env);
            return null_mut();
        }
        ((**env).v1_1.SetByteArrayRegion)(env, array, 0, len, bytes.as_ptr().cast());
        array
    }
}

/// Copies a Java byte[]; `None` for null.
unsafe fn read_byte_array(env: *mut JNIEnv, array: jbyteArray) -> Option<Zeroizing<Vec<u8>>> {
    if array.is_null() {
        return None;
    }
    // SAFETY: the caller passes this thread's env and a live byte[].
    unsafe {
        let len = ((**env).v1_1.GetArrayLength)(env, array);
        let mut buf = Zeroizing::new(vec![0u8; usize::try_from(len).ok()?]);
        ((**env).v1_1.GetByteArrayRegion)(env, array, 0, len, buf.as_mut_ptr().cast());
        Some(buf)
    }
}

unsafe fn read_string(env: *mut JNIEnv, array: jbyteArray) -> Option<Zeroizing<String>> {
    // SAFETY: forwarded from the caller.
    let bytes = unsafe { read_byte_array(env, array) }?;
    std::str::from_utf8(&bytes)
        .ok()
        .map(|s| Zeroizing::new(s.to_owned()))
}

unsafe fn method(env: *mut JNIEnv, obj: jobject, name: &CStr, sig: &CStr) -> jmethodID {
    // SAFETY: the caller passes this thread's env and a live object.
    unsafe {
        let class = ((**env).v1_1.GetObjectClass)(env, obj);
        let id = ((**env).v1_1.GetMethodID)(env, class, name.as_ptr(), sig.as_ptr());
        ((**env).v1_1.DeleteLocalRef)(env, class);
        id
    }
}

fn engine() -> Option<&'static Engine> {
    let engine = ENGINE.get();
    if engine.is_none() {
        tracing::error!("native call before nativeInit");
    }
    engine
}

/// Creates the engine once per process; later calls are ignored.
#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_io_github_priyansx01_stayline_vpn_NativeBridge_nativeInit(
    env: *mut JNIEnv,
    _this: jobject,
    callbacks: jobject,
) {
    if ENGINE.get().is_some() || callbacks.is_null() {
        return;
    }
    #[cfg(target_os = "android")]
    crate::log::init();

    // SAFETY: called by the JVM with a valid env and a live callbacks object.
    let platform = unsafe {
        let mut vm: *mut JavaVM = null_mut();
        if ((**env).v1_1.GetJavaVM)(env, &mut vm) != JNI_OK {
            return;
        }
        let on_event = method(env, callbacks, c"onEvent", c"([B)V");
        let establish = method(env, callbacks, c"establish", c"([B)I");
        if on_event.is_null() || establish.is_null() {
            clear_exception(env);
            tracing::error!("NativeCallbacks is missing onEvent or establish");
            return;
        }
        JniPlatform {
            vm,
            callbacks: ((**env).v1_1.NewGlobalRef)(env, callbacks),
            on_event,
            establish,
        }
    };
    match Engine::new(Arc::new(platform)) {
        Ok(engine) => {
            let _ = ENGINE.set(engine);
        }
        Err(e) => tracing::error!(error = %e, "could not start the engine"),
    }
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_io_github_priyansx01_stayline_vpn_NativeBridge_nativeConnect(
    env: *mut JNIEnv,
    _this: jobject,
    profile_json: jbyteArray,
    password: jbyteArray,
) {
    let Some(engine) = engine() else { return };
    // SAFETY: called by the JVM with a valid env and byte arrays.
    let (profile, password) =
        unsafe { (read_string(env, profile_json), read_string(env, password)) };
    let profile = profile
        .ok_or_else(|| "missing profile".to_owned())
        .and_then(|json| serde_json::from_str::<Profile>(&json).map_err(|e| e.to_string()));
    match profile {
        Ok(profile) => engine.connect(profile, password.unwrap_or_default()),
        Err(e) => tracing::error!(error = %e, "bad profile from the app"),
    }
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_io_github_priyansx01_stayline_vpn_NativeBridge_nativeDisconnect(
    _env: *mut JNIEnv,
    _this: jobject,
) {
    if let Some(engine) = engine() {
        engine.disconnect();
    }
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_io_github_priyansx01_stayline_vpn_NativeBridge_nativeInspectCertificate(
    env: *mut JNIEnv,
    _this: jobject,
    gateway: jbyteArray,
) {
    let Some(engine) = engine() else { return };
    // SAFETY: called by the JVM with a valid env and byte array.
    if let Some(gateway) = unsafe { read_string(env, gateway) } {
        engine.inspect_certificate(gateway.to_string());
    }
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_io_github_priyansx01_stayline_vpn_NativeBridge_nativeNetworkChanged(
    _env: *mut JNIEnv,
    _this: jobject,
    handle: jlong,
    available: jboolean,
) {
    if let Some(engine) = engine() {
        engine.network_changed(handle, available);
    }
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_io_github_priyansx01_stayline_vpn_NativeBridge_nativeResumed(
    _env: *mut JNIEnv,
    _this: jobject,
) {
    if let Some(engine) = engine() {
        engine.resumed();
    }
}
