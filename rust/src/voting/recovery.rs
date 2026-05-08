use std::panic::AssertUnwindSafe;

use anyhow::anyhow;
use ffi_helpers::panic::catch_panic;

use crate::{unwrap_exc_or, unwrap_exc_or_null};

use super::db::VotingDatabaseHandle;
use super::helpers::{bytes_from_ptr, json_to_boxed_slice, str_from_ptr};

// =============================================================================
// Recovery state (TX hashes, bundles, share delegations, keystone sigs)
// =============================================================================

const KEYSTONE_SIGNATURE_LEN: usize = 64;
const PCZT_SIGHASH_LEN: usize = 32;
const RANDOMIZED_KEY_LEN: usize = 32;

#[derive(serde::Serialize)]
struct JsonKeystoneSignatureRecord {
    bundle_index: u32,
    sig: Vec<u8>,
    sighash: Vec<u8>,
    rk: Vec<u8>,
}

impl From<zcash_voting::storage::KeystoneSignatureRecord> for JsonKeystoneSignatureRecord {
    fn from(record: zcash_voting::storage::KeystoneSignatureRecord) -> Self {
        Self {
            bundle_index: record.bundle_index,
            sig: record.sig,
            sighash: record.sighash,
            rk: record.rk,
        }
    }
}

/// Persist the on-chain TX hash of a submitted delegation bundle so
/// crash recovery can find it after app restart.
///
/// # Safety
///
/// - `db` must be a valid, non-null `VotingDatabaseHandle` pointer.
/// - `round_id` and `tx_hash` must be valid UTF-8 pointers with their stated lengths.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn zcashlc_voting_store_delegation_tx_hash(
    db: *mut VotingDatabaseHandle,
    round_id: *const u8,
    round_id_len: usize,
    bundle_index: u32,
    tx_hash: *const u8,
    tx_hash_len: usize,
) -> i32 {
    let db = AssertUnwindSafe(db);
    let res = catch_panic(|| {
        let handle =
            unsafe { db.as_ref() }.ok_or_else(|| anyhow!("VotingDatabaseHandle is null"))?;
        let round_id_str = unsafe { str_from_ptr(round_id, round_id_len) }?;
        let tx_hash_str = unsafe { str_from_ptr(tx_hash, tx_hash_len) }?;
        handle
            .db
            .store_delegation_tx_hash(&round_id_str, bundle_index, &tx_hash_str)
            .map_err(|e| anyhow!("store_delegation_tx_hash failed: {}", e))?;
        Ok(0)
    });
    unwrap_exc_or(res, -1)
}

/// Load a previously stored delegation TX hash. Returns a JSON-encoded
/// `Option<String>` — `null` when no row exists for this bundle.
///
/// # Safety
///
/// - `db` must be a valid, non-null `VotingDatabaseHandle` pointer.
/// - `round_id` must be a valid UTF-8 pointer with its stated length.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn zcashlc_voting_get_delegation_tx_hash(
    db: *mut VotingDatabaseHandle,
    round_id: *const u8,
    round_id_len: usize,
    bundle_index: u32,
) -> *mut crate::ffi::BoxedSlice {
    let db = AssertUnwindSafe(db);
    let res = catch_panic(|| {
        let handle =
            unsafe { db.as_ref() }.ok_or_else(|| anyhow!("VotingDatabaseHandle is null"))?;
        let round_id_str = unsafe { str_from_ptr(round_id, round_id_len) }?;
        let hash = handle
            .db
            .get_delegation_tx_hash(&round_id_str, bundle_index)
            .map_err(|e| anyhow!("get_delegation_tx_hash failed: {}", e))?;
        json_to_boxed_slice(&hash)
    });
    unwrap_exc_or_null(res)
}

