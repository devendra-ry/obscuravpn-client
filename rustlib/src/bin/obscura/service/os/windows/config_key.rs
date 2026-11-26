//! Earlier builds of this fork encrypted `config.json` on Windows (Ombra patch P13) with 32 random bytes in
//! `config.key`, protected with DPAPI for the service account (LocalSystem). That is turned off again:
//!
//! - The config folder is in the SYSTEM profile, which only SYSTEM and administrators can read, and any administrator
//!   can use SYSTEM's DPAPI keys. The encryption kept out nobody who could read the file anyway.
//! - The official service cannot read an encrypted config: after an official update or reinstall it reset the config
//!   and logged the user out.
//! - A `config.key` that did not open was replaced by a new one, which also logged the user out.
//!
//! The service that the service control manager started writes an encrypted config again as plain text, once, and then
//! deletes the key. A run from a console has another account, whose DPAPI cannot open the key, so it refuses an
//! encrypted config instead of resetting it.

use obscuravpn_client::config::{ConfigKey, decrypt_config_file, is_encrypted};
use std::path::Path;
use windows::Win32::Foundation::{HLOCAL, LocalFree};
use windows::Win32::Security::Cryptography::{CRYPT_INTEGER_BLOB, CRYPTPROTECT_UI_FORBIDDEN, CryptUnprotectData};

const KEY_FILE: &str = "config.key";

/// Makes the config of `config_dir` plain text. `Err`: the config is encrypted and this process cannot open it;
/// starting anyway would reset it and log the user out. When the key does not open the file, the config is reset at
/// the load, as one that does not parse (the old behaviour of a lost key).
pub fn decrypt_config(config_dir: &Path, started_by_scm: bool) -> Result<(), ()> {
    if !is_encrypted(config_dir) {
        remove_key_file(config_dir);
        return Ok(());
    }
    if !started_by_scm {
        tracing::error!(
            message_id = "Zk4nWq7B",
            "the config is encrypted for the installed service; use another --config-dir for a console run"
        );
        return Err(());
    }
    let Some(key) = load(config_dir) else {
        tracing::error!(
            message_id = "Hd2vLc9T",
            "could not open the config key, the encrypted config will be reset"
        );
        return Ok(());
    };
    match decrypt_config_file(config_dir, &key) {
        Ok(_) => {
            tracing::info!(message_id = "Nw3hTq8F", "the config is plain text again");
            remove_key_file(config_dir);
        }
        Err(error) => tracing::error!(message_id = "Qm8xTf3R", %error, "could not write the config as plain text"),
    }
    Ok(())
}

fn remove_key_file(config_dir: &Path) {
    match std::fs::remove_file(config_dir.join(KEY_FILE)) {
        Ok(()) => tracing::info!(message_id = "Rc7pVz2K", "deleted the config key, which is no longer used"),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
        Err(error) => tracing::warn!(message_id = "Lb5sWx9M", ?error, "could not delete the config key"),
    }
}

fn load(config_dir: &Path) -> Option<ConfigKey> {
    let protected = std::fs::read(config_dir.join(KEY_FILE)).ok()?;
    let key = unprotect(&protected)
        .inspect_err(|error| tracing::error!(message_id = "Tv6kMd1P", ?error, "CryptUnprotectData failed"))
        .ok()?;
    ConfigKey::new(&key)
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
    use obscuravpn_client::config::{Config, save_with_key};
    use ring::rand::{SecureRandom, SystemRandom};
    use windows::Win32::Security::Cryptography::CryptProtectData;
    use windows::core::PCWSTR;

    /// What an earlier build stored: a DPAPI-protected key in `config.key` and the config encrypted with it.
    fn encrypted_config_dir(config: &Config) -> tempfile::TempDir {
        let dir = tempfile::tempdir().unwrap();
        let mut key = [0u8; 32];
        SystemRandom::new().fill(&mut key).unwrap();
        let input = blob(&key).unwrap();
        let mut out = CRYPT_INTEGER_BLOB::default();
        // SAFETY: as in `unprotect`.
        unsafe { CryptProtectData(&input, PCWSTR::null(), None, None, None, CRYPTPROTECT_UI_FORBIDDEN, &mut out) }.unwrap();
        // SAFETY: the call succeeded.
        std::fs::write(dir.path().join(KEY_FILE), unsafe { take(out) }.as_slice()).unwrap();
        save_with_key(dir.path(), config, Some(&ConfigKey::new(&key).unwrap())).unwrap();
        assert!(is_encrypted(dir.path()));
        dir
    }

    #[test]
    fn the_service_writes_an_encrypted_config_as_plain_text_and_deletes_the_key() {
        let config = Config { auto_connect: true, ..Default::default() };
        let dir = encrypted_config_dir(&config);
        decrypt_config(dir.path(), true).unwrap();
        assert!(!is_encrypted(dir.path()));
        assert!(!dir.path().join(KEY_FILE).exists());
        let json = std::fs::read_to_string(dir.path().join("config.json")).unwrap();
        let loaded: Config = serde_json::from_str(&json).unwrap();
        assert_eq!(loaded, config);

        // A second start finds plain text and does nothing.
        decrypt_config(dir.path(), true).unwrap();
        assert_eq!(std::fs::read_to_string(dir.path().join("config.json")).unwrap(), json);
    }

    #[test]
    fn a_console_run_refuses_an_encrypted_config() {
        let dir = encrypted_config_dir(&Config::default());
        assert!(decrypt_config(dir.path(), false).is_err());
        assert!(is_encrypted(dir.path()));
        assert!(dir.path().join(KEY_FILE).exists());
    }

    #[test]
    fn plain_text_stays_and_a_stray_key_goes() {
        let dir = tempfile::tempdir().unwrap();
        assert!(decrypt_config(dir.path(), false).is_ok());
        std::fs::write(dir.path().join(KEY_FILE), b"left by an earlier build").unwrap();
        decrypt_config(dir.path(), true).unwrap();
        assert!(!dir.path().join(KEY_FILE).exists());
    }

    #[test]
    fn a_key_that_does_not_open_leaves_the_file_for_the_load_to_reset() {
        let dir = encrypted_config_dir(&Config::default());
        std::fs::write(dir.path().join(KEY_FILE), b"not a DPAPI blob").unwrap();
        decrypt_config(dir.path(), true).unwrap();
        assert!(is_encrypted(dir.path()));
    }
}
