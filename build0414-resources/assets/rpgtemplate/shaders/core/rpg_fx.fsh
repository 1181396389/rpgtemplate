#version 150

// 万界 UI shader: one program, three modes selected by uMode.x
//   0 = full-window backdrop (sky, aurora, orbit ring, perspective floor, stars, scan band)
//   1 = card surface (SDF chamfer/rounded corners, conic rim, bevel normal, glass sheen,
//       procedural refraction, scanlines, grain, bloom and drop shadow, tonemap)
//   2 = horizontal haze band
//   3 = rim only: perimeter conic sweep plus scanlines, transparent face (replaces Fx.conic/Fx.scanlines)

in vec2 vUv;
in vec4 vColor;

uniform vec2 uSize;     // quad size in local pixels
uniform vec4 uInner;    // card rect inside the quad (x, y, w, h)
uniform vec4 uRad;      // corner radius, chamfer, border width, shadow strength
uniform vec4 uFace;     // face colour (rgb) and alpha (a)
uniform vec4 uAccent;   // primary accent (violet)
uniform vec4 uAccent2;  // secondary accent (cyan)
uniform vec4 uFx;       // scanline strength, conic strength, grain, bloom
uniform vec4 uWave;     // x = time in seconds
uniform vec4 uRipple;   // ripple centre (xy), age in seconds (z), amplitude (w)
uniform vec4 uMode;     // mode (x), global alpha (y), haze centre (z), haze half height (w)

out vec4 fragColor;

const float K_SQ = 0.70710678;
const vec3 VIOLET = vec3(0.416, 0.173, 0.941);
const vec3 CYAN = vec3(0.0, 0.70, 0.85);
const vec3 INK = vec3(0.050, 0.043, 0.118);
const vec3 DEEP = vec3(0.047, 0.036, 0.155);
const vec3 MID = vec3(0.155, 0.090, 0.390);
const vec3 VIOLET2 = vec3(0.560, 0.300, 0.960);
const vec3 MAGENTA = vec3(0.960, 0.340, 0.720);
const vec3 CYAN2 = vec3(0.110, 0.820, 0.960);

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        v += a * vnoise(p);
        p = p * 2.03 + vec2(7.1, 3.7);
        a *= 0.5;
    }
    return v;
}

// rounded box with the top-right and bottom-left corners chamfered by c pixels
float sdCard(vec2 p, vec2 b, float r, float c) {
    vec2 q = abs(p) - b + vec2(r);
    float box = min(max(q.x, q.y), 0.0) + length(max(q, vec2(0.0))) - r;
    float k = (b.x + b.y) * K_SQ - c;
    float dtr = dot(p, vec2(K_SQ, -K_SQ)) - k;
    float dbl = dot(p, vec2(-K_SQ, K_SQ)) - k;
    return max(box, max(dtr, dbl));
}

vec3 toneMap(vec3 c) {
    vec3 a = clamp((c * (2.51 * c + 0.03)) / (c * (2.43 * c + 0.59) + 0.14), 0.0, 1.0);
    return mix(c, a, 0.35);
}

float starLayer(vec2 uv, float seed, float t) {
    vec2 cell = floor(uv);
    vec2 f = fract(uv);
    float h = hash21(cell + seed);
    if (h < 0.60) {
        return 0.0;
    }
    vec2 pos = vec2(hash21(cell + seed + 3.1), hash21(cell + seed + 7.7));
    float d = length(f - pos);
    float tw = 0.55 + 0.45 * sin(t * (1.0 + h * 2.0) + h * 40.0);
    return smoothstep(0.075, 0.0, d) * tw;
}

