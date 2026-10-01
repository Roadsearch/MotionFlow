precision mediump float;
uniform sampler2D uTexSampler;
uniform float uKeyR;
uniform float uKeyG;
uniform float uKeyB;
uniform float uThreshold;
uniform float uSoftness;
varying vec2 vTexSamplingCoord;
void main() {
  vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
  vec3 key = vec3(uKeyR, uKeyG, uKeyB);
  float distanceToKey = distance(src.rgb, key);
  float alpha = smoothstep(uThreshold, uThreshold + uSoftness, distanceToKey);
  gl_FragColor = vec4(src.rgb, src.a * alpha);
}
