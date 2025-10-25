import os
import json
import hashlib
from pathlib import Path

IMAGE_EXTS = {'.jpg', '.jpeg', '.png', '.gif', '.webp'}

def numeric_name_for_file(file_path: Path, digits: int = 32) -> str:
    # Deterministic numeric-only name derived from file content
    h = hashlib.md5()
    with open(file_path, 'rb') as f:
        for chunk in iter(lambda: f.read(8192), b''):
            h.update(chunk)
    as_int = int(h.hexdigest(), 16)
    s = str(as_int)
    if len(s) < digits:
        s = s.zfill(digits)
    else:
        s = s[-digits:]
    return s

def main():
    base = Path('c:/Users/user/OneDrive/Desktop/PythonProject1')
    images_dir = base / 'static' / 'images'
    if not images_dir.exists():
        print(f"Images directory not found: {images_dir}")
        return

    mapping_path = images_dir / 'image_map.json'
    mapping = {}

    files = [p for p in images_dir.iterdir() if p.is_file() and p.suffix.lower() in IMAGE_EXTS]
    print(f"Found {len(files)} image files to rename in {images_dir}")

    used_names = {p.name for p in images_dir.iterdir() if p.is_file()}

    for src in files:
        numeric = numeric_name_for_file(src, digits=32)
        ext = src.suffix.lower()
        if ext == '.jpeg':
            ext = '.jpg'
        target_name = f"{numeric}{ext}"

        # Ensure uniqueness even for identical-content duplicates
        if target_name in used_names and src.name != target_name:
            # Append incremental bump digits until unique
            bump_counter = 1
            # Use sha1-derived base for reproducibility
            base_digits = str(int(hashlib.sha1(src.read_bytes()).hexdigest(), 16))
            while True:
                bump = base_digits[-4:] + str(bump_counter)
                candidate = f"{numeric}{bump}{ext}"
                if candidate not in used_names:
                    target_name = candidate
                    break
                bump_counter += 1

        dst = images_dir / target_name

        if src.name != target_name:
            print(f"Renaming: {src.name} -> {target_name}")
            os.rename(src, dst)
            used_names.add(target_name)
            used_names.discard(src.name)
        else:
            print(f"Already numeric: {src.name}")

        mapping[src.name] = target_name

    with open(mapping_path, 'w', encoding='utf-8') as f:
        json.dump(mapping, f, indent=2, ensure_ascii=False)
    print(f"Wrote mapping to {mapping_path}")

if __name__ == '__main__':
    main()