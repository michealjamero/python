import os
import shutil
from pathlib import Path
import re

def main():
    # Define paths
    base_dir = Path('.')
    static_dir = base_dir / 'static'
    images_dir = static_dir / 'images'
    
    # Create images directory if it doesn't exist
    os.makedirs(images_dir, exist_ok=True)
    print(f"Ensured directory exists: {images_dir}")
    
    # Get all image files in static directory
    image_extensions = ['.jpg', '.jpeg', '.png', '.gif', '.webp']
    image_files = []
    
    for file in os.listdir(static_dir):
        file_path = static_dir / file
        if file_path.is_file() and any(file.lower().endswith(ext) for ext in image_extensions):
            image_files.append(file)
    
    print(f"Found {len(image_files)} image files in {static_dir}")
    
    # Copy all image files to images directory
    for file in image_files:
        source = static_dir / file
        # Skip if file is already in images directory or is a subdirectory
        if 'images' in str(source) or not source.is_file():
            continue
            
        destination = images_dir / file
        
        # Copy the file if it doesn't exist in the destination
        if not destination.exists():
            try:
                shutil.copy2(source, destination)
                print(f"Copied: {file} to {images_dir}")
            except Exception as e:
                print(f"Error copying {file}: {e}")
    
    # Update templates to use correct image paths
    templates_dir = base_dir / 'templates'
    template_files = ['home.html', 'profile.html', 'Library.html']
    
    for template_file in template_files:
        template_path = templates_dir / template_file
        if not template_path.exists():
            print(f"Template not found: {template_path}")
            continue
            
        try:
            with open(template_path, 'r', encoding='utf-8') as file:
                content = file.read()
                
            # Fix image references in templates
            # Pattern 1: url_for('static', filename='FILENAME') -> url_for('static', filename='images/FILENAME')
            pattern1 = r"url_for\('static', filename='(?!images/)([^']+\.(jpg|jpeg|png|gif|webp))'(?:\))"
            replacement1 = r"url_for('static', filename='images/\1')"
            
            # Apply the replacements
            updated_content = re.sub(pattern1, replacement1, content, flags=re.IGNORECASE)
            
            # Write the updated content back to the file
            if content != updated_content:
                with open(template_path, 'w', encoding='utf-8') as file:
                    file.write(updated_content)
                print(f"Updated image references in {template_file}")
            else:
                print(f"No changes needed in {template_file}")
                
        except Exception as e:
            print(f"Error updating {template_file}: {e}")
    
    print("\nImage path fix completed!")

if __name__ == "__main__":
    main()