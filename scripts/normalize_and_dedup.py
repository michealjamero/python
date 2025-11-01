import sqlite3
import re
from pathlib import Path
from datetime import datetime

BASE = Path(__file__).resolve().parent.parent
DB_PATH = BASE / 'moodtune.db'
STATIC_DIR = BASE / 'static'


def norm_text(s: str) -> str:
    if not s:
        return ''
    s = s.strip()
    # Normalize common punctuation and Unicode apostrophes/quotes
    s = s.replace('\u2019', "'").replace('\u2018', "'").replace('\u201c', '"').replace('\u201d', '"')
    s = s.lower()
    # Remove punctuation except alphanumerics and spaces
    s = re.sub(r"[^a-z0-9\s]", " ", s)
    # Collapse whitespace
    s = re.sub(r"\s+", " ", s).strip()
    return s


def exists_static(filename: str) -> bool:
    if not filename:
        return False
    return (STATIC_DIR / filename).exists()


def parse_ts(ts):
    if not ts:
        return 0
    try:
        return int(datetime.strptime(ts.split('.')[0], '%Y-%m-%d %H:%M:%S').timestamp())
    except Exception:
        return 0


def choose_keeper(items):
    def score(row):
        audio_ok = 1 if exists_static(row['audio_file']) else 0
        cover_ok = 1 if exists_static(row['album_cover']) else 0
        is_pop = int(row['is_popular'] or 0)
        plays = int(row['play_count'] or 0)
        ts = parse_ts(row['created_at'])
        return audio_ok * 1000 + cover_ok * 500 + is_pop * 100 + plays * 10 + ts
    return sorted(items, key=score, reverse=True)[0]


def ensure_audio_for_known(song_title_norm, artist_norm):
    # Map known tracks to preferred audio files when available
    known = {
        ('cant stop the feeling', 'justin timberlake'): 'youtube_V1Pl8CzNzCw_audio.mp3',
        ('gigolo', 'bbno'): None,  # no known youtube id here; leave as-is
    }
    return known.get((song_title_norm, artist_norm))


def normalize_and_dedup_songs(conn):
    conn.row_factory = sqlite3.Row
    rows = conn.execute("SELECT * FROM songs").fetchall()

    # First, targeted normalization fixes
    # - Map 'Ethan Nestor' artist for Gigolo to 'bbno$'
    conn.execute("UPDATE songs SET artist = 'bbno$' WHERE LOWER(song_title) LIKE '%gigolo%' AND LOWER(artist) LIKE '%ethan n%' ")

    # - Normalize titles and artists into new canonical columns for grouping (not persisted, used in-memory)
    groups = {}
    for r in rows:
        title_norm = norm_text(r['song_title'])
        artist_norm = norm_text(r['artist'])
        key = (title_norm, artist_norm)
        groups.setdefault(key, []).append(r)

    to_delete_ids = []
    to_update = []

    for key, items in groups.items():
        if len(items) <= 1:
            # Optionally, set preferred audio for known singletons
            preferred_audio = ensure_audio_for_known(key[0], key[1])
            if preferred_audio and exists_static(preferred_audio):
                row = items[0]
                if row['audio_file'] != preferred_audio:
                    to_update.append((preferred_audio, row['id']))
            continue
        keeper = choose_keeper(items)
        preferred_audio = ensure_audio_for_known(key[0], key[1])
        if preferred_audio and exists_static(preferred_audio) and keeper['audio_file'] != preferred_audio:
            to_update.append((preferred_audio, keeper['id']))
        for r in items:
            if r['id'] == keeper['id']:
                continue
            to_delete_ids.append(r['id'])

    if to_update:
        conn.executemany("UPDATE songs SET audio_file = ? WHERE id = ?", to_update)
    if to_delete_ids:
        conn.executemany("DELETE FROM songs WHERE id = ?", [(i,) for i in to_delete_ids])

    print(f"Songs normalized/dedup: updated_audio={len(to_update)}, deleted={len(to_delete_ids)}")


def normalize_and_dedup_recently_played(conn):
    conn.row_factory = sqlite3.Row
    rows = conn.execute("SELECT * FROM recently_played").fetchall()

    groups = {}
    for r in rows:
        title_norm = norm_text(r['song_title'])
        artist_norm = norm_text(r['artist'])
        # Group per user and normalized song identity, ignore differing audio filenames
        key = (int(r['user_id'] or 0), title_norm, artist_norm)
        groups.setdefault(key, []).append(r)

    to_delete_ids = []
    to_update_artist = []

    for key, items in groups.items():
        if len(items) <= 1:
            continue
        def score(row):
            audio_ok = 1 if exists_static(row['audio_file']) else 0
            ts = parse_ts(row['played_at'])
            return audio_ok * 1000 + ts
        keeper = sorted(items, key=score, reverse=True)[0]
        # Normalize artist for Gigolo entries accidentally labeled Ethan Nestor
        if 'gigolo' in key[1] and 'ethan n' in key[2]:
            for r in items:
                if norm_text(r['artist']) != 'bbno':
                    to_update_artist.append(('bbno$', r['id']))
        for r in items:
            if r['id'] == keeper['id']:
                continue
            to_delete_ids.append(r['id'])

    if to_update_artist:
        conn.executemany("UPDATE recently_played SET artist = ? WHERE id = ?", to_update_artist)
    if to_delete_ids:
        conn.executemany("DELETE FROM recently_played WHERE id = ?", [(i,) for i in to_delete_ids])

    print(f"Recently played normalized/dedup: artist_fixed={len(to_update_artist)}, deleted={len(to_delete_ids)}")


def main():
    if not DB_PATH.exists():
        raise FileNotFoundError(f"DB not found: {DB_PATH}")
    conn = sqlite3.connect(str(DB_PATH))
    try:
        normalize_and_dedup_songs(conn)
        normalize_and_dedup_recently_played(conn)
        conn.commit()
    finally:
        conn.close()
    print("Normalization and deduplication complete.")


if __name__ == '__main__':
    main()