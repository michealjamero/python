import sqlite3
from sqlite3 import IntegrityError
from flask import Flask, render_template, request, redirect, url_for, session, flash, jsonify
from werkzeug.security import generate_password_hash, check_password_hash
from datetime import datetime, timedelta
import os
import random
import json
from pathlib import Path
import mimetypes
import calendar
from collections import Counter

# Ensure correct MIME for MP3 files
mimetypes.add_type('audio/mpeg', '.mp3')

app = Flask(__name__)
app.secret_key = 'supersecretkey'  # ⚠️ Change this in production!

# Mood normalization to canonical labels used in charts
def normalize_mood(m):
    if not m:
        return 'Mixed'
    s = str(m).strip().lower()
    mapping = {
        'happy': 'Happy',
        'sad': 'Sad',
        'relaxed': 'Relaxed',
        'relax': 'Relaxed',
        'calm': 'Relaxed',
        'angry': 'Angry',
        'anger': 'Angry',
        'mad': 'Angry',
        'love': 'Love',
        'romantic': 'Love',
        'in love': 'Love',
        'mixed': 'Mixed',
    }
    return mapping.get(s, 'Mixed')

# ==================== DATABASE ====================
def get_db_connection():
    conn = sqlite3.connect('moodtune.db')
    conn.row_factory = sqlite3.Row
    return conn

def init_db():
    conn = get_db_connection()

    # Users table
    conn.execute('''CREATE TABLE IF NOT EXISTS users (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        username TEXT UNIQUE NOT NULL,
        email TEXT UNIQUE NOT NULL,
        password TEXT NOT NULL,
        is_admin INTEGER DEFAULT 0
    )''')

    # Profiles table
    conn.execute('''CREATE TABLE IF NOT EXISTS profiles (
        user_id INTEGER PRIMARY KEY,
        bio TEXT DEFAULT '',
        image TEXT DEFAULT 'artist_profile.jpg',
        instagram TEXT DEFAULT '',
        twitter TEXT DEFAULT '',
        spotify TEXT DEFAULT '',
        genres TEXT DEFAULT 'Pop,R&B,Afrobeats,Jazz',
        FOREIGN KEY (user_id) REFERENCES users(id)
    )''')

    # Played songs table with mood tracking
    conn.execute('''CREATE TABLE IF NOT EXISTS recently_played (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        user_id INTEGER NOT NULL,
        song_title TEXT NOT NULL,
        artist TEXT NOT NULL,
        album_cover TEXT NOT NULL,
        audio_file TEXT NOT NULL,
        mood TEXT DEFAULT 'Mixed',
        played_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY(user_id) REFERENCES users(id)
    )''')
    
    # Songs table for admin management
    conn.execute('''CREATE TABLE IF NOT EXISTS songs (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        song_title TEXT NOT NULL,
        artist TEXT NOT NULL,
        album_cover TEXT NOT NULL,
        audio_file TEXT NOT NULL,
        mood TEXT DEFAULT 'Mixed',
        duration TEXT DEFAULT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    )''')
    
    # Ensure songs table has popularity columns
    cursor = conn.cursor()
    cursor.execute("PRAGMA table_info(songs)")
    song_columns = [column[1] for column in cursor.fetchall()]
    if 'is_popular' not in song_columns:
        conn.execute("ALTER TABLE songs ADD COLUMN is_popular INTEGER DEFAULT 0")
    if 'play_count' not in song_columns:
        conn.execute("ALTER TABLE songs ADD COLUMN play_count INTEGER DEFAULT 0")
    
    # Check if is_admin column exists in users table, add if not
    cursor = conn.cursor()
    cursor.execute("PRAGMA table_info(users)")
    columns = [column[1] for column in cursor.fetchall()]
    if 'is_admin' not in columns:
        conn.execute("ALTER TABLE users ADD COLUMN is_admin INTEGER DEFAULT 0")
        print("Added is_admin column to users table")
    
    # Play history table for global popularity
    conn.execute('''CREATE TABLE IF NOT EXISTS play_history (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        user_id INTEGER,
        song_id INTEGER,
        played_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    )''')

    # Ranking history snapshots (hourly leaderboard log)
    conn.execute('''CREATE TABLE IF NOT EXISTS ranking_history (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        song_id INTEGER,
        rank INTEGER,
        captured_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    )''')
    
    conn.commit()
    conn.close()

# ==================== ROUTES ====================

IMAGE_MAP_PATH = Path('static') / 'images' / 'image_map.json'
_image_map_cache = None

def get_image_map():
    global _image_map_cache
    if _image_map_cache is None:
        try:
            with open(IMAGE_MAP_PATH, 'r', encoding='utf-8') as f:
                _image_map_cache = json.load(f)
        except Exception:
            _image_map_cache = {}
    return _image_map_cache

