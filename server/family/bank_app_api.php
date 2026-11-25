<?php
/* ============================================================
   Family Bank for Android — sync endpoint   (PHP 5.6 compatible)
   /family/bank_app_api.php

   Used only by the stand-alone "Family Bank" phone app. It reads
   and writes the same tables as the Family Bank page on the
   website (family_kids, family_transactions), so the phone and
   the website always show the same children and balances.

     POST ?action=login    {username, password, device} → {token, user}
     POST ?action=logout   ends this phone's key
     POST ?action=whoami   the signed-in member
     POST ?action=sync     {kids:[…], entries:[…]} → id map + full snapshot

   Rules (the same as the website):
     • Each family sees only its own children.
     • Only parents can add, rename or remove children, or record
       deposits and withdrawals. Children see their family's bank
       read-only.

   Sign-in keys: the phone gets a random 64-character key. Only its
   SHA-256 hash is stored (family_bank_app_tokens). A key ends when
   the member signs out on that phone, changes their password, or
   doesn't use the app for 180 days.

   Needs no SQL import: its two small tables are created the first
   time the app signs in. Words used by the database:
     type 'borrow'  = Deposit  (the family holds more for the child)
     type 'payback' = Withdraw (money given back to the child)
   ============================================================ */
@ini_set('display_errors', '0');
require_once __DIR__ . '/lib.php';
@ini_set('display_errors', '0');
if (file_exists(__DIR__ . '/family_scope.php')) { require_once __DIR__ . '/family_scope.php'; }

header('Cache-Control: no-store');

/* ---------- input ---------- */
$BANK_IN = array();
$raw = file_get_contents('php://input');
if (is_string($raw) && $raw !== '') {
    $j = json_decode($raw, true);
    if (is_array($j)) { $BANK_IN = $j; }
}
if (!$BANK_IN && !empty($_POST)) { $BANK_IN = $_POST; }

function bank_str($a, $k, $d = '')
{
    return (isset($a[$k]) && (is_string($a[$k]) || is_numeric($a[$k]))) ? trim((string) $a[$k]) : $d;
}

function bank_num($a, $k, $d = 0)
{
    return (isset($a[$k]) && is_numeric($a[$k])) ? $a[$k] + 0 : $d;
}

function bank_cut($s, $n)
{
    return function_exists('mb_substr') ? mb_substr($s, 0, $n, 'UTF-8') : substr($s, 0, $n);
}

/* ---------- tables (created once) ---------- */
function bank_tables()
{
    db()->exec('CREATE TABLE IF NOT EXISTS family_bank_app_tokens (
        id INT AUTO_INCREMENT PRIMARY KEY,
        user_id INT NOT NULL,
        token_hash CHAR(64) NOT NULL,
        device VARCHAR(120) NOT NULL DEFAULT \'\',
        pw_check CHAR(64) NOT NULL DEFAULT \'\',
        created_at DATETIME NOT NULL,
        last_used DATETIME NOT NULL,
        UNIQUE KEY token_hash (token_hash),
        KEY user_id (user_id)
    ) DEFAULT CHARSET=utf8mb4');
    /* Remembers which phone row became which website row, so a sync that
       is sent twice (signal dropped before the answer came back) never
       records the same deposit twice. */
    db()->exec('CREATE TABLE IF NOT EXISTS family_bank_app_cids (
        cid VARCHAR(40) NOT NULL PRIMARY KEY,
        family_id INT NOT NULL,
        kind CHAR(1) NOT NULL,
        rid INT NOT NULL,
        created_at DATETIME NOT NULL
    ) DEFAULT CHARSET=utf8mb4');
}

/* Fingerprint of the member's password hash: a password change anywhere signs out every phone. */
function bank_pw_check($userId)
{
    $parts = array();
    foreach (array(
        'SELECT password_hash FROM family_users WHERE id = ?',
        'SELECT gb.password_hash FROM family_users fu JOIN gb_board_users gb ON gb.id = fu.gb_user_id WHERE fu.id = ?',
    ) as $sql) {
        try {
            $st = db()->prepare($sql);
            $st->execute(array($userId));
            $parts[] = (string) $st->fetchColumn();
        } catch (Exception $e) { $parts[] = ''; }
    }
    return hash('sha256', 'bank|' . implode('|', $parts));
}

function bank_random_key()
{
    if (function_exists('random_bytes')) { return bin2hex(random_bytes(32)); }
    return bin2hex(openssl_random_pseudo_bytes(32));
}

function bank_member($userId)
{
    $st = db()->prepare('SELECT id, username, display_name, role, family_id FROM family_users WHERE id = ?');
    $st->execute(array((int) $userId));
    return $st->fetch();
}

