use std::ffi::CString;
use std::fmt::Write;
use std::os::raw::c_char;
use std::panic::AssertUnwindSafe;

use anyhow::anyhow;
use ffi_helpers::panic::catch_panic;
use serde::{Deserialize, Serialize};
use zcash_voting as voting;

use crate::{unwrap_exc_or, unwrap_exc_or_null};

use super::db::VotingDatabaseHandle;
use super::helpers::{bytes_from_ptr, json_to_boxed_slice, str_from_ptr};

// =============================================================================
// Share delegation tracking
// =============================================================================

const SHARE_NULLIFIER_LEN: usize = 32;

/// JSON representation for share delegation records crossing the FFI boundary.
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct JsonShareDelegationRecord {
    pub round_id: String,
    pub bundle_index: u32,
    pub proposal_id: u32,
    pub share_index: u32,
    pub sent_to_urls: Vec<String>,
    pub nullifier: Vec<u8>,
    pub confirmed: bool,
    pub submit_at: u64,
    pub created_at: u64,
}

impl From<voting::ShareDelegationRecord> for JsonShareDelegationRecord {
    fn from(r: voting::ShareDelegationRecord) -> Self {
        Self {
            round_id: r.round_id,
            bundle_index: r.bundle_index,
            proposal_id: r.proposal_id,
            share_index: r.share_index,
            sent_to_urls: r.sent_to_urls,
            nullifier: r.nullifier,
            confirmed: r.confirmed,
            submit_at: r.submit_at,
            created_at: r.created_at,
        }
    }
}

fn bytes_to_hex(bytes: &[u8]) -> String {
    let mut hex = String::with_capacity(bytes.len() * 2);
    for b in bytes {
        write!(&mut hex, "{b:02x}").expect("writing to a String cannot fail");
    }
    hex
}

/// Compute the share reveal nullifier from client-known inputs.
///
/// Returns the 32-byte nullifier as a hex string (64 chars), or null on error.
///
/// # Safety
///
/// - `vote_commitment` must point to exactly 32 bytes.
/// - `primary_blind` must point to exactly 32 bytes.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn zcashlc_voting_compute_share_nullifier(
    vote_commitment: *const u8,
    primary_blind: *const u8,
    share_index: u32,
) -> *mut c_char {
    let res = catch_panic(|| {
        let vc: [u8; 32] = unsafe { std::slice::from_raw_parts(vote_commitment, 32) }
            .try_into()
            .map_err(|_| anyhow!("vote_commitment must be exactly 32 bytes"))?;
        let blind: [u8; 32] = unsafe { std::slice::from_raw_parts(primary_blind, 32) }
            .try_into()
            .map_err(|_| anyhow!("primary_blind must be exactly 32 bytes"))?;

        let nullifier = voting::share_tracking::compute_share_nullifier(&vc, share_index, &blind)
            .map_err(|e| anyhow!("compute_share_nullifier failed: {}", e))?;

        let hex_str = bytes_to_hex(&nullifier);
        let c_str = CString::new(hex_str).map_err(|e| anyhow!("null byte in hex string: {}", e))?;
        Ok(c_str.into_raw())
    });
    unwrap_exc_or_null(res)
}

