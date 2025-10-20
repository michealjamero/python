import sqlite3
from flask import Flask, render_template, request, redirect, url_for, session, flash, jsonify
from werkzeug.security import generate_password_hash, check_password_hash
from datetime import datetime, timedelta
import os

app = Flask(__name__)
app.secret_key = 'supersecretkey'  # ⚠️ Change this in production!


# ------------- DATABASE SETUP -----------------
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
                        password TEXT NOT NULL
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
    # Recently played table
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
    conn.commit()
    conn.close()


# ------------- ROUTES -----------------

@app.route('/')
def home():
    # Get recently played tracks for home page
    conn = get_db_connection()
    recently_played = []
    
    # Check if the recently_played table has the mood column
    cursor = conn.cursor()
    cursor.execute("PRAGMA table_info(recently_played)")
    columns = [column[1] for column in cursor.fetchall()]
    
    # Add mood column if it doesn't exist
    if 'mood' not in columns:
        conn.execute("ALTER TABLE recently_played ADD COLUMN mood TEXT DEFAULT 'Mixed'")
        conn.commit()
    
    if 'user_id' in session:
        recently_played = conn.execute(
            "SELECT * FROM recently_played WHERE user_id=? ORDER BY played_at DESC LIMIT 3",
            (session['user_id'],)
        ).fetchall()
    else:
        # Sample data for non-logged in users
        recently_played = [
            {'song_title': 'Daisy', 'artist': 'Ashnikko', 'album_cover': 'Ashnikko - Daisy.jpg', 'audio_file': 'youtube_6i01tOMgBDU_audio.mp3'},
            {'song_title': 'Hot To Go', 'artist': 'Chappel Roan', 'album_cover': 'Chappel Roan - Hot To Go.jpg', 'audio_file': 'youtube_1mbbr-cJsrk_audio.mp3'},
            {'song_title': 'APT', 'artist': 'ROSE & Bruno Mars', 'album_cover': 'ROSE & Bruno Mars - APT.jpg', 'audio_file': 'youtube_8Ebqe2Dbzls_audio.mp3'}
        ]
    
    conn.close()
    return render_template('home.html', recently_played=recently_played)


@app.route('/library')
def library():
    return render_template('library.html')


@app.route('/about')
def about():
    return render_template('about.html')


# ---------- PROFILE ----------
@app.route('/profile', methods=['GET', 'POST'])
def profile():
    if 'user_id' not in session:
        flash('Please log in to access your profile.', 'warning')
        return redirect(url_for('signin'))

    conn = get_db_connection()
    
    # Check if the recently_played table has the mood column
    cursor = conn.cursor()
    cursor.execute("PRAGMA table_info(recently_played)")
    columns = [column[1] for column in cursor.fetchall()]
    
    # Add mood column if it doesn't exist
    if 'mood' not in columns:
        conn.execute("ALTER TABLE recently_played ADD COLUMN mood TEXT DEFAULT 'Mixed'")
        conn.commit()

    # Fetch user profile
    profile = conn.execute(
        "SELECT * FROM profiles WHERE user_id = ?", (session['user_id'],)
    ).fetchone()
    if not profile:
        conn.execute("INSERT INTO profiles (user_id) VALUES (?)", (session['user_id'],))
        conn.commit()
        profile = conn.execute(
            "SELECT * FROM profiles WHERE user_id = ?", (session['user_id'],)
        ).fetchone()

    # Fetch recently played songs (last 10)
    recently_played = conn.execute(
        "SELECT * FROM recently_played WHERE user_id=? ORDER BY played_at DESC LIMIT 10",
        (session['user_id'],)
    ).fetchall()
    
    # Convert to list of dictionaries for template rendering
    recently_played_list = []
    for track in recently_played:
        track_dict = dict(track)
        # Ensure album cover has the correct path format
        if not track_dict['album_cover'].startswith('images/'):
            track_dict['album_cover'] = 'images/' + track_dict['album_cover']
        recently_played_list.append(track_dict)
    
    # Replace the original with our modified list
    recently_played = recently_played_list
    
    # Get mood data for the past 8 days
    mood_data = []
    for i in range(8, 0, -1):
        day = datetime.now() - timedelta(days=i-1)
        day_str = day.strftime('%b %d')
        
        # Get mood counts for this day
        mood_counts = conn.execute('''
            SELECT mood, COUNT(*) as count 
            FROM recently_played 
            WHERE user_id=? AND date(played_at) = date(?)
            GROUP BY mood
        ''', (session['user_id'], day.strftime('%Y-%m-%d'))).fetchall()
        
        # Convert to dictionary
        mood_dict = {'date': day_str, 'Happy': 0, 'Sad': 0, 'Relaxed': 0, 'Angry': 0, 'Love': 0}
        total_count = 0
        
        for mood in mood_counts:
            if mood['mood'] in mood_dict:
                mood_dict[mood['mood']] = mood['count']
                total_count += mood['count']
        
        # If no data for this day, add sample data
        if total_count == 0:
            # Random sample data for demonstration
            import random
            moods = ['Happy', 'Sad', 'Relaxed', 'Angry', 'Love']
            for mood in moods:
                mood_dict[mood] = random.randint(0, 5)
        
        mood_data.append(mood_dict)

    if request.method == 'POST':
        # Update profile
        bio = request.form['bio']
        instagram = request.form['instagram']
        twitter = request.form['twitter']
        spotify = request.form['spotify']
        genres = request.form['genres']

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
    return render_template('profile.html', profile=profile, recently_played=recently_played,
                           username=session['username'], mood_data=mood_data)


# ---------- SIGN IN ----------
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


# ---------- SIGN UP ----------
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
            flash('⚠️ Username or Email already exists. Try a different one.', 'danger')
        finally:
            conn.close()

    return render_template('signup.html')


# ---------- LOGOUT ----------
@app.route('/logout')
def logout():
    session.clear()
    flash('You have been logged out.', 'info')
    return redirect(url_for('signin'))


# ---------- ADD RECENTLY PLAYED ----------
@app.route('/play_song', methods=['POST'])
def play_song():
    if 'user_id' not in session:
        return jsonify({"error": "Not logged in"}), 403

    song_title = request.form['song_title']
    artist = request.form['artist']
    album_cover = request.form['album_cover']
    audio_file = request.form['audio_file']
    mood = request.form.get('mood', 'Mixed')  # Get mood from request or default to Mixed
    
    # Ensure album_cover has the correct path format
    if not album_cover.startswith('images/'):
        album_cover = 'images/' + album_cover

    conn = get_db_connection()
    conn.execute(
        "INSERT INTO recently_played (user_id, song_title, artist, album_cover, audio_file, mood) VALUES (?, ?, ?, ?, ?, ?)",
        (session['user_id'], song_title, artist, album_cover, audio_file, mood)
    )
    conn.commit()
    conn.close()
    return jsonify({"success": "Song added to recently played"}), 200


# ------------- RUN APP -----------------
if __name__ == '__main__':
    init_db()
    app.run(debug=True)

