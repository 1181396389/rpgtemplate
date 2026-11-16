#version 150

// 竈 / 竈「開」火焰：周期 fbm 噪声 + 温度色带。
// 顶点色：R = 温度，G = 种子，B = 模式（0 火柱 / 0.5 火球 / 1 冲击环），A = 不透明度。
uniform vec4 ColorModulator;
uniform float GameTime;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

float hash21(vec2 p) {
    p = fract(p * vec2(127.1, 311.7));
    p += dot(p, p + 19.19);
    return fract(p.x * p.y);
}

// x 方向以 per 为周期（圆周无接缝）
float vnoise(vec2 p, float per) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float x0 = mod(i.x, per);
    float x1 = mod(i.x + 1.0, per);
    float a = hash21(vec2(x0, i.y));
    float b = hash21(vec2(x1, i.y));
    float c = hash21(vec2(x0, i.y + 1.0));
    float d = hash21(vec2(x1, i.y + 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p, float per) {
    float v = 0.0;
    float a = 0.5;
    for (int k = 0; k < 5; k++) {
        v += a * vnoise(p, per);
        p *= 2.0;
        per *= 2.0;
        a *= 0.5;
    }
    return v;
}

vec3 ramp(float h, float heat) {
    vec3 c = mix(vec3(0.30, 0.02, 0.00), vec3(1.00, 0.22, 0.02), smoothstep(0.25, 0.60, h));
    c = mix(c, vec3(1.00, 0.62, 0.10), smoothstep(0.60, 0.88, h));
    c = mix(c, vec3(1.00, 0.95, 0.78), smoothstep(0.88, 1.20, h) * (0.4 + 0.6 * heat));
    return c;
}

void main() {
    float t = mod(GameTime * 1200.0, 300.0);
    float heat = vertexColor.r;
    float seed = vertexColor.g * 37.0;
    float mode = vertexColor.b;
    float alpha = vertexColor.a;
    vec2 uv = texCoord0;
    float shape;
    if (mode < 0.25) {
        // 火柱：v=0 底部 → 1 顶部，火舌向上翻滚
        float n1 = fbm(vec2(uv.x * 8.0, uv.y * 4.0 - t * 2.2 + seed), 8.0);
        float n2 = fbm(vec2(uv.x * 16.0 + 3.1, uv.y * 9.0 - t * 3.6 + seed), 16.0);
        shape = (n1 - 0.5) * 1.5 + (n2 - 0.5) * 0.9 + 0.62 - uv.y * 0.30;
    } else if (mode < 0.75) {
        // 火球：缓慢翻腾
        float n1 = fbm(vec2(uv.x * 8.0, uv.y * 5.0 + t * 0.9 + seed), 8.0);
        float n2 = fbm(vec2(uv.x * 16.0 + 5.2, uv.y * 9.0 - t * 1.7 + seed), 16.0);
        shape = (n1 - 0.5) * 1.5 + (n2 - 0.5) * 0.8 + 0.58;
    } else {
        // 冲击环：v=0 内圈 → 1 外圈，外缘最亮
        float n1 = fbm(vec2(uv.x * 32.0, uv.y * 3.0 - t * 1.5 + seed), 32.0);
        shape = n1 * 0.9 + 0.55 - abs(uv.y - 0.7) * 1.4;
    }
    float h = clamp(shape * (0.55 + heat * 0.75), 0.0, 1.6);
    float mask = smoothstep(0.30, 0.75, h);
    vec3 col = ramp(h, heat);
    fragColor = vec4(col, mask * alpha) * ColorModulator;
}