/// Persist the on-chain TX hash of a submitted vote (scoped by bundle and
/// proposal) for crash-recovery lookups.
///
/// # Safety
///
/// - `db` must be a valid, non-null `VotingDatabaseHandle` pointer.
/// - `round_id` and `tx_hash` must be valid UTF-8 pointers with their stated lengths.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn zcashlc_voting_store_vote_tx_hash(
    db: *mut VotingDatabaseHandle,
    round_id: *const u8,
    round_id_len: usize,
    bundle_index: u32,
    proposal_id: u32,
    tx_hash: *const u8,
    tx_hash_len: usize,
) -> i32 {
    let db = AssertUnwindSafe(db);
    let res = catch_panic(|| {
        let handle =
            unsafe { db.as_ref() }.ok_or_else(|| anyhow!("VotingDatabaseHandle is null"))?;
        let round_id_str = unsafe { str_from_ptr(round_id, round_id_len) }?;
        let tx_hash_str = unsafe { str_from_ptr(tx_hash, tx_hash_len) }?;
        handle
            .db
            .store_vote_tx_hash(&round_id_str, bundle_index, proposal_id, &tx_hash_str)
            .map_err(|e| anyhow!("store_vote_tx_hash failed: {}", e))?;
        Ok(0)
    });
    unwrap_exc_or(res, -1)
}

/// Load a previously stored vote TX hash. Returns a JSON-encoded
/// `Option<String>` — `null` when no row exists for this bundle/proposal.
///
/// # Safety
///
/// - `db` must be a valid, non-null `VotingDatabaseHandle` pointer.
/// - `round_id` must be a valid UTF-8 pointer with its stated length.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn zcashlc_voting_get_vote_tx_hash(
    db: *mut VotingDatabaseHandle,
    round_id: *const u8,
    round_id_len: usize,
    bundle_index: u32,
    proposal_id: u32,
) -> *mut crate::ffi::BoxedSlice {
    let db = AssertUnwindSafe(db);
    let res = catch_panic(|| {
        let handle =
            unsafe { db.as_ref() }.ok_or_else(|| anyhow!("VotingDatabaseHandle is null"))?;
        let round_id_str = unsafe { str_from_ptr(round_id, round_id_len) }?;
        let hash = handle
            .db
            .get_vote_tx_hash(&round_id_str, bundle_index, proposal_id)
            .map_err(|e| anyhow!("get_vote_tx_hash failed: {}", e))?;
        json_to_boxed_slice(&hash)
    });
    unwrap_exc_or_null(res)
}

/// Persist the vote commitment bundle JSON and VC-tree position before TX
/// submission, so share delegation can resume after a crash between TX
/// confirmation and share send-out.
///
/// # Safety
///
/// - `db` must be a valid, non-null `VotingDatabaseHandle` pointer.
/// - `round_id` and `bundle_json` must be valid UTF-8 pointers with their stated lengths.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn zcashlc_voting_store_commitment_bundle(
    db: *mut VotingDatabaseHandle,
    round_id: *const u8,
    round_id_len: usize,
    bundle_index: u32,
    proposal_id: u32,
    bundle_json: *const u8,
    bundle_json_len: usize,
    vc_tree_position: u64,
) -> i32 {
    let db = AssertUnwindSafe(db);
    let res = catch_panic(|| {
        let handle =
            unsafe { db.as_ref() }.ok_or_else(|| anyhow!("VotingDatabaseHandle is null"))?;
        let round_id_str = unsafe { str_from_ptr(round_id, round_id_len) }?;
        let json_str = unsafe { str_from_ptr(bundle_json, bundle_json_len) }?;
        handle
            .db
            .store_commitment_bundle(
                &round_id_str,
                bundle_index,
                proposal_id,
                &json_str,
                vc_tree_position,
            )
            .map_err(|e| anyhow!("store_commitment_bundle failed: {}", e))?;
        Ok(0)
    });
    unwrap_exc_or(res, -1)
}

