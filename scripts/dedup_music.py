import sqlite3
import os
from pathlib import Path
from datetime import datetime

BASE = Path(__file__).resolve().parent.parent
DB_PATH = BASE / 'moodtune.db'
STATIC_DIR = BASE / 'static'


def exists_static(filename: str) -> bool:
    if not filename:
        return False
    return (STATIC_DIR / filename).exists()


def parse_ts(ts):
    if not ts:
        return 0
    try:
        # SQLite CURRENT_TIMESTAMP default is 'YYYY-MM-DD HH:MM:SS'
        return int(datetime.strptime(ts.split('.')[0], '%Y-%m-%d %H:%M:%S').timestamp())
    except Exception:
        return 0


def dedup_songs(conn):
    conn.row_factory = sqlite3.Row
    rows = conn.execute("SELECT id, song_title, artist, album_cover, audio_file, mood, duration, is_popular, play_count, created_at FROM songs").fetchall()
    groups = {}
    for r in rows:
        key = f"{(r['song_title'] or '').strip().lower()}|{(r['artist'] or '').strip().lower()}"
        groups.setdefault(key, []).append(r)

    to_delete = []
    kept = []

    for key, items in groups.items():
        if len(items) <= 1:
            continue
        # Score candidates: prefer ones with valid audio and cover, popular, higher play_count, newer created_at
        def score(row):
            audio_ok = 1 if exists_static(row['audio_file']) else 0
            cover_ok = 1 if exists_static(row['album_cover']) else 0
            is_pop = int(row['is_popular'] or 0)
            plays = int(row['play_count'] or 0)
            ts = parse_ts(row['created_at'])
            return audio_ok * 1000 + cover_ok * 500 + is_pop * 100 + plays * 10 + ts
        sorted_items = sorted(items, key=score, reverse=True)
        keeper = sorted_items[0]
        kept.append(keeper['id'])
        for dup in sorted_items[1:]:
            to_delete.append(dup['id'])

    if to_delete:
        conn.executemany("DELETE FROM songs WHERE id = ?", [(i,) for i in to_delete])
    print(f"Songs dedup: kept={len(kept)} groups, deleted={len(to_delete)} duplicates")


def dedup_recently_played(conn):
    conn.row_factory = sqlite3.Row
    rows = conn.execute("SELECT id, user_id, song_title, artist, album_cover, audio_file, mood, played_at FROM recently_played").fetchall()
    groups = {}
    for r in rows:
        key = (
            int(r['user_id'] or 0),
            (r['song_title'] or '').strip().lower(),
            (r['artist'] or '').strip().lower(),
            (r['audio_file'] or '').strip()
        )
        groups.setdefault(key, []).append(r)

    to_delete = []
    kept = []

    for key, items in groups.items():
        if len(items) <= 1:
            continue
        # Keep the most recent played_at with valid audio file if possible
        def score(row):
            audio_ok = 1 if exists_static(row['audio_file']) else 0
            ts = parse_ts(row['played_at'])
            return audio_ok * 1000 + ts
        sorted_items = sorted(items, key=score, reverse=True)
        keeper = sorted_items[0]
        kept.append(keeper['id'])
        for dup in sorted_items[1:]:
            to_delete.append(dup['id'])

    if to_delete:
        conn.executemany("DELETE FROM recently_played WHERE id = ?", [(i,) for i in to_delete])
    print(f"Recently played dedup: kept={len(kept)} groups, deleted={len(to_delete)} duplicates")


def main():
    if not DB_PATH.exists():
        raise FileNotFoundError(f"DB not found: {DB_PATH}")
    conn = sqlite3.connect(str(DB_PATH))
    try:
        dedup_songs(conn)
        dedup_recently_played(conn)
        conn.commit()
    finally:
        conn.close()
    print("Deduplication complete.")


if __name__ == '__main__':
    main()