@app.route('/')
def intro():
    # Simple splash/intro page that redirects to Home after a short delay
    return render_template('intro.html')

@app.route('/home')
def home():
    conn = get_db_connection()
    # Fetch songs marked as popular from songs table
    popular_rows = conn.execute('''
        SELECT 
            song_title AS title,
            artist AS artist,
            album_cover AS image,
            audio_file AS audio_file,
            mood AS mood,
            duration AS duration,
            play_count AS plays
        FROM songs
        WHERE is_popular = 1
        ORDER BY play_count DESC, created_at DESC
        LIMIT 10
    ''').fetchall()

    popular_tracks = [dict(r) for r in popular_rows]

    # Map album cover images via image_map if needed
    image_map = get_image_map()
    for t in popular_tracks:
        if not t.get('image'):
            t['image'] = image_map.get(t['title']) or image_map.get('Default')

    conn.close()
    return render_template('home.html', popular_tracks=popular_tracks)

@app.route('/library')
def library():
    conn = get_db_connection()
    songs = conn.execute('SELECT * FROM songs ORDER BY created_at DESC').fetchall()
    songs = [dict(s) for s in songs]
    conn.close()
    songs_by_mood = {}
    for s in songs:
        mood = s.get('mood') or 'Mixed'
        songs_by_mood.setdefault(mood, []).append(s)
    return render_template('Library.html', songs_by_mood=songs_by_mood)

@app.route('/about')
def about():
    return render_template('about.html')

# ==================== PROFILE & MOOD TRACKING ====================

@app.route('/profile', methods=['GET', 'POST'])
def profile():
    if 'user_id' not in session:
        flash('Please log in to access your profile.', 'warning')
        return redirect(url_for('signin'))

    conn = get_db_connection()

    # Ensure profile exists
    profile = conn.execute(
        "SELECT * FROM profiles WHERE user_id = ?", (session['user_id'],)
    ).fetchone()
    if not profile:
        conn.execute("INSERT INTO profiles (user_id) VALUES (?)", (session['user_id'],))
        conn.commit()
        profile = conn.execute(
            "SELECT * FROM profiles WHERE user_id = ?", (session['user_id'],)
        ).fetchone()

    # Recently Played feature removed

    # Mood tracking for the past 8 days
    mood_data = []
    for i in range(8, 0, -1):
        day = datetime.now() - timedelta(days=i-1)
        day_str = day.strftime('%Y-%m-%d')

        # Mood normalization defined at module scope

        mood_counts = conn.execute('''
            SELECT mood, COUNT(*) as count
            FROM recently_played
            WHERE user_id=? AND date(played_at, 'localtime') = date(?)
            GROUP BY mood
        ''', (session['user_id'], day.strftime('%Y-%m-%d'))).fetchall()

        mood_dict = {'date': day_str, 'Happy': 0, 'Sad': 0, 'Relaxed': 0, 'Angry': 0, 'Love': 0}
        total_count = 0

        for mood in mood_counts:
            key = normalize_mood(mood['mood'])
            if key in mood_dict:
                mood_dict[key] = mood['count']
                total_count += mood['count']

        # For days with no data
        if total_count == 0:
            for m in ['Happy', 'Sad', 'Relaxed', 'Angry', 'Love']:
                mood_dict[m] = 0

        mood_data.append(mood_dict)

    # Handle profile updates
    if request.method == 'POST':
        bio = request.form.get('bio', '')
        instagram = request.form.get('instagram', '')
        twitter = request.form.get('twitter', '')
        spotify = request.form.get('spotify', '')
        genres = request.form.get('genres', '')

        image = profile['image']
        if 'image' in request.files:
            uploaded_image = request.files['image']
            if uploaded_image.filename != '':
                uploaded_image.save(f"static/{uploaded_image.filename}")
                image = uploaded_image.filename

        conn.execute(
            "UPDATE profiles SET bio=?, instagram=?, twitter=?, spotify=?, genres=?, image=? WHERE user_id=?",
            (bio, instagram, twitter, spotify, genres, image, session['user_id'])
        )
        conn.commit()
        flash('✅ Profile updated successfully!', 'success')
        return redirect(url_for('profile'))

    conn.close()
    return render_template('profile.html', profile=profile,
                           username=session['username'], mood_data=mood_data)

# ==================== PROFILE UPDATES ====================