/// Load a stored commitment bundle and its VC-tree position. Returns a
/// JSON-encoded `Option<(String, u64)>` — `null` when no row exists.
///
/// # Safety
///
/// - `db` must be a valid, non-null `VotingDatabaseHandle` pointer.
/// - `round_id` must be a valid UTF-8 pointer with its stated length.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn zcashlc_voting_get_commitment_bundle(
    db: *mut VotingDatabaseHandle,
    round_id: *const u8,
    round_id_len: usize,
    bundle_index: u32,
    proposal_id: u32,
) -> *mut crate::ffi::BoxedSlice {
    let db = AssertUnwindSafe(db);
    let res = catch_panic(|| {
        let handle =
            unsafe { db.as_ref() }.ok_or_else(|| anyhow!("VotingDatabaseHandle is null"))?;
        let round_id_str = unsafe { str_from_ptr(round_id, round_id_len) }?;
        let result = handle
            .db
            .get_commitment_bundle(&round_id_str, bundle_index, proposal_id)
            .map_err(|e| anyhow!("get_commitment_bundle failed: {}", e))?;
        json_to_boxed_slice(&result)
    });
    unwrap_exc_or_null(res)
}

/// Persist a Keystone-produced PCZT signature (`sig` + `sighash` + `rk`)
/// so it survives app restarts during the delegation-signing workflow.
///
/// # Safety
///
/// - `db` must be a valid, non-null `VotingDatabaseHandle` pointer.
/// - `round_id` must be a valid UTF-8 pointer with its stated length.
/// - `sig` must point to exactly 64 bytes.
/// - `sighash` and `rk` must each point to exactly 32 bytes.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn zcashlc_voting_store_keystone_signature(
    db: *mut VotingDatabaseHandle,
    round_id: *const u8,
    round_id_len: usize,
    bundle_index: u32,
    sig: *const u8,
    sig_len: usize,
    sighash: *const u8,
    sighash_len: usize,
    rk: *const u8,
    rk_len: usize,
) -> i32 {
    let db = AssertUnwindSafe(db);
    let res = catch_panic(|| {
        let handle =
            unsafe { db.as_ref() }.ok_or_else(|| anyhow!("VotingDatabaseHandle is null"))?;
        let round_id_str = unsafe { str_from_ptr(round_id, round_id_len) }?;
        if sig_len != KEYSTONE_SIGNATURE_LEN {
            return Err(anyhow!(
                "sig must be {} bytes, got {}",
                KEYSTONE_SIGNATURE_LEN,
                sig_len
            ));
        }
        if sighash_len != PCZT_SIGHASH_LEN {
            return Err(anyhow!(
                "sighash must be {} bytes, got {}",
                PCZT_SIGHASH_LEN,
                sighash_len
            ));
        }
        if rk_len != RANDOMIZED_KEY_LEN {
            return Err(anyhow!(
                "rk must be {} bytes, got {}",
                RANDOMIZED_KEY_LEN,
                rk_len
            ));
        }
        let sig_bytes = unsafe { bytes_from_ptr(sig, sig_len) }?;
        let sighash_bytes = unsafe { bytes_from_ptr(sighash, sighash_len) }?;
        let rk_bytes = unsafe { bytes_from_ptr(rk, rk_len) }?;
        handle
            .db
            .store_keystone_signature(
                &round_id_str,
                bundle_index,
                sig_bytes,
                sighash_bytes,
                rk_bytes,
            )
            .map_err(|e| anyhow!("store_keystone_signature failed: {}", e))?;
        Ok(0)
    });
    unwrap_exc_or(res, -1)
}

/// Load all Keystone signatures stored for a round, returned as a JSON array
/// of `{ bundle_index, sig, sighash, rk }` objects.
///
/// # Safety
///
/// - `db` must be a valid, non-null `VotingDatabaseHandle` pointer.
/// - `round_id` must be a valid UTF-8 pointer with its stated length.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn zcashlc_voting_get_keystone_signatures(
    db: *mut VotingDatabaseHandle,
    round_id: *const u8,
    round_id_len: usize,
) -> *mut crate::ffi::BoxedSlice {
    let db = AssertUnwindSafe(db);
    let res = catch_panic(|| {
        let handle =
            unsafe { db.as_ref() }.ok_or_else(|| anyhow!("VotingDatabaseHandle is null"))?;
        let round_id_str = unsafe { str_from_ptr(round_id, round_id_len) }?;
        let sigs = handle
            .db
            .get_keystone_signatures(&round_id_str)
            .map_err(|e| anyhow!("get_keystone_signatures failed: {}", e))?;

        let out: Vec<JsonKeystoneSignatureRecord> = sigs.into_iter().map(Into::into).collect();

        json_to_boxed_slice(&out)
    });
    unwrap_exc_or_null(res)
}

