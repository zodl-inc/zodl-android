use super::db::{VotingDatabaseHandle, zcashlc_voting_db_open, zcashlc_voting_set_wallet_id};

pub(crate) fn open_memory_db() -> *mut VotingDatabaseHandle {
    let path = b":memory:";
    let db = unsafe { zcashlc_voting_db_open(path.as_ptr(), path.len()) };
    assert!(!db.is_null());

    let wallet = b"wallet";
    let code = unsafe { zcashlc_voting_set_wallet_id(db, wallet.as_ptr(), wallet.len()) };
    assert_eq!(code, 0);

    db
}

pub(crate) fn insert_round_and_bundle(db: *mut VotingDatabaseHandle, round_id: &str) {
    let handle = unsafe { db.as_ref() }.expect("db handle");
    let params = zcash_voting::VotingRoundParams {
        vote_round_id: round_id.to_string(),
        snapshot_height: 123,
        ea_pk: vec![7u8; 32],
        nc_root: vec![8u8; 32],
        nullifier_imt_root: vec![9u8; 32],
    };
    handle.db.init_round(&params, None).expect("insert round");

    let notes: Vec<zcash_voting::NoteInfo> = (0..5)
        .map(|position| zcash_voting::NoteInfo {
            commitment: vec![1u8; 32],
            nullifier: vec![2u8; 32],
            value: 13_000_000,
            position,
            diversifier: vec![0u8; 11],
            rho: vec![3u8; 32],
            rseed: vec![4u8; 32],
            scope: 0,
            ufvk_str: String::new(),
        })
        .collect();
    let (count, _) = handle
        .db
        .setup_bundles(round_id, &notes)
        .expect("setup bundle");
    assert_eq!(count, 1);
}