@app.route('/update_profile_image', methods=['POST'])
def update_profile_image():
    if 'user_id' not in session:
        return redirect(url_for('signin'))

    file = request.files.get('profile_image')
    if file and file.filename != '':
        filename = file.filename
        file.save(os.path.join('static', filename))

        conn = get_db_connection()
        conn.execute("UPDATE profiles SET image=? WHERE user_id=?", (filename, session['user_id']))
        conn.commit()
        conn.close()

        session['image'] = filename
        flash('✅ Profile image updated!', 'success')

    return redirect(url_for('profile'))

@app.route('/update_profile_name', methods=['POST'])
def update_profile_name():
    if 'user_id' not in session:
        return jsonify({'success': False}), 403

    data = request.get_json()
    new_name = data.get('name')

    if not new_name:
        return jsonify({'success': False}), 400

    conn = get_db_connection()
    conn.execute("UPDATE users SET username=? WHERE id=?", (new_name, session['user_id']))
    conn.commit()
    conn.close()

    session['username'] = new_name
    return jsonify({'success': True})

# ==================== AUTH ====================

@app.route('/signin', methods=['GET', 'POST'])
def signin():
    if request.method == 'POST':
        email_or_username = request.form['email']
        password = request.form['password']

        conn = get_db_connection()
        user = conn.execute(
            "SELECT * FROM users WHERE email = ? OR username = ?",
            (email_or_username, email_or_username)
        ).fetchone()
        conn.close()

        if user and check_password_hash(user['password'], password):
            session['user_id'] = user['id']
            session['username'] = user['username']
            session['is_admin'] = user['is_admin'] == 1  # Store admin status as boolean
            flash('✅ Login successful!', 'success')
            return redirect(url_for('profile'))
        else:
            flash('❌ Invalid credentials. Please try again.', 'danger')

    return render_template('signin.html')

@app.route('/signup', methods=['GET', 'POST'])
def signup():
    if request.method == 'POST':
        username = request.form['username']
        email = request.form['email']
        password = request.form['password']

        hashed_pw = generate_password_hash(password)

        conn = get_db_connection()
        try:
            conn.execute(
                "INSERT INTO users (username, email, password) VALUES (?, ?, ?)",
                (username, email, hashed_pw)
            )
            conn.commit()
            flash('🎉 Account created successfully! You can now log in.', 'success')
            return redirect(url_for('signin'))
        except sqlite3.IntegrityError:
            flash('⚠️ Username or Email already exists.', 'danger')
        finally:
            conn.close()

    return render_template('signup.html')

@app.route('/logout')
def logout():
    session.clear()
    flash('You have been logged out.', 'info')
    return redirect(url_for('signin'))

# ==================== ADD SONG + MOOD ====================

# play_song route is implemented below (around line 1069)

# ==================== ADMIN ROUTES ====================

@app.route('/admin')
def admin():
    if 'user_id' not in session:
        flash('Please log in to access admin panel.', 'warning')
        return redirect(url_for('signin'))
    
    # Check if user is admin
    conn = get_db_connection()
    user = conn.execute('SELECT is_admin FROM users WHERE id = ?', (session['user_id'],)).fetchone()
    
    if not user or user['is_admin'] != 1:
        conn.close()
        flash('You do not have permission to access the admin panel.', 'danger')
        return redirect(url_for('home'))
    
    # Get all songs
    songs = conn.execute('SELECT * FROM songs ORDER BY created_at DESC').fetchall()
    songs = [dict(song) for song in songs]
    conn.close()
    
    return render_template('admin.html', songs=songs)

@app.route('/admin/add', methods=['POST'])
def admin_add():
    if 'user_id' not in session:
        return redirect(url_for('signin'))
    
    # Check if user is admin
    conn = get_db_connection()
    user = conn.execute('SELECT is_admin FROM users WHERE id = ?', (session['user_id'],)).fetchone()
    
    if not user or user['is_admin'] != 1:
        conn.close()
        flash('You do not have permission to perform this action.', 'danger')
        return redirect(url_for('home'))
    
    # Process form data
    song_title = request.form.get('song_title')
    artist = request.form.get('artist')
    mood = request.form.get('mood')
    duration = request.form.get('duration')
    
    # Handle file uploads
    album_cover = request.files.get('album_cover')
    audio_file = request.files.get('audio_file')
    
    if not song_title or not artist or not album_cover or not audio_file:
        flash('All required fields must be filled.', 'danger')
        return redirect(url_for('admin'))
    
    # Save files
    album_cover_filename = album_cover.filename
    audio_file_filename = audio_file.filename
    
    album_cover.save(os.path.join('static', album_cover_filename))
    audio_file.save(os.path.join('static', audio_file_filename))
    
    # Save to database
    conn.execute(
        'INSERT INTO songs (song_title, artist, album_cover, audio_file, mood, duration) VALUES (?, ?, ?, ?, ?, ?)',
        (song_title, artist, album_cover_filename, audio_file_filename, mood, duration)
    )
    conn.commit()
    conn.close()
    
    flash('Song added successfully!', 'success')
    return redirect(url_for('admin'))