/// Remove all recovery-state rows for a round — TX hashes, commitment
/// bundles, and Keystone signatures — once the round is fully submitted
/// and no longer needs crash-recovery metadata.
///
/// # Safety
///
/// - `db` must be a valid, non-null `VotingDatabaseHandle` pointer.
/// - `round_id` must be a valid UTF-8 pointer with its stated length.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn zcashlc_voting_clear_recovery_state(
    db: *mut VotingDatabaseHandle,
    round_id: *const u8,
    round_id_len: usize,
) -> i32 {
    let db = AssertUnwindSafe(db);
    let res = catch_panic(|| {
        let handle =
            unsafe { db.as_ref() }.ok_or_else(|| anyhow!("VotingDatabaseHandle is null"))?;
        let round_id_str = unsafe { str_from_ptr(round_id, round_id_len) }?;
        handle
            .db
            .clear_recovery_state(&round_id_str)
            .map_err(|e| anyhow!("clear_recovery_state failed: {}", e))?;
        Ok(0)
    });
    unwrap_exc_or(res, -1)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::ffi::zcashlc_free_boxed_slice;
    use crate::voting::db::zcashlc_voting_db_free;
    use crate::voting::share_tracking::{
        zcashlc_voting_get_share_delegations, zcashlc_voting_record_share_delegation,
    };
    use crate::voting::test_helpers::{insert_round_and_bundle, open_memory_db};
    use serde::de::DeserializeOwned;

    fn decode_boxed_json<T: DeserializeOwned>(ptr: *mut crate::ffi::BoxedSlice) -> T {
        assert!(!ptr.is_null());
        let json = unsafe { (*ptr).as_slice() }.to_vec();
        let value = serde_json::from_slice(&json).expect("decode boxed JSON");
        unsafe { zcashlc_free_boxed_slice(ptr) };
        value
    }

    fn insert_vote(db: *mut VotingDatabaseHandle, round_id: &str) {
        let handle = unsafe { db.as_ref() }.expect("voting db handle");
        handle
            .db
            .insert_vote_fixture(round_id, 0, 0, 0, &[0xaa; 32])
            .expect("insert vote");
    }

    #[test]
    fn delegation_tx_hash_round_trips() {
        let db = open_memory_db();
        let round_id = b"round";
        insert_round_and_bundle(db, "round");
        let tx_hash = b"delegation-tx";

        let code = unsafe {
            zcashlc_voting_store_delegation_tx_hash(
                db,
                round_id.as_ptr(),
                round_id.len(),
                0,
                tx_hash.as_ptr(),
                tx_hash.len(),
            )
        };
        assert_eq!(code, 0);

        let result = unsafe {
            zcashlc_voting_get_delegation_tx_hash(db, round_id.as_ptr(), round_id.len(), 0)
        };
        let actual: Option<String> = decode_boxed_json(result);
        assert_eq!(actual.as_deref(), Some("delegation-tx"));

        unsafe { zcashlc_voting_db_free(db) };
    }

    #[test]
    fn vote_tx_hash_round_trips() {
        let db = open_memory_db();
        let round_id = b"round";
        insert_round_and_bundle(db, "round");
        insert_vote(db, "round");
        let tx_hash = b"vote-tx";

        let code = unsafe {
            zcashlc_voting_store_vote_tx_hash(
                db,
                round_id.as_ptr(),
                round_id.len(),
                0,
                0,
                tx_hash.as_ptr(),
                tx_hash.len(),
            )
        };
        assert_eq!(code, 0);

        let result =
            unsafe { zcashlc_voting_get_vote_tx_hash(db, round_id.as_ptr(), round_id.len(), 0, 0) };
        let actual: Option<String> = decode_boxed_json(result);
        assert_eq!(actual.as_deref(), Some("vote-tx"));

        unsafe { zcashlc_voting_db_free(db) };
    }

    #[test]
    fn commitment_bundle_round_trips() {
        let db = open_memory_db();
        let round_id = b"round";
        insert_round_and_bundle(db, "round");
        insert_vote(db, "round");
        let bundle_json = br#"{"bundle":true}"#;

        let code = unsafe {
            zcashlc_voting_store_commitment_bundle(
                db,
                round_id.as_ptr(),
                round_id.len(),
                0,
                0,
                bundle_json.as_ptr(),
                bundle_json.len(),
                42,
            )
        };
        assert_eq!(code, 0);

        let result = unsafe {
            zcashlc_voting_get_commitment_bundle(db, round_id.as_ptr(), round_id.len(), 0, 0)
        };
        let actual: Option<(String, u64)> = decode_boxed_json(result);
        assert_eq!(actual, Some((r#"{"bundle":true}"#.to_string(), 42)));

        unsafe { zcashlc_voting_db_free(db) };
    }

    #[test]
    fn store_keystone_signature_round_trips_valid_signature() {
        let db = open_memory_db();
        let round_id = b"round";
        insert_round_and_bundle(db, "round");
        let sig = [1u8; KEYSTONE_SIGNATURE_LEN];
        let sighash = [2u8; PCZT_SIGHASH_LEN];
        let rk = [3u8; RANDOMIZED_KEY_LEN];

        let code = unsafe {
            zcashlc_voting_store_keystone_signature(
                db,
                round_id.as_ptr(),
                round_id.len(),
                0,
                sig.as_ptr(),
                sig.len(),
                sighash.as_ptr(),
                sighash.len(),
                rk.as_ptr(),
                rk.len(),
            )
        };
        assert_eq!(code, 0);

        let result = unsafe {
            zcashlc_voting_get_keystone_signatures(db, round_id.as_ptr(), round_id.len())
        };
        assert!(!result.is_null());
        let json = unsafe { (*result).as_slice() }.to_vec();
        let actual: serde_json::Value =
            serde_json::from_slice(&json).expect("keystone signatures json");
        assert_eq!(
            actual,
            serde_json::json!([
                {
                    "bundle_index": 0,
                    "sig": sig.to_vec(),
                    "sighash": sighash.to_vec(),
                    "rk": rk.to_vec(),
                }
            ])
        );

        unsafe { zcashlc_free_boxed_slice(result) };
        unsafe { zcashlc_voting_db_free(db) };
    }

    #[test]
    fn store_keystone_signature_rejects_invalid_lengths() {
        let db = open_memory_db();
        let round_id = b"round";
        insert_round_and_bundle(db, "round");
        let sig = [1u8; KEYSTONE_SIGNATURE_LEN];
        let sighash = [2u8; PCZT_SIGHASH_LEN];
        let rk = [3u8; RANDOMIZED_KEY_LEN];

        let cases = [
            (
                KEYSTONE_SIGNATURE_LEN - 1,
                PCZT_SIGHASH_LEN,
                RANDOMIZED_KEY_LEN,
            ),
            (
                KEYSTONE_SIGNATURE_LEN,
                PCZT_SIGHASH_LEN - 1,
                RANDOMIZED_KEY_LEN,
            ),
            (
                KEYSTONE_SIGNATURE_LEN,
                PCZT_SIGHASH_LEN,
                RANDOMIZED_KEY_LEN - 1,
            ),
        ];

        for (sig_len, sighash_len, rk_len) in cases {
            let code = unsafe {
                zcashlc_voting_store_keystone_signature(
                    db,
                    round_id.as_ptr(),
                    round_id.len(),
                    0,
                    sig.as_ptr(),
                    sig_len,
                    sighash.as_ptr(),
                    sighash_len,
                    rk.as_ptr(),
                    rk_len,
                )
            };
            assert_eq!(code, -1);
        }

        unsafe { zcashlc_voting_db_free(db) };
    }

    #[test]
    fn clear_recovery_state_removes_stored_recovery_data() {
        let db = open_memory_db();
        let round_id = b"round";
        insert_round_and_bundle(db, "round");
        insert_vote(db, "round");

        let delegation_tx = b"delegation-tx";
        assert_eq!(
            unsafe {
                zcashlc_voting_store_delegation_tx_hash(
                    db,
                    round_id.as_ptr(),
                    round_id.len(),
                    0,
                    delegation_tx.as_ptr(),
                    delegation_tx.len(),
                )
            },
            0
        );

        let vote_tx = b"vote-tx";
        assert_eq!(
            unsafe {
                zcashlc_voting_store_vote_tx_hash(
                    db,
                    round_id.as_ptr(),
                    round_id.len(),
                    0,
                    0,
                    vote_tx.as_ptr(),
                    vote_tx.len(),
                )
            },
            0
        );

        let bundle_json = br#"{"bundle":true}"#;
        assert_eq!(
            unsafe {
                zcashlc_voting_store_commitment_bundle(
                    db,
                    round_id.as_ptr(),
                    round_id.len(),
                    0,
                    0,
                    bundle_json.as_ptr(),
                    bundle_json.len(),
                    42,
                )
            },
            0
        );

        let sig = [1u8; KEYSTONE_SIGNATURE_LEN];
        let sighash = [2u8; PCZT_SIGHASH_LEN];
        let rk = [3u8; RANDOMIZED_KEY_LEN];
        assert_eq!(
            unsafe {
                zcashlc_voting_store_keystone_signature(
                    db,
                    round_id.as_ptr(),
                    round_id.len(),
                    0,
                    sig.as_ptr(),
                    sig.len(),
                    sighash.as_ptr(),
                    sighash.len(),
                    rk.as_ptr(),
                    rk.len(),
                )
            },
            0
        );

        let urls_json = br#"["https://helper.example"]"#;
        let nullifier = [0xaa; 32];
        assert_eq!(
            unsafe {
                zcashlc_voting_record_share_delegation(
                    db,
                    round_id.as_ptr(),
                    round_id.len(),
                    0,
                    0,
                    0,
                    urls_json.as_ptr(),
                    urls_json.len(),
                    nullifier.as_ptr(),
                    nullifier.len(),
                    0,
                )
            },
            0
        );

        assert_eq!(
            unsafe { zcashlc_voting_clear_recovery_state(db, round_id.as_ptr(), round_id.len()) },
            0
        );

        let delegation_tx: Option<String> = decode_boxed_json(unsafe {
            zcashlc_voting_get_delegation_tx_hash(db, round_id.as_ptr(), round_id.len(), 0)
        });
        assert_eq!(delegation_tx, None);

        let vote_tx: Option<String> = decode_boxed_json(unsafe {
            zcashlc_voting_get_vote_tx_hash(db, round_id.as_ptr(), round_id.len(), 0, 0)
        });
        assert_eq!(vote_tx, None);

        let commitment_bundle: Option<(String, u64)> = decode_boxed_json(unsafe {
            zcashlc_voting_get_commitment_bundle(db, round_id.as_ptr(), round_id.len(), 0, 0)
        });
        assert_eq!(commitment_bundle, None);

        let keystone_sigs: Vec<serde_json::Value> = decode_boxed_json(unsafe {
            zcashlc_voting_get_keystone_signatures(db, round_id.as_ptr(), round_id.len())
        });
        assert!(keystone_sigs.is_empty());

        let share_delegations: Vec<serde_json::Value> = decode_boxed_json(unsafe {
            zcashlc_voting_get_share_delegations(db, round_id.as_ptr(), round_id.len())
        });
        assert!(share_delegations.is_empty());

        unsafe { zcashlc_voting_db_free(db) };
    }
}
