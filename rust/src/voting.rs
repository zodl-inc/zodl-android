#![allow(clippy::missing_safety_doc, unused_imports)]

//! C FFI for shielded voting (`zcashlc_voting_*`).
//!
//! Follows the same patterns as `lib.rs` and `ffi.rs`:
//! - Exports: `#[unsafe(no_mangle)] pub unsafe extern "C" fn zcashlc_voting_*`
//! - Errors: `catch_panic()` with `unwrap_exc_or_null()` / `unwrap_exc_or()`
//! - Opaque handles: `Box::into_raw` / `Box::from_raw`
//! - JSON: serde types in the `json` submodule, re-exported here for `super::…` imports
//! - Small C-facing structs: `#[repr(C)]` where needed
//!
//! **Layout:** `ffi_types`, `helpers`, `json`, `progress`, and `util` are crate-private
//! helpers. Subsystems that expose FFI live in `db`, `delegation`, `notes`, `recovery`,
//! `rounds`, `vote`, `share_tracking`, and `tree`. `json`, `db`, and `share_tracking` use
//! `pub use …::*` so shared types and selected symbols also appear at `crate::voting::…` for
//! cbindgen and tests. Sibling modules refer to each other via `super::module::…` or
//! flattened `super::Json…` imports.

mod ffi_types;
mod helpers;
mod json;
mod progress;
mod util;

pub use json::*;

pub mod db;
pub use db::*;

pub mod delegation;
pub mod notes;
pub mod recovery;
pub mod rounds;
pub mod vote;

pub mod share_tracking;
pub use share_tracking::*;

#[cfg(test)]
pub(crate) mod test_helpers;
pub mod tree;
