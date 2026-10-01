// OpenEditVideo V66–V75: reusable source/destination blend kernels.
// This file is intentionally not wired into a one-input GlEffect: destination sampling
// requires a two-input framebuffer compositor.
vec3 oe_normal(vec3 base, vec3 src) { return src; }
vec3 oe_add(vec3 base, vec3 src) { return min(base + src, vec3(1.0)); }
vec3 oe_multiply(vec3 base, vec3 src) { return base * src; }
vec3 oe_screen(vec3 base, vec3 src) { return 1.0 - (1.0 - base) * (1.0 - src); }
vec3 oe_overlay(vec3 base, vec3 src) {
    return mix(2.0 * base * src, 1.0 - 2.0 * (1.0 - base) * (1.0 - src), step(0.5, base));
}
vec3 oe_darken(vec3 base, vec3 src) { return min(base, src); }
vec3 oe_lighten(vec3 base, vec3 src) { return max(base, src); }
