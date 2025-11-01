import os
import re
import sqlite3

# Absolute path to project root (adjust if needed)
BASE_DIR = r"c:\Users\user\OneDrive\Desktop\PythonProject1"
LIBRARY_HTML = os.path.join(BASE_DIR, "templates", "Library.html")
DB_PATH = os.path.join(BASE_DIR, "moodtune.db")


def parse_library_html(path):
    """Parse Library.html and extract songs as dicts.
    Each dict: {artist, song_title, album_cover, audio_file, mood, duration}
    """
    with open(path, "r", encoding="utf-8", errors="ignore") as f:
        lines = f.readlines()

    songs = []

    # Regexes to extract data from nearby lines
    re_h3 = re.compile(r"<h3[^>]*>(?P<artist>[^<\-]+?)\s*-\s*(?P<title>[^<]+?)</h3>", re.IGNORECASE)
    re_cover = re.compile(r"filename='(?P<cover>[^']+)'", re.IGNORECASE)
    re_audio = re.compile(r"filename='(?P<audio>[^']+)'", re.IGNORECASE)
    re_meta = re.compile(r">\s*(?P<mood>[A-Za-z]+)\s*•\s*(?P<duration>[0-9:]+)\s*<", re.IGNORECASE)

    for i, line in enumerate(lines):
        m = re_h3.search(line)
        if not m:
            continue

        artist = m.group("artist").strip()
        title = m.group("title").strip()

        # Look backward up to 8 lines for the album cover
        cover = None
        for j in range(max(0, i - 8), i):
            if "<img" in lines[j] and "url_for('static" in lines[j]:
                mc = re_cover.search(lines[j])
                if mc:
                    cover = mc.group("cover").strip()
                    break

        # Look forward up to 10 lines for audio source and meta (mood • duration)
        audio = None
        mood = None
        duration = None
        for j in range(i, min(len(lines), i + 12)):
            lj = lines[j]
            if audio is None and "<source" in lj and "url_for('static" in lj:
                ma = re_audio.search(lj)
                if ma:
                    audio = ma.group("audio").strip()
            if mood is None and "<p" in lj and "•" in lj:
                mm = re_meta.search(lj)
                if mm:
                    mood = mm.group("mood").strip()
                    duration = mm.group("duration").strip()
            if audio and mood:
                break

        if not (artist and title and cover and audio):
            # Skip incomplete entries
            continue

        songs.append({
            "artist": artist,
            "song_title": title,
            "album_cover": cover,
            "audio_file": audio,
            "mood": mood or "Mixed",
            "duration": duration or None,
        })

    return songs


def ensure_db(path):
    if not os.path.exists(path):
        raise FileNotFoundError(f"Database not found at {path}")


def insert_songs(conn, songs):
    cur = conn.cursor()
    inserted = 0
    skipped = 0

    for s in songs:
        # Duplicate check: prefer audio_file; fallback to artist+song_title
        cur.execute(
            "SELECT id FROM songs WHERE audio_file = ?",
            (s["audio_file"],)
        )
        row = cur.fetchone()
        if row is None:
            cur.execute(
                "SELECT id FROM songs WHERE artist = ? AND song_title = ?",
                (s["artist"], s["song_title"]) 
            )
            row = cur.fetchone()

        if row:
            skipped += 1
            continue

        cur.execute(
            """
            INSERT INTO songs (song_title, artist, album_cover, audio_file, mood, duration, is_popular, play_count)
            VALUES (?, ?, ?, ?, ?, ?, 0, 0)
            """,
            (
                s["song_title"],
                s["artist"],
                s["album_cover"],
                s["audio_file"],
                s["mood"],
                s["duration"],
            )
        )
        inserted += 1

    conn.commit()
    return inserted, skipped


def main():
    print(f"Parsing: {LIBRARY_HTML}")
    songs = parse_library_html(LIBRARY_HTML)
    print(f"Found {len(songs)} songs in Library.html")

    ensure_db(DB_PATH)
    conn = sqlite3.connect(DB_PATH)
    try:
        inserted, skipped = insert_songs(conn, songs)
        print(f"Inserted: {inserted}")
        print(f"Skipped (already present): {skipped}")
    finally:
        conn.close()


if __name__ == "__main__":
    main()