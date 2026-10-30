# PageCurl

Source: https://github.com/oleksandrbalan/pagecurl/tree/v1.5.1

Historical dependency in 0.4.x: `io.github.oleksandrbalan:pagecurl:1.5.1`.
Removed from the current 0.5.0 dependency graph; replaced by original harism CurlMesh / CurlPage.
The files below are retained as the provenance of earlier shipped APKs, not current rendering claims.
Published sources were inspected against the API used by this app. No upstream source is copied or patched.
Apache 2.0 license is retained here and packaged at `assets/licenses/pagecurl.txt`.

This Compose renderer folds, clips and mirrors actual page composables, with a tinted reverse and shadow.
It is not a cylindrical OpenGL mesh. The app independently supplies a perspective hinged cover, spine,
page-block thickness, page selection, shared paper expansion/collapse and navigation exit sequencing.
All paper contents come from the same Room-backed models used by the editor. Neighboring page semantics
are hidden from accessibility while their visual content remains available during a fold.
Motion-disabled mode uses immediate cover/paper poses and explicit previous/next controls.
Low graphics quality disables the page-fold shadow; all glass controls still use the shared Backdrop engine.

The higher-star `harism/android-pagecurl` was evaluated; its OpenGL ES View/bitmap pipeline would require
a separate texture snapshot and rendering bridge for live Compose content. PageCurl is used here to keep
editing and page rendering synchronized within Compose. A true curved mesh can replace the fold renderer later.
