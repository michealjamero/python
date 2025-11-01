import sqlite3
import os
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent
DB_PATH = BASE_DIR / 'moodtune.db'
STATIC_DIR = BASE_DIR / 'static'

records = [
    {
        'song_title': 'Taste',
        'artist': 'Sabrina Carpenter',
        'album_cover': 'defultimage.jpg',
        'audio_file': 'Sabrina Carpenter - Taste (Lyrics) [4Mk-6QK-Pqg].mp3',
        'mood': 'Happy',
        'duration': None
    },
    {
        'song_title': 'Gigolo',
        'artist': 'bbno$',
        'album_cover': 'gigolo Ethan Nestor.jpg',
        'audio_file': 'bbno_ - gigolo (Lyrics) [Cu61VDpDDWk].mp3',
        'mood': 'Happy',
        'duration': None
    }
]


def upsert_song(conn, rec):
    # Ensure files exist in static
    cover_ok = (STATIC_DIR / rec['album_cover']).exists()
    audio_ok = (STATIC_DIR / rec['audio_file']).exists()
    if not cover_ok:
        # Fallback to default image if cover missing
        rec['album_cover'] = 'defultimage.jpg'
    if not audio_ok:
        raise FileNotFoundError(f"Audio file not found in static: {rec['audio_file']}")

    # Check if song exists by title+artist
    cur = conn.execute(
        "SELECT id FROM songs WHERE song_title = ? AND artist = ?",
        (rec['song_title'], rec['artist'])
    )
    row = cur.fetchone()
    if row:
        conn.execute(
            "UPDATE songs SET album_cover=?, audio_file=?, mood=?, duration=? WHERE id=?",
            (rec['album_cover'], rec['audio_file'], rec['mood'], rec['duration'], row[0])
        )
        print(f"Updated song: {rec['artist']} - {rec['song_title']}")
    else:
        conn.execute(
            "INSERT INTO songs (song_title, artist, album_cover, audio_file, mood, duration) VALUES (?, ?, ?, ?, ?, ?)",
            (rec['song_title'], rec['artist'], rec['album_cover'], rec['audio_file'], rec['mood'], rec['duration'])
        )
        print(f"Inserted song: {rec['artist']} - {rec['song_title']}")


def patch_recently_played(conn, rec):
    # Update any existing 'recently_played' entries matching this title to use these assets
    title_like = f"%{rec['song_title']}%"
    audio_file = rec['audio_file']
    album_cover = rec['album_cover']

    # Only update rows where audio is empty or points to a missing file
    rows = conn.execute(
        "SELECT id, audio_file FROM recently_played WHERE song_title LIKE ?",
        (title_like,)
    ).fetchall()

    for r in rows:
        rid = r[0]
        af = r[1] or ''
        af_path = STATIC_DIR / af
        if not af or (af and not af_path.exists()):
            conn.execute(
                "UPDATE recently_played SET audio_file = ?, album_cover = ?, mood = ? WHERE id = ?",
                (audio_file, album_cover, rec['mood'], rid)
            )
            print(f"Patched recently_played.id={rid} -> audio={audio_file}, cover={album_cover}")


def main():
    if not DB_PATH.exists():
        raise FileNotFoundError(f"DB not found: {DB_PATH}")

    conn = sqlite3.connect(str(DB_PATH))
    try:
        for rec in records:
            upsert_song(conn, rec)
            patch_recently_played(conn, rec)
        conn.commit()
    finally:
        conn.close()
    print("Done inserting/updating Happy tracks.")


if __name__ == '__main__':
    main()