@app.route('/admin/edit/<int:song_id>', methods=['POST'])
def admin_edit(song_id):
    if 'user_id' not in session:
        return redirect(url_for('signin'))
    
    # Check if user is admin
    conn = get_db_connection()
    user = conn.execute('SELECT is_admin FROM users WHERE id = ?', (session['user_id'],)).fetchone()
    
    if not user or user['is_admin'] != 1:
        conn.close()
        flash('You do not have permission to perform this action.', 'danger')
        return redirect(url_for('home'))
    
    # Get existing song
    song = conn.execute('SELECT * FROM songs WHERE id = ?', (song_id,)).fetchone()
    if not song:
        conn.close()
        flash('Song not found.', 'danger')
        return redirect(url_for('admin'))
    
    # Process form data
    song_title = request.form.get('song_title')
    artist = request.form.get('artist')
    mood = request.form.get('mood')
    duration = request.form.get('duration')
    
    # Keep existing filenames by default
    album_cover_filename = song['album_cover']
    audio_file_filename = song['audio_file']
    
    # Handle file uploads if provided
    album_cover = request.files.get('album_cover')
    if album_cover and album_cover.filename:
        album_cover_filename = album_cover.filename
        album_cover.save(os.path.join('static', album_cover_filename))
    
    audio_file = request.files.get('audio_file')
    if audio_file and audio_file.filename:
        audio_file_filename = audio_file.filename
        audio_file.save(os.path.join('static', audio_file_filename))
    
    # Update database
    conn.execute(
        'UPDATE songs SET song_title = ?, artist = ?, album_cover = ?, audio_file = ?, mood = ?, duration = ? WHERE id = ?',
        (song_title, artist, album_cover_filename, audio_file_filename, mood, duration, song_id)
    )
    conn.commit()
    conn.close()
    
    flash('Song updated successfully!', 'success')
    return redirect(url_for('admin'))

@app.route('/admin/delete/<int:song_id>', methods=['POST'])
def admin_delete(song_id):
    if 'user_id' not in session:
        return redirect(url_for('signin'))
    
    # Check if user is admin
    conn = get_db_connection()
    user = conn.execute('SELECT is_admin FROM users WHERE id = ?', (session['user_id'],)).fetchone()
    
    if not user or user['is_admin'] != 1:
        conn.close()
        flash('You do not have permission to perform this action.', 'danger')
        return redirect(url_for('home'))
    
    # Fetch song to know which static files to remove
    song = conn.execute('SELECT id, album_cover, audio_file FROM songs WHERE id = ?', (song_id,)).fetchone()
    if not song:
        conn.close()
        flash('Song not found.', 'danger')
        return redirect(url_for('admin'))

    static_dir = Path('static')

    def safe_delete_static(filename: str, column_name: str):
        if not filename:
            return
        # Only delete file if no other songs reference it
        count_row = conn.execute(
            f"SELECT COUNT(*) AS c FROM songs WHERE {column_name} = ? AND id != ?",
            (filename, song_id)
        ).fetchone()
        count = count_row['c'] if isinstance(count_row, sqlite3.Row) else (count_row[0] if count_row else 0)
        path = static_dir / filename
        # Avoid deleting the shared default image
        if count == 0 and filename.lower() != 'defultimage.jpg' and path.exists():
            try:
                os.remove(path)
            except Exception as e:
                # Log but continue with DB delete
                print(f"[admin_delete] Failed to remove {path}: {e}")

    # Delete DB row first to prevent races
    conn.execute('DELETE FROM songs WHERE id = ?', (song_id,))
    conn.commit()

    # Then attempt to delete unreferenced static files
    safe_delete_static(song['album_cover'], 'album_cover')
    safe_delete_static(song['audio_file'], 'audio_file')

    conn.close()
    flash('Song deleted successfully!', 'success')
    return redirect(url_for('admin'))

# Helper route to make a user an admin (for testing)
@app.route('/make_admin/<int:user_id>')
def make_admin(user_id):
    conn = get_db_connection()
    conn.execute('UPDATE users SET is_admin = 1 WHERE id = ?', (user_id,))
    conn.commit()
    conn.close()
    flash('User has been granted admin privileges.', 'success')
    return redirect(url_for('home'))

# ==================== ADMIN PAGE VARIANTS ====================