/// Record a share delegation after sending to helper servers.
///
/// Returns 0 on success, -1 on error.
///
/// # Safety
///
/// - `db` must be a valid, non-null `VotingDatabaseHandle` pointer.
/// - String params must be valid UTF-8 pointers with correct lengths.
/// - `nullifier_ptr` must point to `nullifier_len` bytes.
/// - `sent_to_urls_json` must be a JSON array of strings.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn zcashlc_voting_record_share_delegation(
    db: *mut VotingDatabaseHandle,
    round_id: *const u8,
    round_id_len: usize,
    bundle_index: u32,
    proposal_id: u32,
    share_index: u32,
    sent_to_urls_json: *const u8,
    sent_to_urls_json_len: usize,
    nullifier_ptr: *const u8,
    nullifier_len: usize,
    submit_at: u64,
) -> i32 {
    let db = AssertUnwindSafe(db);
    let res = catch_panic(|| {
        let handle =
            unsafe { db.as_ref() }.ok_or_else(|| anyhow!("VotingDatabaseHandle is null"))?;
        let round_id_str = unsafe { str_from_ptr(round_id, round_id_len) }?;
        let urls_bytes = unsafe { bytes_from_ptr(sent_to_urls_json, sent_to_urls_json_len) }?;
        let sent_to_urls: Vec<String> = serde_json::from_slice(urls_bytes)?;
        let nullifier = unsafe { bytes_from_ptr(nullifier_ptr, nullifier_len) }?;
        if nullifier.len() != SHARE_NULLIFIER_LEN {
            return Err(anyhow!(
                "nullifier must be {} bytes, got {}",
                SHARE_NULLIFIER_LEN,
                nullifier.len()
            ));
        }

        handle
            .db
            .record_share_delegation(
                &round_id_str,
                bundle_index,
                proposal_id,
                share_index,
                &sent_to_urls,
                nullifier,
                submit_at,
            )
            .map_err(|e| anyhow!("record_share_delegation failed: {}", e))?;
        Ok(0)
    });
    unwrap_exc_or(res, -1)
}

/// Get all share delegations for a round.
///
/// Returns a JSON array of `JsonShareDelegationRecord`, or null on error.
///
/// # Safety
///
/// - `db` must be a valid, non-null `VotingDatabaseHandle` pointer.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn zcashlc_voting_get_share_delegations(
    db: *mut VotingDatabaseHandle,
    round_id: *const u8,
    round_id_len: usize,
) -> *mut crate::ffi::BoxedSlice {
    let db = AssertUnwindSafe(db);
    let res = catch_panic(|| {
        let handle =
            unsafe { db.as_ref() }.ok_or_else(|| anyhow!("VotingDatabaseHandle is null"))?;
        let round_id_str = unsafe { str_from_ptr(round_id, round_id_len) }?;

        let records = handle
            .db
            .get_share_delegations(&round_id_str)
            .map_err(|e| anyhow!("get_share_delegations failed: {}", e))?;

        let json_records: Vec<JsonShareDelegationRecord> =
            records.into_iter().map(Into::into).collect();
        json_to_boxed_slice(&json_records)
    });
    unwrap_exc_or_null(res)
}

/// Get unconfirmed share delegations for a round.
///
/// Returns a JSON array of `JsonShareDelegationRecord`, or null on error.
///
/// # Safety
///
/// - `db` must be a valid, non-null `VotingDatabaseHandle` pointer.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn zcashlc_voting_get_unconfirmed_delegations(
    db: *mut VotingDatabaseHandle,
    round_id: *const u8,
    round_id_len: usize,
) -> *mut crate::ffi::BoxedSlice {
    let db = AssertUnwindSafe(db);
    let res = catch_panic(|| {
        let handle =
            unsafe { db.as_ref() }.ok_or_else(|| anyhow!("VotingDatabaseHandle is null"))?;
        let round_id_str = unsafe { str_from_ptr(round_id, round_id_len) }?;

        let records = handle
            .db
            .get_unconfirmed_delegations(&round_id_str)
            .map_err(|e| anyhow!("get_unconfirmed_delegations failed: {}", e))?;

        let json_records: Vec<JsonShareDelegationRecord> =
            records.into_iter().map(Into::into).collect();
        json_to_boxed_slice(&json_records)
    });
    unwrap_exc_or_null(res)
}

