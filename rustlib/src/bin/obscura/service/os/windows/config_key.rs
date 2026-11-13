//! The key that encrypts `config.json` on Windows (Ombra patch P13). The file holds the account number and the auth
//! token. The key is 32 random bytes in `config.key`, protected with DPAPI for the account of the service
//! (LocalSystem), so another local user who can read the two files cannot open the config.
//!
//! DPAPI and not the TPM key of the WireGuard secret: that key is bound to PCR 7, which a Secure Boot DBX update from
//! Windows Update changes, and a config that no longer opens logs the user out.
//!
//! Only the service that the service control manager started encrypts. A run from a console (development) has another
//! account, whose DPAPI cannot open the key, so it leaves a plain text config as it is and refuses an encrypted one.

use obscuravpn_client::config::{ConfigKey, is_encrypted};
use ring::rand::{SecureRandom, SystemRandom};
use std::path::Path;
use windows::Win32::Foundation::{HLOCAL, LocalFree};
use windows::Win32::Security::Cryptography::{CRYPT_INTEGER_BLOB, CRYPTPROTECT_UI_FORBIDDEN, CryptProtectData, CryptUnprotectData};
use windows::core::PCWSTR;

const KEY_FILE: &str = "config.key";
const KEY_LEN: usize = 32;

/// The key for `config_dir`. `Ok(None)`: the config stays plain text. `Err`: the config is encrypted and this process
/// cannot open it; starting anyway would reset it and log the user out.
pub fn config_key(config_dir: &Path, started_by_scm: bool) -> Result<Option<ConfigKey>, ()> {
    if !started_by_scm {
        if is_encrypted(config_dir) {
            tracing::error!(
                message_id = "Zk4nWq7B",
                "the config is encrypted for the installed service; use another --config-dir for a console run"
            );
            return Err(());
        }
        return Ok(None);
    }
    Ok(load(config_dir).or_else(|| create(config_dir)))
}

fn load(config_dir: &Path) -> Option<ConfigKey> {
    let protected = std::fs::read(config_dir.join(KEY_FILE)).ok()?;
    let key = unprotect(&protected)
        .inspect_err(|error| tracing::error!(message_id = "Hd2vLc9T", ?error, "could not open the config key, making a new one"))
        .ok()?;
    ConfigKey::new(&key)
}

/// A new key. Without one the config stays plain text, as before: the VPN must keep working.
fn create(config_dir: &Path) -> Option<ConfigKey> {
    let mut key = zeroize::Zeroizing::new([0u8; KEY_LEN]);
    SystemRandom::new().fill(&mut *key).ok()?;
    let stored = protect(&*key).and_then(|protected| {
        std::fs::create_dir_all(config_dir)?;
        std::fs::write(config_dir.join(KEY_FILE), protected)?;
        Ok(())
    });
    if let Err(error) = stored {
        tracing::error!(message_id = "Qm8xTf3R", ?error, "could not store a config key, the config stays plain text");
        return None;
    }
    ConfigKey::new(&*key)
}

fn blob(bytes: &[u8]) -> std::io::Result<CRYPT_INTEGER_BLOB> {
    let len = u32::try_from(bytes.len()).map_err(std::io::Error::other)?;
    Ok(CRYPT_INTEGER_BLOB { cbData: len, pbData: bytes.as_ptr().cast_mut() })
}

/// Copies the output of a DPAPI call and frees it.
///
/// SAFETY: `out` must be the output blob of a successful `CryptProtectData` or `CryptUnprotectData` call.
unsafe fn take(out: CRYPT_INTEGER_BLOB) -> zeroize::Zeroizing<Vec<u8>> {
    // SAFETY: DPAPI allocated `cbData` bytes at `pbData` with `LocalAlloc`.
    unsafe {
        let len = usize::try_from(out.cbData).unwrap_or(0);
        let bytes = zeroize::Zeroizing::new(std::slice::from_raw_parts(out.pbData, len).to_vec());
        std::ptr::write_bytes(out.pbData, 0, len);
        LocalFree(Some(HLOCAL(out.pbData.cast())));
        bytes
    }
}

fn protect(plaintext: &[u8]) -> std::io::Result<Vec<u8>> {
    let input = blob(plaintext)?;
    let mut out = CRYPT_INTEGER_BLOB::default();
    // SAFETY: `input` points at `plaintext`, which lives for the call. DPAPI only reads it and fills `out`.
    unsafe { CryptProtectData(&input, PCWSTR::null(), None, None, None, CRYPTPROTECT_UI_FORBIDDEN, &mut out) }.map_err(std::io::Error::other)?;
    // SAFETY: the call succeeded.
    Ok(unsafe { take(out) }.to_vec())
}

fn unprotect(protected: &[u8]) -> std::io::Result<zeroize::Zeroizing<Vec<u8>>> {
    let input = blob(protected)?;
    let mut out = CRYPT_INTEGER_BLOB::default();
    // SAFETY: `input` points at `protected`, which lives for the call. DPAPI only reads it and fills `out`.
    unsafe { CryptUnprotectData(&input, None, None, None, None, CRYPTPROTECT_UI_FORBIDDEN, &mut out) }.map_err(std::io::Error::other)?;
    // SAFETY: the call succeeded.
    Ok(unsafe { take(out) })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn the_key_file_is_protected_and_gives_the_same_key_again() {
        let dir = tempfile::tempdir().unwrap();
        assert!(config_key(dir.path(), true).unwrap().is_some());
        let stored = std::fs::read(dir.path().join(KEY_FILE)).unwrap();
        let key = unprotect(&stored).unwrap();
        assert_eq!(key.len(), KEY_LEN);
        assert!(!stored.windows(KEY_LEN).any(|window| window == &key[..]), "the key is not in the file");

        // A second start reads the key and does not replace it.
        assert!(config_key(dir.path(), true).unwrap().is_some());
        assert_eq!(std::fs::read(dir.path().join(KEY_FILE)).unwrap(), stored);
    }

    #[test]
    fn a_damaged_key_file_is_replaced() {
        let dir = tempfile::tempdir().unwrap();
        std::fs::write(dir.path().join(KEY_FILE), b"not a DPAPI blob").unwrap();
        assert!(config_key(dir.path(), true).unwrap().is_some());
        assert!(unprotect(&std::fs::read(dir.path().join(KEY_FILE)).unwrap()).is_ok());
    }

    #[test]
    fn a_console_run_keeps_plain_text_and_refuses_an_encrypted_config() {
        let dir = tempfile::tempdir().unwrap();
        assert!(config_key(dir.path(), false).unwrap().is_none());
        assert!(!dir.path().join(KEY_FILE).exists());

        std::fs::write(dir.path().join("config.json"), b"OMBRA-CONFIG\x01rest").unwrap();
        assert!(config_key(dir.path(), false).is_err());
    }
}