function bank_user_out($u)
{
    $name = '';
    if (!empty($u['family_id'])) {
        try {
            $st = db()->prepare('SELECT family_name FROM family_families WHERE id = ?');
            $st->execute(array((int) $u['family_id']));
            $name = (string) $st->fetchColumn();
        } catch (Exception $e) { $name = ''; }
    }
    return array(
        'username'     => (string) $u['username'],
        'display_name' => (string) $u['display_name'],
        'role'         => (string) $u['role'],
        'family_name'  => $name,
    );
}

function bank_token_from_request()
{
    $h = '';
    if (!empty($_SERVER['HTTP_AUTHORIZATION'])) { $h = $_SERVER['HTTP_AUTHORIZATION']; }
    elseif (!empty($_SERVER['REDIRECT_HTTP_AUTHORIZATION'])) { $h = $_SERVER['REDIRECT_HTTP_AUTHORIZATION']; }
    if (preg_match('/^Bearer\s+([0-9a-f]{64})$/i', trim($h), $m)) { return strtolower($m[1]); }
    $x = isset($_SERVER['HTTP_X_APP_TOKEN']) ? trim($_SERVER['HTTP_X_APP_TOKEN']) : '';
    return preg_match('/^[0-9a-f]{64}$/i', $x) ? strtolower($x) : '';
}

/* The signed-in member for this phone, or a 401 reply. */
function bank_user()
{
    $tok = bank_token_from_request();
    $bye = array('ok' => false, 'error' => 'This phone was signed out. Sign in again.');
    if ($tok === '') { json_out($bye, 401); }
    db()->exec('DELETE FROM family_bank_app_tokens WHERE last_used < DATE_SUB(NOW(), INTERVAL 180 DAY)');
    $st = db()->prepare('SELECT id, user_id, pw_check FROM family_bank_app_tokens WHERE token_hash = ?');
    $st->execute(array(hash('sha256', $tok)));
    $row = $st->fetch();
    if (!$row) { json_out($bye, 401); }
    $u = bank_member($row['user_id']);
    if (!$u || (string) $row['pw_check'] !== bank_pw_check((int) $row['user_id'])) {
        db()->prepare('DELETE FROM family_bank_app_tokens WHERE id = ?')->execute(array($row['id']));
        json_out($u ? array('ok' => false, 'error' => 'Your password changed. Sign in again.') : $bye, 401);
    }
    if (empty($u['family_id'])) { json_out(array('ok' => false, 'error' => 'Your account isn’t part of a family any more.'), 403); }
    db()->prepare('UPDATE family_bank_app_tokens SET last_used = NOW() WHERE id = ?')->execute(array($row['id']));
    $u['token_id'] = (int) $row['id'];
    return $u;
}

function bank_cid_get($cid, $fam, $kind)
{
    $st = db()->prepare('SELECT rid FROM family_bank_app_cids WHERE cid = ? AND family_id = ? AND kind = ?');
    $st->execute(array($cid, (int) $fam, $kind));
    $r = $st->fetchColumn();
    return $r ? (int) $r : 0;
}

function bank_cid_put($cid, $fam, $kind, $rid)
{
    db()->prepare('INSERT IGNORE INTO family_bank_app_cids (cid, family_id, kind, rid, created_at) VALUES (?,?,?,?,NOW())')
        ->execute(array($cid, (int) $fam, $kind, (int) $rid));
}

function bank_kid_in_family($kidId, $fam)
{
    $st = db()->prepare('SELECT id, name FROM family_kids WHERE id = ? AND family_id = ?');
    $st->execute(array((int) $kidId, (int) $fam));
    return $st->fetch();
}

function bank_valid_cid($cid)
{
    return is_string($cid) && preg_match('/^[A-Za-z0-9-]{8,40}$/', $cid);
}

/* ============================================================ */

$action = isset($_GET['action']) ? (string) $_GET['action'] : bank_str($BANK_IN, 'action');
$method = isset($_SERVER['REQUEST_METHOD']) ? strtoupper($_SERVER['REQUEST_METHOD']) : 'GET';
if ($method !== 'POST') { json_out(array('ok' => false, 'error' => 'Use the Family Bank app.'), 405); }

try {
    bank_tables();
    if (function_exists('fs_ensure')) { fs_ensure('family_kids'); }   /* family_id on family_kids */
} catch (Exception $e) {
    json_out(array('ok' => false, 'error' => 'The website needs a quick database update that could not run. Please contact the site admin.'), 500);
}

