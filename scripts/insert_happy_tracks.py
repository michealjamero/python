import sqlite3
from pathlib import Path

DB_PATH = Path(__file__).resolve().parent.parent / 'moodtune.db'

records = [
    {
        'song_title': 'Taste',
        'artist': 'Sabrina Carpenter',
        'album_cover': 'defultimage.jpg',
        'audio_file': 'youtube_pbMwTqkKSps_audio.mp3',
        'mood': 'Happy',
        'duration': None
    },
    {
        'song_title': 'Gigolo',
        'artist': 'Ethan Nestor',
        'album_cover': 'gigolo Ethan Nestor.jpg',
        'audio_file': 'youtube_-ncIVUXZla8_audio.mp3',
        'mood': 'Happy',
        'duration': None
    }
]

def upsert_song(conn, rec):
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
        print(f"Updated: {rec['artist']} - {rec['song_title']}")
    else:
        conn.execute(
            "INSERT INTO songs (song_title, artist, album_cover, audio_file, mood, duration) VALUES (?, ?, ?, ?, ?, ?)",
            (rec['song_title'], rec['artist'], rec['album_cover'], rec['audio_file'], rec['mood'], rec['duration'])
        )
        print(f"Inserted: {rec['artist']} - {rec['song_title']}")


def main():
    if not DB_PATH.exists():
        raise FileNotFoundError(f"DB not found: {DB_PATH}")
    conn = sqlite3.connect(str(DB_PATH))
    try:
        for rec in records:
            upsert_song(conn, rec)
        conn.commit()
    finally:
        conn.close()
    print("Done.")

if __name__ == '__main__':
    main()