@app.route('/admin/home')
def admin_home():
    if 'user_id' not in session:
        flash('Please log in to access admin pages.', 'warning')
        return redirect(url_for('signin'))
    conn = get_db_connection()
    user = conn.execute('SELECT is_admin FROM users WHERE id = ?', (session['user_id'],)).fetchone()
    conn.close()
    if not user or user['is_admin'] != 1:
        flash('You do not have permission to access admin pages.', 'danger')
        return redirect(url_for('home'))
    return render_template('admin_home.html')

@app.route('/admin/library')
def admin_library_page():
    if 'user_id' not in session:
        flash('Please log in to access admin pages.', 'warning')
        return redirect(url_for('signin'))
    conn = get_db_connection()
    user = conn.execute('SELECT is_admin FROM users WHERE id = ?', (session['user_id'],)).fetchone()
    conn.close()
    if not user or user['is_admin'] != 1:
        flash('You do not have permission to access admin pages.', 'danger')
        return redirect(url_for('home'))
    return render_template('admin_library.html')

@app.route('/admin/about')
def admin_about_page():
    if 'user_id' not in session:
        flash('Please log in to access admin pages.', 'warning')
        return redirect(url_for('signin'))
    conn = get_db_connection()
    user = conn.execute('SELECT is_admin FROM users WHERE id = ?', (session['user_id'],)).fetchone()
    conn.close()
    if not user or user['is_admin'] != 1:
        flash('You do not have permission to access admin pages.', 'danger')
        return redirect(url_for('home'))
    return render_template('admin_about.html')

@app.route('/admin/profile')
def admin_profile_page():
    if 'user_id' not in session:
        flash('Please log in to access admin pages.', 'warning')
        return redirect(url_for('signin'))
    conn = get_db_connection()
    user = conn.execute('SELECT is_admin FROM users WHERE id = ?', (session['user_id'],)).fetchone()
    conn.close()
    if not user or user['is_admin'] != 1:
        flash('You do not have permission to access admin pages.', 'danger')
        return redirect(url_for('home'))
    return render_template('admin_profile.html')

@app.route('/api/mood-data', methods=['GET'])
def api_mood_data():
    if 'user_id' not in session:
        # Provide demo data when not logged in
        import datetime as _dt, random as _rand
        moods = ['Happy','Sad','Relaxed','Angry','Love']
        today = _dt.date.today()
        demo = []
        for i in range(8):
            d = today - _dt.timedelta(days=(7 - i))
            entry = {'date': d.strftime('%Y-%m-%d')}
            for m in moods:
                entry[m] = _rand.randint(0, 10)
            demo.append(entry)
        return jsonify({"mood_data": demo}), 200

    conn = get_db_connection()
    mood_data = []
    for i in range(8, 0, -1):
        day = datetime.now() - timedelta(days=i-1)
        day_str = day.strftime('%Y-%m-%d')
        mood_counts = conn.execute('''
            SELECT mood, COUNT(*) as count
            FROM recently_played
            WHERE user_id=? AND date(played_at) = date(?)
            GROUP BY mood
        ''', (session['user_id'], day.strftime('%Y-%m-%d'))).fetchall()
        mood_dict = {'date': day_str, 'Happy': 0, 'Sad': 0, 'Relaxed': 0, 'Angry': 0, 'Love': 0}
        for m in mood_counts:
            key = normalize_mood(m['mood'])
            if key in mood_dict:
                mood_dict[key] = m['count']
        mood_data.append(mood_dict)
    conn.close()
    return jsonify({"mood_data": mood_data}), 200

from datetime import datetime, timedelta