vec3 backdrop(vec2 p) {
    float t = uWave.x;
    vec2 size = uSize;

    // ripple: a click or tab change sends a ring that warps the starfield
    vec2 dv = p - uRipple.xy;
    float r = length(dv);
    float ring = r - uRipple.z * 520.0;
    float amp = uRipple.w * exp(-abs(ring) * 0.02);
    p += normalize(dv + vec2(1e-4)) * sin(ring * 0.08) * amp * 12.0;
    vec2 uv = p / size;

    // deep space gradient, lifted toward the upper-left
    vec3 col = mix(MID, DEEP, smoothstep(0.0, 1.0, uv.y * 0.9 + uv.x * 0.25));

    // nebula: three drifting fbm clouds in violet, magenta and cyan
    vec2 np = p * 0.0042;
    float n1 = fbm(np + vec2(t * 0.010, -t * 0.006));
    float n2 = fbm(np * 1.9 + vec2(-t * 0.016, t * 0.009) + 5.2);
    float n3 = fbm(np * 2.6 + vec2(9.1 + t * 0.008, 0.0));
    float cloud = smoothstep(0.40, 0.85, n1);
    col = mix(col, VIOLET2, cloud * 0.55);
    col = mix(col, MAGENTA, smoothstep(0.52, 0.92, n2) * cloud * 0.45);
    col = mix(col, CYAN2, smoothstep(0.55, 0.92, n3) * 0.28);

    // parallax starfield: three layers at different speeds, nearer layers brighter
    vec3 stars = vec3(0.0);
    stars += starLayer(vec2((p.x + t * 5.0) / 64.0, p.y / 64.0), 1.0, t) * vec3(0.90, 0.95, 1.00) * 0.55;
    stars += starLayer(vec2((p.x + t * 12.0) / 38.0, p.y / 38.0), 17.0, t) * vec3(0.80, 0.95, 1.00) * 0.75;
    stars += starLayer(vec2((p.x + t * 24.0) / 20.0, p.y / 20.0), 41.0, t) * vec3(1.00, 1.00, 1.00) * 0.95;
    col += stars * 1.35;

    // dimensional ring: additive glow, cyan nodes every 5 degrees, depth sets brightness
    float rcy = size.y * 0.56;
    float rx = min(size.x, size.y) * 0.5;
    float ry = rx * 0.26;
    vec2 e = vec2((p.x - size.x * 0.5) / rx, (p.y - rcy) / ry);
    float ed = length(e);
    float ang = atan(e.y, e.x);
    float band = exp(-abs(ed - 1.0) * 40.0);
    float cell = fract(ang * (72.0 / 6.2831853) + t * 0.10);
    float node = 1.0 - smoothstep(0.08, 0.18, abs(cell - 0.5));
    float depth = 0.5 + 0.5 * sin(ang);
    col += VIOLET2 * band * 0.24 * (0.4 + depth);
    col += CYAN2 * node * band * 0.85 * (0.3 + depth);

    // planet in the lower right: banded clouds, lit from the upper left, cyan atmosphere rim
    vec2 pc = vec2(size.x * 0.86, size.y * 1.10);
    float pr = min(size.x, size.y) * 0.46;
    float pd = length(p - pc);
    col += CYAN2 * exp(-max(pd - pr, 0.0) * 0.030) * 0.16;
    if (pd < pr) {
        vec2 q = (p - pc) / pr;
        float z = sqrt(max(1.0 - dot(q, q), 0.0));
        float bands = sin(q.y * 14.0 + fbm(q * 2.0 + vec2(t * 0.02, 0.0)) * 3.0 + t * 0.04);
        vec3 pl = mix(vec3(0.24, 0.11, 0.52), vec3(0.10, 0.44, 0.66), bands * 0.5 + 0.5);
        float lit = clamp(dot(vec3(q, z), normalize(vec3(-0.40, -0.50, 0.77))), 0.0, 1.0);
        col = pl * (0.35 + 0.75 * lit) + CYAN2 * pow(1.0 - z, 3.0) * 0.45;
    }

    // shooting star: one streak every 13 s, from a random start toward the lower left
    float period = 13.0;
    float k = floor(t / period);
    float local = t - k * period;
    if (local < 1.1) {
        vec2 start = vec2(size.x * (0.2 + 0.8 * hash21(vec2(k, 3.0))), -20.0 + size.y * 0.35 * hash21(vec2(k, 7.0)));
        vec2 dir = normalize(vec2(-1.0, 0.55));
        vec2 head = start + dir * local * 760.0;
        vec2 a = head - dir * 150.0;
        vec2 ba = head - a;
        float h = clamp(dot(p - a, ba) / dot(ba, ba), 0.0, 1.0);
        float dd = length(p - a - ba * h);
        col += vec3(0.90, 0.95, 1.00) * pow(h, 3.0) * exp(-dd * 0.9) * (1.0 - local / 1.1) * 1.2;
    }

    // slow scan band and vignette
    float scanY = mod(t * 26.0, size.y + 40.0) - 20.0;
    col += CYAN2 * exp(-abs(p.y - scanY) * 0.6) * 0.05;
    float vig = smoothstep(0.0, 0.16, min(uv.x, 1.0 - uv.x)) * smoothstep(0.0, 0.16, min(uv.y, 1.0 - uv.y));
    col *= mix(0.70, 1.0, vig);
    return toneMap(col);
}