/// Mark a share delegation as confirmed on-chain.
///
/// Returns 0 on success, -1 on error.
///
/// # Safety
///
/// - `db` must be a valid, non-null `VotingDatabaseHandle` pointer.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn zcashlc_voting_mark_share_confirmed(
    db: *mut VotingDatabaseHandle,
    round_id: *const u8,
    round_id_len: usize,
    bundle_index: u32,
    proposal_id: u32,
    share_index: u32,
) -> i32 {
    let db = AssertUnwindSafe(db);
    let res = catch_panic(|| {
        let handle =
            unsafe { db.as_ref() }.ok_or_else(|| anyhow!("VotingDatabaseHandle is null"))?;
        let round_id_str = unsafe { str_from_ptr(round_id, round_id_len) }?;

        handle
            .db
            .mark_share_confirmed(&round_id_str, bundle_index, proposal_id, share_index)
            .map_err(|e| anyhow!("mark_share_confirmed failed: {}", e))?;
        Ok(0)
    });
    unwrap_exc_or(res, -1)
}

/// Append new server URLs to a share delegation's sent_to_urls.
///
/// Returns 0 on success, -1 on error.
///
/// # Safety
///
/// - `db` must be a valid, non-null `VotingDatabaseHandle` pointer.
/// - `new_urls_json` must be a JSON array of strings.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn zcashlc_voting_add_sent_servers(
    db: *mut VotingDatabaseHandle,
    round_id: *const u8,
    round_id_len: usize,
    bundle_index: u32,
    proposal_id: u32,
    share_index: u32,
    new_urls_json: *const u8,
    new_urls_json_len: usize,
) -> i32 {
    let db = AssertUnwindSafe(db);
    let res = catch_panic(|| {
        let handle =
            unsafe { db.as_ref() }.ok_or_else(|| anyhow!("VotingDatabaseHandle is null"))?;
        let round_id_str = unsafe { str_from_ptr(round_id, round_id_len) }?;
        let urls_bytes = unsafe { bytes_from_ptr(new_urls_json, new_urls_json_len) }?;
        let new_urls: Vec<String> = serde_json::from_slice(urls_bytes)?;

        handle
            .db
            .add_sent_servers(
                &round_id_str,
                bundle_index,
                proposal_id,
                share_index,
                &new_urls,
            )
            .map_err(|e| anyhow!("add_sent_servers failed: {}", e))?;
        Ok(0)
    });
    unwrap_exc_or(res, -1)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::ffi::zcashlc_free_boxed_slice;
    use crate::voting::db::zcashlc_voting_db_free;
    use crate::voting::test_helpers::{insert_round_and_bundle, open_memory_db};

    #[test]
    fn record_share_delegation_rejects_invalid_nullifier_length() {
        let db = open_memory_db();
        let round_id = b"round";
        insert_round_and_bundle(db, "round");
        let urls_json = br#"["https://helper.example"]"#;
        let nullifier = [0xaa; SHARE_NULLIFIER_LEN - 1];

        let code = unsafe {
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
        };

        assert_eq!(code, -1);
        unsafe { zcashlc_voting_db_free(db) };
    }

    #[test]
    fn record_share_delegation_round_trips_byte_nullifier() {
        let db = open_memory_db();
        let round_id = b"round";
        insert_round_and_bundle(db, "round");
        let urls_json = br#"["https://helper.example"]"#;
        let nullifier = (0u8..SHARE_NULLIFIER_LEN as u8).collect::<Vec<_>>();

        let code = unsafe {
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
        };
        assert_eq!(code, 0);

        let result =
            unsafe { zcashlc_voting_get_share_delegations(db, round_id.as_ptr(), round_id.len()) };
        assert!(!result.is_null());
        let json = unsafe { (*result).as_slice() }.to_vec();
        let records: Vec<JsonShareDelegationRecord> =
            serde_json::from_slice(&json).expect("share delegation records");
        assert_eq!(records.len(), 1);
        assert_eq!(records[0].nullifier, nullifier);

        unsafe { zcashlc_free_boxed_slice(result) };
        unsafe { zcashlc_voting_db_free(db) };
    }
}