# --- Mood Calendar API ---
@app.route('/api/mood-calendar', methods=['GET'])
def api_mood_calendar():
    """
    Returns user's dominant mood per day for a given month/year.
    Query params: ?year=YYYY&month=MM  (defaults to current month/year)
    Response: { "year": YYYY, "month": MM, "days": [ { "date": "YYYY-MM-DD", "mood": "Happy", "count": N }, ... ] }
    """
    if 'user_id' not in session:
        return jsonify({"year": None, "month": None, "days": []}), 200

    # parse query params
    try:
        y = int(request.args.get('year')) if request.args.get('year') else datetime.now().year
        m = int(request.args.get('month')) if request.args.get('month') else datetime.now().month
        # sanitize month
        if m < 1 or m > 12:
            raise ValueError()
    except Exception:
        y = datetime.now().year
        m = datetime.now().month

    # compute start/end dates for month
    first_day = datetime(y, m, 1)
    _, days_in_month = calendar.monthrange(y, m)
    last_day = datetime(y, m, days_in_month)

    conn = get_db_connection()
    cur = conn.cursor()

    # Query counts grouped by date and mood
    # Note: date(played_at) is used so it works with timestamps
    rows = cur.execute('''
        SELECT date(played_at, 'localtime') AS d, mood, COUNT(*) as cnt
        FROM recently_played
        WHERE user_id = ?
          AND date(played_at, 'localtime') BETWEEN date(?) AND date(?)
        GROUP BY d, mood
    ''', (session['user_id'], first_day.strftime('%Y-%m-%d'), last_day.strftime('%Y-%m-%d'))).fetchall()

    # Transform into dictionary: { 'YYYY-MM-DD': { 'Happy': n, 'Sad': n, ... } }
    per_day = {}
    for r in rows:
        d = r['d']
        mood_key = normalize_mood(r['mood'])
        per_day.setdefault(d, {})[mood_key] = per_day[d].get(mood_key, 0) + r['cnt']

    # For each day in month pick dominant mood (mode) and count (0 if none)
    result_days = []
    for day in range(1, days_in_month + 1):
        date_str = f"{y:04d}-{m:02d}-{day:02d}"
        mood_counts = per_day.get(date_str, {})
        if mood_counts:
            # choose mood with max count; if tie, pick first after sorting by count desc then name
            dominant = max(sorted(mood_counts.items(), key=lambda kv: (-kv[1], kv[0])), key=lambda kv: kv[1])[0]
            count = mood_counts.get(dominant, 0)
        else:
            dominant = None
            count = 0
        result_days.append({"date": date_str, "mood": dominant, "count": count})

    conn.close()
    return jsonify({"year": y, "month": m, "days": result_days}), 200

@app.route('/api/top_artist')
def api_top_artist():
    conn = get_db_connection()

    # 🎧 Find the artist with the most total plays
    top_artist = conn.execute('''
        SELECT 
            m.artist AS name, 
            m.image, 
            m.audio_file, 
            m.mood, 
            m.title AS top_song, 
            COUNT(p.id) AS plays, 
            COUNT(DISTINCT p.user_id) AS listeners 
        FROM music m 
        LEFT JOIN play_history p ON m.id = p.song_id 
        GROUP BY m.artist 
        ORDER BY plays DESC 
        LIMIT 1 
    ''').fetchone()

    conn.close()

    if not top_artist:
        return jsonify({ 
            "name": "Unknown Artist", 
            "image": "default_artist.png", 
            "top_song": "No data", 
            "plays": 0, 
            "listeners": 0, 
            "mood": "Unknown", 
            "audio_file": "" 
        })

    return jsonify(dict(top_artist))

@app.route('/api/top_artists')
def api_top_artists():
    try:
        conn = get_db_connection()
        cur = conn.cursor()
    
        # Detect tables to avoid SQL errors
        cur.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='music'")
        has_music = cur.fetchone() is not None
        cur.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='play_history'")
        has_history = cur.fetchone() is not None
        cur.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='songs'")
        has_songs = cur.fetchone() is not None
    
        top_artists = []
        if has_music:
            if has_history:
                sql = '''
                    SELECT 
                        m.artist AS name, 
                        m.image, 
                        m.audio_file, 
                        m.mood, 
                        m.title AS top_song, 
                        COUNT(p.id) AS plays, 
                        COUNT(DISTINCT p.user_id) AS listeners 
                    FROM music m 
                    LEFT JOIN play_history p ON m.id = p.song_id 
                    GROUP BY m.artist 
                    ORDER BY plays DESC 
                    LIMIT 3 
                '''
            else:
                # No play_history table; return artists with zero plays/listeners
                sql = '''
                    SELECT 
                        m.artist AS name, 
                        m.image, 
                        m.audio_file, 
                        m.mood, 
                        m.title AS top_song, 
                        0 AS plays, 
                        0 AS listeners 
                    FROM music m 
                    GROUP BY m.artist 
                    ORDER BY name ASC 
                    LIMIT 3 
                '''
            top_artists = cur.execute(sql).fetchall()
        elif has_songs:
            # Use songs table; if play_history exists, aggregate plays/listeners per artist
            if has_history:
                sql = '''
                    SELECT 
                        s.artist AS name,
                        s.album_cover AS image,
                        s.audio_file AS audio_file,
                        s.mood AS mood,
                        MAX(s.song_title) AS top_song,
                        COUNT(p.id) AS plays,
                        COUNT(DISTINCT p.user_id) AS listeners
                    FROM songs s
                    LEFT JOIN play_history p ON s.id = p.song_id
                    GROUP BY s.artist
                    ORDER BY plays DESC
                    LIMIT 3
                '''
            else:
                # Fallback to songs table with zero plays/listeners
                sql = '''
                    SELECT 
                        s.artist AS name,
                        s.album_cover AS image,
                        s.audio_file AS audio_file,
                        s.mood AS mood,
                        s.song_title AS top_song,
                        0 AS plays,
                        0 AS listeners
                    FROM songs s
                    GROUP BY s.artist
                    ORDER BY s.artist ASC
                    LIMIT 3
                '''
            top_artists = cur.execute(sql).fetchall()
    
        conn.close()
    
        if not top_artists:
            return jsonify([{ 
                "name": "Unknown Artist", 
                "image": "default_artist.png", 
                "top_song": "No data", 
                "plays": 0, 
                "listeners": 0, 
                "mood": "Unknown", 
                "audio_file": "" 
            }])
    
        return jsonify([dict(a) for a in top_artists])
    except Exception:
        # Ensure connection is closed if opened
        try:
            conn.close()
        except Exception:
            pass
        return jsonify([{ 
            "name": "Unknown Artist", 
            "image": "default_artist.png", 
            "top_song": "No data", 
            "plays": 0, 
            "listeners": 0, 
            "mood": "Unknown", 
            "audio_file": "" 
        }])