vec4 surface(vec2 p) {
    float t = uWave.x;
    vec2 b = uInner.zw * 0.5;
    vec2 q = p - (uInner.xy + b);
    float rad = uRad.x;
    float ch = uRad.y;
    float bw = max(uRad.z, 0.5);

    float d = sdCard(q, b, rad, ch);
    float cov = 1.0 - smoothstep(-0.7, 0.7, d);

    // bevel normal from the SDF gradient, lit from the upper left
    vec2 gr = vec2(sdCard(q + vec2(1.0, 0.0), b, rad, ch) - d,
                   sdCard(q + vec2(0.0, 1.0), b, rad, ch) - d);
    float bevel = 1.0 - smoothstep(0.0, bw * 3.0, -d);
    vec3 n = normalize(vec3(-gr * 2.0 * bevel, 1.0));
    float lam = dot(n, normalize(vec3(-0.45, -0.6, 0.66)));

    // conic rim: a bright head sweeps around the card with a fading tail
    float ang = atan(q.y, q.x);
    float frac = fract(ang / 6.2831853 + 0.5);
    float behind = fract(fract(t * 0.22) - frac);
    float tail = pow(max(0.0, 1.0 - behind / 0.28), 2.0);
    float edge = (1.0 - smoothstep(0.0, bw, -d)) * cov;
    vec3 conicCol = mix(uAccent.rgb, uAccent2.rgb, frac);

    // glass: top sheen, diagonal reflection band, procedural refraction and frost
    float sheen = (0.5 - (p.y - uInner.y) / max(uInner.w, 1.0)) * 0.10;
    float diag = exp(-abs((q.x - q.y) * K_SQ - sin(t * 0.35) * b.x * 0.9) * 0.035) * 0.06;
    vec2 rf = gr * bevel * 6.0;
    float pat = step(0.5, fract((q.x + rf.x * 3.0 + q.y + rf.y * 3.0) * 0.045)) * 0.035;
    float frost = (fbm(q * 0.035 + vec2(t * 0.02, 0.0)) - 0.5) * 0.08;

    vec3 col = uFace.rgb + vec3(sheen + diag + frost + pat);

    // haze band across the card (used by the header)
    if (uMode.w > 0.0) {
        float yf = (p.y - uInner.y) / max(uInner.w, 1.0);
        float zz = (yf - uMode.z) / uMode.w;
        float band2 = exp(-zz * zz);
        float wv = 0.5 + 0.5 * sin((p.x - uInner.x) * 0.045 + t * 0.9 + yf * 4.0);
        col = mix(col, CYAN, band2 * wv * 0.12);
    }

    col = mix(col, uAccent.rgb, edge * 0.42);
    col += conicCol * tail * uFx.y * edge * 1.1;
    col += vec3(max(lam - 0.62, 0.0) * 0.9 * bevel);
    col -= vec3(max(0.62 - lam, 0.0) * 0.10 * bevel);

    // scanlines every 3 px and fine grain that re-rolls at 24 Hz
    float sc = smoothstep(0.55, 1.0, 0.5 + 0.5 * sin((p.y - t * 6.0) * 2.0944));
    col = mix(col, uAccent2.rgb, sc * uFx.x * cov);
    float gn = hash21(floor(p) + floor(t * 24.0) * vec2(13.1, 7.7)) - 0.5;
    col += vec3(gn * uFx.z);
    col = toneMap(col);

    // bloom outside the edge, violet drop shadow below
    float outside = max(d, 0.0);
    float glowA = exp(-outside * 0.12) * uFx.w;
    float ds = sdCard(q - vec2(0.0, 3.0), b, rad, ch);
    float shadowA = exp(-max(ds, 0.0) * 0.22) * uRad.w;
    float aOut = clamp(glowA + shadowA, 0.0, 1.0) * (1.0 - cov);
    vec3 cOut = (uAccent.rgb * glowA + vec3(0.20, 0.12, 0.42) * shadowA) / max(glowA + shadowA, 1e-4);
    float aIn = uFace.a * cov;
    float aFinal = aIn + aOut;
    vec3 cFinal = (col * aIn + cOut * aOut) / max(aFinal, 1e-4);
    return vec4(cFinal, aFinal);
}

