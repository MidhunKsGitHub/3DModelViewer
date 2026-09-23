# 3D Model Viewer

Single-activity Android app that opens several GLB models at once. Each model is its own window: drag and pinch move the window, **Interact** orbits the camera, **Labels** draws part names from the file.

## 3D library

**Google Filament 1.66** (`filament-android`, `gltfio-android`, `filament-utils-android`).

Filament is a mobile PBR renderer with a real glTF loader, and the engine is something the app owns. That matters here because every window is a separate view that still has to share one GPU context. `UbershaderProvider` compiles one material family instead of a shader per glTF material, so opening a second or third model does not stall on shader compile. Sceneform is deprecated, and a higher-level wrapper such as SceneView would hide the engine and make a shared, quality-stripped setup harder to control.

## Performance

- **One engine for every window.** `SharedFilament` keeps a single `Engine`, asset loader, resource loader, ubershader provider, and indirect light, released only when the last window closes.
- **Cheap view.** MSAA, ambient occlusion, bloom, post-processing, skybox, and shadows are off. The colour buffer is medium quality. One directional light, plus a 1-band spherical-harmonic ambient term instead of a cubemap image-based light.
- **Frames stop when nothing is moving.** Each window is a `TextureView` on a `Choreographer` loop. After load (or after the finger lifts) it draws a few more frames and then freezes. The last image stays on screen, so idle windows cost almost nothing.
- **Rate depends on how many windows are open and which one is in front.** While orbiting: about 30 / 25 / 20 fps for 1 / 2 / 4+ windows. The front window at rest is slower (about 25 / 20 / 15). Windows behind that tick at about 10 fps only while they are still settling.
- **One GLB decodes at a time.** `GlbLoadGate` serialises loads. Bytes are read off the UI thread, handed to Filament asynchronously, and the source buffer is released as soon as the load starts. Closing a window evicts that asset.
- **Labels are a 2D overlay**, not extra scene entities. Positions are projected from the camera matrices into a normal Android view.

## Trade-offs

Image quality is the main one. Edges alias, there are no contact shadows or reflections, and metal/glass reads flatter than a full IBL setup. `TextureView` also copies each frame so windows can stack and overlap; a single `SurfaceView` would be cheaper and could not composite this way. Capping frame rate and freezing background windows keeps five models usable, and it also means a background model does not keep animating. Serialising loads avoids a memory spike and makes the second model appear later. The ubershader path loads faster and approximates some glTF materials.

## With more time

I would profile with Perfetto and scale each window’s render resolution to its on-screen size, dropping resolution while a window is being dragged. I would play glTF animations on the front window only, add a per-window loading state (failures are currently silent), and stop label boxes from drawing on top of each other. A real environment map, toggled only for the front window, would bring back reflections without paying for it on every card. I would also cap how many windows can be open and say so in the UI.

## Known limitations

- A model that fails to load leaves an empty window. Import errors from the file picker are shown; in-window load errors are not.
- Labels come only from node `extras.prop`. If the node name is missing, the fallback position adds parent translations and ignores rotation and scale, so the tag can sit in the wrong place.
- A `.gltf` that references external `.bin` or texture files will not load. The picker copies one file.
- No skeletal or keyframe animation.
- There is no hard limit on window count. Fiagena and Solar System are the heavy bundled files; several of them at once will still hitch on a 2 GB device.
- The release APK is signed with the local debug keystore so it can be installed directly.

## Tested on

Emulators only, both arm64 with 2 GB RAM:

- **Pixel** AVD, Android 16 (API 37), Play Store image — main pass: bundled models, two windows, orbit, labels, import.
- **Medium Phone** AVD, Android 16 (API 36), Play Store image — low-RAM pass for the same flows.

I have not tested on a physical device.
