from pathlib import Path
from PIL import Image, ImageDraw


SOURCE_LOGO = Path(r"C:\Users\alwin\.gemini\antigravity-ide\brain\7c26ea48-1dca-4a35-b912-8b2269997d7c\scream_logo_source_1785159621673.png")
BASE_RES = Path(r"D:\scream\app\src\main\res")
WEB_DIR = Path(r"D:\scream\web")
PROJECT_LOGO = Path(r"D:\scream\SCREAM-logo.png")

SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}


def load_square_logo() -> Image.Image:
    if not SOURCE_LOGO.exists():
        raise FileNotFoundError(f"Logo source not found: {SOURCE_LOGO}")

    image = Image.open(SOURCE_LOGO).convert("RGBA")
    width, height = image.size
    side = min(width, height)
    left = (width - side) // 2
    top = (height - side) // 2
    return image.crop((left, top, left + side, top + side))


def apply_circle_mask(image: Image.Image) -> Image.Image:
    mask = Image.new("L", image.size, 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, image.size[0], image.size[1]), fill=255)

    rounded = image.copy()
    rounded.putalpha(mask)
    return rounded


def main() -> None:
    source = load_square_logo()

    for folder, size in SIZES.items():
        directory = BASE_RES / folder
        directory.mkdir(parents=True, exist_ok=True)

        launcher = source.resize((size, size), Image.Resampling.LANCZOS)
        launcher.save(directory / "ic_launcher.png")
        apply_circle_mask(launcher).save(directory / "ic_launcher_round.png")

    WEB_DIR.mkdir(parents=True, exist_ok=True)
    source.resize((512, 512), Image.Resampling.LANCZOS).save(WEB_DIR / "logo.png")
    source.resize((1024, 1024), Image.Resampling.LANCZOS).save(PROJECT_LOGO)

    print("SCREAM icons generated successfully.")


if __name__ == "__main__":
    main()
