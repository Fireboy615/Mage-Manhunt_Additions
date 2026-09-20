# Editing the in-game How to Play pages

Each minigame has a JSON page under:

`src/main/resources/assets/mageadditions/how_to_play/`

The screen supports any number of sections, paragraphs, and optional images.

Example:

```json
{
  "title": "BLTZ 15 — How to Play",
  "sections": [
    {
      "heading": "Looting",
      "body": [
        "First paragraph.",
        "Second paragraph."
      ],
      "image": "mageadditions:textures/gui/how_to_play/loot_example.png",
      "caption": "Optional image caption.",
      "width": 640,
      "height": 360,
      "textureWidth": 640,
      "textureHeight": 360
    }
  ]
}
```

Put PNG files under:

`src/main/resources/assets/mageadditions/textures/gui/how_to_play/`

`width` and `height` are how large the image should appear in the page. `textureWidth` and `textureHeight` are the actual pixel dimensions of the PNG. Images wider than the page are scaled down automatically.