vec4 haze(vec2 p) {
    float zz = (vUv.y - uMode.z) / max(uMode.w, 0.001);
    float band = exp(-zz * zz);
    float wv = 0.5 + 0.5 * sin(p.x * 0.045 + uWave.x * 0.9 + vUv.y * 4.0);
    return vec4(uAccent.rgb, band * wv * uFace.a);
}

vec4 rim(vec2 p) {
    float t = uWave.x;
    vec2 b = uInner.zw * 0.5;
    vec2 q = p - (uInner.xy + b);
    float bw = max(uRad.z, 0.5);
    float dd = -max(abs(q.x) - b.x, abs(q.y) - b.y);
    float edge = 1.0 - smoothstep(0.0, bw, dd);
    float inside = smoothstep(-0.5, 0.5, dd);

    // perimeter position, clockwise from the top-left corner, normalised to 0..1
    float dt = abs(q.y + b.y);
    float dr = abs(q.x - b.x);
    float db = abs(b.y - q.y);
    float dl = abs(q.x + b.x);
    float m = min(min(dt, dr), min(db, dl));
    float s;
    if (m == dt) {
        s = q.x + b.x;
    } else if (m == dr) {
        s = 2.0 * b.x + (q.y + b.y);
    } else if (m == db) {
        s = 2.0 * b.x + 2.0 * b.y + (b.x - q.x);
    } else {
        s = 4.0 * b.x + 2.0 * b.y + (b.y - q.y);
    }
    float frac = s / max(4.0 * (b.x + b.y), 1.0);

    float behind = fract(fract(t * 0.22) - frac);
    float tail = pow(max(0.0, 1.0 - behind / 0.22), 2.0);
    vec3 cc = mix(uAccent.rgb, uAccent2.rgb, frac);
    float aRim = clamp(edge * tail * uFx.y, 0.0, 1.0);

    float sc = smoothstep(0.6, 1.0, 0.5 + 0.5 * sin((p.y - t * 6.0) * 2.0944));
    float aSc = clamp(sc * uFx.x * inside, 0.0, 1.0);

    float a = clamp(aRim + aSc, 0.0, 1.0);
    vec3 c = (cc * aRim + uAccent2.rgb * aSc) / max(a, 1e-4);
    return vec4(c, a);
}

void main() {
    vec2 p = vUv * uSize;
    vec4 o;
    if (uMode.x < 0.5) {
        o = vec4(backdrop(p), 1.0);
    } else if (uMode.x < 1.5) {
        o = surface(p);
    } else if (uMode.x < 2.5) {
        o = haze(p);
    } else {
        o = rim(p);
    }
    fragColor = vec4(o.rgb, o.a * uMode.y) * vColor;
}
