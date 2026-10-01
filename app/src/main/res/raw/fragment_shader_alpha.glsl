precision mediump float;
uniform sampler2D uTexSampler;
uniform float uAlpha;
varying vec2 vTexSamplingCoord;
void main() {
  vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
  gl_FragColor = vec4(src.rgb, src.a * uAlpha);
}
