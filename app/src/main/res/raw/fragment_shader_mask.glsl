#extension GL_OES_EGL_image_external : require
precision mediump float;

uniform samplerExternalOES uTexSampler;
uniform vec4 uMaskRect;
uniform float uFeather;
uniform float uMaskType;
uniform float uInvert;

varying vec2 vTexSamplingCoord;

float rectAlpha(vec2 uv) {
    vec2 minP = uMaskRect.xy;
    vec2 maxP = uMaskRect.xy + uMaskRect.zw;
    float feather = max(uFeather, 0.00001);
    float dx = min(uv.x - minP.x, maxP.x - uv.x);
    float dy = min(uv.y - minP.y, maxP.y - uv.y);
    float edge = min(dx, dy);
    return smoothstep(-feather, feather, edge);
}

float circleAlpha(vec2 uv) {
    vec2 center = uMaskRect.xy + uMaskRect.zw * 0.5;
    vec2 radius = uMaskRect.zw * 0.5;
    float r = min(radius.x, radius.y);
    float d = distance(uv, center);
    return 1.0 - smoothstep(r - uFeather, r + uFeather, d);
}

float linearAlpha(vec2 uv) {
    float t = clamp((uv.x - uMaskRect.x) / max(uMaskRect.z, 0.0001), 0.0, 1.0);
    return 1.0 - t;
}

float radialAlpha(vec2 uv) {
    vec2 center = uMaskRect.xy + uMaskRect.zw * 0.5;
    float d = distance(uv, center);
    float r = max(min(uMaskRect.z, uMaskRect.w) * 0.5, 0.0001);
    return 1.0 - smoothstep(r - uFeather, r + uFeather, d);
}

void main() {
    vec4 color = texture2D(uTexSampler, vTexSamplingCoord);
    float a;
    if (uMaskType > 0.5 && uMaskType < 1.5) a = circleAlpha(vTexSamplingCoord);
    else if (uMaskType > 1.5 && uMaskType < 2.5) a = linearAlpha(vTexSamplingCoord);
    else if (uMaskType > 2.5) a = radialAlpha(vTexSamplingCoord);
    else a = rectAlpha(vTexSamplingCoord);
    if (uInvert > 0.5) a = 1.0 - a;
    gl_FragColor = vec4(color.rgb, color.a * a);
}
