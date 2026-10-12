# art/ — 3D-арт мода (W3X + текстури)

Сюди кладуться **експортовані моделі веж** — те, що SDK бачить як `ART:`-асети
(тека копіюється разом із модом у `RA3ModSDK\Mods\FortressTowers\Art\`, регістр у Windows неважливий).

```
art/
  FT/                              ← перші дві літери імен файлів (конвенція SAGE, див. VERIFY)
    FTPrismSpire.w3x               ← W3DHierarchy FTPrismSpire_SKL + меші + W3DContainer FTPrismSpire
    FTTenguSpireNanocore.w3x       ← … (усього 12: 9 веж + 3 наноядра Імперії)
    FTAllied_panel_white_dif.tga   ← текстури: FT<Набір>_<ім'я>_<dif|nrm|spm>.tga
    FTAllied_panel_white_dif.xml   ← Texture-асет (його підключає сам .w3x через ART:…xml)
```

- **Руками сюди нічого не пишіть** — усе генерує `scripts/build_models.py --blender <blender.exe>`
  (всередині — `scripts/blender_export_w3x.py`). Джерела: `models/towers/*.py` → `models/glb/*.glb`,
  текстури — `models/textures/<set>/*.png`.
- Моделі підключає `data/Art/FortressModels.xml` (`<Include type="all" source="ART:FTPrismSpire.w3x" />`),
  а в Draw-модулі вежі пишеться ім'я контейнера: `<Model Name="FTPrismSpire" />`.
- Текстури — `.tga` (Blender пише TGA). RA3 у грі використовує DDS; `BinaryAssetBuilder` зазвичай
  сам стискає TGA при збиранні. Якщо ні — сконвертуйте в DXT5 `.dds` з тим самим ім'ям
  (texconv / NVIDIA Texture Tools) і виправте `File=` у відповідному `.xml`. `<!-- VERIFY -->`
- VERIFY: чи знаходить SDK файли в підтеці `FT/`. Якщо збирання пише «file not found» для
  `ART:FTPrismSpire.w3x` — перенесіть усе з `art/FT/` просто в `art/`
  (`build_models.py --art FortressTowers/art`).

Детальніше — розділ «3D-моделі» в [README.md](../../README.md).
