# Open source engineering choices

This revision uses public AndroidX Media3 APIs for the stable render path and borrows architectural ideas, not source code, from open-source editors.

Useful references reviewed:
- ActionCut: https://github.com/naveenneog/ActionCut
- ClearCut: https://github.com/SysAdminDoc/ClearCut
- WizardCut: https://github.com/BigWizard94/wizardcut
- Coil: https://github.com/coil-kt/coil

OpenEditVideo remains the primary implementation. No third-party source files were copied into this revision.

Important Media3 limitation: VideoCompositorSettings exposes alpha/transform/placement, but true programmable source/destination blend math and general two-input crossfades still require a lower-level compositor strategy. Unsupported modes remain gated rather than silently ignored.
