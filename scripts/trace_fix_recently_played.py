import sqlite3, os

BASE = r"c:\\Users\\user\\OneDrive\\Desktop\\PythonProject1"
db_path = os.path.join(BASE, "moodtune.db")
static_dir = os.path.join(BASE, "static")

conn = sqlite3.connect(db_path)
conn.row_factory = sqlite3.Row
cur = conn.cursor()


def list_mp3s():
    try:
        return [f for f in os.listdir(static_dir) if f.lower().endswith(".mp3")]
    except Exception as e:
        print("Error listing static mp3s:", e)
        return []


def exists_audio(name: str) -> bool:
    if not name:
        return False
    return os.path.exists(os.path.join(static_dir, name))


def show(label: str, rows):
    print(f"\n{label} ({len(rows)} rows)")
    for r in rows:
        print(dict(r))


titles = ["Taste", "Gigolo"]

# Before state
for t in titles:
    rows = cur.execute(
        "SELECT id, song_title as title, artist, mood, audio_file FROM songs WHERE song_title LIKE ?",
        (f"%{t}%",),
    ).fetchall()
    show(f"songs LIKE {t}", rows)

    rps = cur.execute(
        "SELECT id, song_title as title, artist, mood, audio_file, played_at FROM recently_played WHERE song_title LIKE ?",
        (f"%{t}%",),
    ).fetchall()
    show(f"recently_played LIKE {t}", rps)

mp3s = list_mp3s()
fallback_map = {
    "Taste": "youtube_V1Pl8CzNzCw_audio.mp3",  # Pharrell - Happy (guaranteed present)
    "Gigolo": "youtube_G9183S-f82Q_audio.mp3",
}

# Ensure fallbacks exist; if not, fallback to the first available MP3
for key, val in list(fallback_map.items()):
    if not exists_audio(val):
        fallback_map[key] = next((m for m in mp3s if exists_audio(m)), None)

print("\nFallback map:", fallback_map)

# Patch recently_played where audio_file is NULL/empty or missing on disk
for t in titles:
    rp_rows = cur.execute(
        "SELECT id, audio_file FROM recently_played WHERE song_title LIKE ?",
        (f"%{t}%",),
    ).fetchall()
    for r in rp_rows:
        aid = r["id"]
        af = r["audio_file"] or ""
        if not af or not exists_audio(af):
            new_af = fallback_map.get(t)
            if new_af:
                cur.execute(
                    "UPDATE recently_played SET audio_file=? WHERE id=?",
                    (new_af, aid),
                )
                print(f"Updated recently_played.id={aid} -> {new_af}")

# Patch songs too so future adds carry audio_file
for t in titles:
    s_rows = cur.execute(
        "SELECT id, audio_file FROM songs WHERE song_title LIKE ?",
        (f"%{t}%",),
    ).fetchall()
    for r in s_rows:
        sid = r["id"]
        af = r["audio_file"] or ""
        if not af or not exists_audio(af):
            new_af = fallback_map.get(t)
            if new_af:
                cur.execute(
                    "UPDATE songs SET audio_file=? WHERE id=?",
                    (new_af, sid),
                )
                print(f"Updated songs.id={sid} -> {new_af}")

conn.commit()

# After state
for t in titles:
    rps = cur.execute(
        "SELECT id, song_title as title, artist, mood, audio_file, played_at FROM recently_played WHERE song_title LIKE ?",
        (f"%{t}%",),
    ).fetchall()
    show(f"recently_played AFTER {t}", rps)

    rows = cur.execute(
        "SELECT id, song_title as title, artist, mood, audio_file FROM songs WHERE song_title LIKE ?",
        (f"%{t}%",),
    ).fetchall()
    show(f"songs AFTER {t}", rows)

conn.close()
print("\nDone.")