switch ($action) {

case 'login': {
    $res = attempt_login(bank_str($BANK_IN, 'username'), isset($BANK_IN['password']) && is_string($BANK_IN['password']) ? $BANK_IN['password'] : '');
    if (empty($res['ok'])) {
        $msg = isset($res['error']) && $res['error'] ? $res['error'] : 'Wrong username or password.';
        if (!empty($res['unlinked'])) {
            $msg = 'Your account isn’t part of a family yet. Open the Family page on the website once to set one up, then sign in here.';
        }
        json_out(array('ok' => false, 'error' => $msg), 401);
    }
    $u = bank_member((int) $res['user']['id']);
    if (!$u || empty($u['family_id'])) {
        json_out(array('ok' => false, 'error' => 'Your account isn’t part of a family yet. Join one on the website first.'), 403);
    }
    $token = bank_random_key();
    db()->prepare('INSERT INTO family_bank_app_tokens (user_id, token_hash, device, pw_check, created_at, last_used) VALUES (?,?,?,?,NOW(),NOW())')
        ->execute(array((int) $u['id'], hash('sha256', $token), bank_cut(bank_str($BANK_IN, 'device', 'Phone'), 120), bank_pw_check((int) $u['id'])));
    json_out(array('ok' => true, 'token' => $token, 'user' => bank_user_out($u)));
}

case 'logout': {
    $u = bank_user();
    db()->prepare('DELETE FROM family_bank_app_tokens WHERE id = ?')->execute(array($u['token_id']));
    json_out(array('ok' => true));
}

case 'whoami': {
    $u = bank_user();
    json_out(array('ok' => true, 'user' => bank_user_out($u)));
}

case 'sync': {
    $u = bank_user();
    $fam = (int) $u['family_id'];
    $parent = $u['role'] === 'parent';
    $map = array('kids' => new stdClass(), 'entries' => new stdClass());
    $mapK = array(); $mapE = array();
    $skipped = 0;
    $db = db();

    if ($parent) {
        $db->beginTransaction();
        try {
            /* ---- children ---- */
            $kids = isset($BANK_IN['kids']) && is_array($BANK_IN['kids']) ? array_slice($BANK_IN['kids'], 0, 200) : array();
            foreach ($kids as $K) {
                if (!is_array($K)) { continue; }
                $cid = bank_str($K, 'cid');
                $rid = (int) bank_num($K, 'rid', 0);
                $name = bank_cut(preg_replace('/\s+/u', ' ', bank_str($K, 'name')), 80);
                $deleted = !empty($K['deleted']);
                $known = $rid > 0;   /* the phone already knew this child's website id */
                if (!$rid && bank_valid_cid($cid)) { $rid = bank_cid_get($cid, $fam, 'K'); }
                if ($rid) {
                    if (!bank_kid_in_family($rid, $fam)) { continue; }   /* removed on the website */
                    if ($deleted) {
                        $db->prepare('DELETE FROM family_transactions WHERE kid_id = ?')->execute(array($rid));
                        $db->prepare('DELETE FROM family_kids WHERE id = ? AND family_id = ?')->execute(array($rid, $fam));
                        continue;
                    }
                    if ($known && $name !== '') {   /* a resent "new child" never renames */
                        $db->prepare('UPDATE family_kids SET name = ? WHERE id = ? AND family_id = ?')->execute(array($name, $rid, $fam));
                    }
                    if (bank_valid_cid($cid)) { $mapK[$cid] = $rid; }
                    continue;
                }
                if ($deleted || $name === '' || !bank_valid_cid($cid)) { continue; }
                /* A child with the same name already in the family bank: use that child. */
                $st = $db->prepare('SELECT id FROM family_kids WHERE family_id = ? AND LOWER(name) = LOWER(?) ORDER BY id LIMIT 1');
                $st->execute(array($fam, $name));
                $rid = (int) $st->fetchColumn();
                if (!$rid) {
                    $db->prepare('INSERT INTO family_kids (name, family_id) VALUES (?, ?)')->execute(array($name, $fam));
                    $rid = (int) $db->lastInsertId();
                }
                bank_cid_put($cid, $fam, 'K', $rid);
                $mapK[$cid] = $rid;
            }

            /* ---- deposits and withdrawals ---- */
            $entries = isset($BANK_IN['entries']) && is_array($BANK_IN['entries']) ? array_slice($BANK_IN['entries'], 0, 2000) : array();
            $now = time();
            foreach ($entries as $E) {
                if (!is_array($E)) { continue; }
                $cid = bank_str($E, 'cid');
                $rid = (int) bank_num($E, 'rid', 0);
                if (!$rid && bank_valid_cid($cid)) { $rid = bank_cid_get($cid, $fam, 'E'); }
                if (!empty($E['deleted'])) {
                    if (!$rid) { continue; }
                    $st = $db->prepare('SELECT t.id FROM family_transactions t JOIN family_kids k ON k.id = t.kid_id WHERE t.id = ? AND k.family_id = ?');
                    $st->execute(array($rid, $fam));
                    if ($st->fetchColumn()) { $db->prepare('DELETE FROM family_transactions WHERE id = ?')->execute(array($rid)); }
                    continue;
                }
                if ($rid) { if (bank_valid_cid($cid)) { $mapE[$cid] = $rid; } continue; }   /* already recorded */
                if (!bank_valid_cid($cid)) { continue; }
                $kid = (int) bank_num($E, 'kid_rid', 0);
                $kcid = bank_str($E, 'kid_cid');
                if (!$kid && isset($mapK[$kcid])) { $kid = $mapK[$kcid]; }
                if (!$kid && bank_valid_cid($kcid)) { $kid = bank_cid_get($kcid, $fam, 'K'); }
                if (!$kid || !bank_kid_in_family($kid, $fam)) { $skipped++; continue; }
                $type = bank_str($E, 'type');
                if ($type !== 'deposit' && $type !== 'withdraw') { $skipped++; continue; }
                $amount = round((float) bank_num($E, 'amount', 0), 2);
                if ($amount < 0.01 || $amount > 1000000) { $skipped++; continue; }
                $note = bank_cut(bank_str($E, 'note'), 255);
                /* When it happened on the phone (it may have been recorded with no signal). */
                $at = (int) bank_num($E, 'at', 0);
                if ($at > 100000000000) { $at = (int) floor($at / 1000); }   /* milliseconds */
                if ($at < 946684800 || $at > $now + 300) { $at = $now; }
                /* FROM_UNIXTIME uses the database's own clock zone, the same one UNIX_TIMESTAMP reads back below. */
                $db->prepare('INSERT INTO family_transactions (kid_id, type, amount, note, created_by, created_at) VALUES (?,?,?,?,?,FROM_UNIXTIME(?))')
                   ->execute(array($kid, $type === 'deposit' ? 'borrow' : 'payback', $amount, $note === '' ? null : $note,
                                   (int) $u['id'], $at));
                $rid = (int) $db->lastInsertId();
                bank_cid_put($cid, $fam, 'E', $rid);
                $mapE[$cid] = $rid;
            }
            $db->commit();
        } catch (Exception $e) {
            $db->rollBack();
            error_log('bank_app_api sync: ' . $e->getMessage());
            json_out(array('ok' => false, 'error' => 'The website couldn’t save your changes. Try again in a moment.'), 500);
        }
        if ($mapK) { $map['kids'] = $mapK; }
        if ($mapE) { $map['entries'] = $mapE; }
    }

    /* ---- the family's whole bank, as the website has it ---- */
    $st = $db->prepare('SELECT id, name FROM family_kids WHERE family_id = ? ORDER BY name');
    $st->execute(array($fam));
    $outKids = array();
    foreach ($st->fetchAll() as $k) { $outKids[] = array('rid' => (int) $k['id'], 'name' => (string) $k['name']); }

    $st = $db->prepare(
        'SELECT t.id, t.kid_id, t.type, t.amount, t.note, UNIX_TIMESTAMP(t.created_at) AS at,
                COALESCE(NULLIF(u.display_name, \'\'), u.username, \'\') AS who
           FROM family_transactions t
           JOIN family_kids k ON k.id = t.kid_id
      LEFT JOIN family_users u ON u.id = t.created_by
          WHERE k.family_id = ?
       ORDER BY t.created_at DESC, t.id DESC
          LIMIT 20000');
    $st->execute(array($fam));
    $outE = array();
    foreach ($st->fetchAll() as $t) {
        $outE[] = array(
            'rid'     => (int) $t['id'],
            'kid_rid' => (int) $t['kid_id'],
            'type'    => $t['type'] === 'borrow' ? 'deposit' : 'withdraw',
            'amount'  => round((float) $t['amount'], 2),
            'note'    => (string) $t['note'],
            'at'      => (int) $t['at'] * 1000,
            'who'     => (string) $t['who'],
        );
    }

    json_out(array(
        'ok'        => true,
        'read_only' => $parent ? 0 : 1,
        'skipped'   => $skipped,
        'map'       => $map,
        'kids'      => $outKids,
        'entries'   => $outE,
        'user'      => bank_user_out($u),
    ));
}

default:
    json_out(array('ok' => false, 'error' => 'Unknown action.'), 400);
}
