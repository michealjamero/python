import os
import re

def rename_image_files(directory):
    """
    Renames all image files in the given directory by replacing spaces with underscores
    and removing special characters.
    """
    # Check if directory exists
    if not os.path.exists(directory):
        print(f"Directory {directory} does not exist.")
        return
    
    # Get all files in the directory
    files = os.listdir(directory)
    
    # Filter for image files
    image_extensions = ['.jpg', '.jpeg', '.png', '.gif', '.bmp']
    image_files = [f for f in files if os.path.splitext(f)[1].lower() in image_extensions]
    
    # Create images directory if it doesn't exist
    images_dir = os.path.join(directory, 'images')
    if not os.path.exists(images_dir):
        os.makedirs(images_dir)
        print(f"Created directory: {images_dir}")
    
    renamed_count = 0
    
    for filename in image_files:
        # Get file extension
        name, ext = os.path.splitext(filename)
        
        # Replace spaces with underscores
        new_name = name.replace(' ', '_')
        
        # Replace apostrophes with nothing
        new_name = new_name.replace("'", "")
        
        # Replace other special characters with underscores
        new_name = re.sub(r'[^\w\-]', '_', new_name)
        
        # Remove consecutive underscores
        new_name = re.sub(r'_+', '_', new_name)
        
        # Add extension back
        new_filename = new_name + ext
        
        # Source and destination paths
        src_path = os.path.join(directory, filename)
        dst_path = os.path.join(images_dir, new_filename)
        
        # Rename file
        try:
            # Copy to images directory with new name
            with open(src_path, 'rb') as src_file:
                with open(dst_path, 'wb') as dst_file:
                    dst_file.write(src_file.read())
            
            print(f"Copied: {filename} -> images/{new_filename}")
            renamed_count += 1
        except Exception as e:
            print(f"Error copying {filename}: {e}")
    
    print(f"\nSuccessfully copied and renamed {renamed_count} image files to the images directory.")

if __name__ == "__main__":
    # Path to the static directory
    static_dir = r"c:\Users\user\OneDrive\Desktop\PythonProject1\static"
    rename_image_files(static_dir)