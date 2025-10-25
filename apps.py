import sqlite3
from flask import Flask, render_template, request, redirect, url_for, session, flash, jsonify
from werkzeug.security import generate_password_hash, check_password_hash
from datetime import datetime, timedelta
import os
import random
import json
from pathlib import Path

app = Flask(__name__)
app.secret_key = 'supersecretkey'  # ⚠️ Change this in production!

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
    
    # Check if is_admin column exists in users table, add if not
    cursor = conn.cursor()
    cursor.execute("PRAGMA table_info(users)")
    columns = [column[1] for column in cursor.fetchall()]
    if 'is_admin' not in columns:
        conn.execute("ALTER TABLE users ADD COLUMN is_admin INTEGER DEFAULT 0")
        print("Added is_admin column to users table")
    
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
def home():
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("PRAGMA table_info(recently_played)")
    columns = [column[1] for column in cursor.fetchall()]
    if 'mood' not in columns:
        conn.execute("ALTER TABLE recently_played ADD COLUMN mood TEXT DEFAULT 'Mixed'")
        conn.commit()

    if 'user_id' in session:
        recently_played_rows = conn.execute(
            "SELECT * FROM recently_played WHERE user_id=? ORDER BY played_at DESC LIMIT 15",
            (session['user_id'],)
        ).fetchall()
        # Convert sqlite3.Row items to plain dicts so Jinja can use `.get`
        recently_played = [dict(row) for row in recently_played_rows]
    else:
        recently_played = [
            {'song_title': 'Daisy', 'artist': 'Ashnikko', 'album_cover': 'Ashnikko - Daisy.jpg', 'audio_file': 'youtube_6i01tOMgBDU_audio.mp3', 'mood': 'Happy'},
            {'song_title': 'Hot To Go', 'artist': 'Chappel Roan', 'album_cover': 'Chappel Roan - Hot To Go.jpg', 'audio_file': 'youtube_1mbbr-cJsrk_audio.mp3', 'mood': 'Relaxed'},
            {'song_title': 'APT', 'artist': 'ROSE & Bruno Mars', 'album_cover': 'ROSE & Bruno Mars - APT.jpg', 'audio_file': 'youtube_8Ebqe2Dbzls_audio.mp3', 'mood': 'Love'}
        ]

    # map album_cover to numeric filenames
    image_map = get_image_map()
    for t in recently_played:
        cover = t.get('album_cover')
        if cover:
            t['album_cover'] = image_map.get(cover, cover)
    print('[home] RecentlyPlayed covers:', [t.get('album_cover') for t in recently_played])

    conn.close()
    return render_template('home.html', recently_played=recently_played)

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

    # Fetch recently played
    recently_played = conn.execute(
        "SELECT * FROM recently_played WHERE user_id=? ORDER BY played_at DESC LIMIT 15",
        (session['user_id'],)
    ).fetchall()

    # Convert to dictionary list
    recently_played_list = [dict(track) for track in recently_played]

    # map album_cover to numeric filenames
    image_map = get_image_map()
    for t in recently_played_list:
        cover = t.get('album_cover')
        if cover:
            t['album_cover'] = image_map.get(cover, cover)
    print('[profile] RecentlyPlayed covers:', [t.get('album_cover') for t in recently_played_list])

    # Mood tracking for the past 8 days
    mood_data = []
    for i in range(8, 0, -1):
        day = datetime.now() - timedelta(days=i-1)
        day_str = day.strftime('%b %d')

        mood_counts = conn.execute('''
            SELECT mood, COUNT(*) as count
            FROM recently_played
            WHERE user_id=? AND date(played_at) = date(?)
            GROUP BY mood
        ''', (session['user_id'], day.strftime('%Y-%m-%d'))).fetchall()

        mood_dict = {'date': day_str, 'Happy': 0, 'Sad': 0, 'Relaxed': 0, 'Angry': 0, 'Love': 0}
        total_count = 0

        for mood in mood_counts:
            if mood['mood'] in mood_dict:
                mood_dict[mood['mood']] = mood['count']
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
    return render_template('profile.html', profile=profile, recently_played=recently_played_list,
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

@app.route('/play_song', methods=['POST'])
def play_song():
    if 'user_id' not in session:
        return jsonify({"error": "Not logged in"}), 403

    song_title = request.form['song_title']
    artist = request.form['artist']
    album_cover = request.form['album_cover']
    audio_file = request.form['audio_file']
    mood = request.form.get('mood', 'Mixed')

    conn = get_db_connection()
    conn.execute(
        "INSERT INTO recently_played (user_id, song_title, artist, album_cover, audio_file, mood) VALUES (?, ?, ?, ?, ?, ?)",
        (session['user_id'], song_title, artist, album_cover, audio_file, mood)
    )
    conn.commit()
    conn.close()

    return jsonify({"success": "Song added to recently played with mood tracking"}), 200

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
    
    # Delete song
    conn.execute('DELETE FROM songs WHERE id = ?', (song_id,))
    conn.commit()
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

# ==================== RUN ====================
if __name__ == '__main__':
    init_db()
    app.run(debug=True)