@app.route('/api/popular')
def api_popular():
    conn = get_db_connection()
    cur = conn.cursor()
    # Detect whether "music" table exists; fallback to "songs"
    cur.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='music'")
    has_music = cur.fetchone() is not None
    if has_music:
        sql = '''
            SELECT 
                m.id,
                m.title,
                m.artist,
                m.mood,
                m.duration,
                m.image,
                m.audio_file,
                COUNT(p.id) AS play_count
            FROM music m
            LEFT JOIN play_history p ON m.id = p.song_id
            GROUP BY m.id
            ORDER BY play_count DESC
            LIMIT 15
        '''
    else:
        sql = '''
            SELECT 
                s.id,
                s.song_title AS title,
                s.artist AS artist,
                s.mood AS mood,
                s.duration AS duration,
                s.album_cover AS image,
                s.audio_file AS audio_file,
                COUNT(p.id) AS play_count
            FROM songs s
            LEFT JOIN play_history p ON s.id = p.song_id
            GROUP BY s.id
            ORDER BY play_count DESC, s.created_at DESC
            LIMIT 15
        '''
    popular_rows = conn.execute(sql).fetchall()

    # General fallback: if no rows, fetch latest songs to ensure Top 15 is populated
    if not popular_rows:
        cur.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='music'")
        has_music = cur.fetchone() is not None
        if has_music:
            sql_fallback = '''
                SELECT 
                    m.id,
                    m.title,
                    m.artist,
                    m.mood,
                    m.duration,
                    m.image,
                    m.audio_file,
                    0 AS play_count
                FROM music m
                ORDER BY m.id DESC
                LIMIT 15
            '''
        else:
            sql_fallback = '''
                SELECT 
                    s.id,
                    s.song_title AS title,
                    s.artist AS artist,
                    s.mood AS mood,
                    s.duration AS duration,
                    s.album_cover AS image,
                    s.audio_file AS audio_file,
                    0 AS play_count
                FROM songs s
                ORDER BY s.created_at DESC
                LIMIT 15
            '''
        popular_rows = conn.execute(sql_fallback).fetchall()

    popular_tracks = [dict(r) for r in popular_rows]

    # No example fallback: return only real popular tracks from the database

    # Get ranks from 1 hour ago (latest snapshot per song)
    one_hour_ago = datetime.utcnow() - timedelta(hours=1)
    prev_rows = conn.execute('''
        SELECT song_id, rank, captured_at
        FROM ranking_history
        WHERE captured_at >= ?
        ORDER BY captured_at DESC
    ''', (one_hour_ago,)).fetchall()

    prev_rank_map = {}
    for r in prev_rows:
        sid = r['song_id']
        if sid not in prev_rank_map:  # keep latest due to DESC order
            prev_rank_map[sid] = r['rank']

    # Prepare response + save new ranks
    now = datetime.utcnow()
    for index, track in enumerate(popular_tracks, start=1):
        old_rank = prev_rank_map.get(track['id'])
        if old_rank is None:
            trend = 'new'
        elif old_rank > index:
            trend = 'up'
        elif old_rank < index:
            trend = 'down'
        else:
            trend = 'same'

        track['rank'] = index
        track['trend'] = trend

        # Save new rank snapshot only for real DB songs (positive IDs)
        try:
            if isinstance(track.get('id'), int) and track['id'] > 0:
                conn.execute(
                    'INSERT INTO ranking_history (song_id, rank, captured_at) VALUES (?, ?, ?)',
                    (track['id'], index, now)
                )
        except Exception:
            # Skip any errors for example tracks or missing references
            pass

    conn.commit()
    conn.close()
    return jsonify(popular_tracks)

