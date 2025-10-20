import os
import shutil
import re
from pathlib import Path

def main():
    # Define paths
    static_dir = Path('static')
    images_dir = static_dir / 'images'
    
    # Create images directory if it doesn't exist
    if not images_dir.exists():
        os.makedirs(images_dir, exist_ok=True)
        print(f"Created directory: {images_dir}")
    
    # Get all image files in static directory
    image_extensions = ['.jpg', '.jpeg', '.png', '.gif', '.webp']
    image_files = []
    
    for file in os.listdir(static_dir):
        file_path = static_dir / file
        if file_path.is_file() and file_path.suffix.lower() in image_extensions:
            image_files.append(file)
    
    print(f"Found {len(image_files)} image files in {static_dir}")
    
    # Check if "Icona Pop - All Night" exists and where
    icona_pop_file = None
    for file in image_files:
        if "Icona Pop - All Night" in file:
            icona_pop_file = file
            print(f"Found working image: {file}")
            break
    
    # Move all image files to images directory
    moved_files = []
    for file in image_files:
        source = static_dir / file
        destination = images_dir / file
        
        # Skip if file is already in images directory
        if source.parent == images_dir:
            continue
            
        # Move the file
        try:
            shutil.copy2(source, destination)
            moved_files.append(file)
            print(f"Copied: {file} to {images_dir}")
        except Exception as e:
            print(f"Error copying {file}: {e}")
    
    print(f"Copied {len(moved_files)} image files to {images_dir}")
    
    # List all files in the images directory
    print("\nFiles in images directory:")
    for file in os.listdir(images_dir):
        print(f"- {file}")

if __name__ == "__main__":
    main()