@app.route('/api/track_play', methods=['POST'])
def track_play():
    data = request.get_json(silent=True) or {}
    song_id = data.get('song_id')
    user_id = session.get('user_id')

    if not song_id:
        return jsonify({'error': 'No song_id provided'}), 400

    conn = get_db_connection()
    conn.execute('INSERT INTO play_history (user_id, song_id) VALUES (?, ?)', (user_id, song_id))
    conn.commit()
    conn.close()

    return jsonify({'message': 'Play recorded'}), 200

@app.route('/api/recently_played', methods=['GET'])
def api_recently_played():
    """
    Returns each unique song once (latest play per user),
    sorted by most recent play time.
    """
    if 'user_id' not in session:
        return jsonify([]), 200

    conn = get_db_connection()
    rows = conn.execute('''
        SELECT 
            song_title AS title,
            artist,
            album_cover AS image,
            audio_file,
            mood,
            MAX(played_at) AS played_at
        FROM recently_played
        WHERE user_id = ?
        GROUP BY song_title, artist
        ORDER BY played_at DESC
        LIMIT 10
    ''', (session['user_id'],)).fetchall()
    conn.close()

    return jsonify([dict(r) for r in rows]), 200

@app.route('/play_song', methods=['POST'])
def play_song():
    """
    Record a play into `recently_played`. If the same song (by audio_file or by title+artist)
    already exists for this user, update its played_at timestamp (move it to top).
    Supports JSON and form-encoded POST.
    """
    if 'user_id' not in session:
        return jsonify({'error': 'Not logged in'}), 401

    # Accept JSON or form-encoded data
    data = {}
    if request.is_json:
        data = request.get_json(silent=True) or {}
    else:
        # handle application/x-www-form-urlencoded or multipart/form-data
        data['song_title'] = request.form.get('song_title') or request.form.get('title') or ''
        data['artist'] = request.form.get('artist') or ''
        data['album_cover'] = request.form.get('album_cover') or request.form.get('image') or ''
        data['audio_file'] = request.form.get('audio_file') or request.form.get('audio') or ''
        data['mood'] = request.form.get('mood') or ''

    song_title = (data.get('song_title') or data.get('title') or '').strip()
    artist = (data.get('artist') or '').strip()
    album_cover = (data.get('album_cover') or data.get('image') or '').strip()
    audio_file = (data.get('audio_file') or data.get('audio_file') or '').strip()
    mood = normalize_mood(data.get('mood'))

    user_id = session['user_id']
    conn = get_db_connection()
    cur = conn.cursor()

    try:
        # Try to find an existing row for this user and this song.
        # Primary match: audio_file if available, fallback to (song_title + artist)
        existing = None
        if audio_file:
            existing = cur.execute('''
                SELECT id FROM recently_played
                WHERE user_id = ? AND audio_file = ?
                ''', (user_id, audio_file)).fetchone()

        if not existing and song_title and artist:
            existing = cur.execute('''
                SELECT id FROM recently_played
                WHERE user_id = ? AND song_title = ? AND artist = ?
                ''', (user_id, song_title, artist)).fetchone()

        if existing:
            # Update timestamp and optionally album_cover/mood if incoming data exists
            rp_id = existing['id']
            cur.execute('''
                UPDATE recently_played
                SET played_at = CURRENT_TIMESTAMP,
                    album_cover = COALESCE(NULLIF(?,''), album_cover),
                    mood = COALESCE(NULLIF(?,''), mood)
                WHERE id = ?
            ''', (album_cover, mood, rp_id))
        else:
            # Insert a new row
            cur.execute('''
                INSERT INTO recently_played (user_id, song_title, artist, album_cover, audio_file, mood, played_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            ''', (user_id, song_title or 'Unknown', artist or 'Unknown', album_cover or 'defultimage.jpg', audio_file or '', mood))
            # Optional: also increment songs.play_count and play_history
            # If audio_file corresponds to songs.audio_file, we could update play_count / play_history elsewhere.

        conn.commit()

        # Optional: keep only the most recent 100 records per user to avoid db growth
        try:
            cur.execute('''
                DELETE FROM recently_played
                WHERE user_id = ?
                AND id NOT IN (
                    SELECT id FROM recently_played
                    WHERE user_id = ?
                    ORDER BY played_at DESC
                    LIMIT 100
                )
            ''', (user_id, user_id))
            conn.commit()
        except Exception:
            pass

        return jsonify({'message': 'Recorded recently played'}), 200

    except IntegrityError as e:
        conn.rollback()
        return jsonify({'error': 'DB integrity error', 'detail': str(e)}), 500
    except Exception as e:
        conn.rollback()
        return jsonify({'error': 'Server error', 'detail': str(e)}), 500
    finally:
        conn.close()

# ==================== RUN ====================
if __name__ == '__main__':
    init_db()
    app.run(debug=True)
